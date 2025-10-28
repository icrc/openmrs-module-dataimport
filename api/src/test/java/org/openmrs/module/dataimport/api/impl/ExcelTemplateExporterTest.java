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
import org.openmrs.module.dataimport.test.TestDataLoader;

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
    @DisplayName("Should generate Excel with dropdowns for radio button fields")
    void testGenerateExcelWithRadioButtonDropdowns() throws Exception {
        String json = TestDataLoader.loadRadioButtonsForm();
        FormModel form = parser.parse(json, null);

        byte[] excelBytes = exporter.export(form, false);

        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            assertTrue(wb.getNumberOfSheets() >= 2);
            Sheet listsSheet = wb.getSheet("__lists");
            assertNotNull(listsSheet, "Missing __lists sheet");
            assertTrue(wb.isSheetHidden(wb.getSheetIndex(listsSheet)), "__lists sheet should be hidden");

            Sheet formSheet = null;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                String sheetName = wb.getSheetName(i);
                if (sheetName.contains("Sample Form with Radio Buttons") || sheetName.contains("Radio")) {
                    formSheet = wb.getSheetAt(i);
                    break;
                }
            }
            assertNotNull(formSheet, "Missing form sheet");

            Row headerRow = formSheet.getRow(0);
            assertNotNull(headerRow, "Missing header row");

            boolean hasGenderHeader = false;
            boolean hasAnesthesiaHeader = false;
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                String cellValue = headerRow.getCell(i).getStringCellValue();
                if ("Gender".equals(cellValue)) {
                    hasGenderHeader = true;
                }
                if ("Anesthesia Type".equals(cellValue)) {
                    hasAnesthesiaHeader = true;
                }
            }
            assertTrue(hasGenderHeader, "Missing 'Gender' header");
            assertTrue(hasAnesthesiaHeader, "Missing 'Anesthesia Type' header");

            // Check that __lists sheet has the dropdown options
            assertTrue(listsSheet.getLastRowNum() >= 0, "__lists sheet should have data");

            // Verify that named ranges exist for the dropdowns
            assertTrue(wb.getName("L1_gender") != null || wb.getName("Gender") != null,
                    "Missing named range for gender dropdown");
        }
    }

    @Test
    @DisplayName("Should generate Excel with proper field types")
    void testGenerateExcelWithMixedFieldTypes() throws Exception {
        String json = TestDataLoader.loadMixedFieldsForm();
        FormModel form = parser.parse(json, null);

        byte[] excelBytes = exporter.export(form, false);

        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet formSheet = null;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                String sheetName = wb.getSheetName(i);
                if (sheetName.contains("Mixed Field Types") || sheetName.contains("Mixed")) {
                    formSheet = wb.getSheetAt(i);
                    break;
                }
            }
            assertNotNull(formSheet, "Missing form sheet");

            Row headerRow = formSheet.getRow(0);
            assertNotNull(headerRow, "Missing header row");

            String[] expectedHeaders = { "Patient Name", "Age", "Date of Birth", "Last Visit", "Is Active", "Status" };

            for (String expectedHeader : expectedHeaders) {
                boolean found = false;
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    if (expectedHeader.equals(headerRow.getCell(i).getStringCellValue())) {
                        found = true;
                        break;
                    }
                }
                assertTrue(found, "Missing header: " + expectedHeader);
            }
        }
    }

    @Test
    @DisplayName("Should create dropdown for Anesthesia Type with exact JSON structure")
    void testAnesthesiaTypeDropdown() throws Exception {
        String jsonContent = "{\n" + "  \"name\": \"Surgical Operation\",\n" + "  \"description\": \"Surgery Form\",\n"
                + "  \"version\": \"1\",\n" + "  \"published\": true,\n" + "  \"retired\": false,\n"
                + "  \"encounter\": \"Consultation\",\n" + "  \"pages\": [\n" + "    {\n"
                + "      \"label\": \"Surgical Operation\",\n" + "      \"sections\": [\n" + "        {\n"
                + "          \"label\": \"Surgical progress\",\n" + "          \"isExpanded\": \"true\",\n"
                + "          \"questions\": [\n" + "            {\n" + "              \"label\": \"Anesthesia Type\",\n"
                + "              \"type\": \"obs\",\n" + "              \"questionOptions\": {\n"
                + "                \"rendering\": \"radio\",\n"
                + "                \"concept\": \"3017f680-5c3c-4f7b-94e1-6afda9e07eaf\",\n"
                + "                \"answers\": [\n" + "                  {\n"
                + "                    \"concept\": \"3ca389b4-6b9d-487c-bdd9-73d2cfeeab0c\",\n"
                + "                    \"label\": \"General\",\n" + "                    \"conceptMappings\": []\n"
                + "                  },\n" + "                  {\n"
                + "                    \"concept\": \"5392d074-eed3-4f88-a8e9-5bae333e7589\",\n"
                + "                    \"label\": \"Monitored Anesthesia Care\",\n"
                + "                    \"conceptMappings\": []\n" + "                  },\n" + "                  {\n"
                + "                    \"concept\": \"84ba7929-3714-4023-843a-2b1814563207\",\n"
                + "                    \"label\": \"Knee Block\",\n" + "                    \"conceptMappings\": []\n"
                + "                  }\n" + "                ]\n" + "              },\n"
                + "              \"id\": \"Anesthesia_Type\"\n" + "            }\n" + "          ]\n" + "        }\n"
                + "      ]\n" + "    }\n" + "  ],\n" + "  \"processor\": \"EncounterFormProcessor\",\n"
                + "  \"uuid\": \"test-anesthesia-form\",\n" + "  \"referencedForms\": []\n" + "}";

        FormModel form = parser.parse(jsonContent, null);

        assertEquals("Surgical Operation", form.name);
        assertEquals(1, form.sheets.size());

        SheetModel sheet = form.sheets.get(0);
        assertEquals(1, sheet.fields.size());

        FieldModel anesthesiaField = sheet.fields.get(0);
        assertEquals("Anesthesia_Type", anesthesiaField.id);
        assertEquals("Anesthesia Type", anesthesiaField.label);
        assertEquals(FieldDatatype.CODED, anesthesiaField.datatype);
        assertEquals(3, anesthesiaField.answers.size());

        AnswerModel generalAnswer = anesthesiaField.answers.get(0);
        assertEquals("General", generalAnswer.label);
        assertEquals("3ca389b4-6b9d-487c-bdd9-73d2cfeeab0c", generalAnswer.conceptUuid);

        AnswerModel monitoredAnswer = anesthesiaField.answers.get(1);
        assertEquals("Monitored Anesthesia Care", monitoredAnswer.label);
        assertEquals("5392d074-eed3-4f88-a8e9-5bae333e7589", monitoredAnswer.conceptUuid);

        byte[] excelBytes = exporter.export(form, false);
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            // Should have form sheet and hidden __lists sheet
            assertTrue(wb.getNumberOfSheets() >= 2);

            // Check that __lists sheet exists and is hidden
            Sheet listsSheet = wb.getSheet("__lists");
            assertNotNull(listsSheet, "Missing __lists sheet");
            assertTrue(wb.isSheetHidden(wb.getSheetIndex(listsSheet)), "__lists sheet should be hidden");

            // Find the form sheet
            Sheet formSheet = null;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                String sheetName = wb.getSheetName(i);
                if (sheetName.contains("Surgical Operation") || sheetName.contains("Surgical")) {
                    formSheet = wb.getSheetAt(i);
                    break;
                }
            }
            assertNotNull(formSheet, "Missing form sheet");

            Row headerRow = formSheet.getRow(0);
            assertNotNull(headerRow, "Missing header row");

            boolean hasAnesthesiaHeader = false;
            int anesthesiaColumnIndex = -1;
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                String cellValue = headerRow.getCell(i).getStringCellValue();
                if ("Anesthesia Type".equals(cellValue)) {
                    hasAnesthesiaHeader = true;
                    anesthesiaColumnIndex = i;
                    break;
                }
            }
            assertTrue(hasAnesthesiaHeader, "Missing 'Anesthesia Type' header");
            assertTrue(listsSheet.getLastRowNum() >= 2, "__lists sheet should have dropdown data");
            assertTrue(wb.getNumberOfNames() > 0, "Missing named ranges for dropdowns");
        }
    }

    @Test
    @DisplayName("Should handle repeat groups on main sheet")
    void testGenerateExcelWithRepeatGroups() throws Exception {
        String json = TestDataLoader.loadRepeatGroupsForm();
        FormModel form = parser.parse(json, null);

        byte[] excelBytes = exporter.export(form, false);

        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {

            Sheet mainSheet = null;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                String sheetName = wb.getSheetName(i);
                if (!"__lists".equals(sheetName) && !"__meta".equals(sheetName)) {
                    mainSheet = wb.getSheetAt(i);
                    break;
                }
            }
            assertNotNull(mainSheet, "Missing main form sheet");

            Row headerRow = mainSheet.getRow(0);
            assertNotNull(headerRow, "Missing header row");

            boolean hasTemperatureHeader = false;
            boolean hasBloodPressureHeader = false;
            boolean hasPatientNameHeader = false;
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                String cellValue = headerRow.getCell(i).getStringCellValue();
                if ("Temperature".equals(cellValue)) {
                    hasTemperatureHeader = true;
                }
                if ("Blood Pressure".equals(cellValue)) {
                    hasBloodPressureHeader = true;
                }
                if ("Patient Name".equals(cellValue)) {
                    hasPatientNameHeader = true;
                }
            }
            assertTrue(hasPatientNameHeader, "Missing 'Patient Name' header in main sheet");
            assertTrue(hasTemperatureHeader, "Missing 'Temperature' header in main sheet");
            assertTrue(hasBloodPressureHeader, "Missing 'Blood Pressure' header in main sheet");
        }
    }
	
	@Test
	@DisplayName("Should export basic form to Excel template")
	void testExportBasicForm() throws Exception {
		FormModel form = createBasicForm();
		
		byte[] result = exporter.export(form, true);
		
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
		
		byte[] result = exporter.export(form, true);
		
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
			List<String> sheetNames = new ArrayList<>();
			for (int i = 0; i < wb.getNumberOfSheets(); i++) {
				sheetNames.add(wb.getSheetName(i));
			}
			
			assertTrue(sheetNames.contains("Patient Registration"), "Missing Patient Registration sheet");
			
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
		
		byte[] result = exporter.export(form, true);
		
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
			assertTrue(wb.getNumberOfSheets() > 0);
			
			// Check that _RefData sheet has the coded answers
			Sheet listsSheet = wb.getSheet("__lists");
			assertNotNull(listsSheet);
		}
	}
	
	@Test
	@DisplayName("Should handle repeat groups on main sheet")
	void testExportRepeatGroupsOnMainSheet() throws Exception {
		FormModel form = createFormWithRepeatGroups();
		
		byte[] result = exporter.export(form, true);
		
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result))) {
			List<String> sheetNames = new ArrayList<>();
			for (int i = 0; i < wb.getNumberOfSheets(); i++) {
				sheetNames.add(wb.getSheetName(i));
			}
			
			// Should have main sheet with repeat group fields, not a separate repeat sheet
			// The main sheet should contain the temperature field from the "repeat group"
			Sheet mainSheet = null;
			for (int i = 0; i < wb.getNumberOfSheets(); i++) {
				String sheetName = sheetNames.get(i);
				if (!"__lists".equals(sheetName) && !"__meta".equals(sheetName)
				        && !"Patient Registration".equals(sheetName)) {
					mainSheet = wb.getSheetAt(i);
					break;
				}
			}
			assertNotNull(mainSheet, "Missing main form sheet");
			
			Row headerRow = mainSheet.getRow(0);
			boolean hasTemperatureHeader = false;
			for (int i = 0; i < headerRow.getLastCellNum(); i++) {
				String cellValue = headerRow.getCell(i).getStringCellValue();
				if ("Temperature".equals(cellValue)) {
					hasTemperatureHeader = true;
					break;
				}
			}
			assertTrue(hasTemperatureHeader, "Missing 'Temperature' header in main sheet");
		}
	}
	
	@Test
	@DisplayName("Should parse O3 JSON and export to Excel")
	void testParseAndExportO3Json() throws Exception {
		String o3Json = createBasicO3Json();
		
		FormModel form = parser.parse(o3Json, null);
		byte[] result = exporter.export(form, true);
		
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
		
		byte[] result = exporter.export(form, true);
		
		assertNotNull(result);
		assertTrue(result.length > 0);
	}

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
		
		FieldModel tempField = new FieldModel();
		tempField.id = "temperature";
		tempField.label = "Temperature";
		tempField.datatype = FieldDatatype.NUMERIC;
		tempField.conceptUuid = "temperature.concept";
		
		form.sheets.get(0).fields.add(tempField);
		
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
