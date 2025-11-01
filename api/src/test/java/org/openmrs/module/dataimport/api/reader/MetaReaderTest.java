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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.model.ColumnMeta;
import org.openmrs.module.dataimport.model.TemplateMeta;

public class MetaReaderTest {
	
	@Test
	@DisplayName("Should read multi-form template metadata correctly")
	void testMultiFormTemplateMetadata() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet metaSheet = workbook.createSheet("__meta");
		
		Row templateRow1 = metaSheet.createRow(0);
		templateRow1.createCell(0).setCellValue("templateVersion");
		templateRow1.createCell(1).setCellValue("1");
		
		Row templateRow2 = metaSheet.createRow(1);
		templateRow2.createCell(0).setCellValue("generatedAt");
		templateRow2.createCell(1).setCellValue("2024-01-15T10:30:00Z");
		
		Row templateRow3 = metaSheet.createRow(2);
		templateRow3.createCell(0).setCellValue("locale");
		templateRow3.createCell(1).setCellValue("en");
		
		Row templateRow4 = metaSheet.createRow(3);
		templateRow4.createCell(0).setCellValue("templateName");
		templateRow4.createCell(1).setCellValue("Surgical Operations Package");
		
		Row templateRow5 = metaSheet.createRow(4);
		templateRow5.createCell(0).setCellValue("defaultLocationUuid");
		templateRow5.createCell(1).setCellValue("44c3efb0-2583-4c80-a79e-1f756a03c0a1");
		
		Row templateRow6 = metaSheet.createRow(5);
		templateRow6.createCell(0).setCellValue("formCount");
		templateRow6.createCell(1).setCellValue("2");
		
		metaSheet.createRow(6);
		
		Row formHeaderRow = metaSheet.createRow(7);
		formHeaderRow.createCell(0).setCellValue("formUuid");
		formHeaderRow.createCell(1).setCellValue("formName");
		formHeaderRow.createCell(2).setCellValue("formVersion");
		formHeaderRow.createCell(3).setCellValue("encounterTypeUuid");
		formHeaderRow.createCell(4).setCellValue("sheetName");
		
		Row formRow1 = metaSheet.createRow(8);
		formRow1.createCell(0).setCellValue("96637f12-3c04-311f-b477-3fa6a866e895");
		formRow1.createCell(1).setCellValue("Surgical Operation");
		formRow1.createCell(2).setCellValue("2.1");
		formRow1.createCell(3).setCellValue("dd528487-82a5-4082-9c72-ed246bd49591");
		formRow1.createCell(4).setCellValue("Surgical Operation");
		
		Row formRow2 = metaSheet.createRow(9);
		formRow2.createCell(0).setCellValue("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
		formRow2.createCell(1).setCellValue("Post-Op Follow-up");
		formRow2.createCell(2).setCellValue("1.5");
		formRow2.createCell(3).setCellValue("ee639598-93b6-5193-ad83-5e357fae5062");
		formRow2.createCell(4).setCellValue("Post-Op Follow-up");
		
		metaSheet.createRow(10);
		
		Row columnHeaderRow = metaSheet.createRow(11);
		columnHeaderRow.createCell(0).setCellValue("sheetName");
		columnHeaderRow.createCell(1).setCellValue("colIndex");
		columnHeaderRow.createCell(2).setCellValue("fieldId");
		columnHeaderRow.createCell(3).setCellValue("label");
		columnHeaderRow.createCell(4).setCellValue("conceptUuid");
		columnHeaderRow.createCell(5).setCellValue("datatype");
		columnHeaderRow.createCell(6).setCellValue("isRepeat");
		columnHeaderRow.createCell(7).setCellValue("isMulti");
		columnHeaderRow.createCell(8).setCellValue("answersJson");
		
		Row columnRow1 = metaSheet.createRow(12);
		columnRow1.createCell(0).setCellValue("Surgical Operation");
		columnRow1.createCell(1).setCellValue(0);
		columnRow1.createCell(2).setCellValue("patientRef");
		columnRow1.createCell(3).setCellValue("Patient Ref");
		columnRow1.createCell(4).setCellValue("");
		columnRow1.createCell(5).setCellValue("TEXT");
		columnRow1.createCell(6).setCellValue(false);
		columnRow1.createCell(7).setCellValue(false);
		columnRow1.createCell(8).setCellValue("{}");
		
		Row columnRow2 = metaSheet.createRow(13);
		columnRow2.createCell(0).setCellValue("Post-Op Follow-up");
		columnRow2.createCell(1).setCellValue(0);
		columnRow2.createCell(2).setCellValue("patientRef");
		columnRow2.createCell(3).setCellValue("Patient Ref");
		columnRow2.createCell(4).setCellValue("");
		columnRow2.createCell(5).setCellValue("TEXT");
		columnRow2.createCell(6).setCellValue(false);
		columnRow2.createCell(7).setCellValue(false);
		columnRow2.createCell(8).setCellValue("{}");
		
		TemplateMeta meta = MetaReader.read(workbook);
		
		assertNotNull(meta);
		assertEquals(1, meta.templateVersion);
		assertEquals("Surgical Operations Package", meta.templateName);
		assertEquals("44c3efb0-2583-4c80-a79e-1f756a03c0a1", meta.defaultLocationUuid);
		assertEquals(2, meta.formCount);
		
		assertEquals(2, meta.forms.size());
		
		TemplateMeta.FormInfo form1 = meta.forms.get(0);
		assertEquals("96637f12-3c04-311f-b477-3fa6a866e895", form1.formUuid);
		assertEquals("Surgical Operation", form1.formName);
		assertEquals("2.1", form1.formVersion);
		assertEquals("dd528487-82a5-4082-9c72-ed246bd49591", form1.encounterTypeUuid);
		assertEquals("Surgical Operation", form1.sheetName);
		
		TemplateMeta.FormInfo form2 = meta.forms.get(1);
		assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", form2.formUuid);
		assertEquals("Post-Op Follow-up", form2.formName);
		assertEquals("1.5", form2.formVersion);
		assertEquals("ee639598-93b6-5193-ad83-5e357fae5062", form2.encounterTypeUuid);
		assertEquals("Post-Op Follow-up", form2.sheetName);
		
		assertEquals(2, meta.sheets.size());
		
		org.openmrs.module.dataimport.model.SheetMeta surgicalSheet = meta.getSheetMeta("Surgical Operation");
		assertNotNull(surgicalSheet);
		assertEquals("96637f12-3c04-311f-b477-3fa6a866e895", surgicalSheet.formUuid);
		assertEquals("Surgical Operation", surgicalSheet.formName);
		assertEquals("dd528487-82a5-4082-9c72-ed246bd49591", surgicalSheet.encounterTypeUuid);
		
		org.openmrs.module.dataimport.model.SheetMeta followupSheet = meta.getSheetMeta("Post-Op Follow-up");
		assertNotNull(followupSheet);
		assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", followupSheet.formUuid);
		assertEquals("Post-Op Follow-up", followupSheet.formName);
		assertEquals("ee639598-93b6-5193-ad83-5e357fae5062", followupSheet.encounterTypeUuid);
		
		workbook.close();
	}
	
