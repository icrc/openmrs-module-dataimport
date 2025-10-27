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
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.EncounterBlueprint;
import org.openmrs.module.dataimport.api.ImportPlan;
import org.openmrs.module.dataimport.api.ImportResult;
import org.openmrs.module.dataimport.api.ImportStats;
import org.openmrs.module.dataimport.api.ObsBlueprint;
import org.openmrs.module.dataimport.api.QuarantineItem;
import org.openmrs.module.dataimport.api.RowPlan;
import org.openmrs.module.dataimport.api.WorkbookImportService;
import org.openmrs.module.dataimport.api.reader.EncounterReader;
import org.openmrs.module.dataimport.api.reader.MetaReader;
import org.openmrs.module.dataimport.api.reader.ObsReader;
import org.openmrs.module.dataimport.api.reader.RegistrationReader;
import org.openmrs.module.dataimport.api.service.EncounterSaver;
import org.openmrs.module.dataimport.api.service.ObsSaver;
import org.openmrs.module.dataimport.api.service.PatientSaver;
import org.openmrs.module.dataimport.api.storage.IdempotencyService;
import org.openmrs.module.dataimport.api.storage.ImportPlanStorageService;
import org.openmrs.module.dataimport.api.util.Idempotency;
import org.openmrs.module.dataimport.api.util.Validation;
import org.openmrs.module.dataimport.api.util.WorkbookUtil;
import org.openmrs.module.dataimport.model.SheetMeta;
import org.openmrs.module.dataimport.model.TemplateMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of the workbook import service.
 */
public class WorkbookImportServiceImpl implements WorkbookImportService {
	
	private static final Logger log = LoggerFactory.getLogger(WorkbookImportServiceImpl.class);
	
	private static final String PATIENT_SHEET_NAME = "Patient Registration";
	
	// Services for storage and idempotency
	private final ImportPlanStorageService importPlanStorage = new ImportPlanStorageService();
	
	private final IdempotencyService idempotencyService = new IdempotencyService();
	
	@Override
	public ImportPlan dryRun(InputStream xlsx) {
		try (XSSFWorkbook workbook = new XSSFWorkbook(xlsx)) {
			TemplateMeta meta = MetaReader.read(workbook);
			RegistrationReader.PatientIndex patientIndex = RegistrationReader.indexPatients(workbook);
			List<RowPlan> okRows = new ArrayList<>();
			List<QuarantineItem> quarantineItems = new ArrayList<>();
			List<Sheet> sheets = WorkbookUtil.nonPatientSheets(workbook, PATIENT_SHEET_NAME);
			for (Sheet sheet : sheets) {
				processSheet(sheet, meta, patientIndex, okRows, quarantineItems);
			}
			
			ImportStats stats = ImportStats.of(okRows, quarantineItems);
			UUID batchId = UUID.randomUUID();
			
			log.info("Dry run completed: {} OK rows, {} quarantined rows", okRows.size(), quarantineItems.size());
			
			ImportPlan plan = ImportPlan.builder().batchId(batchId).rowsOk(okRows).issues(quarantineItems).stats(stats)
			        .patientIndex(patientIndex).build();
			importPlanStorage.storePlan(plan);
			return plan;
			
		}
		catch (Exception e) {
			log.error("Failed to perform dry run", e);
			throw new RuntimeException("Failed to perform dry run: " + e.getMessage(), e);
		}
	}
	
