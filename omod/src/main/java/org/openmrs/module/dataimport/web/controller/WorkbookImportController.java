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

import org.openmrs.module.dataimport.api.ImportPlan;
import org.openmrs.module.dataimport.api.ImportResult;
import org.openmrs.module.dataimport.api.WorkbookImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for workbook import operations.
 */
@Controller
@RequestMapping("/dataimport")
@SuppressWarnings("unused")
public class WorkbookImportController {
	
	private static final Logger log = LoggerFactory.getLogger(WorkbookImportController.class);
	
	@Autowired
	private WorkbookImportService workbookImportService;
	
	/**
	 * Performs a dry-run validation of an uploaded Excel workbook. POST /dataimport/imports/dry-run
	 * Content-Type: multipart/form-data
	 * 
	 * @param file The Excel workbook file
	 * @return JSON response with validation results
	 */
	@PostMapping(value = "/imports/dry-run", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> dryRun(@RequestParam("file") MultipartFile file) {
		try {
			if (file.isEmpty()) {
				return ResponseEntity.badRequest().body(createErrorResponse("No file uploaded"));
			}
			
			if (!isExcelFile(file)) {
				return ResponseEntity.badRequest()
				        .body(createErrorResponse("File must be an Excel workbook (.xlsx) / (.xls)"));
			}
			
			log.info("Starting dry run for file: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
			
			try (InputStream inputStream = file.getInputStream()) {
				ImportPlan importPlan = workbookImportService.dryRun(inputStream);
				
				Map<String, Object> response = new HashMap<>();
				response.put("success", true);
				response.put("batchId", importPlan.getBatchId().toString());
				response.put("stats", importPlan.getStats());
				response.put("okRows", importPlan.getRowsOk());
				response.put("quarantinedRows", importPlan.getIssues());
				response.put("message", "Dry run completed successfully");
				
				log.info("Dry run completed: {} OK rows, {} quarantined rows", importPlan.getRowsOk().size(),
				    importPlan.getIssues().size());
				
				return ResponseEntity.ok(response);
			}
			
		}
		catch (Exception e) {
			log.error("Dry run failed", e);
			return ResponseEntity.status(500).body(createErrorResponse("Dry run failed: " + e.getMessage()));
		}
	}
	
	/**
	 * Commits a validated import plan to the database. POST /dataimport/imports/commit?batchId={uuid}
	 * 
	 * @return JSON response with commit results
	 */
	@PostMapping("/imports/commit")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> commit(@RequestParam("batchId") String batchIdStr) {
		try {
			UUID batchId = UUID.fromString(batchIdStr);
			
			log.info("Starting commit for batch: {}", batchId);
			
			ImportResult result = workbookImportService.commit(batchId);
			
			Map<String, Object> response = new HashMap<>();
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
			
			return ResponseEntity.ok(response);
			
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(createErrorResponse("Invalid batch ID: " + e.getMessage()));
		}
		catch (Exception e) {
			log.error("Commit failed for batch: " + batchIdStr, e);
			return ResponseEntity.status(500).body(createErrorResponse("Commit failed: " + e.getMessage()));
		}
	}
	
	private boolean isExcelFile(MultipartFile file) {
		String filename = file.getOriginalFilename();
		if (filename == null)
			return false;
		
		String lowerFilename = filename.toLowerCase();
		return lowerFilename.endsWith(".xlsx") || lowerFilename.endsWith(".xls");
	}
	
	private Map<String, Object> createErrorResponse(String message) {
		Map<String, Object> response = new HashMap<>();
		response.put("success", false);
		response.put("message", message);
		return response;
	}
}
