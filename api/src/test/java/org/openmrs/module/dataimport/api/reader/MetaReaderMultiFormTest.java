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
import org.openmrs.module.dataimport.model.TemplateMeta;

@DisplayName("MetaReader Multi-Form Tests")
class MetaReaderMultiFormTest {
	
	@Test
	@DisplayName("Should read multi-form template metadata correctly")
	void testMultiFormTemplateMetadata() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet metaSheet = workbook.createSheet("__meta");
		
		// Create template metadata
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
		
		// Skip a row
		metaSheet.createRow(6);
		
		// Create form list table
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
		
		// Skip a row
		metaSheet.createRow(10);
		
		// Create column metadata table
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
		
		// Test reading the metadata
		TemplateMeta meta = MetaReader.read(workbook);
		
		// Verify template metadata
		assertNotNull(meta);
		assertEquals(1, meta.templateVersion);
		assertEquals("Surgical Operations Package", meta.templateName);
		assertEquals("44c3efb0-2583-4c80-a79e-1f756a03c0a1", meta.defaultLocationUuid);
		assertEquals(2, meta.formCount);
		
		// Verify form list
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
		
		// Verify sheet metadata
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
		
		// Create old-style template metadata (no form list)
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
		
		// Skip a row
		metaSheet.createRow(4);
		
		// Create column metadata table (no form list)
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
		
		// Test reading the metadata
		TemplateMeta meta = MetaReader.read(workbook);
		
		// Verify template metadata
		assertNotNull(meta);
		assertEquals(1, meta.templateVersion);
		assertEquals("Single Form Template", meta.templateName);
		assertEquals("44c3efb0-2583-4c80-a79e-1f756a03c0a1", meta.defaultLocationUuid);
		assertEquals(0, meta.formCount);
		
		// Verify no form list (backward compatibility)
		assertEquals(0, meta.forms.size());
		
		// Verify sheet metadata still works
		assertEquals(1, meta.sheets.size());
		org.openmrs.module.dataimport.model.SheetMeta sheet = meta.getSheetMeta("Single Form");
		assertNotNull(sheet);
		assertEquals("Single Form", sheet.sheetName);
		
		workbook.close();
	}
}
