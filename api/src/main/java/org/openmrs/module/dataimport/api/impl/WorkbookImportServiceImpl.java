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
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.reader.ExcelReader;
import org.openmrs.module.dataimport.api.service.EncounterService;
import org.openmrs.module.dataimport.api.service.ObsService;
import org.openmrs.module.dataimport.api.service.PatientService;
import org.openmrs.module.dataimport.api.service.VisitImportService;
import org.openmrs.module.dataimport.api.service.WorkbookImportService;
import org.openmrs.module.dataimport.api.storage.IdempotencyService;
import org.openmrs.module.dataimport.api.storage.ImportPlanStorageService;
import org.openmrs.module.dataimport.api.util.DataImportConstants;
import org.openmrs.module.dataimport.api.util.Idempotency;
import org.openmrs.module.dataimport.api.util.Validation;
import org.openmrs.module.dataimport.api.util.WorkbookUtil;
import org.openmrs.module.dataimport.model.EncounterBlueprint;
import org.openmrs.module.dataimport.model.ImportPlan;
import org.openmrs.module.dataimport.model.ImportResult;
import org.openmrs.module.dataimport.model.ImportStats;
import org.openmrs.module.dataimport.model.ObsBlueprint;
import org.openmrs.module.dataimport.model.QuarantineItem;
import org.openmrs.module.dataimport.model.RowPlan;
import org.openmrs.module.dataimport.model.SheetMeta;
import org.openmrs.module.dataimport.model.TemplateMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkbookImportServiceImpl implements WorkbookImportService {
	
	private static final Logger log = LoggerFactory.getLogger(WorkbookImportServiceImpl.class);
	
	private final ImportPlanStorageService importPlanStorage;
	
	private final IdempotencyService idempotencyService;
	
	private final PatientService patientService;
	
	private final EncounterService encounterService;
	
	private final ObsService obsService;
	
	private final VisitImportService visitImportService;
	
	@Autowired
	public WorkbookImportServiceImpl(ImportPlanStorageService importPlanStorage, IdempotencyService idempotencyService,
	    PatientService patientService, EncounterService encounterService, ObsService obsService,
	    VisitImportService visitImportService) {
		this.importPlanStorage = importPlanStorage;
		this.idempotencyService = idempotencyService;
		this.patientService = patientService;
		this.encounterService = encounterService;
		this.obsService = obsService;
		this.visitImportService = visitImportService;
	}
	
	@Override
	public ImportPlan dryRun(InputStream xlsx) {
		UUID batchId = UUID.randomUUID();
		try (XSSFWorkbook workbook = new XSSFWorkbook(xlsx)) {
			TemplateMeta meta = ExcelReader.read(workbook, false);
			java.util.List<ExcelReader.RowData> concepts = ExcelReader.readIfPresent(workbook);
			if (!concepts.isEmpty()) {
				validateConceptsSheet(meta, concepts);
			}
			ExcelReader.PatientIndex patientIndex = ExcelReader.indexPatients(workbook);
			List<RowPlan> okRows = new ArrayList<>();
			List<QuarantineItem> quarantineItems = new ArrayList<>();
			List<Sheet> sheets = WorkbookUtil.nonPatientSheets(workbook, DataImportConstants.PATIENT_SHEET_NAME);
			for (Sheet sheet : sheets) {
				processSheet(sheet, meta, patientIndex, okRows, quarantineItems);
			}
			
			ImportStats stats = ImportStats.of(okRows, quarantineItems);
			ImportPlan plan = ImportPlan.builder().batchId(batchId).rowsOk(okRows).issues(quarantineItems).stats(stats)
			        .patientIndex(patientIndex).build();
			importPlanStorage.storePlan(plan);
			return plan;
			
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to perform dry run: " + e.getMessage(), e);
		}
	}
	
	@Override
	public ImportResult commit(UUID batchId) {
		try {
			ImportPlan plan = importPlanStorage.loadPlan(batchId);
			if (plan == null) {
				return ImportResult.failure(batchId, "Import plan not found for batch: " + batchId,
				    Collections.singletonList("Batch ID not found: " + batchId));
			}
			
			PatientCreationOutcome creationOutcome = processPatientRegistrationSheet(plan);
			Map<String, String> refToPatientUuid = creationOutcome.refToPatientUuid;
			
			if (refToPatientUuid.isEmpty()) {
				// Do not fail the import if there are no patients to create from the
				// registration sheet. We can still proceed if rows already carry a
				// resolvable patientUuid (existing patients). Rows without a resolvable
				// patientUuid will be skipped below.
				log.warn(
				    "No patients were created from registration sheet; proceeding to process encounters for resolvable patients only");
			}
			
			// Map to cache visits: visitKey -> visitUuid
			// Visit key format: patientRef + "|" + locationUuid + "|" + dateKey
			Map<String, String> visitCache = new HashMap<>();
			
			List<String> createdEncounterUuids = new ArrayList<>();
			List<String> commitErrors = new ArrayList<>(creationOutcome.errors);
			
			int failedEncounters = 0;
			int failedObs = 0;
			int skippedRows = 0;
			
			for (RowPlan rp : plan.getRowsOk()) {
				if (idempotencyService.exists(rp.getIdempotencyKey())) {
					continue;
				}
				
				Patient patient = resolvePatientForRow(rp, creationOutcome, refToPatientUuid);
				if (patient == null) {
					skippedRows++;
					continue;
				}
				
				Visit visit = resolveOrCreateVisitForRow(patient, rp, visitCache, commitErrors);
				Encounter enc = createEncounterForRow(patient, rp, visit, createdEncounterUuids, commitErrors);
				if (enc == null) {
					failedEncounters++;
					continue;
				}
				
				failedObs += processObsForEncounter(enc, rp);
				idempotencyService.record(rp.getIdempotencyKey(), enc.getUuid());
			}
			
			importPlanStorage.removePlan(batchId);
			
			ImportStats finalStats = ImportStats.of(plan.getRowsOk(), plan.getIssues());
			String summary = buildImportSummary(refToPatientUuid.size(), createdEncounterUuids.size(), failedEncounters,
			    failedObs, skippedRows, creationOutcome.errors);
			
			return new ImportResult(true, summary, batchId, finalStats, createdEncounterUuids, commitErrors);
			
		}
		catch (Exception e) {
			return ImportResult.failure(batchId, "Commit failed: " + e.getMessage(),
			    Collections.singletonList(e.getMessage()));
		}
	}
	
	@Override
	public byte[] generateQuarantineReport(InputStream originalWorkbook, UUID batchId) {
		try {
			ImportPlan plan = importPlanStorage.loadPlan(batchId);
			if (plan == null) {
				throw new IllegalArgumentException("Import plan not found for batch: " + batchId);
			}
			
			QuarantineReportGenerator generator = new QuarantineReportGenerator();
			return generator.generateReport(originalWorkbook, plan);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to generate quarantine report: " + e.getMessage(), e);
		}
	}
	
	private static class PatientCreationOutcome {
		
		final Map<String, String> refToPatientUuid = new HashMap<>();
		
		final Set<String> failedPatientRefs = new java.util.HashSet<>();
		
		final List<String> errors = new ArrayList<>();
	}
	
	private PatientCreationOutcome processPatientRegistrationSheet(ImportPlan plan) {
		PatientCreationOutcome outcome = new PatientCreationOutcome();
		String defaultIdentifierTypeUuid = patientService.getDefaultIdentifierTypeUuid();
		String defaultLocationUuid = patientService.getDefaultLocationUuid();
		
		Map<String, ExcelReader.PatientRow> patientsToCreate = extractPatientsToCreate(plan);
		for (ExcelReader.PatientRow patientRow : patientsToCreate.values()) {
			try {
				Date birthdate = null;
				if (patientRow.birthdate != null) {
					birthdate = Date.from(patientRow.birthdate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
				}
				
				Patient p = patientService.createPatientWithGeneratedIdentifier(patientRow.givenName, patientRow.familyName,
				    patientRow.gender, birthdate, defaultIdentifierTypeUuid, defaultLocationUuid, patientRow.addressLine1,
				    patientRow.cityVillage, patientRow.country, patientRow.phone);
				
				outcome.refToPatientUuid.put(patientRow.patientRef(), p.getUuid());
			}
			catch (Exception e) {
				String errorMsg = "Failed to create patient '" + patientRow.patientRef() + "': " + e.getMessage();
				outcome.failedPatientRefs.add(patientRow.patientRef());
				outcome.errors.add(errorMsg);
			}
		}
		
		return outcome;
	}
	
	private Map<String, ExcelReader.PatientRow> extractPatientsToCreate(ImportPlan plan) {
		Map<String, ExcelReader.PatientRow> patientsToCreate = new HashMap<>();
		
		for (RowPlan rowPlan : plan.getRowsOk()) {
			String patientRef = rowPlan.getPatientRef();
			if (patientRef != null && !patientsToCreate.containsKey(patientRef)) {
				ExcelReader.PatientRow patientRow = getPatientFromRegistrationSheet(patientRef, plan);
				if (patientRow != null) {
					patientsToCreate.put(patientRef, patientRow);
				}
			}
		}
		
		return patientsToCreate;
	}
	
	private ExcelReader.PatientRow getPatientFromRegistrationSheet(String patientRef, ImportPlan plan) {
		if (plan.getPatientIndex() == null) {
			return null;
		}
		
		ExcelReader.PatientResolution resolution = plan.getPatientIndex().resolve(patientRef);
		if (resolution.isResolved() && resolution.needsCreation()) {
			return resolution.getPatientRow();
		}
		
		return null;
	}
	
	private void processSheet(Sheet sheet, TemplateMeta meta, ExcelReader.PatientIndex patientIndex, List<RowPlan> okRows,
	        List<QuarantineItem> quarantineItems) {
		
		String sheetName = sheet.getSheetName();
		SheetMeta sheetMeta = meta.getSheetMeta(sheetName);
		
		if (sheetMeta == null) {
			return;
		}
		
		int lastRow = WorkbookUtil.lastDataRow(sheet);
		if (lastRow < 1) {
			return;
		}
		
		for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
			if (WorkbookUtil.isEmptyRow(sheet, rowIndex))
				continue;
			
			try {
				processRow(sheet, rowIndex, sheetMeta, patientIndex, okRows, quarantineItems, meta.defaultLocationUuid);
			}
			catch (Exception e) {
				quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, 0, "PROCESSING_ERROR",
				    "Failed to process row: " + e.getMessage()));
			}
		}
	}
	
	private void processRow(Sheet sheet, int rowIndex, SheetMeta sheetMeta, ExcelReader.PatientIndex patientIndex,
	        List<RowPlan> okRows, List<QuarantineItem> quarantineItems, String templateDefaultLocationUuid) {
		
		String sheetName = sheet.getSheetName();
		
		String patientRef = WorkbookUtil.Cells.getString(sheet, rowIndex, 0);
		if (patientRef == null || patientRef.trim().isEmpty()) {
			quarantineItems
			        .add(QuarantineItem.error(sheetName, rowIndex, 0, "MISSING_PATIENT_REF", "Patient Ref is required"));
			return;
		}
		
		ExcelReader.PatientResolution patientResolution = patientIndex.resolve(patientRef);
		if (!patientResolution.isResolved()) {
			quarantineItems.add(
			    QuarantineItem.error(sheetName, rowIndex, 0, "UNKNOWN_PATIENT_REF", patientResolution.getErrorMessage()));
			return;
		}
		
		EncounterBlueprint encounter = ExcelReader.buildEncounter(sheetMeta, templateDefaultLocationUuid);
		List<ObsBlueprint> obs = ExcelReader.buildObs(sheet, rowIndex, sheetMeta);
		Validation.checkRow(obs, sheetMeta, quarantineItems, sheetName, rowIndex);
		
		if (!Validation.hasBlocking(quarantineItems, sheetName, rowIndex)) {
			String idempotencyKey = Idempotency.key(sheet.getWorkbook(), sheetName, rowIndex, encounter, obs);
			
			RowPlan rowPlan = new RowPlan(sheetName, rowIndex, patientRef, patientResolution.getPatientUuid(), encounter,
			        obs, idempotencyKey);
			okRows.add(rowPlan);
		}
	}
	
	private void validateConceptsSheet(TemplateMeta meta, List<ExcelReader.RowData> concepts) {
		Map<String, SheetMeta> sheetByName = new HashMap<>(meta.sheets);
		for (ExcelReader.RowData rd : concepts) {
			SheetMeta sheetMeta = sheetByName.get(rd.sheetName);
			if (sheetMeta == null) {
				continue;
			}
			org.openmrs.module.dataimport.model.ColumnMeta col = null;
			for (org.openmrs.module.dataimport.model.ColumnMeta c : sheetMeta.columns) {
				if (c.fieldId != null && c.fieldId.equals(rd.fieldId)) {
					col = c;
					break;
				}
				if (col == null && c.label != null && c.label.equals(rd.questionLabel)) {
					col = c;
				}
			}
			if (col == null) {
				continue;
			}
			if (col.conceptUuid != null && rd.questionConceptUuid != null
			        && !col.conceptUuid.equals(rd.questionConceptUuid)) {
				log.warn("Concept UUID mismatch for field '{}' on sheet '{}': __meta={} vs __concepts={} ", rd.fieldId,
				    rd.sheetName, col.conceptUuid, rd.questionConceptUuid);
			}
			if (col.datatype != null && col.datatype == org.openmrs.module.dataimport.model.FieldDatatype.CODED) {
				if ((col.answers == null || col.answers.isEmpty()) && rd.answerLabel != null
				        && rd.answerConceptUuid != null) {
					if (col.answers == null)
						col.answers = new HashMap<>();
					col.answers.put(DataImportConstants.normalizeLabel(rd.answerLabel), rd.answerConceptUuid);
				}
				if (rd.answerLabel != null && rd.answerConceptUuid != null) {
					String mapped = col.getAnswerConceptUuid(rd.answerLabel);
					if (mapped == null || !mapped.equals(rd.answerConceptUuid)) {
						log.warn(
						    "Answer mapping mismatch for field '{}' on sheet '{}': label='{}' metaUuid='{}' conceptsUuid='{}'",
						    rd.fieldId, rd.sheetName, rd.answerLabel, mapped, rd.answerConceptUuid);
					}
				}
			}
		}
	}
	
	private String buildImportSummary(int patientsCreated, int encountersCreated, int failedEncounters, int failedObs,
	        int skippedRows, List<String> patientErrors) {
		String summary = String.format("Import completed: %d patients, %d encounters", patientsCreated, encountersCreated);
		
		if (failedEncounters > 0 || failedObs > 0 || skippedRows > 0 || !patientErrors.isEmpty()) {
			List<String> issues = new ArrayList<>();
			if (failedEncounters > 0) {
				issues.add(String.format("%d encounter failures", failedEncounters));
			}
			if (failedObs > 0) {
				issues.add(String.format("%d obs failures", failedObs));
			}
			if (skippedRows > 0) {
				issues.add(String.format("%d rows skipped", skippedRows));
			}
			if (!patientErrors.isEmpty()) {
				issues.add(String.format("%d patient creation errors", patientErrors.size()));
			}
			
			summary = summary + " (" + String.join(", ", issues) + ")";
		}
		
		return summary;
	}
	
	private Patient resolvePatientForRow(RowPlan rp, PatientCreationOutcome creationOutcome,
	        Map<String, String> refToPatientUuid) {
		if (creationOutcome.failedPatientRefs.contains(rp.getPatientRef())) {
			return null;
		}
		
		String patientUuid = refToPatientUuid.getOrDefault(rp.getPatientRef(), rp.getPatientUuid());
		if (patientUuid == null) {
			return null;
		}
		
		return Context.getPatientService().getPatientByUuid(patientUuid);
	}
	
	private Visit resolveOrCreateVisitForRow(Patient patient, RowPlan rp, Map<String, String> visitCache,
	        List<String> commitErrors) {
		try {
			String visitKey = generateVisitKey(rp);
			String cachedVisitUuid = visitCache.get(visitKey);
			if (cachedVisitUuid != null) {
				org.openmrs.api.VisitService vs = Context.getVisitService();
				Visit cachedVisit = vs.getVisitByUuid(cachedVisitUuid);
				if (cachedVisit != null) {
					return cachedVisit;
				}
				visitCache.remove(visitKey);
			}
			
			java.time.Instant encounterInstant = rp.getEncounter().getEncounterDatetime();
			Date encounterDate = encounterInstant != null ? Date.from(encounterInstant) : new Date();
			
			String locationUuid = rp.getEncounter().getLocationUuid();
			if (locationUuid == null || locationUuid.trim().isEmpty()) {
				locationUuid = encounterService.getDefaultLocationUuid();
			}
			
			Visit visit = visitImportService.findReusableOpenVisit(patient.getUuid(), locationUuid, encounterDate);
			if (visit == null) {
				visit = visitImportService.createVisit(patient.getUuid(), null, locationUuid, encounterDate);
			}
			
			visitCache.put(visitKey, visit.getUuid());
			
			return visit;
		}
		catch (Exception e) {
			commitErrors.add(String.format("Row %d (sheet %s): Visit creation failed: %s", rp.getRowIndex(),
			    rp.getSheetName(), e.getMessage()));
			return null;
		}
	}
	
	private String generateVisitKey(RowPlan rp) {
		String patientRef = rp.getPatientRef();
		String locationUuid = rp.getEncounter().getLocationUuid();
		if (locationUuid == null || locationUuid.trim().isEmpty()) {
			locationUuid = "default";
		}
		
		java.time.Instant encounterInstant = rp.getEncounter().getEncounterDatetime();
		String dateKey;
		if (encounterInstant != null) {
			java.time.LocalDate date = java.time.LocalDateTime.ofInstant(encounterInstant, java.time.ZoneId.systemDefault())
			        .toLocalDate();
			dateKey = date.toString();
		} else {
			dateKey = "today";
		}
		
		return patientRef + "|" + locationUuid + "|" + dateKey;
	}
	
	private Encounter createEncounterForRow(Patient patient, RowPlan rp, Visit visit, List<String> createdEncounterUuids,
	        List<String> commitErrors) {
		try {
			Encounter enc = encounterService.createEncounterFromBlueprint(patient, rp.getEncounter(), visit);
			createdEncounterUuids.add(enc.getUuid());
			return enc;
		}
		catch (Exception e) {
			commitErrors.add(String.format("Row %d (sheet %s): Encounter creation failed: %s", rp.getRowIndex(),
			    rp.getSheetName(), e.getMessage()));
			return null;
		}
	}
	
	private int processObsForEncounter(Encounter enc, RowPlan rp) {
		int failedObsCount = 0;
		for (ObsBlueprint obsBlueprint : rp.getObs()) {
			try {
				Obs obs = obsService.createObsFromBlueprint(enc, obsBlueprint);
				if (obs != null) {
					log.debug("Created obs: {} = {}", obs.getConcept().getName().getName(),
					    obs.getValueAsString(Context.getLocale()));
				}
			}
			catch (Exception e) {
				failedObsCount++;
				log.warn("Failed to create obs for row {}: {}", rp.getRowIndex(), e.getMessage());
			}
		}
		return failedObsCount;
	}
}
