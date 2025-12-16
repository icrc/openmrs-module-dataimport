/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.web.controller;

import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.model.ImportAuditLog;
import org.openmrs.module.dataimport.api.service.ImportAuditService;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for import audit logs. Provides endpoints to query import history and trigger
 * cleanup.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/dataimport/audit")
public class ImportAuditController extends BaseRestController {
	
	/**
	 * Gets paginated audit logs with optional filters.
	 *
	 * @param status optional status filter (SUCCESS, PARTIAL_SUCCESS, FAILED, VALIDATION_ERRORS)
	 * @param action optional action filter (DRY_RUN, COMMIT)
	 * @param userId optional creator user ID filter
	 * @param startDate optional start date filter
	 * @param endDate optional end date filter
	 * @param startIndex starting index for pagination (alternative to page)
	 * @param maxResults maximum results per page (alternative to pageSize)
	 * @return paginated list of audit logs
	 */
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> getAuditLogs(@RequestParam(required = false) String status,
	        @RequestParam(required = false) String action, @RequestParam(required = false) Integer userId,
	        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
	        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
	        @RequestParam(required = false) Integer startIndex, @RequestParam(required = false) Integer maxResults) {
		
		ImportAuditService service = getAuditService();
		
		// Default pagination
		int actualStartIndex = startIndex != null ? startIndex : 0;
		int actualMaxResults = maxResults != null ? maxResults : 20;
		
		ImportAuditLog.Status statusEnum = status != null ? ImportAuditLog.Status.valueOf(status) : null;
		ImportAuditLog.Action actionEnum = action != null ? ImportAuditLog.Action.valueOf(action) : null;
		
		// Convert startIndex to page for service call
		int page = actualStartIndex / actualMaxResults;
		
		List<ImportAuditLog> logs = service.getAuditLogs(statusEnum, actionEnum, userId, startDate, endDate, page,
		    actualMaxResults);
		long totalCount = service.countAuditLogs(statusEnum, actionEnum, userId, startDate, endDate);
		
		Map<String, Object> response = new HashMap<>();
		response.put("results", logs.stream().map(this::toDto).collect(Collectors.toList()));
		response.put("totalCount", totalCount);
		response.put("startIndex", actualStartIndex);
		response.put("maxResults", actualMaxResults);
		response.put("totalPages", (int) Math.ceil((double) totalCount / actualMaxResults));
		
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Gets recent audit logs (shortcut for dashboard).
	 *
	 * @param limit maximum number of results (default 10)
	 * @return list of recent audit logs
	 */
	@GetMapping(value = "/recent", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<Map<String, Object>>> getRecentAuditLogs(@RequestParam(defaultValue = "10") int limit) {
		
		ImportAuditService service = getAuditService();
		List<ImportAuditLog> logs = service.getRecentAuditLogs(limit);
		
		List<Map<String, Object>> results = logs.stream().map(this::toDto).collect(Collectors.toList());
		
		return ResponseEntity.ok(results);
	}
	
	/**
	 * Gets a specific audit log by UUID.
	 *
	 * @param uuid the UUID of the audit log
	 * @return the audit log details
	 */
	@GetMapping(value = "/{uuid}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> getAuditLogByUuid(@PathVariable String uuid) {
		ImportAuditService service = getAuditService();
		ImportAuditLog log = service.getByUuid(uuid);
		
		if (log == null) {
			return ResponseEntity.notFound().build();
		}
		
		return ResponseEntity.ok(toDetailedDto(log));
	}
	
	/**
	 * Gets all audit logs for a specific batch ID.
	 *
	 * @param batchId the batch ID
	 * @return list of audit logs for the batch
	 */
	@GetMapping(value = "/batch/{batchId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<List<Map<String, Object>>> getAuditLogsByBatchId(@PathVariable String batchId) {
		ImportAuditService service = getAuditService();
		List<ImportAuditLog> logs = service.getAllByBatchId(batchId);
		
		List<Map<String, Object>> results = logs.stream().map(this::toDetailedDto).collect(Collectors.toList());
		
		return ResponseEntity.ok(results);
	}
	
	/**
	 * Triggers cleanup of old audit logs. Uses the configured retention period from global property.
	 *
	 * @return number of deleted records
	 */
	@PostMapping(value = "/cleanup", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> cleanupOldLogs() {
		ImportAuditService service = getAuditService();
		int retentionDays = service.getRetentionDays();
		int deleted = service.cleanupOldLogs(retentionDays);
		
		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("deletedCount", deleted);
		response.put("retentionDays", retentionDays);
		response.put("message", "Deleted " + deleted + " audit log entries older than " + retentionDays + " days");
		
		return ResponseEntity.ok(response);
	}
	
	/**
	 * Gets the current retention configuration.
	 *
	 * @return retention configuration
	 */
	@GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> getConfig() {
		ImportAuditService service = getAuditService();
		
		Map<String, Object> response = new HashMap<>();
		response.put("retentionDays", service.getRetentionDays());
		
		return ResponseEntity.ok(response);
	}
	
	private ImportAuditService getAuditService() {
		return Context.getRegisteredComponent("importAuditService", ImportAuditService.class);
	}
	
	private Map<String, Object> toDto(ImportAuditLog log) {
		Map<String, Object> dto = new HashMap<>();
		dto.put("uuid", log.getUuid());
		dto.put("batchId", log.getBatchId());
		dto.put("filename", log.getFilename());
		dto.put("status", log.getStatus() != null ? log.getStatus().name() : null);
		dto.put("action", log.getAction() != null ? log.getAction().name() : null);
		dto.put("totalRows", log.getTotalRows());
		dto.put("successfulRows", log.getSuccessfulRows());
		dto.put("quarantinedRows", log.getQuarantinedRows());
		dto.put("patientsCreated", log.getPatientsCreated());
		dto.put("encountersCreated", log.getEncountersCreated());
		dto.put("obsCreated", log.getObsCreated());
		dto.put("errorSummary", log.getErrorSummary());
		dto.put("creator", log.getCreator() != null ? log.getCreator().getUsername() : null);
		dto.put("creatorName",
		    log.getCreator() != null && log.getCreator().getPersonName() != null
		            ? log.getCreator().getPersonName().getFullName()
		            : (log.getCreator() != null ? log.getCreator().getUsername() : null));
		dto.put("creatorUuid", log.getCreator() != null ? log.getCreator().getUuid() : null);
		dto.put("dateCreated", log.getDateCreated());
		dto.put("durationMs", log.getDurationMs());
		return dto;
	}
	
	private Map<String, Object> toDetailedDto(ImportAuditLog log) {
		Map<String, Object> dto = toDto(log);
		dto.put("locationUuid", log.getLocationUuid());
		dto.put("formUuids", log.getFormUuids());
		
		// Include error details
		if (log.getErrors() != null && !log.getErrors().isEmpty()) {
			List<Map<String, Object>> errors = log.getErrors().stream().map(error -> {
				Map<String, Object> errorDto = new HashMap<>();
				errorDto.put("sheetName", error.getSheetName());
				errorDto.put("rowIndex", error.getRowIndex());
				errorDto.put("columnIndex", error.getColumnIndex());
				errorDto.put("errorCode", error.getErrorCode());
				errorDto.put("errorMessage", error.getErrorMessage());
				return errorDto;
			}).collect(Collectors.toList());
			dto.put("errors", errors);
			dto.put("errorCount", log.getErrors().size());
		}
		
		return dto;
	}
}
