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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.service.TemplateGenerationService;
import org.openmrs.module.dataimport.api.service.WorkbookImportService;
import org.openmrs.module.dataimport.api.storage.FormFileStorageService;
import org.openmrs.module.dataimport.model.TemplateOptions;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/dataimport")
@SuppressWarnings("unused")
public class FormTemplateController extends BaseRestController {
	
	private static final Logger log = LoggerFactory.getLogger(FormTemplateController.class);
	
	@Autowired
	private TemplateGenerationService templateGenerationService;
	
	@Autowired
	private FormFileStorageService formFileStorageService;
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	/**
	 * Upload form files (JSON for O3, XML for HFE) for template generation. Supports batch upload of
	 * multiple files.
	 *
	 * @param files array of form files to upload
	 * @return JSON response with file identifiers
	 */
	@PostMapping(value = "/forms/upload", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String, Object>> uploadFormFiles(@RequestParam("files") MultipartFile[] files) {
		if (files == null || files.length == 0) {
			throw new APIException("At least one form file is required");
		}
		
		List<FormFileStorageService.FormFileData> formFiles = new ArrayList<>();
		List<Map<String, String>> fileInfoList = new ArrayList<>();
		
		for (MultipartFile file : files) {
			if (file.isEmpty()) {
				log.warn("Skipping empty file: {}", file.getOriginalFilename());
				continue;
			}
			
			try {
				byte[] content = file.getBytes();
				String filename = file.getOriginalFilename();
				String formType = detectFormType(content);
				
				if (formType == null) {
					throw new APIException(
					        "Unable to detect form type for file: " + filename + ". File must be valid O3 JSON or HFE XML.");
				}
				
				formFiles.add(new FormFileStorageService.FormFileData(filename, content, formType));
				
				Map<String, String> fileInfo = new HashMap<>();
				fileInfo.put("filename", filename);
				fileInfo.put("detectedType", formType);
				fileInfo.put("size", String.valueOf(content.length));
				fileInfoList.add(fileInfo);
				
			}
			catch (Exception e) {
				log.error("Failed to process uploaded file: {}", file.getOriginalFilename(), e);
				throw new APIException("Failed to process file '" + file.getOriginalFilename() + "': " + e.getMessage());
			}
		}
		
		if (formFiles.isEmpty()) {
			throw new APIException("No valid form files uploaded");
		}
		
		try {
			List<String> fileIds = formFileStorageService.storeFiles(formFiles);
			
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("fileCount", fileIds.size());
			response.put("fileIds", fileIds);
			response.put("files", fileInfoList);
			response.put("message",
			    "Successfully uploaded " + fileIds.size() + " form file(s). Use the fileIds in template generation.");
			
			log.info("Successfully uploaded {} form files", fileIds.size());
			
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			log.error("Failed to store uploaded form files", e);
			throw new APIException("Failed to store form files: " + e.getMessage());
		}
	}
	
	@PostMapping(value = "/forms/template.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	public ResponseEntity<byte[]> generateTemplateFromPost(@RequestBody TemplateRequest request) {
		if (request.formUuids == null || request.formUuids.isEmpty()) {
			throw new APIException("At least one formUuid is required");
		}
		
		TemplateOptions options = request.options != null ? request.options : new TemplateOptions();
		
		byte[] bytes = templateGenerationService.generateFromFormUuids(request.formUuids, options);
		
		String filename = options.getFilename();
		if (filename == null || filename.trim().isEmpty()) {
			filename = "o3_form_template.xlsx";
		}
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
		        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
		        .contentLength(bytes.length).body(bytes);
	}
	
	@PostMapping(value = "/quarantine-report.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	public ResponseEntity<byte[]> downloadQuarantineReport(@RequestParam("file") MultipartFile file,
	        @RequestParam("batchId") String batchIdStr) {
		if (file == null || file.isEmpty()) {
			throw new APIException("No file uploaded");
		}
		
		if (batchIdStr == null || batchIdStr.isEmpty()) {
			throw new APIException("batchId parameter is required");
		}
		
		try {
			UUID batchId = UUID.fromString(batchIdStr);
			WorkbookImportService service = Context.getRegisteredComponent("workbookImportService",
			    WorkbookImportService.class);
			
			if (service == null) {
				throw new APIException("WorkbookImportService not available");
			}
			
			try (InputStream inputStream = file.getInputStream()) {
				byte[] reportBytes = service.generateQuarantineReport(inputStream, batchId);
				
				String filename = "quarantine_report_" + batchId.toString() + ".xlsx";
				return ResponseEntity.ok()
				        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				        .contentType(
				            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				        .contentLength(reportBytes.length).body(reportBytes);
			}
		}
		catch (IllegalArgumentException e) {
			throw new APIException("Invalid batchId: " + batchIdStr);
		}
		catch (Exception e) {
			log.error("Failed to generate quarantine report", e);
			throw new APIException("Failed to generate quarantine report: " + e.getMessage());
		}
	}
	
	/**
	 * Auto-detects form type from file content. Returns "O3_JSON" or "HFE_XML" or null if unable to
	 * detect.
	 */
	private String detectFormType(byte[] content) {
		String contentString = new String(content, StandardCharsets.UTF_8).trim();
		
		// Try JSON detection
		if (contentString.startsWith("{") || contentString.startsWith("[")) {
			try {
				JsonNode root = objectMapper.readTree(content);
				// Check for O3 form structure (has "pages" array)
				if (root.has("pages") && root.get("pages").isArray()) {
					return "O3_JSON";
				}
			}
			catch (Exception e) {
				log.debug("Failed to parse as JSON: {}", e.getMessage());
			}
		}
		
		// Try XML detection
		if (contentString.startsWith("<")) {
			try {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				// Security: disable external entities
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
				factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
				factory.setXIncludeAware(false);
				factory.setExpandEntityReferences(false);
				
				DocumentBuilder builder = factory.newDocumentBuilder();
				org.w3c.dom.Document doc = builder.parse(new ByteArrayInputStream(content));
				
				String rootTag = doc.getDocumentElement().getTagName();
				if ("htmlform".equalsIgnoreCase(rootTag)) {
					return "HFE_XML";
				}
			}
			catch (Exception e) {
				log.debug("Failed to parse as XML: {}", e.getMessage());
			}
		}
		
		return null;
	}
	
	public static class TemplateRequest {
		
		public List<String> formUuids;
		
		public TemplateOptions options;
	}
}
