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

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.model.ColumnMeta;
import org.openmrs.module.dataimport.model.TemplateMeta;

@DisplayName("MetaReader Validation Tests")
class MetaReaderValidationTest {
	
	@Test
	@DisplayName("Should validate missing datatype and fail fast")
	void testMissingDatatypeValidation() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet metaSheet = workbook.createSheet("__meta");
		
		// Create template metadata
		Row templateRow = metaSheet.createRow(0);
		templateRow.createCell(0).setCellValue("templateVersion");
		templateRow.createCell(1).setCellValue("1");
		
		// Create column header row
		Row headerRow = metaSheet.createRow(1);
		headerRow.createCell(0).setCellValue("sheetName");
		headerRow.createCell(1).setCellValue("colIndex");
		headerRow.createCell(2).setCellValue("fieldId");
		headerRow.createCell(3).setCellValue("label");
		headerRow.createCell(4).setCellValue("conceptUuid");
		headerRow.createCell(5).setCellValue("datatype");
		headerRow.createCell(6).setCellValue("isRepeat");
		headerRow.createCell(7).setCellValue("isMulti");
		headerRow.createCell(8).setCellValue("answersJson");
		
		// Create invalid column row - missing datatype
		Row invalidRow = metaSheet.createRow(2);
		invalidRow.createCell(0).setCellValue("Test Sheet");
		invalidRow.createCell(1).setCellValue(1);
		invalidRow.createCell(2).setCellValue("testField");
		invalidRow.createCell(3).setCellValue("Test Field");
		invalidRow.createCell(4).setCellValue("test-concept-uuid");
		// Missing datatype cell (cell 5)
		invalidRow.createCell(6).setCellValue(false);
		invalidRow.createCell(7).setCellValue(false);
		invalidRow.createCell(8).setCellValue("{}");
		