	@Test
	@DisplayName("Should handle backward compatibility with single-form templates")
	void testBackwardCompatibility() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet metaSheet = workbook.createSheet("__meta");
		
		Row templateRow1 = metaSheet.createRow(0);
		templateRow1.createCell(0).setCellValue("templateVersion");
		templateRow1.createCell(1).setCellValue("1");
		
		Row templateRow2 = metaSheet.createRow(1);
		templateRow2.createCell(0).setCellValue("templateName");
		templateRow2.createCell(1).setCellValue("Single Form Template");
		
		Row templateRow3 = metaSheet.createRow(2);
		templateRow3.createCell(0).setCellValue("defaultLocationUuid");
		templateRow3.createCell(1).setCellValue("44c3efb0-2583-4c80-a79e-1f756a03c0a1");
		
		Row templateRow4 = metaSheet.createRow(3);
		templateRow4.createCell(0).setCellValue("formCount");
		templateRow4.createCell(1).setCellValue("0");
		
		metaSheet.createRow(4);
		
		Row columnHeaderRow = metaSheet.createRow(5);
		columnHeaderRow.createCell(0).setCellValue("sheetName");
		columnHeaderRow.createCell(1).setCellValue("colIndex");
		columnHeaderRow.createCell(2).setCellValue("fieldId");
		columnHeaderRow.createCell(3).setCellValue("label");
		columnHeaderRow.createCell(4).setCellValue("conceptUuid");
		columnHeaderRow.createCell(5).setCellValue("datatype");
		columnHeaderRow.createCell(6).setCellValue("isRepeat");
		columnHeaderRow.createCell(7).setCellValue("isMulti");
		columnHeaderRow.createCell(8).setCellValue("answersJson");
		
		Row columnRow = metaSheet.createRow(6);
		columnRow.createCell(0).setCellValue("Single Form");
		columnRow.createCell(1).setCellValue(0);
		columnRow.createCell(2).setCellValue("patientRef");
		columnRow.createCell(3).setCellValue("Patient Ref");
		columnRow.createCell(4).setCellValue("");
		columnRow.createCell(5).setCellValue("TEXT");
		columnRow.createCell(6).setCellValue(false);
		columnRow.createCell(7).setCellValue(false);
		columnRow.createCell(8).setCellValue("{}");
		
		TemplateMeta meta = MetaReader.read(workbook);
		
		assertNotNull(meta);
		assertEquals(1, meta.templateVersion);
		assertEquals("Single Form Template", meta.templateName);
		assertEquals("44c3efb0-2583-4c80-a79e-1f756a03c0a1", meta.defaultLocationUuid);
		assertEquals(0, meta.formCount);
		
		assertEquals(0, meta.forms.size());
		
		assertEquals(1, meta.sheets.size());
		org.openmrs.module.dataimport.model.SheetMeta sheet = meta.getSheetMeta("Single Form");
		assertNotNull(sheet);
		assertEquals("Single Form", sheet.sheetName);
		
