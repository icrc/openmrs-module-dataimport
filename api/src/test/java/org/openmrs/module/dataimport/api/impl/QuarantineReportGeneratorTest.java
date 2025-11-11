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

class QuarantineReportGeneratorTest {
	
	@Test
	void generateReport_shouldIncludeOnlySheetsWithIssues_andFilterPatientRows() throws Exception {
		try (XSSFWorkbook sourceWorkbook = new XSSFWorkbook()) {
			Sheet patientSheet = sourceWorkbook.createSheet(DataImportConstants.PATIENT_SHEET_NAME);
			createRow(patientSheet, 0, "Patient Ref", "Given Name");
			createRow(patientSheet, 1, "P1", "Alice");
			createRow(patientSheet, 2, "P2", "Bob");
			
			Sheet encounterSheet = sourceWorkbook.createSheet("encounterSheet");
			createRow(encounterSheet, 0, "Patient Ref", "Observation");
			createRow(encounterSheet, 1, "P1", "Value A");
			createRow(encounterSheet, 2, "P2", "Value B");
			
			Sheet anotherSheet = sourceWorkbook.createSheet("anotherSheet");
			createRow(anotherSheet, 0, "Patient Ref");
			createRow(anotherSheet, 1, "P3");
			
			ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
			sourceWorkbook.write(sourceBytes);
			
			List<QuarantineItem> issues = new ArrayList<>();
			issues.add(QuarantineItem.error("encounterSheet", 1, 1, "ERROR", "Encounter issue"));
			issues.add(QuarantineItem.error(DataImportConstants.PATIENT_SHEET_NAME, 2, 0, "ERROR", "Patient sheet issue"));
			
			ImportPlan plan = ImportPlan.builder().batchId(UUID.randomUUID()).rowsOk(Collections.emptyList()).issues(issues)
			        .stats(null).patientIndex(null).build();
			
			QuarantineReportGenerator generator = new QuarantineReportGenerator();
			byte[] reportBytes = generator.generateReport(new ByteArrayInputStream(sourceBytes.toByteArray()), plan);
			
			try (XSSFWorkbook reportWorkbook = new XSSFWorkbook(new ByteArrayInputStream(reportBytes))) {
				assertEquals(2, reportWorkbook.getNumberOfSheets());
				assertNotNull(reportWorkbook.getSheet("encounterSheet"));
				assertNull(reportWorkbook.getSheet("anotherSheet"));
				
				Sheet reportPatientSheet = reportWorkbook.getSheet(DataImportConstants.PATIENT_SHEET_NAME);
				assertNotNull(reportPatientSheet);
				
				assertEquals("Patient Ref", reportPatientSheet.getRow(0).getCell(0).getStringCellValue());
				assertEquals("P1", reportPatientSheet.getRow(1).getCell(0).getStringCellValue());
				assertEquals("P2", reportPatientSheet.getRow(2).getCell(0).getStringCellValue());
			}
		}
	}
	
	private void createRow(Sheet sheet, int rowIndex, String... values) {
		Row row = sheet.createRow(rowIndex);
		for (int i = 0; i < values.length; i++) {
			row.createCell(i).setCellValue(values[i]);
		}
	}
}