		// Should throw exception during validation
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
			MetaReader.read(workbook);
		});
		
		assertTrue(exception.getMessage().contains("missing datatype"));
		assertTrue(exception.getMessage().contains("Test Field"));
		
		workbook.close();
	}
	
	@Test
	@DisplayName("Should validate missing conceptUuid and fail fast")
	void testMissingConceptUuidValidation() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet metaSheet = workbook.createSheet("__meta");
		
		// Create template metadata
		Row templateRow = metaSheet.createRow(0);
		templateRow.createCell(0).setCellValue("templateVersion");
		templateRow.createCell(1).setCellValue("1");
		
		// Create column header row
		Row headerRow = metaSheet.createRow(1);
		headerRow.createCell(0).setCellValue("sheetName");
		headerRow.createCell(1).setCellValue("colIndex");
		headerRow.createCell(2).setCellValue("fieldId");
		headerRow.createCell(3).setCellValue("label");
		headerRow.createCell(4).setCellValue("conceptUuid");
		headerRow.createCell(5).setCellValue("datatype");
		headerRow.createCell(6).setCellValue("isRepeat");
		headerRow.createCell(7).setCellValue("isMulti");
		headerRow.createCell(8).setCellValue("answersJson");
		
		// Create invalid column row - missing conceptUuid
		Row invalidRow = metaSheet.createRow(2);
		invalidRow.createCell(0).setCellValue("Test Sheet");
		invalidRow.createCell(1).setCellValue(1);
		invalidRow.createCell(2).setCellValue("testField");
		invalidRow.createCell(3).setCellValue("Test Field");
		// Missing conceptUuid cell (cell 4)
		invalidRow.createCell(5).setCellValue("TEXT");
		invalidRow.createCell(6).setCellValue(false);
		invalidRow.createCell(7).setCellValue(false);
		invalidRow.createCell(8).setCellValue("{}");
		
		// Should throw exception during validation
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
			MetaReader.read(workbook);
		});
		
		assertTrue(exception.getMessage().contains("missing conceptUuid"));
		assertTrue(exception.getMessage().contains("Test Field"));
		
		workbook.close();
	}
	
	@Test
	@DisplayName("Should validate missing coded answers and fail fast")
	void testMissingCodedAnswersValidation() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet metaSheet = workbook.createSheet("__meta");
		
		// Create template metadata
		Row templateRow = metaSheet.createRow(0);
		templateRow.createCell(0).setCellValue("templateVersion");
		templateRow.createCell(1).setCellValue("1");
		
		// Create column header row
		Row headerRow = metaSheet.createRow(1);
		headerRow.createCell(0).setCellValue("sheetName");
		headerRow.createCell(1).setCellValue("colIndex");
		headerRow.createCell(2).setCellValue("fieldId");
		headerRow.createCell(3).setCellValue("label");
		headerRow.createCell(4).setCellValue("conceptUuid");
		headerRow.createCell(5).setCellValue("datatype");
		headerRow.createCell(6).setCellValue("isRepeat");
		headerRow.createCell(7).setCellValue("isMulti");
		headerRow.createCell(8).setCellValue("answersJson");
		
		// Create invalid column row - CODED field without answers
		Row invalidRow = metaSheet.createRow(2);
		invalidRow.createCell(0).setCellValue("Test Sheet");
		invalidRow.createCell(1).setCellValue(1);
		invalidRow.createCell(2).setCellValue("testField");
		invalidRow.createCell(3).setCellValue("Test Field");
		invalidRow.createCell(4).setCellValue("test-concept-uuid");
		invalidRow.createCell(5).setCellValue("CODED");
		invalidRow.createCell(6).setCellValue(false);
		invalidRow.createCell(7).setCellValue(false);
		// Missing answers cell (cell 8)
		
		// Should throw exception during validation
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
			MetaReader.read(workbook);
		});
		
		assertTrue(exception.getMessage().contains("missing coded answers mapping"));
		assertTrue(exception.getMessage().contains("Test Field"));
		
		workbook.close();
	}
	
	@Test
	@DisplayName("Should allow patientRef columns without conceptUuid")
	void testPatientRefColumnAllowedWithoutConceptUuid() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet metaSheet = workbook.createSheet("__meta");
		
		// Create template metadata
		Row templateRow = metaSheet.createRow(0);
		templateRow.createCell(0).setCellValue("templateVersion");
		templateRow.createCell(1).setCellValue("1");
		
		// Create column header row
		Row headerRow = metaSheet.createRow(1);
		headerRow.createCell(0).setCellValue("sheetName");
		headerRow.createCell(1).setCellValue("colIndex");
		headerRow.createCell(2).setCellValue("fieldId");
		headerRow.createCell(3).setCellValue("label");
		headerRow.createCell(4).setCellValue("conceptUuid");
		headerRow.createCell(5).setCellValue("datatype");
		headerRow.createCell(6).setCellValue("isRepeat");
		headerRow.createCell(7).setCellValue("isMulti");
		headerRow.createCell(8).setCellValue("answersJson");
		
		// Create valid patientRef column row
		Row patientRefRow = metaSheet.createRow(2);
		patientRefRow.createCell(0).setCellValue("Test Sheet");
		patientRefRow.createCell(1).setCellValue(0);
		patientRefRow.createCell(2).setCellValue("patientRef");
		patientRefRow.createCell(3).setCellValue("Patient Ref");
		// No conceptUuid for patientRef - should be allowed
		patientRefRow.createCell(5).setCellValue("TEXT");
		patientRefRow.createCell(6).setCellValue(false);
		patientRefRow.createCell(7).setCellValue(false);
		patientRefRow.createCell(8).setCellValue("{}");
		
		// Should not throw exception
		assertDoesNotThrow(() -> {
			TemplateMeta meta = MetaReader.read(workbook);
			assertNotNull(meta);
			assertEquals(1, meta.sheets.size());
			assertEquals(1, meta.getSheetMeta("Test Sheet").columns.size());
		});
		
		workbook.close();
	}
	
	@Test
	@DisplayName("Should validate valid meta sheet successfully")
	void testValidMetaSheet() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet metaSheet = workbook.createSheet("__meta");
		
		// Create template metadata
		Row templateRow = metaSheet.createRow(0);
		templateRow.createCell(0).setCellValue("templateVersion");
		templateRow.createCell(1).setCellValue("1");
		
		// Create column header row
		Row headerRow = metaSheet.createRow(1);
		headerRow.createCell(0).setCellValue("sheetName");
		headerRow.createCell(1).setCellValue("colIndex");
		headerRow.createCell(2).setCellValue("fieldId");
		headerRow.createCell(3).setCellValue("label");
		headerRow.createCell(4).setCellValue("conceptUuid");
		headerRow.createCell(5).setCellValue("datatype");
		headerRow.createCell(6).setCellValue("isRepeat");
		headerRow.createCell(7).setCellValue("isMulti");
		headerRow.createCell(8).setCellValue("answersJson");
		
		// Create valid patientRef column
		Row patientRefRow = metaSheet.createRow(2);
		patientRefRow.createCell(0).setCellValue("Test Sheet");
		patientRefRow.createCell(1).setCellValue(0);
		patientRefRow.createCell(2).setCellValue("patientRef");
		patientRefRow.createCell(3).setCellValue("Patient Ref");
		patientRefRow.createCell(5).setCellValue("TEXT");
		patientRefRow.createCell(6).setCellValue(false);
		patientRefRow.createCell(7).setCellValue(false);
		patientRefRow.createCell(8).setCellValue("{}");
		
		// Create valid coded field column
		Row codedRow = metaSheet.createRow(3);
		codedRow.createCell(0).setCellValue("Test Sheet");
		codedRow.createCell(1).setCellValue(1);
		codedRow.createCell(2).setCellValue("symptoms");
		codedRow.createCell(3).setCellValue("Symptoms");
		codedRow.createCell(4).setCellValue("symptoms-concept-uuid");
		codedRow.createCell(5).setCellValue("CODED");
		codedRow.createCell(6).setCellValue(false);
		codedRow.createCell(7).setCellValue(false);
		codedRow.createCell(8).setCellValue("{\"Fever\":\"fever-uuid\",\"Headache\":\"headache-uuid\"}");
		
		// Should not throw exception
		assertDoesNotThrow(() -> {
			TemplateMeta meta = MetaReader.read(workbook);
			assertNotNull(meta);
			assertEquals(1, meta.sheets.size());
			assertEquals(2, meta.getSheetMeta("Test Sheet").columns.size());
			
			// Verify coded field has answers
			ColumnMeta codedColumn = meta.getSheetMeta("Test Sheet").columns.stream()
			        .filter(c -> c.fieldId.equals("symptoms")).findFirst().orElse(null);
			assertNotNull(codedColumn);
			assertEquals(2, codedColumn.answers.size());
			assertTrue(codedColumn.answers.containsKey("Fever"));
			assertTrue(codedColumn.answers.containsKey("Headache"));
		});
		
		workbook.close();
	}
}
