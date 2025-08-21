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

@DisplayName("O3FormParser - Datatype Inference Tests")
class O3FormParserDatatypesTest {
	
	private O3FormParser parser;
	
	@BeforeEach
	void setUp() {
		parser = new O3FormParser();
	}
	
	@Test
	@DisplayName("Should correctly infer datatypes for all field types")
	void testInferDatatypes() throws Exception {
		String json = TestDataLoader.loadMixedFieldsForm();
		FormModel form = parser.parse(json, Locale.ENGLISH);
		
		SheetModel sheet = form.sheets.get(0);
		
		// Test text field
		FieldModel textField = findField(sheet, "patient_name");
		assertEquals(FieldDatatype.TEXT, textField.datatype);
		
		// Test number field
		FieldModel numberField = findField(sheet, "age");
		assertEquals(FieldDatatype.NUMERIC, numberField.datatype);
		assertEquals(0.0, numberField.min);
		assertEquals(120.0, numberField.max);
		
		// Test date field
		FieldModel dateField = findField(sheet, "date_of_birth");
		assertEquals(FieldDatatype.DATE, dateField.datatype);
		
		// Test datetime field
		FieldModel datetimeField = findField(sheet, "last_visit");
		assertEquals(FieldDatatype.DATETIME, datetimeField.datatype);
		
		// Test boolean field
		FieldModel booleanField = findField(sheet, "is_active");
		assertEquals(FieldDatatype.BOOLEAN, booleanField.datatype);
		
		// Test coded field (select)
		FieldModel codedField = findField(sheet, "status");
		assertEquals(FieldDatatype.CODED, codedField.datatype);
		assertEquals(2, codedField.answers.size());
	}
	
	@Test
	@DisplayName("Should handle repeat groups correctly")
	void testParseRepeatGroups() throws Exception {
		String json = TestDataLoader.loadRepeatGroupsForm();
		FormModel form = parser.parse(json, Locale.ENGLISH);
		
		// Should have main sheet + repeat sheet
		assertEquals(2, form.sheets.size());
		
		// Main sheet should have patient_name
		SheetModel mainSheet = form.sheets.get(0);
		FieldModel nameField = findField(mainSheet, "patient_name");
		assertEquals("Patient Name", nameField.label);
		
		// Repeat sheet should have temperature and blood_pressure
		SheetModel repeatSheet = form.sheets.get(1);
		assertTrue(repeatSheet.isRepeat);
		assertEquals("vitals_group", repeatSheet.repeatGroupId);
		assertEquals(2, repeatSheet.fields.size());
		
		FieldModel tempField = findField(repeatSheet, "temperature");
		assertEquals(FieldDatatype.NUMERIC, tempField.datatype);
		assertEquals(30.0, tempField.min);
		assertEquals(45.0, tempField.max);
		
		FieldModel bpField = findField(repeatSheet, "blood_pressure");
		assertEquals(FieldDatatype.TEXT, bpField.datatype);
	}
	
	private FieldModel findField(SheetModel sheet, String fieldId) {
		return sheet.fields.stream().filter(f -> fieldId.equals(f.id)).findFirst()
		        .orElseThrow(() -> new AssertionError("Field not found: " + fieldId));
	}
}
