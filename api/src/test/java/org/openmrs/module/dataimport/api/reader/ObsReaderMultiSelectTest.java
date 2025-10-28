/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.reader;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.api.ObsBlueprint;
import org.openmrs.module.dataimport.model.ColumnMeta;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.SheetMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DisplayName("ObsReader Multi-Select Tests")
class ObsReaderMultiSelectTest {
	
	@Test
	@DisplayName("Should create multiple observations for multi-select coded field")
	void testMultiSelectCodedField() throws Exception {
		// Create test workbook with multi-select data
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Test Sheet");
		
		// Create header row
		Row headerRow = sheet.createRow(0);
		headerRow.createCell(0).setCellValue("Patient Ref");
		headerRow.createCell(1).setCellValue("Symptoms");
		
		// Create data row with multi-select values
		Row dataRow = sheet.createRow(1);
		dataRow.createCell(0).setCellValue("P001");
		dataRow.createCell(1).setCellValue("Fever|Headache|Nausea");
		
		// Create column metadata for multi-select coded field
		ColumnMeta symptomsColumn = new ColumnMeta();
		symptomsColumn.sheetName = "Test Sheet";
		symptomsColumn.colIndex = 1;
		symptomsColumn.fieldId = "symptoms";
		symptomsColumn.label = "Symptoms";
		symptomsColumn.conceptUuid = "symptoms-concept-uuid";
		symptomsColumn.datatype = FieldDatatype.CODED;
		symptomsColumn.isMulti = true;
		
		// Set up answers mapping
		Map<String, String> answers = new HashMap<>();
		answers.put("fever", "fever-concept-uuid");
		answers.put("headache", "headache-concept-uuid");
		answers.put("nausea", "nausea-concept-uuid");
		symptomsColumn.answers = answers;
		
		// Create sheet metadata
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.sheetName = "Test Sheet";
		sheetMeta.columns.add(symptomsColumn);
		
		// Test multi-select observation creation
		List<ObsBlueprint> obs = ObsReader.buildObs(sheet, 1, sheetMeta);
		
		// Should create 3 separate observations
		assertEquals(3, obs.size(), "Should create one observation per selected answer");
		
		// Verify each observation
		assertTrue(obs.stream().anyMatch(o -> o.getConceptUuid().equals("symptoms-concept-uuid")
		        && ((ObsBlueprint.ObsValue.CodedValue) o.getValue()).getAnswerConceptUuid().equals("fever-concept-uuid")),
		    "Should have fever observation");
		
		assertTrue(obs.stream().anyMatch(o -> o.getConceptUuid().equals("symptoms-concept-uuid")
		        && ((ObsBlueprint.ObsValue.CodedValue) o.getValue()).getAnswerConceptUuid().equals("headache-concept-uuid")),
		    "Should have headache observation");
		
		assertTrue(obs.stream().anyMatch(o -> o.getConceptUuid().equals("symptoms-concept-uuid")
		        && ((ObsBlueprint.ObsValue.CodedValue) o.getValue()).getAnswerConceptUuid().equals("nausea-concept-uuid")),
		    "Should have nausea observation");
		
		workbook.close();
	}
	
	@Test
	@DisplayName("Should handle unknown values in multi-select gracefully")
	void testMultiSelectWithUnknownValues() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Test Sheet");
		
		Row dataRow = sheet.createRow(1);
		dataRow.createCell(1).setCellValue("Fever|UnknownSymptom|Headache");
		
		ColumnMeta symptomsColumn = new ColumnMeta();
		symptomsColumn.sheetName = "Test Sheet";
		symptomsColumn.colIndex = 1;
		symptomsColumn.fieldId = "symptoms";
		symptomsColumn.label = "Symptoms";
		symptomsColumn.conceptUuid = "symptoms-concept-uuid";
		symptomsColumn.datatype = FieldDatatype.CODED;
		symptomsColumn.isMulti = true;
		
		Map<String, String> answers = new HashMap<>();
		answers.put("fever", "fever-concept-uuid");
		answers.put("headache", "headache-concept-uuid");
		symptomsColumn.answers = answers;
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.sheetName = "Test Sheet";
		sheetMeta.columns.add(symptomsColumn);
		
		List<ObsBlueprint> obs = ObsReader.buildObs(sheet, 1, sheetMeta);
		
		// Should only create observations for known values
		assertEquals(2, obs.size(), "Should only create observations for known values");
		
		workbook.close();
	}
	
	@Test
	@DisplayName("Should handle empty multi-select values")
	void testEmptyMultiSelect() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Test Sheet");
		
		Row dataRow = sheet.createRow(1);
		dataRow.createCell(1).setCellValue("");
		
		ColumnMeta symptomsColumn = new ColumnMeta();
		symptomsColumn.sheetName = "Test Sheet";
		symptomsColumn.colIndex = 1;
		symptomsColumn.fieldId = "symptoms";
		symptomsColumn.label = "Symptoms";
		symptomsColumn.conceptUuid = "symptoms-concept-uuid";
		symptomsColumn.datatype = FieldDatatype.CODED;
		symptomsColumn.isMulti = true;
		symptomsColumn.answers = new HashMap<>();
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.sheetName = "Test Sheet";
		sheetMeta.columns.add(symptomsColumn);
		
		List<ObsBlueprint> obs = ObsReader.buildObs(sheet, 1, sheetMeta);
		
		assertEquals(0, obs.size(), "Should not create observations for empty values");
		
		workbook.close();
	}
	
	@Test
	@DisplayName("Should handle single value in multi-select field")
	void testSingleValueInMultiSelect() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Test Sheet");
		
		Row dataRow = sheet.createRow(1);
		dataRow.createCell(1).setCellValue("Fever");
		
		ColumnMeta symptomsColumn = new ColumnMeta();
		symptomsColumn.sheetName = "Test Sheet";
		symptomsColumn.colIndex = 1;
		symptomsColumn.fieldId = "symptoms";
		symptomsColumn.label = "Symptoms";
		symptomsColumn.conceptUuid = "symptoms-concept-uuid";
		symptomsColumn.datatype = FieldDatatype.CODED;
		symptomsColumn.isMulti = true;
		
		Map<String, String> answers = new HashMap<>();
		answers.put("fever", "fever-concept-uuid");
		symptomsColumn.answers = answers;
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.sheetName = "Test Sheet";
		sheetMeta.columns.add(symptomsColumn);
		
		List<ObsBlueprint> obs = ObsReader.buildObs(sheet, 1, sheetMeta);
		
		assertEquals(1, obs.size(), "Should create one observation for single value");
		
		ObsBlueprint obsBlueprint = obs.get(0);
		assertEquals("symptoms-concept-uuid", obsBlueprint.getConceptUuid());
		assertEquals("fever-concept-uuid",
		    ((ObsBlueprint.ObsValue.CodedValue) obsBlueprint.getValue()).getAnswerConceptUuid());
		
		workbook.close();
	}
}
