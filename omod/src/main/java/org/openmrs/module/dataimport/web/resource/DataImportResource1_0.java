/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.web.resource;

import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.ImportPlan;
import org.openmrs.module.dataimport.api.ImportResult;
import org.openmrs.module.dataimport.api.WorkbookImportService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.Uploadable;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Resource(name = RestConstants.VERSION_1 + "/dataimport", supportedClass = SimpleObject.class, supportedOpenmrsVersions = {
        "2.*" })
@SuppressWarnings("unused")
public class DataImportResource1_0 extends DelegatingCrudResource<SimpleObject> implements Uploadable {
	
	private static final Logger log = LoggerFactory.getLogger(DataImportResource1_0.class);
	
	private WorkbookImportService getService() {
		return Context.getRegisteredComponent("workbookImportService", WorkbookImportService.class);
	}
	
	@Override
	public SimpleObject upload(MultipartFile file, RequestContext context) throws ResponseException {
		if (file == null || file.isEmpty()) {
			return error("No file uploaded");
		}
		
		String filename = file.getOriginalFilename();
		if (filename == null) {
			return error("File name is required");
		}
		
		String lowerFilename = filename.toLowerCase();
		if (!lowerFilename.endsWith(".xlsx") && !lowerFilename.endsWith(".xls")) {
			return error("File must be an Excel workbook (.xlsx) or (.xls)");
		}
		
		try (InputStream inputStream = file.getInputStream()) {
			WorkbookImportService service = getService();
			if (service == null) {
				return error("Service not available");
			}
			
			ImportPlan importPlan = service.dryRun(inputStream);
			if (importPlan == null) {
				return error("Dry run returned null result");
			}
			
			SimpleObject response = new SimpleObject();
			response.put("success", true);
			response.put("batchId", importPlan.getBatchId().toString());
			response.put("stats", importPlan.getStats());
			response.put("okRows", importPlan.getRowsOk());
			response.put("quarantinedRows", importPlan.getIssues());
			response.put("message", "Dry run completed successfully");
			return response;
		}
		catch (Exception e) {
			log.error("Dry run failed for file: {}", filename, e);
			String errorMessage = extractErrorMessage(e);
			return error("Dry run failed: " + errorMessage);
		}
	}
	
	@Override
	public SimpleObject create(SimpleObject post, RequestContext context) throws ResponseException {
		String batchIdStr = context.getParameter("batchId");
		if (batchIdStr == null || batchIdStr.isEmpty()) {
			return error("batchId is required");
		}
		
		try {
			UUID batchId = UUID.fromString(batchIdStr);
			log.info("Starting commit for batch: {}", batchId);
			
			WorkbookImportService service = getService();
			if (service == null) {
				log.error("WorkbookImportService not found. Is the module properly initialized?");
				return error("Service not available");
			}
			
			ImportResult result = service.commit(batchId);
			
			SimpleObject response = new SimpleObject();
			response.put("success", result.isSuccess());
			response.put("message", result.getMessage());
			response.put("batchId", result.getBatchId().toString());
			
			if (result.isSuccess()) {
				response.put("stats", result.getStats());
				response.put("createdEncounterUuids", result.getCreatedEncounterUuids());
				log.info("Commit completed successfully for batch: {}", batchId);
			} else {
				response.put("errors", result.getErrors());
				log.warn("Commit failed for batch: {} - {}", batchId, result.getMessage());
			}
			
			return response;
		}
		catch (IllegalArgumentException e) {
			log.error("Invalid batch ID: {}", batchIdStr, e);
			return error("Invalid batch ID: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Commit failed for batch: {}", batchIdStr, e);
			return error("Commit failed: " + e.getMessage());
		}
	}
	
	@Override
	public SimpleObject newDelegate() {
		return new SimpleObject();
	}
	
	@Override
	public SimpleObject save(SimpleObject delegate) {
		throw new ResourceDoesNotSupportOperationException();
	}
	
	@Override
	public SimpleObject getByUniqueId(String uniqueId) {
		return null;
	}
	
	@Override
	public void delete(SimpleObject delegate, String reason, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException();
	}
	
	@Override
	public void purge(SimpleObject delegate, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException();
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		return new DelegatingResourceDescription();
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		return new DelegatingResourceDescription();
	}
	
	private SimpleObject error(String msg) {
		SimpleObject error = new SimpleObject();
		error.put("success", false);
		error.put("message", msg);
		return error;
	}
	
	private String extractErrorMessage(Throwable e) {
		// Get the root cause message for better error reporting
		Throwable cause = e;
		String message = e.getMessage();
		
		// Walk down the cause chain to find the most specific error
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
			if (cause.getMessage() != null && !cause.getMessage().isEmpty()) {
				// Prefer messages that seem more specific (contain validation details)
				String causeMsg = cause.getMessage();
				if (causeMsg.contains("validation") || causeMsg.contains("missing") || causeMsg.contains("invalid")) {
					message = causeMsg;
					break;
				}
			}
		}
		
		// If we didn't find a better message, use the original or the root cause
		if (message == null || message.isEmpty()) {
			message = cause.getMessage();
		}
		if (message == null || message.isEmpty()) {
			message = e.getClass().getSimpleName();
		}
		
		return message;
	}
}