		workbook.close();
	}
	
	@Test
	@DisplayName("Should validate missing datatype and fail fast")
	void testMissingDatatypeValidation() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet metaSheet = workbook.createSheet("__meta");
		
		Row templateRow = metaSheet.createRow(0);
		templateRow.createCell(0).setCellValue("templateVersion");
		templateRow.createCell(1).setCellValue("1");
		
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
		
		Row invalidRow = metaSheet.createRow(2);
		invalidRow.createCell(0).setCellValue("Test Sheet");
		invalidRow.createCell(1).setCellValue(1);
		invalidRow.createCell(2).setCellValue("testField");
		invalidRow.createCell(3).setCellValue("Test Field");
		invalidRow.createCell(4).setCellValue("test-concept-uuid");
		invalidRow.createCell(6).setCellValue(false);
		invalidRow.createCell(7).setCellValue(false);
		invalidRow.createCell(8).setCellValue("{}");
		
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
			MetaReader.read(workbook);
		});
		
		assertTrue(exception.getMessage().contains("missing datatype"));
		assertTrue(exception.getMessage().contains("Test Field"));
		
		workbook.close();
	}
	
	@Test
	@DisplayName("Should allow missing conceptUuid for non-Patient Ref columns (will be skipped during import)")
	void testMissingConceptUuidValidation() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet metaSheet = workbook.createSheet("__meta");
		
		Row templateRow = metaSheet.createRow(0);
		templateRow.createCell(0).setCellValue("templateVersion");
		templateRow.createCell(1).setCellValue("1");
		
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
		
		Row invalidRow = metaSheet.createRow(2);
		invalidRow.createCell(0).setCellValue("Test Sheet");
		invalidRow.createCell(1).setCellValue(1);
		invalidRow.createCell(2).setCellValue("testField");
		invalidRow.createCell(3).setCellValue("Test Field");
		invalidRow.createCell(5).setCellValue("TEXT");
		invalidRow.createCell(6).setCellValue(false);
		invalidRow.createCell(7).setCellValue(false);
		invalidRow.createCell(8).setCellValue("{}");
		
		// Should not throw - columns without conceptUuid are allowed
		// They will be skipped during obs creation
		TemplateMeta meta = MetaReader.read(workbook);
		assertNotNull(meta);
		assertEquals(1, meta.sheets.size());
		
		workbook.close();
	}
	
	@Test
	@DisplayName("Should validate missing coded answers and fail fast")
	void testMissingCodedAnswersValidation() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet metaSheet = workbook.createSheet("__meta");
		
		Row templateRow = metaSheet.createRow(0);
		templateRow.createCell(0).setCellValue("templateVersion");
		templateRow.createCell(1).setCellValue("1");
		
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
		
		Row invalidRow = metaSheet.createRow(2);
		invalidRow.createCell(0).setCellValue("Test Sheet");
		invalidRow.createCell(1).setCellValue(1);
		invalidRow.createCell(2).setCellValue("testField");
		invalidRow.createCell(3).setCellValue("Test Field");
		invalidRow.createCell(4).setCellValue("test-concept-uuid");
		invalidRow.createCell(5).setCellValue("CODED");
		invalidRow.createCell(6).setCellValue(false);
		invalidRow.createCell(7).setCellValue(false);
		
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
		
		Row templateRow = metaSheet.createRow(0);
		templateRow.createCell(0).setCellValue("templateVersion");
		templateRow.createCell(1).setCellValue("1");
		
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
		
		Row patientRefRow = metaSheet.createRow(2);
		patientRefRow.createCell(0).setCellValue("Test Sheet");
		patientRefRow.createCell(1).setCellValue(0);
		patientRefRow.createCell(2).setCellValue("patientRef");
		patientRefRow.createCell(3).setCellValue("Patient Ref");
		patientRefRow.createCell(5).setCellValue("TEXT");
		patientRefRow.createCell(6).setCellValue(false);
		patientRefRow.createCell(7).setCellValue(false);
		patientRefRow.createCell(8).setCellValue("{}");
		
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
		
		Row templateRow = metaSheet.createRow(0);
		templateRow.createCell(0).setCellValue("templateVersion");
		templateRow.createCell(1).setCellValue("1");
		
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
		
		Row patientRefRow = metaSheet.createRow(2);
		patientRefRow.createCell(0).setCellValue("Test Sheet");
		patientRefRow.createCell(1).setCellValue(0);
		patientRefRow.createCell(2).setCellValue("patientRef");
		patientRefRow.createCell(3).setCellValue("Patient Ref");
		patientRefRow.createCell(5).setCellValue("TEXT");
		patientRefRow.createCell(6).setCellValue(false);
		patientRefRow.createCell(7).setCellValue(false);
		patientRefRow.createCell(8).setCellValue("{}");
		
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
		
		assertDoesNotThrow(() -> {
			TemplateMeta meta = MetaReader.read(workbook);
			assertNotNull(meta);
			assertEquals(1, meta.sheets.size());
			assertEquals(2, meta.getSheetMeta("Test Sheet").columns.size());
			
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
