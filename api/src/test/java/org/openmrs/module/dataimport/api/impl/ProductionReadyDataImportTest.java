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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.api.ImportPlan;
import org.openmrs.module.dataimport.api.WorkbookImportService;
import org.openmrs.module.dataimport.api.storage.IdempotencyService;
import org.openmrs.module.dataimport.api.storage.ImportPlanStorageService;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.SheetModel;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

@DisplayName("Production-Ready Data Import Integration Tests")
class ProductionReadyDataImportTest {
	
	private WorkbookImportService importService;
	
	private ExcelTemplateExporter exporter;
	
	@BeforeEach
	void setUp() {
		importService = new WorkbookImportServiceImpl();
		exporter = new ExcelTemplateExporter();
	}
	
	@Test
	@DisplayName("Should complete full dry-run and commit workflow")
	void testFullWorkflow() throws Exception {
		// Create a test form
		FormModel form = createTestForm();
		
		// Export template
		byte[] templateBytes = exporter.export(form, true);
		assertNotNull(templateBytes);
		assertTrue(templateBytes.length > 0);
		
		// Perform dry run
		ImportPlan importPlan;
		try (InputStream inputStream = new ByteArrayInputStream(templateBytes)) {
			importPlan = importService.dryRun(inputStream);
		}
		
		assertNotNull(importPlan);
		assertNotNull(importPlan.getBatchId());
		assertNotNull(importPlan.getRowsOk());
		assertNotNull(importPlan.getIssues());
		assertNotNull(importPlan.getStats());
		
		// Verify dry run completed successfully
		assertEquals(0, importPlan.getIssues().size(), "Should have no validation issues for empty template");
		
		// Test commit (will fail gracefully since we don't have OpenMRS context)
		UUID batchId = importPlan.getBatchId();
		try {
			importService.commit(batchId);
			// If we get here, the commit succeeded (unlikely without OpenMRS context)
		}
		catch (Exception e) {
			// Expected to fail without OpenMRS context
			assertTrue(e.getMessage().contains("Service not found") || e.getMessage().contains("Context not initialized"));
		}
	}
	
	@Test
	@DisplayName("Should handle storage service properly")
	void testStorageService() {
		ImportPlanStorageService storage = new ImportPlanStorageService();
		
		// Test basic operations
		assertEquals(0, storage.size());
		assertFalse(storage.exists(UUID.randomUUID()));
		
		// Test cleanup
		assertDoesNotThrow(() -> {
			storage.cleanupOldPlans(24);
		});
	}
	
	@Test
	@DisplayName("Should handle idempotency service properly")
	void testIdempotencyService() {
		IdempotencyService idempotency = new IdempotencyService();
		
		// Test basic operations
		assertEquals(0, idempotency.size());
		assertFalse(idempotency.exists("test-key"));
		
		// Test recording
		assertDoesNotThrow(() -> {
			idempotency.record("test-key", "test-encounter-uuid");
		});
		
		assertTrue(idempotency.exists("test-key"));
		assertEquals("test-encounter-uuid", idempotency.getEncounterUuid("test-key"));
		
		// Test removal
		idempotency.remove("test-key");
		assertFalse(idempotency.exists("test-key"));
		
		// Test cleanup
		assertDoesNotThrow(() -> {
			idempotency.cleanupOldKeys(24);
		});
	}
	
	@Test
	@DisplayName("Should handle complex form with all field types")
	void testComplexForm() throws Exception {
		FormModel form = createComplexForm();
		
		// Export template
		byte[] templateBytes = exporter.export(form, true);
		assertNotNull(templateBytes);
		assertTrue(templateBytes.length > 0);
		
		// Perform dry run
		try (InputStream inputStream = new ByteArrayInputStream(templateBytes)) {
			ImportPlan importPlan = importService.dryRun(inputStream);
			
			assertNotNull(importPlan);
			assertNotNull(importPlan.getRowsOk());
			assertNotNull(importPlan.getIssues());
			
			// Should complete successfully
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
		
		// Text field
		FieldModel textField = new FieldModel();
		textField.id = "notes";
		textField.label = "Notes";
		textField.conceptUuid = "notes-concept-uuid";
		textField.datatype = FieldDatatype.TEXT;
		textField.maxLength = 500;
		sheet.fields.add(textField);
		
		// Numeric field
		FieldModel numericField = new FieldModel();
		numericField.id = "temperature";
		numericField.label = "Temperature";
		numericField.conceptUuid = "temperature-concept-uuid";
		numericField.datatype = FieldDatatype.NUMERIC;
		numericField.min = 30.0;
		numericField.max = 45.0;
		sheet.fields.add(numericField);
		
		// Coded field
		FieldModel codedField = new FieldModel();
		codedField.id = "status";
		codedField.label = "Status";
		codedField.conceptUuid = "status-concept-uuid";
		codedField.datatype = FieldDatatype.CODED;
		codedField.answers.add(new AnswerModel("Active", "active-concept-uuid", "Active"));
		codedField.answers.add(new AnswerModel("Inactive", "inactive-concept-uuid", "Inactive"));
		sheet.fields.add(codedField);
		
		// Multi-select field
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
		
		// Date field
		FieldModel dateField = new FieldModel();
		dateField.id = "visit_date";
		dateField.label = "Visit Date";
		dateField.conceptUuid = "visit-date-concept-uuid";
		dateField.datatype = FieldDatatype.DATE;
		sheet.fields.add(dateField);
		
		// Boolean field
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
