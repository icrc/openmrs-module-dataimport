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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.FormType;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for HfeFormParser.
 * <p>
 * Note: Integration tests with OpenMRS Context are in DataImportIntegrationTest. These unit tests
 * focus on standalone parsing logic.
 */
@DisplayName("HfeFormParser Unit Tests")
class HfeFormParserTest {
	
	private HfeFormParser parser;
	
	@BeforeEach
	void setUp() {
		parser = new HfeFormParser();
	}
	
	@Test
	@DisplayName("Should throw IllegalArgumentException when HFE XML is null")
	void testParseNullXml() {
		assertThrows(IllegalArgumentException.class, () -> {
			parser.parse(null, createMockForm(), null);
		});
	}
	
	@Test
	@DisplayName("Should throw IllegalArgumentException when HFE XML is empty")
	void testParseEmptyXml() {
		assertThrows(IllegalArgumentException.class, () -> {
			parser.parse("", createMockForm(), null);
		});
	}
	
	@Test
	@DisplayName("Should throw IllegalArgumentException when Form is null")
	void testParseNullForm() {
		assertThrows(IllegalArgumentException.class, () -> {
			parser.parse("<htmlform></htmlform>", null, null);
		});
	}
	
	@Test
	@DisplayName("Should parse repeat 'with' attribute into tuples correctly")
	void testParseRepeatWith() throws Exception {
		// Use reflection to test private method
		Method method = HfeFormParser.class.getDeclaredMethod("parseRepeatWith", String.class);
		method.setAccessible(true);
		
		// Test case 1: Two tuples with three values each
		String withAttr = "['label1','CONCEPT:1','comment1'], ['label2','CONCEPT:2','comment2']";
		@SuppressWarnings("unchecked")
		List<List<String>> result = (List<List<String>>) method.invoke(parser, withAttr);
		
		assertNotNull(result);
		assertEquals(2, result.size(), "Should parse 2 tuples");
		
		assertEquals(3, result.get(0).size(), "First tuple should have 3 values");
		assertEquals("label1", result.get(0).get(0));
		assertEquals("CONCEPT:1", result.get(0).get(1));
		assertEquals("comment1", result.get(0).get(2));
		
		assertEquals(3, result.get(1).size(), "Second tuple should have 3 values");
		assertEquals("label2", result.get(1).get(0));
		assertEquals("CONCEPT:2", result.get(1).get(1));
		assertEquals("comment2", result.get(1).get(2));
	}
	
	@Test
	@DisplayName("Should parse repeat 'with' attribute with single tuple")
	void testParseRepeatWithSingleTuple() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("parseRepeatWith", String.class);
		method.setAccessible(true);
		
