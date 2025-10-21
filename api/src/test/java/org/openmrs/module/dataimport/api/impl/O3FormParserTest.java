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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.model.*;

import java.util.Locale;

@DisplayName("O3FormParser Tests")
class O3FormParserTest {
	
	private O3FormParser parser;
	
	@BeforeEach
	void setUp() {
		parser = new O3FormParser();
	}
	
	@Test
	@DisplayName("Should parse basic form with metadata")
	void testParseBasicForm() throws Exception {
		String json = "{\n" + "    \"uuid\": \"form-uuid-123\",\n" + "    \"name\": \"Test Form\",\n"
		        + "    \"display\": \"Test Form Display\",\n" + "    \"version\": \"1.0\",\n"
		        + "    \"encounterType\": \"encounter-type-uuid\",\n" + "    \"pages\": [\n" + "        {\n"
		        + "            \"sections\": []\n" + "        }\n" + "    ]\n" + "}";
		
		FormModel result = parser.parse(json, Locale.ENGLISH);
		
		assertNotNull(result);
		assertEquals("form-uuid-123", result.formUuid);
		assertEquals("Test Form", result.name);
		assertEquals("1.0", result.version);
		assertEquals("encounter-type-uuid", result.encounterTypeUuid);
		assertNotNull(result.sheets);
		assertTrue(result.sheets.size() > 0);
	}
	
	@Test
	@DisplayName("Should parse form with questions")
	void testParseFormWithQuestions() throws Exception {
		String json = "{\n" + "    \"uuid\": \"form-with-questions\",\n" + "    \"name\": \"Form with Questions\",\n"
		        + "    \"pages\": [\n" + "        {\n" + "            \"sections\": [\n" + "                {\n"
		        + "                    \"questions\": [\n" + "                        {\n"
		        + "                            \"id\": \"patientName\",\n"
		        + "                            \"label\": \"Patient Name\",\n"
		        + "                            \"required\": true,\n"
		        + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"text\"\n" + "                            }\n"
		        + "                        },\n" + "                        {\n"
		        + "                            \"id\": \"patientAge\",\n"
		        + "                            \"label\": \"Patient Age\",\n"
		        + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"number\"\n" + "                            }\n"
		        + "                        }\n" + "                    ]\n" + "                }\n" + "            ]\n"
		        + "        }\n" + "    ]\n" + "}";
		
		FormModel result = parser.parse(json, Locale.ENGLISH);
		
		assertNotNull(result);
		assertEquals(1, result.sheets.size());
		
		SheetModel sheet = result.sheets.get(0);
		assertEquals("Form with Questions", sheet.sheetName);
		assertEquals(2, sheet.fields.size());
		
		// Check first field
		FieldModel nameField = sheet.fields.get(0);
		assertEquals("patientName", nameField.id);
		assertEquals("Patient Name", nameField.label);
		assertEquals(FieldDatatype.TEXT, nameField.datatype);
		assertTrue(nameField.requiredAlways);
		
		// Check second field
		FieldModel ageField = sheet.fields.get(1);
		assertEquals("patientAge", ageField.id);
		assertEquals("Patient Age", ageField.label);
		assertEquals(FieldDatatype.NUMERIC, ageField.datatype);
	}
	
	@Test
	@DisplayName("Should parse coded field with answers")
	void testParseCodedFieldWithAnswers() throws Exception {
		String json = "{\n" + "    \"uuid\": \"coded-form\",\n" + "    \"name\": \"Coded Form\",\n" + "    \"pages\": [\n"
		        + "        {\n" + "            \"sections\": [\n" + "                {\n"
		        + "                    \"questions\": [\n" + "                        {\n"
		        + "                            \"id\": \"gender\",\n"
		        + "                            \"label\": \"Gender\",\n"
		        + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"select\",\n"
		        + "                                \"answers\": [\n" + "                                    {\n"
		        + "                                        \"label\": \"Male\",\n"
		        + "                                        \"concept\": \"male.concept\"\n"
		        + "                                    },\n" + "                                    {\n"
		        + "                                        \"label\": \"Female\",\n"
		        + "                                        \"concept\": \"female.concept\"\n"
		        + "                                    }\n" + "                                ]\n"
		        + "                            }\n" + "                        }\n" + "                    ]\n"
		        + "                }\n" + "            ]\n" + "        }\n" + "    ]\n" + "}";
		
		FormModel result = parser.parse(json, Locale.ENGLISH);
		
		FieldModel genderField = result.sheets.get(0).fields.get(0);
		assertEquals(FieldDatatype.CODED, genderField.datatype);
		assertEquals(2, genderField.answers.size());
		
		AnswerModel maleAnswer = genderField.answers.get(0);
		assertEquals("Male", maleAnswer.label);
		assertEquals("male.concept", maleAnswer.conceptUuid);
		
		AnswerModel femaleAnswer = genderField.answers.get(1);
		assertEquals("Female", femaleAnswer.label);
		assertEquals("female.concept", femaleAnswer.conceptUuid);
	}
	
