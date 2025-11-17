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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.model.ColumnMeta;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.ObsBlueprint;
import org.openmrs.module.dataimport.model.SheetMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ObsReaderTest {
	
	@Test
	public void testMultiSelectCodedField() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Test Sheet");
		
		Row headerRow = sheet.createRow(0);
		headerRow.createCell(0).setCellValue("Patient Ref");
		headerRow.createCell(1).setCellValue("Symptoms");
		
		Row dataRow = sheet.createRow(1);
		dataRow.createCell(0).setCellValue("P001");
		dataRow.createCell(1).setCellValue("Fever|Headache|Nausea");
		
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
		answers.put("nausea", "nausea-concept-uuid");
		symptomsColumn.answers = answers;
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.sheetName = "Test Sheet";
		sheetMeta.columns.add(symptomsColumn);
		
		ExcelReader.ObsBuildOutcome outcome = ExcelReader.buildObsDetailed(sheet, 1, sheetMeta);
		List<ObsBlueprint> obs = outcome.getObs();
		
		assertEquals(3, obs.size(), "Should create one observation per selected answer");
		
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
	public void testMultiSelectWithUnknownValues() throws Exception {
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
		
		ExcelReader.ObsBuildOutcome outcome = ExcelReader.buildObsDetailed(sheet, 1, sheetMeta);
		
		assertEquals(2, outcome.getObs().size(), "Should still capture known coded values");
		assertEquals(1, outcome.getIssues().size(), "Should report unknown coded values");
		assertTrue(outcome.getIssues().get(0).getMessage().contains("Unknown coded values"));
		assertEquals("Fever|UnknownSymptom|Headache", outcome.getIssues().get(0).getRawValue());
		
		workbook.close();
	}
	
	@Test
	public void testUnknownCodedValueRecordsIssue() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Test Sheet");
		
		Row dataRow = sheet.createRow(1);
		dataRow.createCell(1).setCellValue("UnknownSymptom");
		
		ColumnMeta symptomsColumn = new ColumnMeta();
		symptomsColumn.sheetName = "Test Sheet";
		symptomsColumn.colIndex = 1;
		symptomsColumn.fieldId = "symptoms";
		symptomsColumn.label = "Symptoms";
		symptomsColumn.conceptUuid = "symptoms-concept-uuid";
		symptomsColumn.datatype = FieldDatatype.CODED;
		symptomsColumn.answers = new HashMap<>();
		symptomsColumn.answers.put("fever", "fever-concept-uuid");
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.sheetName = "Test Sheet";
		sheetMeta.columns.add(symptomsColumn);
		
		ExcelReader.ObsBuildOutcome outcome = ExcelReader.buildObsDetailed(sheet, 1, sheetMeta);
		assertEquals(0, outcome.getObs().size(), "Should not create observation for unknown coded value");
		assertEquals(1, outcome.getIssues().size(), "Should record a single issue");
		ExcelReader.ObsBuildIssue issue = outcome.getIssues().get(0);
		assertTrue(issue.getMessage().contains("Unknown coded value"), "Issue message should mention unknown value");
		assertEquals("UnknownSymptom", issue.getRawValue());
		
		workbook.close();
	}
	
	@Test
	public void testInvalidNumericValueRecordsIssue() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Vitals");
		
		Row dataRow = sheet.createRow(1);
		dataRow.createCell(1).setCellValue("NotANumber");
		
		ColumnMeta weightColumn = new ColumnMeta();
		weightColumn.sheetName = "Vitals";
		weightColumn.colIndex = 1;
		weightColumn.fieldId = "weight";
		weightColumn.label = "Weight";
		weightColumn.conceptUuid = "weight-concept-uuid";
		weightColumn.datatype = FieldDatatype.NUMERIC;
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.sheetName = "Vitals";
		sheetMeta.columns.add(weightColumn);
		
		ExcelReader.ObsBuildOutcome outcome = ExcelReader.buildObsDetailed(sheet, 1, sheetMeta);
		
		assertEquals(0, outcome.getObs().size(), "Should not create obs for invalid numeric value");
		assertEquals(1, outcome.getIssues().size(), "Should record numeric validation issue");
		assertTrue(outcome.getIssues().get(0).getMessage().contains("Expected numeric value"));
		assertEquals("NotANumber", outcome.getIssues().get(0).getRawValue());
		
		workbook.close();
	}
	
	@Test
	public void testInvalidDateValueRecordsIssue() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Vitals");
		
		Row dataRow = sheet.createRow(1);
		dataRow.createCell(1).setCellValue("2025-02-30");
		
		ColumnMeta visitDateColumn = new ColumnMeta();
		visitDateColumn.sheetName = "Vitals";
		visitDateColumn.colIndex = 1;
		visitDateColumn.fieldId = "visitDate";
		visitDateColumn.label = "Visit Date";
		visitDateColumn.conceptUuid = "visit-date-concept-uuid";
		visitDateColumn.datatype = FieldDatatype.DATE;
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.sheetName = "Vitals";
		sheetMeta.columns.add(visitDateColumn);
		
		ExcelReader.ObsBuildOutcome outcome = ExcelReader.buildObsDetailed(sheet, 1, sheetMeta);
		
		assertEquals(0, outcome.getObs().size(), "Should not create obs for invalid date value");
		assertEquals(1, outcome.getIssues().size(), "Should record date validation issue");
		assertTrue(outcome.getIssues().get(0).getMessage().contains("Expected date value"));
		assertEquals("2025-02-30", outcome.getIssues().get(0).getRawValue());
		
		workbook.close();
	}
	
	@Test
	public void testInvalidBooleanValueRecordsIssue() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Vitals");
		
		Row dataRow = sheet.createRow(1);
		dataRow.createCell(1).setCellValue("maybe");
		
		ColumnMeta pregnantColumn = new ColumnMeta();
		pregnantColumn.sheetName = "Vitals";
		pregnantColumn.colIndex = 1;
		pregnantColumn.fieldId = "pregnant";
		pregnantColumn.label = "Pregnant";
		pregnantColumn.conceptUuid = "pregnant-concept-uuid";
		pregnantColumn.datatype = FieldDatatype.BOOLEAN;
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.sheetName = "Vitals";
		sheetMeta.columns.add(pregnantColumn);
		
		ExcelReader.ObsBuildOutcome outcome = ExcelReader.buildObsDetailed(sheet, 1, sheetMeta);
		
		assertEquals(0, outcome.getObs().size(), "Should not create obs for invalid boolean value");
		assertEquals(1, outcome.getIssues().size(), "Should record boolean validation issue");
		assertTrue(outcome.getIssues().get(0).getMessage().contains("Expected boolean value"));
		assertEquals("maybe", outcome.getIssues().get(0).getRawValue());
		
		workbook.close();
	}
	
	@Test
	public void testEmptyMultiSelect() throws Exception {
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
		
		ExcelReader.ObsBuildOutcome outcome = ExcelReader.buildObsDetailed(sheet, 1, sheetMeta);
		List<ObsBlueprint> obs = outcome.getObs();
		
		assertEquals(0, obs.size(), "Should not create observations for empty values");
		
		workbook.close();
	}
	
	@Test
	public void testSingleValueInMultiSelect() throws Exception {
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
		
		ExcelReader.ObsBuildOutcome outcome = ExcelReader.buildObsDetailed(sheet, 1, sheetMeta);
		List<ObsBlueprint> obs = outcome.getObs();
		
		assertEquals(1, obs.size(), "Should create one observation for single value");
		
		ObsBlueprint obsBlueprint = obs.get(0);
		assertEquals("symptoms-concept-uuid", obsBlueprint.getConceptUuid());
		assertEquals("fever-concept-uuid",
		    ((ObsBlueprint.ObsValue.CodedValue) obsBlueprint.getValue()).getAnswerConceptUuid());
		
		workbook.close();
	}
}
