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
import org.openmrs.module.dataimport.api.reader.ExcelReader;
import org.openmrs.module.dataimport.api.service.EncounterImportOrchestrator;
import org.openmrs.module.dataimport.api.service.PatientImportService;
import org.openmrs.module.dataimport.api.service.WorkbookImportService;
import org.openmrs.module.dataimport.api.service.WorkbookValidationService;
import org.openmrs.module.dataimport.api.storage.ImportPlanStorageService;
import org.openmrs.module.dataimport.api.util.DataImportConstants;
import org.openmrs.module.dataimport.api.util.WorkbookUtil;
import org.openmrs.module.dataimport.model.ImportPlan;
import org.openmrs.module.dataimport.model.ImportResult;
import org.openmrs.module.dataimport.model.ImportStats;
import org.openmrs.module.dataimport.model.SheetMeta;
import org.openmrs.module.dataimport.model.TemplateMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkbookImportServiceImpl implements WorkbookImportService {
	
	private static final Logger log = LoggerFactory.getLogger(WorkbookImportServiceImpl.class);
	
	private final ImportPlanStorageService importPlanStorage;
	
	private final WorkbookValidationService workbookValidationService;
	
	private final PatientImportService patientImportService;
	
	private final EncounterImportOrchestrator encounterImportOrchestrator;
	
	@Autowired
	public WorkbookImportServiceImpl(ImportPlanStorageService importPlanStorage,
	    WorkbookValidationService workbookValidationService, PatientImportService patientImportService,
	    EncounterImportOrchestrator encounterImportOrchestrator) {
		this.importPlanStorage = importPlanStorage;
		this.workbookValidationService = workbookValidationService;
		this.patientImportService = patientImportService;
		this.encounterImportOrchestrator = encounterImportOrchestrator;
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
			
			List<Sheet> sheets = WorkbookUtil.nonPatientSheets(workbook, DataImportConstants.PATIENT_SHEET_NAME);
			WorkbookValidationService.ValidationResult validationResult = workbookValidationService.validate(sheets, meta,
			    patientIndex, meta.defaultLocationUuid);
			
			ImportStats stats = ImportStats.of(validationResult.okRows, validationResult.quarantineItems,
			    validationResult.blockedPatientRefs);
			ImportPlan plan = ImportPlan.builder().batchId(batchId).rowsOk(validationResult.okRows)
			        .issues(validationResult.quarantineItems).stats(stats).patientIndex(patientIndex)
			        .blockedPatientRefs(validationResult.blockedPatientRefs).build();
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
			
			PatientImportService.PatientCreationOutcome patientCreationOutcome = patientImportService
			        .createPatientsFromPlan(plan);
			
			if (patientCreationOutcome.refToPatientUuid.isEmpty()) {
				log.warn(
				    "No patients were created from registration sheet; proceeding to process encounters for resolvable patients only");
			}
			
			EncounterImportOrchestrator.EncounterProcessingOutcome encounterOutcome = encounterImportOrchestrator
			        .processEncounters(plan, patientCreationOutcome);
			
			importPlanStorage.removePlan(batchId);
			
			ImportStats finalStats = ImportStats.of(plan.getRowsOk(), plan.getIssues(), plan.getBlockedPatientRefs());
			String summary = buildImportSummary(patientCreationOutcome.refToPatientUuid.size(),
			    encounterOutcome.createdEncounterUuids.size(), encounterOutcome.failedEncounters, encounterOutcome.failedObs,
			    encounterOutcome.skippedRows, patientCreationOutcome.errors);
			
			return new ImportResult(true, summary, batchId, finalStats, encounterOutcome.createdEncounterUuids,
			        encounterOutcome.errors);
			
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
}
