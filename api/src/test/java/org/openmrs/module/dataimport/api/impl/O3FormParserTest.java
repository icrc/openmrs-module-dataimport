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
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.SheetModel;
import org.openmrs.module.dataimport.test.TestDataLoader;

import java.util.Locale;

@DisplayName("O3FormParser Tests")
class O3FormParserTest {
	
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
		
		FieldModel genderField = sheet.fields.get(0);
		assertEquals("gender", genderField.id);
		assertEquals("Gender", genderField.label);
		assertEquals("gender.concept.uuid", genderField.conceptUuid);
		assertEquals(FieldDatatype.CODED, genderField.datatype);
		assertEquals(3, genderField.answers.size());
		
		AnswerModel maleAnswer = genderField.answers.get(0);
		assertEquals("Male", maleAnswer.label);
		assertEquals("male.concept.uuid", maleAnswer.conceptUuid);
		assertEquals("male.concept.uuid", maleAnswer.value);
		
		AnswerModel femaleAnswer = genderField.answers.get(1);
		assertEquals("Female", femaleAnswer.label);
		assertEquals("female.concept.uuid", femaleAnswer.conceptUuid);
		assertEquals("female.concept.uuid", femaleAnswer.value);
		
		FieldModel anesthesiaField = sheet.fields.get(1);
		assertEquals("anesthesia_type", anesthesiaField.id);
		assertEquals("Anesthesia Type", anesthesiaField.label);
		assertEquals("anesthesia.concept.uuid", anesthesiaField.conceptUuid);
		assertEquals(FieldDatatype.CODED, anesthesiaField.datatype);
		assertEquals(3, anesthesiaField.answers.size());
		
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
		
		FieldModel nameField = sheet.fields.stream().filter(f -> "patient_name".equals(f.id)).findFirst()
		        .orElseThrow(() -> new AssertionError("Patient name field not found"));
		
