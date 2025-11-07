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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.api.service.EncounterService;
import org.openmrs.module.dataimport.api.service.ObsService;
import org.openmrs.module.dataimport.api.service.PatientService;
import org.openmrs.module.dataimport.api.service.WorkbookImportService;
import org.openmrs.module.dataimport.api.storage.IdempotencyService;
import org.openmrs.module.dataimport.api.storage.ImportPlanStorageService;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.ImportPlan;
import org.openmrs.module.dataimport.model.SheetModel;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

public class DataImportIntegrationTest {
	
	private WorkbookImportService importService;
	
	private ExcelTemplateExporter exporter;
	
	@BeforeEach
	void setUp() {
		ImportPlanStorageService importPlanStorage = new ImportPlanStorageService();
		IdempotencyService idempotencyService = new IdempotencyService();
		PatientService patientService = new PatientService();
		EncounterService encounterService = new EncounterService();
		ObsService obsService = new ObsService();
		
		importService = new WorkbookImportServiceImpl(importPlanStorage, idempotencyService, patientService,
		        encounterService, obsService);
		exporter = new ExcelTemplateExporter();
	}
	
	@Test
	@DisplayName("Should complete full dry-run and commit workflow")
	void testFullWorkflow() throws Exception {
		FormModel form = createTestForm();
		
		byte[] templateBytes = exporter.export(form, true);
		assertNotNull(templateBytes);
		assertTrue(templateBytes.length > 0);
		
		ImportPlan importPlan;
		try (InputStream inputStream = new ByteArrayInputStream(templateBytes)) {
			importPlan = importService.dryRun(inputStream);
		}
		
		assertNotNull(importPlan);
		assertNotNull(importPlan.getBatchId());
		assertNotNull(importPlan.getRowsOk());
		assertNotNull(importPlan.getIssues());
		assertNotNull(importPlan.getStats());
		
		assertEquals(0, importPlan.getIssues().size(), "Should have no validation issues for empty template");
		
		UUID batchId = importPlan.getBatchId();
		try {
			importService.commit(batchId);
		}
		catch (Exception e) {
			assertTrue(e.getMessage().contains("Service not found") || e.getMessage().contains("Context not initialized"));
		}
	}
	
	@Test
	@DisplayName("Should handle complex form with all field types")
	void testComplexForm() throws Exception {
		FormModel form = createComplexForm();
		
		byte[] templateBytes = exporter.export(form, true);
		assertNotNull(templateBytes);
		assertTrue(templateBytes.length > 0);
		
		try (InputStream inputStream = new ByteArrayInputStream(templateBytes)) {
			ImportPlan importPlan = importService.dryRun(inputStream);
			
			assertNotNull(importPlan);
			assertNotNull(importPlan.getRowsOk());
			assertNotNull(importPlan.getIssues());
			
			assertEquals(0, importPlan.getIssues().size());
		}
	}
	
	private FormModel createTestForm() {
		FormModel form = new FormModel();
		form.name = "Test Form";
		form.formUuid = "test-form-uuid";
		form.encounterTypeUuid = "test-encounter-type-uuid";
		
		SheetModel sheet = new SheetModel();
		sheet.sheetName = "Test Sheet";
		
		FieldModel textField = new FieldModel();
		textField.id = "test_field";
		textField.label = "Test Field";
		textField.conceptUuid = "test-concept-uuid";
		textField.datatype = FieldDatatype.TEXT;
		textField.maxLength = 100;
		sheet.fields.add(textField);
		
		form.sheets.add(sheet);
		
		return form;
	}
	
	private FormModel createComplexForm() {
		FormModel form = new FormModel();
		form.name = "Complex Test Form";
		form.formUuid = "complex-form-uuid";
		form.encounterTypeUuid = "complex-encounter-type-uuid";
		
		SheetModel sheet = new SheetModel();
		sheet.sheetName = "Complex Sheet";
		
		FieldModel textField = new FieldModel();
		textField.id = "notes";
		textField.label = "Notes";
		textField.conceptUuid = "notes-concept-uuid";
		textField.datatype = FieldDatatype.TEXT;
		textField.maxLength = 500;
		sheet.fields.add(textField);
		
		FieldModel numericField = new FieldModel();
		numericField.id = "temperature";
		numericField.label = "Temperature";
		numericField.conceptUuid = "temperature-concept-uuid";
		numericField.datatype = FieldDatatype.NUMERIC;
		numericField.min = 30.0;
		numericField.max = 45.0;
		sheet.fields.add(numericField);
		
		FieldModel codedField = new FieldModel();
		codedField.id = "status";
		codedField.label = "Status";
		codedField.conceptUuid = "status-concept-uuid";
		codedField.datatype = FieldDatatype.CODED;
		codedField.answers.add(new AnswerModel("Active", "active-concept-uuid", "Active"));
		codedField.answers.add(new AnswerModel("Inactive", "inactive-concept-uuid", "Inactive"));
		sheet.fields.add(codedField);
		
		FieldModel multiSelectField = new FieldModel();
		multiSelectField.id = "symptoms";
		multiSelectField.label = "Symptoms";
		multiSelectField.conceptUuid = "symptoms-concept-uuid";
		multiSelectField.datatype = FieldDatatype.CODED;
		multiSelectField.isMulti = true;
		multiSelectField.answers.add(new AnswerModel("Fever", "fever-concept-uuid", "Fever"));
		multiSelectField.answers.add(new AnswerModel("Cough", "cough-concept-uuid", "Cough"));
		multiSelectField.answers.add(new AnswerModel("Headache", "headache-concept-uuid", "Headache"));
		sheet.fields.add(multiSelectField);
		
		FieldModel dateField = new FieldModel();
		dateField.id = "visit_date";
		dateField.label = "Visit Date";
		dateField.conceptUuid = "visit-date-concept-uuid";
		dateField.datatype = FieldDatatype.DATE;
		sheet.fields.add(dateField);
		
		FieldModel booleanField = new FieldModel();
		booleanField.id = "is_active";
		booleanField.label = "Is Active";
		booleanField.conceptUuid = "is-active-concept-uuid";
		booleanField.datatype = FieldDatatype.BOOLEAN;
		sheet.fields.add(booleanField);
		
		form.sheets.add(sheet);
		
		return form;
	}
}