		String withAttr = "['onlyLabel','CIEL:5089']";
		@SuppressWarnings("unchecked")
		List<List<String>> result = (List<List<String>>) method.invoke(parser, withAttr);
		
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(2, result.get(0).size());
		assertEquals("onlyLabel", result.get(0).get(0));
		assertEquals("CIEL:5089", result.get(0).get(1));
	}
	
	@Test
	@DisplayName("Should parse repeat 'with' attribute with complex mappings")
	void testParseRepeatWithComplexMappings() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("parseRepeatWith", String.class);
		method.setAccessible(true);
		
		// Real-world example from care plan form
		String withAttr = "['icrcforms.wttc.carePlan.hipSurgery','ICRC:HIP_SURGERY', 'ICRC:HIP_SURGERY_DISPOSITION_COMMENT'],"
		        + "['icrcforms.wttc.carePlan.kneeSurgery','ICRC:KNEE_SURGERY','ICRC:KNEE_SURGERY_DISPOSITION_COMMENT']";
		
		@SuppressWarnings("unchecked")
		List<List<String>> result = (List<List<String>>) method.invoke(parser, withAttr);
		
		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals("ICRC:HIP_SURGERY", result.get(0).get(1));
		assertEquals("ICRC:KNEE_SURGERY", result.get(1).get(1));
	}
	
	@Test
	@DisplayName("Should parse single tuple correctly")
	void testParseTuple() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("parseTuple", String.class);
		method.setAccessible(true);
		
		String tupleStr = "'value1','value2','value3'";
		@SuppressWarnings("unchecked")
		List<String> result = (List<String>) method.invoke(parser, tupleStr);
		
		assertNotNull(result);
		assertEquals(3, result.size());
		assertEquals("value1", result.get(0));
		assertEquals("value2", result.get(1));
		assertEquals("value3", result.get(2));
	}
	
	@Test
	@DisplayName("Should substitute placeholders correctly")
	void testSubstitutePlaceholders() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("substitutePlaceholders", String.class, List.class);
		method.setAccessible(true);
		
		String text = "Obs for {0} with concept {1} and comment {2}";
		List<String> tuple = Arrays.asList("Weight", "CIEL:5089", "patient_weight");
		
		String result = (String) method.invoke(parser, text, tuple);
		
		assertNotNull(result);
		assertEquals("Obs for Weight with concept CIEL:5089 and comment patient_weight", result);
	}
	
	@Test
	@DisplayName("Should handle placeholders with no matching tuple values")
	void testSubstitutePlaceholdersOutOfBounds() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("substitutePlaceholders", String.class, List.class);
		method.setAccessible(true);
		
		String text = "Value {0}, {1}, {5}"; // {5} is out of bounds
		List<String> tuple = Arrays.asList("first", "second");
		
		String result = (String) method.invoke(parser, text, tuple);
		
		// Should replace {0} and {1}, but leave {5} unchanged
		assertTrue(result.contains("first"));
		assertTrue(result.contains("second"));
		assertTrue(result.contains("{5}")); // Unchanged
	}
	
	@Test
	@DisplayName("Should sanitize field IDs correctly")
	void testSanitizeFieldId() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("sanitizeFieldId", String.class);
		method.setAccessible(true);
		
		// Test case 1: Replace spaces with underscores
		String result1 = (String) method.invoke(parser, "field with spaces");
		assertEquals("field_with_spaces", result1);
		
		// Test case 2: Remove special characters
		String result2 = (String) method.invoke(parser, "field@with#special$chars");
		assertTrue(result2.matches("[a-zA-Z0-9_]+"));
		
		// Test case 3: Handle Excel cell reference collision
		String result3 = (String) method.invoke(parser, "A1");
		assertFalse(result3.equals("A1")); // Should be transformed to avoid collision
	}
	
	@Test
	@DisplayName("Should detect Excel cell references correctly")
	void testIsExcelCellReference() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("isExcelCellReference", String.class);
		method.setAccessible(true);
		
		// Valid Excel cell references
		assertTrue((Boolean) method.invoke(parser, "A1"));
		assertTrue((Boolean) method.invoke(parser, "Z99"));
		assertTrue((Boolean) method.invoke(parser, "AA123"));
		
		// Invalid Excel cell references
		assertFalse((Boolean) method.invoke(parser, "A"));
		assertFalse((Boolean) method.invoke(parser, "1A"));
		assertFalse((Boolean) method.invoke(parser, "field_name"));
		assertFalse((Boolean) method.invoke(parser, ""));
	}
	
	@Test
	@DisplayName("Should sanitize sheet names correctly")
	void testSanitizeSheetName() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("sanitizeSheetName", String.class);
		method.setAccessible(true);
		
		// Test case 1: Long name should be truncated
		String longName = "This is a very long sheet name that exceeds the maximum allowed length";
		String result1 = (String) method.invoke(parser, longName);
		assertTrue(result1.length() <= 31, "Sheet name should be <= 31 characters");
		
		// Test case 2: Invalid characters should be replaced
		String result2 = (String) method.invoke(parser, "Sheet:With*Invalid/Chars");
		assertFalse(result2.contains(":"));
		assertFalse(result2.contains("*"));
		assertFalse(result2.contains("/"));
		
		// Test case 3: Null/empty should return default
		String result3 = (String) method.invoke(parser, (Object) null);
		assertNotNull(result3);
		assertFalse(result3.isEmpty());
	}
	
	/**
	 * Create a mock Form object for testing. In real tests, this would use OpenMRS Context and actual
	 * Form objects.
	 */
	private org.openmrs.Form createMockForm() {
		org.openmrs.Form form = new org.openmrs.Form();
		form.setUuid("test-form-uuid");
		form.setName("Test HFE Form");
		form.setVersion("1.0");
		return form;
	}
}
