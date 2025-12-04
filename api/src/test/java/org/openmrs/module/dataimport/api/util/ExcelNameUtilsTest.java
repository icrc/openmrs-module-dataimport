/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class ExcelNameUtilsTest {
	
	@Test
	public void isExcelCellReference_shouldDetectValidCellReferences() {
		assertTrue(ExcelNameUtils.isExcelCellReference("A1"));
		assertTrue(ExcelNameUtils.isExcelCellReference("Z99"));
		assertTrue(ExcelNameUtils.isExcelCellReference("AA123"));
		assertTrue(ExcelNameUtils.isExcelCellReference("XFD1048576")); // Max Excel cell reference
	}
	
	@Test
	public void isExcelCellReference_shouldRejectInvalidCellReferences() {
		assertFalse(ExcelNameUtils.isExcelCellReference("A"));
		assertFalse(ExcelNameUtils.isExcelCellReference("1A"));
		assertFalse(ExcelNameUtils.isExcelCellReference("field_name"));
		assertFalse(ExcelNameUtils.isExcelCellReference(""));
		assertFalse(ExcelNameUtils.isExcelCellReference(null));
		assertFalse(ExcelNameUtils.isExcelCellReference("A1B2"));
		assertFalse(ExcelNameUtils.isExcelCellReference("1"));
	}
	
	@Test
	public void sanitizeSheetName_shouldHandleLongNames() {
		String longName = "This is a very long sheet name that exceeds the maximum allowed length";
		String result = ExcelNameUtils.sanitizeSheetName(longName);
		assertTrue(result.length() <= 31, "Sheet name should be <= 31 characters");
	}
	
	@Test
	public void sanitizeSheetName_shouldRemoveInvalidCharacters() {
		String result = ExcelNameUtils.sanitizeSheetName("Sheet:With*Invalid/Chars");
		assertFalse(result.contains(":"));
		assertFalse(result.contains("*"));
		assertFalse(result.contains("/"));
		assertFalse(result.contains("\\"));
		assertFalse(result.contains("?"));
		assertFalse(result.contains("["));
		assertFalse(result.contains("]"));
	}
	
	@Test
	public void sanitizeSheetName_shouldHandleNullAndEmpty() {
		String result1 = ExcelNameUtils.sanitizeSheetName(null);
		assertNotNull(result1);
		assertEquals("Sheet", result1);
		
		String result2 = ExcelNameUtils.sanitizeSheetName("");
		assertNotNull(result2);
		assertEquals("Sheet", result2);
		
		String result3 = ExcelNameUtils.sanitizeSheetName("   ");
		assertNotNull(result3);
		assertEquals("Sheet", result3);
	}
	
	@Test
	public void getUniqueSheetName_shouldReturnOriginalWhenUnique() {
		Map<String, Integer> usedNames = new HashMap<>();
		String result = ExcelNameUtils.getUniqueSheetName("MySheet", usedNames);
		assertEquals("MySheet", result);
		assertEquals(1, usedNames.get("MySheet"));
	}
	
	@Test
	public void getUniqueSheetName_shouldAppendNumberWhenDuplicate() {
		Map<String, Integer> usedNames = new HashMap<>();
		usedNames.put("MySheet", 1);
		
		String result = ExcelNameUtils.getUniqueSheetName("MySheet", usedNames);
		assertEquals("MySheet_1", result);
		assertEquals(2, usedNames.get("MySheet"));
	}
	
	@Test
	public void getUniqueSheetName_shouldHandleMultipleDuplicates() {
		Map<String, Integer> usedNames = new HashMap<>();
		
		String result1 = ExcelNameUtils.getUniqueSheetName("Sheet", usedNames);
		assertEquals("Sheet", result1);
		
		String result2 = ExcelNameUtils.getUniqueSheetName("Sheet", usedNames);
		assertEquals("Sheet_1", result2);
		
		String result3 = ExcelNameUtils.getUniqueSheetName("Sheet", usedNames);
		assertEquals("Sheet_2", result3);
	}
	
	@Test
	public void generateFallbackId_shouldGenerateCamelCase() {
		String result = ExcelNameUtils.generateFallbackId("Patient Name");
		assertEquals("patientName", result);
		
		String result2 = ExcelNameUtils.generateFallbackId("Date of Birth");
		assertEquals("dateOfBirth", result2);
	}
	
	@Test
	public void generateFallbackId_shouldRemoveSpecialCharacters() {
		String result = ExcelNameUtils.generateFallbackId("Field@#$%Name");
		assertEquals("fieldname", result);
	}
	
	@Test
	public void generateFallbackId_shouldHandleNullAndEmpty() {
		String result1 = ExcelNameUtils.generateFallbackId(null);
		assertNotNull(result1);
		assertTrue(result1.startsWith("field_"));
		
		String result2 = ExcelNameUtils.generateFallbackId("");
		assertNotNull(result2);
		assertTrue(result2.startsWith("field_"));
		
		String result3 = ExcelNameUtils.generateFallbackId("   ");
		assertNotNull(result3);
		assertTrue(result3.startsWith("field_"));
	}
	
	@Test
	public void generateFallbackId_shouldHandleOnlySpecialCharacters() {
		String result = ExcelNameUtils.generateFallbackId("@#$%&*");
		assertNotNull(result);
		assertTrue(result.startsWith("field_"));
	}
	
	@Test
	public void generateFallbackId_shouldLimitLength() {
		String veryLongLabel = "This is a very long label that should be truncated to a reasonable length for field identification purposes";
		String result = ExcelNameUtils.generateFallbackId(veryLongLabel);
		assertTrue(result.length() <= 64, "Generated ID should not exceed 64 characters");
	}
}
