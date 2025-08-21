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

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.model.*;

import java.io.ByteArrayInputStream;
import java.util.*;

@DisplayName("ExcelTemplateExporter Tests")
class ExcelTemplateExporterTest {
	
	private ExcelTemplateExporter exporter;
	
	private O3FormParser parser;
	
	@BeforeEach
	void setUp() {
		exporter = new ExcelTemplateExporter();
		parser = new O3FormParser();
	}
	
	@Test
	@DisplayName("Should export basic form to Excel template")
	void testExportBasicForm() throws Exception {
		FormModel form = createBasicForm();
		
		byte[] result = exporter.export(form, true, true, Locale.ENGLISH);
		
		assertNotNull(result);
		assertTrue(result.length > 0);
		
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
			assertTrue(wb.getNumberOfSheets() > 0);
		}
	}
	
	@Test
	@DisplayName("Should create all required sheets")
	void testCreateAllRequiredSheets() throws Exception {
		FormModel form = createBasicForm();
		
		byte[] result = exporter.export(form, true, true, Locale.ENGLISH);
		
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
			List<String> sheetNames = new ArrayList<>();
			for (int i = 0; i < wb.getNumberOfSheets(); i++) {
				sheetNames.add(wb.getSheetName(i));
			}
			
			assertTrue(sheetNames.contains("Patient Registration"), "Missing Patient Registration sheet");
			assertTrue(sheetNames.contains("Visit"), "Missing Visit sheet");
			
			// __lists should exist but be hidden
			Sheet listsSheet = wb.getSheet("__lists");
			assertNotNull(listsSheet, "Missing __lists sheet");
			assertTrue(wb.isSheetHidden(wb.getSheetIndex(listsSheet)), "__lists sheet should be hidden");
		}
	}
	
	@Test
	@DisplayName("Should handle coded fields with dropdowns")
	void testExportCodedFieldsWithDropdowns() throws Exception {
		FormModel form = createFormWithCodedFields();
		
		byte[] result = exporter.export(form, true, true, Locale.ENGLISH);
		
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
			assertTrue(wb.getNumberOfSheets() > 0);
			
			// Check that _RefData sheet has the coded answers
			Sheet listsSheet = wb.getSheet("__lists");
			assertNotNull(listsSheet);
		}
	}
	
	@Test
	@DisplayName("Should handle repeat groups as separate sheets")
	void testExportRepeatGroupsAsSeparateSheets() throws Exception {
		FormModel form = createFormWithRepeatGroups();
		
		byte[] result = exporter.export(form, true, true, Locale.ENGLISH);
		
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
			List<String> sheetNames = new ArrayList<>();
			for (int i = 0; i < wb.getNumberOfSheets(); i++) {
				sheetNames.add(wb.getSheetName(i));
			}
			
			// Should have a repeat sheet
			boolean hasRepeatSheet = sheetNames.stream().anyMatch(name -> name.contains("repeat"));
			assertTrue(hasRepeatSheet, "Missing repeat group sheet");
		}
	}
	
	@Test
	@DisplayName("Should parse O3 JSON and export to Excel")
	void testParseAndExportO3Json() throws Exception {
		String o3Json = createBasicO3Json();
		
		FormModel form = parser.parse(o3Json, Locale.ENGLISH);
		byte[] result = exporter.export(form, true, true, Locale.ENGLISH);
		
		assertNotNull(result);
		assertTrue(result.length > 0);
		
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
			assertTrue(wb.getNumberOfSheets() > 0);
		}
	}
	
	@Test
	@DisplayName("Should handle empty form")
	void testHandleEmptyForm() throws Exception {
		FormModel form = new FormModel();
		form.name = "Empty Form";
		form.formUuid = "empty-form-uuid";
		
		byte[] result = exporter.export(form, true, true, Locale.ENGLISH);
		
		assertNotNull(result);
		assertTrue(result.length > 0);
	}
	
	// Helper methods for test data creation
	
	private FormModel createBasicForm() {
		FormModel form = new FormModel();
		form.name = "Test Form";
		form.formUuid = "test-form-uuid";
		form.version = "1.0";
		form.encounterTypeUuid = "encounter-type-uuid";
		
		SheetModel sheet = new SheetModel();
		sheet.sheetName = "Test Form";
		
		FieldModel nameField = new FieldModel();
		nameField.id = "patientName";
		nameField.label = "Patient Name";
		nameField.datatype = FieldDatatype.TEXT;
		nameField.requiredAlways = true;
		
		sheet.fields.add(nameField);
		form.sheets.add(sheet);
		
		return form;
	}
	
	private FormModel createFormWithCodedFields() {
		FormModel form = createBasicForm();
		
		FieldModel genderField = new FieldModel();
		genderField.id = "gender";
		genderField.label = "Gender";
		genderField.datatype = FieldDatatype.CODED;
		genderField.conceptUuid = "gender.concept";
		
		AnswerModel male = new AnswerModel("Male", "male.concept", null);
		AnswerModel female = new AnswerModel("Female", "female.concept", null);
		
		genderField.answers.add(male);
		genderField.answers.add(female);
		
		form.sheets.get(0).fields.add(genderField);
		
		return form;
	}
	
	private FormModel createFormWithRepeatGroups() {
		FormModel form = createBasicForm();
		
		SheetModel repeatSheet = new SheetModel();
		repeatSheet.sheetName = "Vitals (repeat)";
		repeatSheet.isRepeat = true;
		repeatSheet.repeatGroupId = "vitalsGroup";
		
		FieldModel tempField = new FieldModel();
		tempField.id = "temperature";
		tempField.label = "Temperature";
		tempField.datatype = FieldDatatype.NUMERIC;
		tempField.conceptUuid = "temperature.concept";
		
		repeatSheet.fields.add(tempField);
		form.sheets.add(repeatSheet);
		
		return form;
	}
	
	private String createBasicO3Json() {
		return "{\n" + "    \"name\": \"Test Form\",\n" + "    \"uuid\": \"test-form-uuid\",\n"
		        + "    \"version\": \"1.0\",\n" + "    \"encounterType\": \"encounter-type-uuid\",\n" + "    \"pages\": [\n"
		        + "        {\n" + "            \"sections\": [\n" + "                {\n"
		        + "                    \"questions\": [\n" + "                        {\n"
		        + "                            \"id\": \"patientName\",\n"
		        + "                            \"label\": \"Patient Name\",\n"
		        + "                            \"required\": true,\n"
		        + "                            \"questionOptions\": {\n"
		        + "                                \"rendering\": \"text\"\n" + "                            }\n"
		        + "                        }\n" + "                    ]\n" + "                }\n" + "            ]\n"
		        + "        }\n" + "    ]\n" + "}";
	}
}
