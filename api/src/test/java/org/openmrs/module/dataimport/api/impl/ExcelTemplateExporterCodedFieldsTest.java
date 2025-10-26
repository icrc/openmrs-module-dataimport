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

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.model.*;
import org.openmrs.module.dataimport.test.TestDataLoader;

import java.io.ByteArrayInputStream;

@DisplayName("ExcelTemplateExporter - Coded Fields Tests")
class ExcelTemplateExporterCodedFieldsTest {
	
	private O3FormParser parser;
	
	private ExcelTemplateExporter exporter;
	
	@BeforeEach
	void setUp() {
		parser = new O3FormParser();
		exporter = new ExcelTemplateExporter();
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
			// Should have form sheet and hidden _RefData sheet
			assertTrue(wb.getNumberOfSheets() >= 2);
			
			// Check that __lists sheet exists and is hidden
			Sheet listsSheet = wb.getSheet("__lists");
			assertNotNull(listsSheet, "Missing __lists sheet");
			assertTrue(wb.isSheetHidden(wb.getSheetIndex(listsSheet)), "__lists sheet should be hidden");
			
			// Check that the form sheet exists (name might be sanitized)
			Sheet formSheet = null;
			for (int i = 0; i < wb.getNumberOfSheets(); i++) {
				String sheetName = wb.getSheetName(i);
				if (sheetName.contains("Sample Form with Radio Buttons") || sheetName.contains("Radio")) {
					formSheet = wb.getSheetAt(i);
					break;
				}
			}
			assertNotNull(formSheet, "Missing form sheet");
			
			// Check that headers are present
			Row headerRow = formSheet.getRow(0);
			assertNotNull(headerRow, "Missing header row");
			
			// Verify Gender header exists
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
			// Find the form sheet (name might be sanitized)
			Sheet formSheet = null;
			for (int i = 0; i < wb.getNumberOfSheets(); i++) {
				String sheetName = wb.getSheetName(i);
				if (sheetName.contains("Mixed Field Types") || sheetName.contains("Mixed")) {
					formSheet = wb.getSheetAt(i);
					break;
				}
			}
			assertNotNull(formSheet, "Missing form sheet");
			
			// Check that headers are present for all field types
			Row headerRow = formSheet.getRow(0);
			assertNotNull(headerRow, "Missing header row");
			
			// Verify all expected headers exist
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
		// Test with the exact JSON structure from the user
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
		
		// Verify parsing worked correctly
		assertEquals("Surgical Operation", form.name);
		assertEquals(1, form.sheets.size());
		
		SheetModel sheet = form.sheets.get(0);
		assertEquals(1, sheet.fields.size());
		
		FieldModel anesthesiaField = sheet.fields.get(0);
		assertEquals("Anesthesia_Type", anesthesiaField.id);
		assertEquals("Anesthesia Type", anesthesiaField.label);
		assertEquals(FieldDatatype.CODED, anesthesiaField.datatype);
		assertEquals(3, anesthesiaField.answers.size());
		
		// Verify answers are parsed correctly
		AnswerModel generalAnswer = anesthesiaField.answers.get(0);
		assertEquals("General", generalAnswer.label);
		assertEquals("3ca389b4-6b9d-487c-bdd9-73d2cfeeab0c", generalAnswer.conceptUuid);
		
		AnswerModel monitoredAnswer = anesthesiaField.answers.get(1);
		assertEquals("Monitored Anesthesia Care", monitoredAnswer.label);
		assertEquals("5392d074-eed3-4f88-a8e9-5bae333e7589", monitoredAnswer.conceptUuid);
		
		// Test Excel export
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
			
			// Check that Anesthesia Type header exists
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
			
			// Check that __lists sheet has the dropdown options
			assertTrue(listsSheet.getLastRowNum() >= 2, "__lists sheet should have dropdown data");
			
			// Verify that the workbook has named ranges (simplified check)
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
			// Should have main sheet + __lists sheet (repeat groups are on main sheet)
			assertTrue(wb.getNumberOfSheets() >= 2);
			
			// Find the main sheet
			Sheet mainSheet = null;
			for (int i = 0; i < wb.getNumberOfSheets(); i++) {
				String sheetName = wb.getSheetName(i);
				if (!"__lists".equals(sheetName)) {
					mainSheet = wb.getSheetAt(i);
					break;
				}
			}
			assertNotNull(mainSheet, "Missing main form sheet");
			
			// Verify main sheet has all fields including those from repeat groups
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
}
