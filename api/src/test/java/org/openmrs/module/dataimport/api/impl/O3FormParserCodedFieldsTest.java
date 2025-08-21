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

@DisplayName("O3FormParser - Coded Fields Tests")
class O3FormParserCodedFieldsTest {
	
	private O3FormParser parser;
	
	@BeforeEach
	void setUp() {
		parser = new O3FormParser();
	}
	
	@Test
	@DisplayName("Should parse radio button fields with answers correctly")
	void testParseRadioButtonFields() throws Exception {
		String json = TestDataLoader.loadRadioButtonsForm();
		FormModel form = parser.parse(json, Locale.ENGLISH);
		
		assertEquals("Sample Form with Radio Buttons", form.name);
		assertEquals("test-form-radio-uuid", form.formUuid);
		assertEquals(1, form.sheets.size());
		
		SheetModel sheet = form.sheets.get(0);
		assertEquals(2, sheet.fields.size());
		
		// Test Gender field
		FieldModel genderField = sheet.fields.get(0);
		assertEquals("gender", genderField.id);
		assertEquals("Gender", genderField.label);
		assertEquals("gender.concept.uuid", genderField.conceptUuid);
		assertEquals(FieldDatatype.CODED, genderField.datatype);
		assertEquals(3, genderField.answers.size());
		
		// Verify gender answers
		AnswerModel maleAnswer = genderField.answers.get(0);
		assertEquals("Male", maleAnswer.label);
		assertEquals("male.concept.uuid", maleAnswer.conceptUuid);
		assertEquals("male.concept.uuid", maleAnswer.value);
		
		AnswerModel femaleAnswer = genderField.answers.get(1);
		assertEquals("Female", femaleAnswer.label);
		assertEquals("female.concept.uuid", femaleAnswer.conceptUuid);
		assertEquals("female.concept.uuid", femaleAnswer.value);
		
		// Test Anesthesia Type field
		FieldModel anesthesiaField = sheet.fields.get(1);
		assertEquals("anesthesia_type", anesthesiaField.id);
		assertEquals("Anesthesia Type", anesthesiaField.label);
		assertEquals("anesthesia.concept.uuid", anesthesiaField.conceptUuid);
		assertEquals(FieldDatatype.CODED, anesthesiaField.datatype);
		assertEquals(3, anesthesiaField.answers.size());
		
		// Verify anesthesia answers
		AnswerModel generalAnswer = anesthesiaField.answers.get(0);
		assertEquals("General", generalAnswer.label);
		assertEquals("general.concept.uuid", generalAnswer.conceptUuid);
	}
	
	@Test
	@DisplayName("Should handle fields without answers as TEXT type")
	void testParseFieldsWithoutAnswers() throws Exception {
		String json = TestDataLoader.loadMixedFieldsForm();
		FormModel form = parser.parse(json, Locale.ENGLISH);
		
		SheetModel sheet = form.sheets.get(0);
		
		// Find the patient name field (should be TEXT)
		FieldModel nameField = sheet.fields.stream().filter(f -> "patient_name".equals(f.id)).findFirst()
		        .orElseThrow(() -> new AssertionError("Patient name field not found"));
		
		assertEquals("Patient Name", nameField.label);
		assertEquals(FieldDatatype.TEXT, nameField.datatype);
		assertTrue(nameField.answers.isEmpty());
	}
}
