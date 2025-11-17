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

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.model.EncounterBlueprint;
import org.openmrs.module.dataimport.model.SheetMeta;

public class EncounterReaderLocationTest {
	
	@Test
	public void testTemplateDefaultLocation() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Test Sheet");
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.sheetName = "Test Sheet";
		sheetMeta.encounterTypeUuid = "test-encounter-type-uuid";
		
		String templateDefaultLocationUuid = "template-default-location-uuid";
		EncounterBlueprint encounter = ExcelReader.buildEncounter(sheetMeta, templateDefaultLocationUuid);
		
		assertNotNull(encounter);
		assertEquals("test-encounter-type-uuid", encounter.getEncounterTypeUuid());
		assertEquals("template-default-location-uuid", encounter.getLocationUuid());
		workbook.close();
	}
	
	@Test
	public void testSheetLocationOverridesTemplate() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("Test Sheet");
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.sheetName = "Test Sheet";
		sheetMeta.encounterTypeUuid = "test-encounter-type-uuid";
		sheetMeta.locationUuid = "sheet-specific-location-uuid";
		
		String templateDefaultLocationUuid = "template-default-location-uuid";
		EncounterBlueprint encounter = ExcelReader.buildEncounter(sheetMeta, templateDefaultLocationUuid);
		
		assertNotNull(encounter);
		assertEquals("test-encounter-type-uuid", encounter.getEncounterTypeUuid());
		assertEquals("sheet-specific-location-uuid", encounter.getLocationUuid());
		
		workbook.close();
	}
	
	@Test
	public void testNullTemplateDefaultLocation() throws Exception {
		XSSFWorkbook workbook = new XSSFWorkbook();
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.sheetName = "Test Sheet";
		sheetMeta.encounterTypeUuid = "test-encounter-type-uuid";
		EncounterBlueprint encounter = ExcelReader.buildEncounter(sheetMeta, null);
		
		assertNotNull(encounter);
		assertEquals("test-encounter-type-uuid", encounter.getEncounterTypeUuid());
		assertNull(encounter.getLocationUuid());
		
		workbook.close();
	}
}
