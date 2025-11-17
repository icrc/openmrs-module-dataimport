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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.api.util.DataImportConstants;
import org.openmrs.module.dataimport.model.ImportPlan;
import org.openmrs.module.dataimport.model.QuarantineItem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class QuarantineReportGeneratorTest {
	
	@Test
	public void testQuarantineReport_shouldFilterSheets() throws Exception {
		byte[] workbookBytes = createSampleWorkbook();
		
		List<QuarantineItem> issues = new ArrayList<>();
		issues.add(QuarantineItem.error("Vitals", 1, 1, "OBS_PARSE_ERROR", "Expected numeric value"));
		
		ImportPlan plan = ImportPlan.builder().batchId(UUID.randomUUID()).rowsOk(Collections.emptyList()).issues(issues)
		        .stats(null).patientIndex(null).blockedPatientRefs(Collections.singleton("P001")).build();
		
		QuarantineReportGenerator generator = new QuarantineReportGenerator();
		byte[] reportBytes = generator.generateReport(new ByteArrayInputStream(workbookBytes), plan);
		
		try (XSSFWorkbook reportWb = new XSSFWorkbook(new ByteArrayInputStream(reportBytes))) {
			assertEquals(3, reportWb.getNumberOfSheets(), "Expected meta plus sheets with issues");
			
			Sheet patientSheet = reportWb.getSheet(DataImportConstants.PATIENT_SHEET_NAME);
			Sheet vitalsSheet = reportWb.getSheet("Vitals");
			Sheet medsSheet = reportWb.getSheet("Medications");
			Sheet metaSheet = reportWb.getSheet(DataImportConstants.META_SHEET);
			
			assertNotNull(patientSheet, "Patient sheet should be present");
			assertNotNull(vitalsSheet, "Vitals sheet should be present");
			assertNull(medsSheet, "Medications sheet should be omitted");
			assertNotNull(metaSheet, "__meta sheet should be preserved");
			assertEquals("templateVersion", metaSheet.getRow(1).getCell(0).getStringCellValue());
			assertEquals("1", metaSheet.getRow(1).getCell(1).getStringCellValue());
			assertTrue(reportWb.isSheetHidden(reportWb.getSheetIndex(metaSheet)),
			    "__meta sheet hidden state should match original");
			
			Row patientRow = patientSheet.getRow(1);
			assertEquals("P001", patientRow.getCell(8).getStringCellValue(), "Patient ref should match blocked patient");
			
			Row vitalsRow = vitalsSheet.getRow(1);
			assertEquals("P001", vitalsRow.getCell(0).getStringCellValue(), "Vitals row should match patient ref");
		}
	}
	
	private byte[] createSampleWorkbook() throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			Sheet metaSheet = workbook.createSheet(DataImportConstants.META_SHEET);
			Row metaHeader = metaSheet.createRow(0);
			metaHeader.createCell(0).setCellValue("key");
			metaHeader.createCell(1).setCellValue("value");
			Row metaRow = metaSheet.createRow(1);
			metaRow.createCell(0).setCellValue("templateVersion");
			metaRow.createCell(1).setCellValue("1");
			int metaIndex = workbook.getSheetIndex(metaSheet);
			workbook.setSheetHidden(metaIndex, true);
			
			Sheet patientSheet = workbook.createSheet(DataImportConstants.PATIENT_SHEET_NAME);
			Row patientHeader = patientSheet.createRow(0);
			patientHeader.createCell(0).setCellValue("Given Name");
			patientHeader.createCell(1).setCellValue("Family Name");
			patientHeader.createCell(8).setCellValue(DataImportConstants.PATIENT_REF_HEADER);
			Row patientRow = patientSheet.createRow(1);
			patientRow.createCell(0).setCellValue("Jane");
			patientRow.createCell(1).setCellValue("Doe");
			patientRow.createCell(8).setCellValue("P001");
			
			Sheet vitalsSheet = workbook.createSheet("Vitals");
			Row vitalsHeader = vitalsSheet.createRow(0);
			vitalsHeader.createCell(0).setCellValue("Patient Ref");
			vitalsHeader.createCell(1).setCellValue("Weight");
			Row vitalsRow = vitalsSheet.createRow(1);
			vitalsRow.createCell(0).setCellValue("P001");
			vitalsRow.createCell(1).setCellValue("NotANumber");
			
			Sheet medsSheet = workbook.createSheet("Medications");
			Row medsHeader = medsSheet.createRow(0);
			medsHeader.createCell(0).setCellValue("Patient Ref");
			Row medsRow = medsSheet.createRow(1);
			medsRow.createCell(0).setCellValue("P001");
			medsRow.createCell(1).setCellValue("Drug A");
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			workbook.write(baos);
			return baos.toByteArray();
		}
	}
}