	@Test
	@DisplayName("Should parse repeat group")
	void testParseRepeatGroup() throws Exception {
		String json = "{\n" + "    \"uuid\": \"repeat-form\",\n" + "    \"name\": \"Repeat Form\",\n" + "    \"pages\": [\n"
		        + "        {\n" + "            \"sections\": [\n" + "                {\n"
		        + "                    \"questions\": [\n" + "                        {\n"
		        + "                            \"id\": \"vitalsGroup\",\n"
		        + "                            \"label\": \"Vitals\",\n"
		        + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"repeating\"\n" + "                            },\n"
		        + "                            \"questions\": [\n" + "                                {\n"
		        + "                                    \"id\": \"temperature\",\n"
		        + "                                    \"label\": \"Temperature\",\n"
		        + "                                    \"questionOptions\": {\n"
		        + "                                        \"rendering\": \"number\"\n"
		        + "                                    }\n" + "                                }\n"
		        + "                            ]\n" + "                        }\n" + "                    ]\n"
		        + "                }\n" + "            ]\n" + "        }\n" + "    ]\n" + "}";
		
		FormModel result = parser.parse(json, Locale.ENGLISH);
		
		// Should have 2 sheets: main form + repeat sheet
		assertEquals(2, result.sheets.size());
		
		// Find the repeat sheet
		SheetModel repeatSheet = result.sheets.stream().filter(s -> s.isRepeat).findFirst().orElse(null);
		
		assertNotNull(repeatSheet);
		assertEquals("Vitals (repeat)", repeatSheet.sheetName);
		assertEquals("vitalsGroup", repeatSheet.repeatGroupId);
		assertEquals(1, repeatSheet.fields.size());
		assertEquals("temperature", repeatSheet.fields.get(0).id);
	}
	
	@Test
	@DisplayName("Should parse form with rules")
	void testParseFormWithRules() throws Exception {
		String json = "{\n" + "    \"uuid\": \"rules-form\",\n" + "    \"name\": \"Rules Form\",\n" + "    \"pages\": [\n"
		        + "        {\n" + "            \"sections\": [\n" + "                {\n"
		        + "                    \"questions\": [\n" + "                        {\n"
		        + "                            \"id\": \"showField\",\n"
		        + "                            \"label\": \"Show Field\",\n" + "                            \"hide\": {\n"
		        + "                                \"hideWhenExpression\": \"triggerField == 'hide'\"\n"
		        + "                            },\n"
		        + "                            \"required\": \"triggerField == 'required'\"\n"
		        + "                        }\n" + "                    ]\n" + "                }\n" + "            ]\n"
		        + "        }\n" + "    ]\n" + "}";
		
		FormModel result = parser.parse(json, Locale.ENGLISH);
		
		assertNotNull(result.rules);
		assertTrue(result.rules.size() > 0);
		
		// Should have relevance and required-if rules
		boolean hasRelevanceRule = result.rules.stream().anyMatch(r -> "relevance".equals(r.type));
		boolean hasRequiredRule = result.rules.stream().anyMatch(r -> "required-if".equals(r.type));
		
		assertTrue(hasRelevanceRule);
		assertTrue(hasRequiredRule);
	}
	
	@Test
	@DisplayName("Should handle malformed JSON gracefully")
	void testMalformedJsonHandling() {
		String malformedJson = "{ invalid json }";
		
		assertThrows(IllegalArgumentException.class, () -> parser.parse(malformedJson, Locale.ENGLISH));
	}
	
	@Test
	@DisplayName("Should handle missing required fields")
	void testMissingRequiredFields() throws Exception {
		String json = "{\n" + "    \"pages\": [\n" + "        {\n" + "            \"sections\": []\n" + "        }\n"
		        + "    ]\n" + "}";
		
		FormModel result = parser.parse(json, Locale.ENGLISH);
		
		assertNotNull(result);
		assertNull(result.formUuid);
		assertNull(result.name); // No fallback provided in this test case
		assertNull(result.version);
		assertNull(result.encounterTypeUuid);
		assertEquals("Form", result.sheets.get(0).sheetName); // Sheet gets the fallback name
	}
	
	@Test
	@DisplayName("Should handle different datatypes")
	void testDifferentDatatypes() throws Exception {
		String json = "{\n" + "    \"uuid\": \"datatypes-form\",\n" + "    \"name\": \"Datatypes Form\",\n"
		        + "    \"pages\": [\n" + "        {\n" + "            \"sections\": [\n" + "                {\n"
		        + "                    \"questions\": [\n" + "                        {\n"
		        + "                            \"id\": \"textField\",\n"
		        + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"text\"\n" + "                            }\n"
		        + "                        },\n" + "                        {\n"
		        + "                            \"id\": \"dateField\",\n"
		        + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"date\"\n" + "                            }\n"
		        + "                        },\n" + "                        {\n"
		        + "                            \"id\": \"booleanField\",\n"
		        + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"checkbox\"\n" + "                            }\n"
		        + "                        }\n" + "                    ]\n" + "                }\n" + "            ]\n"
		        + "        }\n" + "    ]\n" + "}";
		
		FormModel result = parser.parse(json, Locale.ENGLISH);
		
		SheetModel sheet = result.sheets.get(0);
		assertEquals(FieldDatatype.TEXT, sheet.fields.get(0).datatype);
		assertEquals(FieldDatatype.DATE, sheet.fields.get(1).datatype);
		assertEquals(FieldDatatype.TEXT, sheet.fields.get(2).datatype);
	}
}