		assertEquals("Patient Name", nameField.label);
		assertEquals(FieldDatatype.TEXT, nameField.datatype);
		assertTrue(nameField.answers.isEmpty());
	}
	
	@Test
	@DisplayName("Should mark ui-select-extended fields as coded with source type")
	void testParseUiSelectExtendedField() throws Exception {
		String json = "{\n" + "    \"uuid\": \"encounter-role-form\",\n" + "    \"name\": \"Encounter Role Form\",\n"
		        + "    \"pages\": [\n" + "        {\n" + "            \"sections\": [\n" + "                {\n"
		        + "                    \"questions\": [\n" + "                        {\n"
		        + "                            \"id\": \"role\",\n" + "                            \"label\": \"Role:\",\n"
		        + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"ui-select-extended\",\n"
		        + "                                \"type\": \"encounterRole\"\n" + "                            }\n"
		        + "                        }\n" + "                    ]\n" + "                }\n" + "            ]\n"
		        + "        }\n" + "    ]\n" + "}";
		
		FormModel form = parser.parse(json, Locale.ENGLISH);
		SheetModel sheet = form.sheets.get(0);
		FieldModel roleField = sheet.fields.get(0);
		
		assertEquals(FieldDatatype.CODED, roleField.datatype);
		assertEquals("encounterRole", roleField.uiSelectSourceType);
		assertTrue(roleField.answers.isEmpty());
	}
	
	@Test
	@DisplayName("Should infer ui-select source type from question type when not specified")
	void testUiSelectExtendedFieldUsingQuestionType() throws Exception {
		String json = "{\n" + "    \"uuid\": \"encounter-provider-form\",\n" + "    \"name\": \"Encounter Provider Form\",\n"
		        + "    \"pages\": [\n" + "        {\n" + "            \"sections\": [\n" + "                {\n"
		        + "                    \"questions\": [\n" + "                        {\n"
		        + "                            \"id\": \"provider\",\n"
		        + "                            \"type\": \"encounterProvider\",\n"
		        + "                            \"label\": \"Encounter Provider:\",\n"
		        + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"ui-select-extended\"\n"
		        + "                            }\n" + "                        }\n" + "                    ]\n"
		        + "                }\n" + "            ]\n" + "        }\n" + "    ]\n" + "}";
		
		FormModel form = parser.parse(json, Locale.ENGLISH);
		FieldModel providerField = form.sheets.get(0).fields.get(0);
		
		assertEquals(FieldDatatype.CODED, providerField.datatype);
		assertEquals("encounterProvider", providerField.uiSelectSourceType);
	}
	
	@Test
	@DisplayName("Should capture datasource configuration for ui-select-extended fields")
	void testUiSelectExtendedFieldDatasourceConfig() throws Exception {
		String json = "{\n" + "    \"uuid\": \"location-form\",\n" + "    \"name\": \"Location Form\",\n"
		        + "    \"pages\": [\n" + "        {\n" + "            \"sections\": [\n" + "                {\n"
		        + "                    \"questions\": [\n" + "                        {\n"
		        + "                            \"id\": \"admitToLocation\",\n"
		        + "                            \"type\": \"obs\",\n" + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"ui-select-extended\",\n"
		        + "                                \"datasource\": {\n"
		        + "                                    \"name\": \"location_datasource\",\n"
		        + "                                    \"config\": {\n"
		        + "                                        \"tag\": \"Admission Location\"\n"
		        + "                                    }\n" + "                                }\n"
		        + "                            }\n" + "                        }\n" + "                    ]\n"
		        + "                }\n" + "            ]\n" + "        }\n" + "    ]\n" + "}";
		
		FormModel form = parser.parse(json, Locale.ENGLISH);
		FieldModel locationField = form.sheets.get(0).fields.get(0);
		
		assertEquals(FieldDatatype.CODED, locationField.datatype);
		assertEquals("location_datasource", locationField.uiSelectDatasourceName);
		assertEquals("Admission Location", locationField.uiSelectDatasourceConfig.get("tag"));
	}
	
	@Test
	@DisplayName("Should correctly infer datatypes for all field types")
	void testInferDatatypes() throws Exception {
		String json = TestDataLoader.loadMixedFieldsForm();
		FormModel form = parser.parse(json, Locale.ENGLISH);
		
		SheetModel sheet = form.sheets.get(0);
		
		FieldModel textField = findField(sheet, "patient_name");
		assertEquals(FieldDatatype.TEXT, textField.datatype);
		
		FieldModel numberField = findField(sheet, "age");
		assertEquals(FieldDatatype.NUMERIC, numberField.datatype);
		assertEquals(0.0, numberField.min);
		assertEquals(120.0, numberField.max);
		
		FieldModel dateField = findField(sheet, "date_of_birth");
		assertEquals(FieldDatatype.DATE, dateField.datatype);
		
		FieldModel datetimeField = findField(sheet, "last_visit");
		assertEquals(FieldDatatype.DATETIME, datetimeField.datatype);
		
		FieldModel booleanField = findField(sheet, "is_active");
		assertEquals(FieldDatatype.CODED, booleanField.datatype); // checkbox without answers is CODED
		
		FieldModel codedField = findField(sheet, "status");
		assertEquals(FieldDatatype.CODED, codedField.datatype);
		assertEquals(2, codedField.answers.size());
	}
	
	@Test
	@DisplayName("Should handle repeat groups as regular groups on main sheet")
	void testParseRepeatGroups() throws Exception {
		String json = TestDataLoader.loadRepeatGroupsForm();
		FormModel form = parser.parse(json, Locale.ENGLISH);
		
		assertEquals(1, form.sheets.size());
		SheetModel mainSheet = form.sheets.get(0);
		FieldModel nameField = findField(mainSheet, "patient_name");
		assertEquals("Patient Name", nameField.label);
		
		FieldModel tempField = findField(mainSheet, "temperature");
		assertEquals(FieldDatatype.NUMERIC, tempField.datatype);
		assertEquals(30.0, tempField.min);
		assertEquals(45.0, tempField.max);
		
		FieldModel bpField = findField(mainSheet, "blood_pressure");
		assertEquals(FieldDatatype.TEXT, bpField.datatype);
	}
	
	private FieldModel findField(SheetModel sheet, String fieldId) {
		return sheet.fields.stream().filter(f -> fieldId.equals(f.id)).findFirst()
		        .orElseThrow(() -> new AssertionError("Field not found: " + fieldId));
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
	@DisplayName("Should parse repeat group as regular group on main sheet")
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
		
		// Should have 1 sheet: main form (repeat groups are treated as regular groups)
		assertEquals(1, result.sheets.size());
		
		// The temperature field should be on the main sheet
		SheetModel mainSheet = result.sheets.get(0);
		assertEquals(1, mainSheet.fields.size());
		assertEquals("temperature", mainSheet.fields.get(0).id);
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
		assertEquals(FieldDatatype.CODED, sheet.fields.get(2).datatype); // checkbox without answers is CODED
	}
	
	@Test
	@DisplayName("Should set isMulti flag for multiCheckbox fields")
	void testMultiCheckboxIsMultiFlag() throws Exception {
		String json = "{\n" + "    \"uuid\": \"checkbox-form\",\n" + "    \"name\": \"Checkbox Form\",\n"
		        + "    \"pages\": [\n" + "        {\n" + "            \"sections\": [\n" + "                {\n"
		        + "                    \"questions\": [\n" + "                        {\n"
		        + "                            \"id\": \"singleCheckbox\",\n"
		        + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"checkbox\",\n"
		        + "                                \"concept\": \"checkbox.concept\",\n"
		        + "                                \"answers\": [\n"
		        + "                                    {\"label\": \"Yes\", \"concept\": \"yes.concept\"},\n"
		        + "                                    {\"label\": \"No\", \"concept\": \"no.concept\"}\n"
		        + "                                ]\n" + "                            }\n" + "                        },\n"
		        + "                        {\n" + "                            \"id\": \"multiCheckbox\",\n"
		        + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"multiCheckbox\",\n"
		        + "                                \"concept\": \"allergies.concept\",\n"
		        + "                                \"answers\": [\n"
		        + "                                    {\"label\": \"Penicillin\", \"concept\": \"pen.concept\"},\n"
		        + "                                    {\"label\": \"Sulfa\", \"concept\": \"sulfa.concept\"}\n"
		        + "                                ]\n" + "                            }\n" + "                        }\n"
		        + "                    ]\n" + "                }\n" + "            ]\n" + "        }\n" + "    ]\n" + "}";
		
		FormModel result = parser.parse(json, Locale.ENGLISH);
		
		SheetModel sheet = result.sheets.get(0);
		FieldModel singleCheckbox = findField(sheet, "singleCheckbox");
		assertEquals(FieldDatatype.CODED, singleCheckbox.datatype);
		assertEquals(2, singleCheckbox.answers.size());
		assertTrue(singleCheckbox.isMulti == false); // checkbox is NOT multi
		
		FieldModel multiCheckbox = findField(sheet, "multiCheckbox");
		assertEquals(FieldDatatype.CODED, multiCheckbox.datatype);
		assertEquals(2, multiCheckbox.answers.size());
		assertTrue(multiCheckbox.isMulti == true); // multiCheckbox IS multi
	}
}
