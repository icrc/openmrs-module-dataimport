/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.impl;

import org.apache.poi.ss.usermodel.Sheet;
import org.openmrs.Location;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.reader.ExcelReader;
import org.openmrs.module.dataimport.api.reader.ExcelReader.ObsBuildIssue;
import org.openmrs.module.dataimport.api.reader.ExcelReader.ObsBuildOutcome;
import org.openmrs.module.dataimport.api.service.WorkbookValidationService;
import org.openmrs.module.dataimport.api.util.Idempotency;
import org.openmrs.module.dataimport.api.util.LocationValidation;
import org.openmrs.module.dataimport.api.util.LocationValidationResult;
import org.openmrs.module.dataimport.api.util.Validation;
import org.openmrs.module.dataimport.api.util.WorkbookUtil;
import org.openmrs.module.dataimport.model.ColumnMeta;
import org.openmrs.module.dataimport.model.EncounterBlueprint;
import org.openmrs.module.dataimport.model.ObsBlueprint;
import org.openmrs.module.dataimport.model.QuarantineItem;
import org.openmrs.module.dataimport.model.RowPlan;
import org.openmrs.module.dataimport.model.SheetMeta;
import org.openmrs.module.dataimport.model.TemplateMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class WorkbookValidationServiceImpl implements WorkbookValidationService {
	
	private static final Logger log = LoggerFactory.getLogger(WorkbookValidationServiceImpl.class);
	
	@Override
	public ValidationResult validate(List<Sheet> sheets, TemplateMeta meta, ExcelReader.PatientIndex patientIndex,
	        String templateDefaultLocationUuid) {
		if (sheets == null || sheets.isEmpty()) {
			return new ValidationResult(new ArrayList<>(), new ArrayList<>(), new HashSet<>());
		}
		
		// Build location cache for validation (T028)
		Map<String, Location> locationCache = buildLocationCache();
		Set<String> allowedLocationUuids = new HashSet<>(meta.allowedLocationUuids);
		
		int availableProcessors = Runtime.getRuntime().availableProcessors();
		int threadCount = Math.min(Math.max(availableProcessors, 1), sheets.size());
		List<SheetProcessingResult> sheetResults;
		
		if (threadCount <= 1) {
			sheetResults = processSheetsSequentially(sheets, meta, patientIndex, templateDefaultLocationUuid, locationCache,
			    allowedLocationUuids);
		} else {
			sheetResults = processSheetsInParallel(sheets, meta, patientIndex, templateDefaultLocationUuid, threadCount,
			    locationCache, allowedLocationUuids);
		}
		
		return aggregateResults(sheetResults);
	}
	
	/**
	 * Builds a cache of all locations indexed by UUID for efficient validation. This avoids per-row
	 * database queries during validation.
	 */
	protected Map<String, Location> buildLocationCache() {
		Map<String, Location> cache = new HashMap<>();
		try {
			LocationService locationService = Context.getLocationService();
			if (locationService != null) {
				List<Location> allLocations = locationService.getAllLocations(true); // include retired for validation
				if (allLocations != null) {
					for (Location location : allLocations) {
						cache.put(location.getUuid(), location);
					}
				}
			}
		}
		catch (Exception e) {
			log.warn("Failed to build location cache: {}. Location validation will be skipped.", e.getMessage());
		}
		return cache;
	}
	
	private List<SheetProcessingResult> processSheetsSequentially(List<Sheet> sheets, TemplateMeta meta,
	        ExcelReader.PatientIndex patientIndex, String templateDefaultLocationUuid, Map<String, Location> locationCache,
	        Set<String> allowedLocationUuids) {
		List<SheetProcessingResult> results = new ArrayList<>();
		for (Sheet sheet : sheets) {
			results.add(
			    processSheet(sheet, meta, patientIndex, templateDefaultLocationUuid, locationCache, allowedLocationUuids));
		}
		return results;
	}
	
	private List<SheetProcessingResult> processSheetsInParallel(List<Sheet> sheets, TemplateMeta meta,
	        ExcelReader.PatientIndex patientIndex, String templateDefaultLocationUuid, int threadCount,
	        Map<String, Location> locationCache, Set<String> allowedLocationUuids) {
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		try {
			List<Future<SheetProcessingResult>> futures = new ArrayList<>();
			for (Sheet sheet : sheets) {
				futures.add(executor.submit(() -> processSheet(sheet, meta, patientIndex, templateDefaultLocationUuid,
				    locationCache, allowedLocationUuids)));
			}
			
			List<SheetProcessingResult> results = new ArrayList<>();
			for (Future<SheetProcessingResult> future : futures) {
				results.add(future.get());
			}
			return results;
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Dry run interrupted", ie);
		}
		catch (ExecutionException ee) {
			throw new RuntimeException("Dry run failed: " + ee.getCause().getMessage(), ee.getCause());
		}
		finally {
			executor.shutdownNow();
		}
	}
	
	private ValidationResult aggregateResults(List<SheetProcessingResult> sheetResults) {
		List<RowPlan> okRows = new ArrayList<>();
		List<QuarantineItem> quarantineItems = new ArrayList<>();
		Set<String> globalBlocked = new HashSet<>();
		
		for (SheetProcessingResult sheetResult : sheetResults) {
			globalBlocked.addAll(sheetResult.blockedPatientRefs);
			quarantineItems.addAll(sheetResult.quarantineItems);
		}
		
		for (SheetProcessingResult sheetResult : sheetResults) {
			for (RowPlan rowPlan : sheetResult.okRows) {
				String patientRef = rowPlan.getPatientRef();
				if (patientRef != null && globalBlocked.contains(patientRef)) {
					quarantineItems
					        .add(createPatientBlockedIssue(rowPlan.getSheetName(), rowPlan.getRowIndex(), patientRef));
				} else {
					okRows.add(rowPlan);
				}
			}
		}
		
		return new ValidationResult(okRows, quarantineItems, globalBlocked);
	}
	
	private SheetProcessingResult processSheet(Sheet sheet, TemplateMeta meta, ExcelReader.PatientIndex patientIndex,
	        String templateDefaultLocationUuid, Map<String, Location> locationCache, Set<String> allowedLocationUuids) {
		SheetProcessingResult result = new SheetProcessingResult();
		String sheetName = sheet.getSheetName();
		SheetMeta sheetMeta = meta.getSheetMeta(sheetName);
		
		if (sheetMeta == null) {
			return result;
		}
		
		// Find encounterLocation column index if it exists
		int locationColumnIndex = findEncounterLocationColumnIndex(sheetMeta);
		
		int lastRow = WorkbookUtil.lastDataRow(sheet);
		if (lastRow < 1) {
			return result;
		}
		
		for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
			if (WorkbookUtil.isEmptyRow(sheet, rowIndex)) {
				continue;
			}
			try {
				processRow(sheet, rowIndex, sheetMeta, patientIndex, result, templateDefaultLocationUuid, locationCache,
				    allowedLocationUuids, meta.restrictToAllowedLocations, locationColumnIndex);
			}
			catch (Exception e) {
				result.quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, 0, "PROCESSING_ERROR",
				    "Failed to process row: " + e.getMessage()));
			}
		}
		
		return result;
	}
	
	/**
	 * Finds the column index for encounterLocation field in the sheet metadata.
	 *
	 * @param sheetMeta the sheet metadata
	 * @return column index, or -1 if not found
	 */
	private int findEncounterLocationColumnIndex(SheetMeta sheetMeta) {
		if (sheetMeta == null || sheetMeta.columns == null) {
			return -1;
		}
		for (ColumnMeta column : sheetMeta.columns) {
			if (column.fieldId != null && column.fieldId.toLowerCase().contains("encounterlocation")) {
				return column.colIndex;
			}
			if (column.label != null && column.label.toLowerCase().contains("location")) {
				return column.colIndex;
			}
		}
		return -1;
	}
	
	private void processRow(Sheet sheet, int rowIndex, SheetMeta sheetMeta, ExcelReader.PatientIndex patientIndex,
	        SheetProcessingResult result, String templateDefaultLocationUuid, Map<String, Location> locationCache,
	        Set<String> allowedLocationUuids, boolean restrictToAllowedLocations, int locationColumnIndex) {
		String sheetName = sheet.getSheetName();
		
		String patientRef = WorkbookUtil.Cells.getString(sheet, rowIndex, 0);
		if (patientRef == null || patientRef.trim().isEmpty()) {
			result.quarantineItems
			        .add(QuarantineItem.error(sheetName, rowIndex, 0, "MISSING_PATIENT_REF", "Patient Ref is required"));
			return;
		}
		
		ExcelReader.PatientResolution patientResolution = patientIndex.resolve(patientRef);
		if (!patientResolution.isResolved()) {
			result.blockedPatientRefs.add(patientRef);
			result.quarantineItems.add(
			    QuarantineItem.error(sheetName, rowIndex, 0, "UNKNOWN_PATIENT_REF", patientResolution.getErrorMessage()));
			return;
		}
		
		int issuesBefore = result.quarantineItems.size();
		
		// Validate location if location cache is available (T024-T027)
		String rowLocationUuid = null;
		if (!locationCache.isEmpty() && locationColumnIndex >= 0) {
			String locationCellValue = WorkbookUtil.Cells.getString(sheet, rowIndex, locationColumnIndex);
			LocationValidationResult locationResult = LocationValidation.validateLocation(locationCellValue, locationCache,
			    allowedLocationUuids, restrictToAllowedLocations, templateDefaultLocationUuid);
			
			if (!locationResult.isValid()) {
				QuarantineItem locationError = LocationValidation.createQuarantineItem(locationResult, sheetName, rowIndex,
				    locationColumnIndex);
				result.quarantineItems.add(locationError);
			} else {
				rowLocationUuid = locationResult.getResolvedLocationUuid();
			}
		}
		
		EncounterBlueprint encounter = ExcelReader.buildEncounter(sheetMeta, templateDefaultLocationUuid);
		// If we validated a per-row location, use it instead of the template default
		if (rowLocationUuid != null && !rowLocationUuid.isEmpty()) {
			encounter = EncounterBlueprint.builder().encounterTypeUuid(encounter.getEncounterTypeUuid())
			        .encounterDatetime(encounter.getEncounterDatetime()).locationUuid(rowLocationUuid)
			        .providerUuid(encounter.getProviderUuid()).formUuid(encounter.getFormUuid()).build();
		}
		
		ObsBuildOutcome obsOutcome = ExcelReader.buildObsDetailed(sheet, rowIndex, sheetMeta);
		
		for (ObsBuildIssue issue : obsOutcome.getIssues()) {
			ColumnMeta column = issue.getColumn();
			int colIndex = column != null ? column.colIndex : 0;
			String message = issue.getMessage() != null ? issue.getMessage()
			        : ("Failed to parse value for column '" + ExcelReader.describeColumn(column) + "'");
			String rawValue = issue.getRawValue();
			if (rawValue != null && !rawValue.trim().isEmpty()) {
				message = message + " (value: '" + rawValue + "')";
			}
			result.quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, colIndex, "OBS_PARSE_ERROR", message));
		}
		
		List<ObsBlueprint> obs = obsOutcome.getObs();
		Validation.checkRow(obs, sheetMeta, result.quarantineItems, sheetName, rowIndex);
		
		boolean rowHasIssues = result.quarantineItems.size() > issuesBefore;
		if (rowHasIssues) {
			result.blockedPatientRefs.add(patientRef);
			return;
		}
		
		String idempotencyKey = Idempotency.key(sheet.getWorkbook(), sheetName, rowIndex, encounter, obs);
		RowPlan rowPlan = new RowPlan(sheetName, rowIndex, patientRef, patientResolution.getPatientUuid(), encounter, obs,
		        idempotencyKey);
		result.okRows.add(rowPlan);
	}
	
	private QuarantineItem createPatientBlockedIssue(String sheetName, int rowIndex, String patientRef) {
		return QuarantineItem.error(sheetName, rowIndex, 0, "PATIENT_BLOCKED",
		    "Patient '" + patientRef + "' has other rows with issues; skipping row");
	}
	
	private static class SheetProcessingResult {
		
		private final List<RowPlan> okRows = new ArrayList<>();
		
		private final List<QuarantineItem> quarantineItems = new ArrayList<>();
		
		private final Set<String> blockedPatientRefs = new HashSet<>();
	}
}