	@Override
	@Transactional
	public ImportResult commit(UUID batchId) {
		try {
			log.info("Commit requested for batch: {}", batchId);
			
			ImportPlan plan = importPlanStorage.loadPlan(batchId);
			if (plan == null) {
				return ImportResult.failure(batchId, "Import plan not found for batch: " + batchId,
				    Collections.singletonList("Batch ID not found: " + batchId));
			}
			
			Map<String, String> refToPatientUuid = new HashMap<>();
			PatientSaver patientSaver = new PatientSaver();
			String defaultIdentifierTypeUuid = patientSaver.getDefaultIdentifierTypeUuid();
			String defaultLocationUuid = patientSaver.getDefaultLocationUuid();
			Map<String, RegistrationReader.PatientRow> patientsToCreate = extractPatientsToCreate(plan);
			
			for (RegistrationReader.PatientRow patientRow : patientsToCreate.values()) {
				Date birthdate = null;
				if (patientRow.birthdate != null) {
					birthdate = Date.from(patientRow.birthdate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
				}
				
				Patient p = patientSaver.getOrCreateByIdentifier(patientRow.patientRef(), defaultIdentifierTypeUuid,
				    defaultLocationUuid, patientRow.givenName, patientRow.familyName, patientRow.gender, birthdate);
				refToPatientUuid.put(patientRow.patientRef(), p.getUuid());
			}
			
			EncounterSaver encounterSaver = new EncounterSaver();
			ObsSaver obsSaver = new ObsSaver();
			List<String> createdEncounterUuids = new ArrayList<>();
			
			for (RowPlan rp : plan.getRowsOk()) {
				if (idempotencyService.exists(rp.getIdempotencyKey())) {
					log.debug("Skipping duplicate row with idempotency key: {}", rp.getIdempotencyKey());
					continue;
				}
				
				String patientUuid = refToPatientUuid.getOrDefault(rp.getPatientRef(), rp.getPatientUuid());
				if (patientUuid == null) {
					log.warn("No patient UUID found for patient ref: {}", rp.getPatientRef());
					continue;
				}
				
				Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
				if (patient == null) {
					log.warn("Patient not found for UUID: {}", patientUuid);
					continue;
				}
				
				Encounter enc = encounterSaver.createEncounterFromBlueprint(patient, rp.getEncounter());
				createdEncounterUuids.add(enc.getUuid());
				
				for (ObsBlueprint obsBlueprint : rp.getObs()) {
					Obs obs = obsSaver.createObsFromBlueprint(enc, obsBlueprint);
					if (obs != null) {
						log.debug("Created obs: {} = {}", obs.getConcept().getName().getName(),
						    obs.getValueAsString(Context.getLocale()));
					}
				}
				
				idempotencyService.record(rp.getIdempotencyKey(), enc.getUuid());
			}
			
			importPlanStorage.removePlan(batchId);
			
			ImportStats finalStats = ImportStats.of(plan.getRowsOk(), plan.getIssues());
			log.info("Commit completed successfully for batch: {} - {} encounters created", batchId,
			    createdEncounterUuids.size());
			
			return ImportResult.success(batchId, finalStats, createdEncounterUuids);
			
		}
		catch (Exception e) {
			log.error("Commit failed for batch: {}", batchId, e);
			return ImportResult.failure(batchId, "Commit failed: " + e.getMessage(),
			    Collections.singletonList(e.getMessage()));
		}
	}
	
	private Map<String, RegistrationReader.PatientRow> extractPatientsToCreate(ImportPlan plan) {
		Map<String, RegistrationReader.PatientRow> patientsToCreate = new HashMap<>();
		
		for (RowPlan rowPlan : plan.getRowsOk()) {
			String patientRef = rowPlan.getPatientRef();
			if (patientRef != null && !patientsToCreate.containsKey(patientRef)) {
				// Look up the patient in the registration sheet data
				// The patientRef is just a key - the actual data comes from the registration sheet
				RegistrationReader.PatientRow patientRow = getPatientFromRegistrationSheet(patientRef, plan);
				if (patientRow != null) {
					patientsToCreate.put(patientRef, patientRow);
				}
			}
		}
		
		return patientsToCreate;
	}
	
	/**
	 * Gets patient data from the registration sheet by patientRef. Uses the PatientIndex stored in the
	 * ImportPlan to look up actual patient data.
	 * 
	 * @param patientRef The patient reference to look up
	 * @param plan The ImportPlan containing the PatientIndex
	 * @return PatientRow with actual data from registration sheet, or null if not found
	 */
	private RegistrationReader.PatientRow getPatientFromRegistrationSheet(String patientRef, ImportPlan plan) {
		if (plan.getPatientIndex() == null) {
			log.warn("No PatientIndex available in ImportPlan for patientRef: {}", patientRef);
			return null;
		}
		
		RegistrationReader.PatientResolution resolution = plan.getPatientIndex().resolve(patientRef);
		if (resolution.isResolved() && resolution.needsCreation()) {
			return resolution.getPatientRow();
		}
		
		log.warn("Patient reference '{}' not found in registration sheet: {}", patientRef, resolution.getErrorMessage());
		return null;
	}
	
	private void processSheet(Sheet sheet, TemplateMeta meta, RegistrationReader.PatientIndex patientIndex,
	        List<RowPlan> okRows, List<QuarantineItem> quarantineItems) {
		
		String sheetName = sheet.getSheetName();
		SheetMeta sheetMeta = meta.getSheetMeta(sheetName);
		
		if (sheetMeta == null) {
			log.warn("No metadata found for sheet: {}", sheetName);
			return;
		}
		
		for (int rowIndex = 1; rowIndex <= WorkbookUtil.lastDataRow(sheet); rowIndex++) {
			if (WorkbookUtil.isEmptyRow(sheet, rowIndex))
				continue;
			
			try {
				processRow(sheet, rowIndex, sheetMeta, patientIndex, okRows, quarantineItems);
			}
			catch (Exception e) {
				log.warn("Failed to process row {} in sheet {}: {}", rowIndex, sheetName, e.getMessage());
				quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, 0, "PROCESSING_ERROR",
				    "Failed to process row: " + e.getMessage()));
			}
		}
	}
	
	private void processRow(Sheet sheet, int rowIndex, SheetMeta sheetMeta, RegistrationReader.PatientIndex patientIndex,
	        List<RowPlan> okRows, List<QuarantineItem> quarantineItems) {
		
		String sheetName = sheet.getSheetName();
		
		String patientRef = WorkbookUtil.Cells.getString(sheet, rowIndex, 0);
		if (patientRef == null || patientRef.trim().isEmpty()) {
			quarantineItems
			        .add(QuarantineItem.error(sheetName, rowIndex, 0, "MISSING_PATIENT_REF", "Patient Ref is required"));
			return;
		}
		
		RegistrationReader.PatientResolution patientResolution = patientIndex.resolve(patientRef);
		if (!patientResolution.isResolved()) {
			quarantineItems.add(
			    QuarantineItem.error(sheetName, rowIndex, 0, "UNKNOWN_PATIENT_REF", patientResolution.getErrorMessage()));
			return;
		}
		
		EncounterBlueprint encounter = EncounterReader.buildEncounter(sheet, rowIndex, sheetMeta);
		List<ObsBlueprint> obs = ObsReader.buildObs(sheet, rowIndex, sheetMeta);
		Validation.checkRow(obs, sheetMeta, quarantineItems, sheetName, rowIndex);
		
		if (!Validation.hasBlocking(quarantineItems, sheetName, rowIndex)) {
			String idempotencyKey = Idempotency.key(sheet.getWorkbook(), sheetName, rowIndex, encounter, obs);
			
			RowPlan rowPlan = new RowPlan(sheetName, rowIndex, patientRef, patientResolution.getPatientUuid(), encounter,
			        obs, idempotencyKey);
			okRows.add(rowPlan);
		}
	}
}
