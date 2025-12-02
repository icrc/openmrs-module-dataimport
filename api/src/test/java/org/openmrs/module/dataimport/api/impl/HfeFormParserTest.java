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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class HfeFormParserTest {
	
	private HfeFormParser parser;
	
	private ConceptAnswersService conceptAnswersService;
	
	@BeforeEach
	public void setUp() {
		conceptAnswersService = mock(ConceptAnswersService.class);
		when(conceptAnswersService.resolveConceptUuid(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		parser = new HfeFormParser(conceptAnswersService);
	}
	
	@Test
	public void parse_shouldThrowIllegalArgumentExceptionWhenHfeXmlIsNull() {
		assertThrows(IllegalArgumentException.class, () -> {
			parser.parse(null, createMockForm(), null);
		});
	}
	
	@Test
	public void parse_shouldThrowIllegalArgumentExceptionWhenHfeXmlIsEmpty() {
		assertThrows(IllegalArgumentException.class, () -> {
			parser.parse("", createMockForm(), null);
		});
	}
	
	@Test
	public void parse_shouldThrowIllegalArgumentExceptionWhenFormIsNull() {
		assertThrows(IllegalArgumentException.class, () -> {
			parser.parse("<htmlform></htmlform>", null, null);
		});
	}
	
	@Test
	public void parseRepeatWith_shouldParseRepeatWithAttributeIntoTuplesCorrectly() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("parseRepeatWith", String.class);
		method.setAccessible(true);
		
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
	public void parseRepeatWith_shouldParseRepeatWithAttributeWithSingleTuple() throws Exception {
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
	public void parseRepeatWith_shouldParseRepeatWithAttributeWithComplexMappings() throws Exception {
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
	public void parseTuple_shouldParseSingleTupleCorrectly() throws Exception {
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
	public void substitutePlaceholders_shouldSubstitutePlaceholdersCorrectly() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("substitutePlaceholders", String.class, List.class);
		method.setAccessible(true);
		
		String text = "Obs for {0} with concept {1} and comment {2}";
		List<String> tuple = Arrays.asList("Weight", "CIEL:5089", "patient_weight");
		
		String result = (String) method.invoke(parser, text, tuple);
		
		assertNotNull(result);
		assertEquals("Obs for Weight with concept CIEL:5089 and comment patient_weight", result);
	}
	
	@Test
	public void substitutePlaceholders_shouldHandlePlaceholdersWithNoMatchingTupleValues() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("substitutePlaceholders", String.class, List.class);
		method.setAccessible(true);
		
		String text = "Value {0}, {1}, {5}"; // {5} is out of bounds
		List<String> tuple = Arrays.asList("first", "second");
		
		String result = (String) method.invoke(parser, text, tuple);
		
		assertTrue(result.contains("first"));
		assertTrue(result.contains("second"));
		assertTrue(result.contains("{5}")); // Unchanged
	}
	
	@Test
	public void sanitizeFieldId_shouldSanitizeFieldIdsCorrectly() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("sanitizeFieldId", String.class);
		method.setAccessible(true);
		
		String result1 = (String) method.invoke(parser, "field with spaces");
		assertEquals("field_with_spaces", result1);
		
		String result2 = (String) method.invoke(parser, "field@with#special$chars");
		assertTrue(result2.matches("[a-zA-Z0-9_]+"));
		
		String result3 = (String) method.invoke(parser, "A1");
		assertFalse(result3.equals("A1")); // Should be transformed to avoid collision
	}
	
	@Test
	public void isExcelCellReference_shouldDetectExcelCellReferencesCorrectly() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("isExcelCellReference", String.class);
		method.setAccessible(true);
		
		assertTrue((Boolean) method.invoke(parser, "A1"));
		assertTrue((Boolean) method.invoke(parser, "Z99"));
		assertTrue((Boolean) method.invoke(parser, "AA123"));
		
		assertFalse((Boolean) method.invoke(parser, "A"));
		assertFalse((Boolean) method.invoke(parser, "1A"));
		assertFalse((Boolean) method.invoke(parser, "field_name"));
		assertFalse((Boolean) method.invoke(parser, ""));
	}
	
	@Test
	public void sanitizeSheetName_shouldSanitizeSheetNamesCorrectly() throws Exception {
		Method method = HfeFormParser.class.getDeclaredMethod("sanitizeSheetName", String.class);
		method.setAccessible(true);
		
		String longName = "This is a very long sheet name that exceeds the maximum allowed length";
		String result1 = (String) method.invoke(parser, longName);
		assertTrue(result1.length() <= 31, "Sheet name should be <= 31 characters");
		
		String result2 = (String) method.invoke(parser, "Sheet:With*Invalid/Chars");
		assertFalse(result2.contains(":"));
		assertFalse(result2.contains("*"));
		assertFalse(result2.contains("/"));
		
		String result3 = (String) method.invoke(parser, (Object) null);
		assertNotNull(result3);
		assertFalse(result3.isEmpty());
	}
	
	private org.openmrs.Form createMockForm() {
		org.openmrs.Form form = new org.openmrs.Form();
		form.setUuid("test-form-uuid");
		form.setName("Test HFE Form");
		form.setVersion("1.0");
		return form;
	}
}
