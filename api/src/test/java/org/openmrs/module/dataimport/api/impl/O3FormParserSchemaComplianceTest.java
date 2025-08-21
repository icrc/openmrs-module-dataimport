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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.model.*;
import org.openmrs.module.dataimport.test.TestDataLoader;

import java.util.Locale;

@DisplayName("O3FormParser - Schema Compliance Tests")
class O3FormParserSchemaComplianceTest {
	
	private O3FormParser parser;
	
	@BeforeEach
	void setUp() {
		parser = new O3FormParser();
	}
	
	@Test
	@DisplayName("Should parse schema-compliant forms with all required fields")
	void testParseSchemaCompliantForm() throws Exception {
		String json = TestDataLoader.loadAdvancedFeaturesForm();
		FormModel form = parser.parse(json, Locale.ENGLISH);
		
		// Verify form metadata
		assertEquals("Sample Form with Advanced Features", form.name);
		assertEquals("test-form-advanced-uuid", form.formUuid);
		assertEquals("1.0", form.version);
		
		// Verify form structure
		assertEquals(1, form.sheets.size());
		SheetModel sheet = form.sheets.get(0);
		assertTrue(sheet.sheetName.contains("Advanced Features") || sheet.sheetName.contains("Advanced"),
		    "Sheet name should contain 'Advanced Features' but was: " + sheet.sheetName);
		
		// Verify all fields are parsed correctly
		assertEquals(5, sheet.fields.size());
		
		// Test patient name field (text with maxLength)
		FieldModel nameField = findField(sheet, "patient_name");
		assertEquals("Patient Name", nameField.label);
		assertEquals("patient.name.concept.uuid", nameField.conceptUuid);
		assertEquals(FieldDatatype.TEXT, nameField.datatype);
		
		// Test age field (number with min/max constraints)
		FieldModel ageField = findField(sheet, "age");
		assertEquals("Age in Years", ageField.label);
		assertEquals("age.concept.uuid", ageField.conceptUuid);
		assertEquals(FieldDatatype.NUMERIC, ageField.datatype);
		assertEquals(0.0, ageField.min);
		assertEquals(150.0, ageField.max);
		
		// Test radio button field
		FieldModel surgeryField = findField(sheet, "previous_surgery");
		assertEquals("Previous Surgery", surgeryField.label);
		assertEquals("previous.surgery.concept.uuid", surgeryField.conceptUuid);
		assertEquals(FieldDatatype.CODED, surgeryField.datatype);
		assertEquals(2, surgeryField.answers.size());
		
		// Test textarea field
		FieldModel medicationsField = findField(sheet, "medications");
		assertEquals("Medications", medicationsField.label);
		assertEquals("medications.concept.uuid", medicationsField.conceptUuid);
		assertEquals(FieldDatatype.TEXT, medicationsField.datatype);
		
		// Test multiCheckbox field
		FieldModel allergiesField = findField(sheet, "allergies");
		assertEquals("Allergies", allergiesField.label);
		assertEquals("allergies.concept.uuid", allergiesField.conceptUuid);
		assertEquals(FieldDatatype.CODED, allergiesField.datatype);
		assertEquals(4, allergiesField.answers.size());
		
		// Verify allergy answers
		AnswerModel penicillinAnswer = allergiesField.answers.get(0);
		assertEquals("Penicillin", penicillinAnswer.label);
		assertEquals("penicillin.concept.uuid", penicillinAnswer.conceptUuid);
		
		AnswerModel noneAnswer = allergiesField.answers.get(3);
		assertEquals("None", noneAnswer.label);
		assertEquals("none.concept.uuid", noneAnswer.conceptUuid);
	}
	
	@Test
	@DisplayName("Should handle repeat groups with proper schema structure")
	void testParseRepeatGroupsWithSchema() throws Exception {
		String json = TestDataLoader.loadRepeatGroupsForm();
		FormModel form = parser.parse(json, Locale.ENGLISH);
		
		// Should have main sheet + repeat sheet
		assertEquals(2, form.sheets.size());
		
		// Main sheet should have patient_name
		SheetModel mainSheet = form.sheets.get(0);
		FieldModel nameField = findField(mainSheet, "patient_name");
		assertEquals("Patient Name", nameField.label);
		assertEquals("patient.name.concept.uuid", nameField.conceptUuid);
		
		// Repeat sheet should have temperature and blood_pressure
		SheetModel repeatSheet = form.sheets.get(1);
		assertTrue(repeatSheet.isRepeat);
		assertEquals("vitals_group", repeatSheet.repeatGroupId);
		assertEquals(2, repeatSheet.fields.size());
		
		FieldModel tempField = findField(repeatSheet, "temperature");
		assertEquals("Temperature", tempField.label);
		assertEquals("temperature.concept.uuid", tempField.conceptUuid);
		assertEquals(FieldDatatype.NUMERIC, tempField.datatype);
		assertEquals(30.0, tempField.min);
		assertEquals(45.0, tempField.max);
		
		FieldModel bpField = findField(repeatSheet, "blood_pressure");
		assertEquals("Blood Pressure", bpField.label);
		assertEquals("blood.pressure.concept.uuid", bpField.conceptUuid);
		assertEquals(FieldDatatype.TEXT, bpField.datatype);
	}
	
	@Test
	@DisplayName("Should handle mixed field types with proper concept mapping")
	void testParseMixedFieldsWithConcepts() throws Exception {
		String json = TestDataLoader.loadMixedFieldsForm();
		FormModel form = parser.parse(json, Locale.ENGLISH);
		
		SheetModel sheet = form.sheets.get(0);
		
		// Verify all fields have concept UUIDs
		FieldModel nameField = findField(sheet, "patient_name");
		assertEquals("patient.name.concept.uuid", nameField.conceptUuid);
		
		FieldModel ageField = findField(sheet, "age");
		assertEquals("age.concept.uuid", ageField.conceptUuid);
		
		FieldModel dateField = findField(sheet, "date_of_birth");
		assertEquals("date.of.birth.concept.uuid", dateField.conceptUuid);
		
		FieldModel datetimeField = findField(sheet, "last_visit");
		assertEquals("last.visit.concept.uuid", datetimeField.conceptUuid);
		
		FieldModel booleanField = findField(sheet, "is_active");
		assertEquals("is.active.concept.uuid", booleanField.conceptUuid);
		
		FieldModel statusField = findField(sheet, "status");
		assertEquals("status.concept.uuid", statusField.conceptUuid);
	}
	
	private FieldModel findField(SheetModel sheet, String fieldId) {
		return sheet.fields.stream().filter(f -> fieldId.equals(f.id)).findFirst()
		        .orElseThrow(() -> new AssertionError("Field not found: " + fieldId));
	}
}
