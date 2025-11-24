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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.model.AnswerModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnswerMapperTest {
	
	@Test
	public void toMap_shouldConvertListToMapCorrectly() {
		List<AnswerModel> answers = Arrays.asList(new AnswerModel("Male", "male-uuid", "male-uuid"),
		    new AnswerModel("Female", "female-uuid", "female-uuid"),
		    new AnswerModel("Unknown", "unknown-uuid", "unknown-uuid"));
		
		Map<String, String> map = AnswerMapper.toMap(answers);
		
		assertNotNull(map);
		assertEquals(3, map.size());
		assertTrue(map.containsKey("male"));
		assertTrue(map.containsKey("female"));
		assertTrue(map.containsKey("unknown"));
		assertEquals("male-uuid", map.get("male"));
	}
	
	@Test
	public void toMap_shouldHandleNullList() {
		Map<String, String> map = AnswerMapper.toMap(null);
		assertNotNull(map);
		assertTrue(map.isEmpty());
	}
	
	@Test
	public void toMap_shouldHandleEmptyList() {
		Map<String, String> map = AnswerMapper.toMap(new ArrayList<>());
		assertNotNull(map);
		assertTrue(map.isEmpty());
	}
	
	@Test
	public void toMap_shouldSkipAnswersWithNullLabelsOrUUIDs() {
		List<AnswerModel> answers = Arrays.asList(new AnswerModel(null, "uuid1", "uuid1"),
		    new AnswerModel("Valid", "uuid2", "uuid2"), new AnswerModel("NoUuid", null, null));
		
		Map<String, String> map = AnswerMapper.toMap(answers);
		
		assertNotNull(map);
		assertEquals(1, map.size());
		assertEquals("uuid2", map.get("valid"));
	}
	
	@Test
	public void toList_shouldConvertMapToListCorrectly() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("Active", "active-uuid");
		map.put("Inactive", "inactive-uuid");
		map.put("Pending", "pending-uuid");
		
		List<AnswerModel> list = AnswerMapper.toList(map);
		
		assertNotNull(list);
		assertEquals(3, list.size());
		assertEquals("Active", list.get(0).label);
		assertEquals("active-uuid", list.get(0).conceptUuid);
	}
	
	@Test
	public void toList_shouldHandleNullMap() {
		List<AnswerModel> list = AnswerMapper.toList(null);
		
		assertNotNull(list);
		assertTrue(list.isEmpty());
	}
	
	@Test
	public void getAnswerConceptUuid_shouldFindAnswerUuidByExactLabelMatch() {
		Map<String, String> answers = new HashMap<>();
		answers.put("Positive", "positive-uuid");
		answers.put("Negative", "negative-uuid");
		
		String uuid = AnswerMapper.getAnswerConceptUuid(answers, "Positive");
		
		assertEquals("positive-uuid", uuid);
	}
	
	@Test
	public void getAnswerConceptUuid_shouldFindAnswerUuidByNormalizedLabelMatch() {
		Map<String, String> answers = new HashMap<>();
		answers.put("Positive", "positive-uuid");
		answers.put("Negative", "negative-uuid");
		
		// Test case-insensitive and whitespace normalization
		String uuid = AnswerMapper.getAnswerConceptUuid(answers, "  positive  ");
		
		assertEquals("positive-uuid", uuid);
	}
	
	@Test
	public void getAnswerConceptUuid_shouldReturnNullForNonExistentLabel() {
		Map<String, String> answers = new HashMap<>();
		answers.put("Yes", "yes-uuid");
		
		String uuid = AnswerMapper.getAnswerConceptUuid(answers, "No");
		
		assertNull(uuid);
	}
	
	@Test
	public void getAnswerConceptUuid_shouldHandleNullInputs() {
		Map<String, String> answers = new HashMap<>();
		answers.put("Test", "test-uuid");
		
		assertNull(AnswerMapper.getAnswerConceptUuid((Map<String, String>) null, "Test"));
		assertNull(AnswerMapper.getAnswerConceptUuid(answers, null));
		assertNull(AnswerMapper.getAnswerConceptUuid((Map<String, String>) null, null));
	}
	
	@Test
	public void getAnswerConceptUuid_shouldFindAnswerUuidFromListByLabel() {
		List<AnswerModel> answers = Arrays.asList(new AnswerModel("Type 1", "type1-uuid", "type1-uuid"),
		    new AnswerModel("Type 2", "type2-uuid", "type2-uuid"));
		
		String uuid = AnswerMapper.getAnswerConceptUuid(answers, "Type 1");
		
		assertEquals("type1-uuid", uuid);
	}
	
	@Test
	public void isValidAnswer_shouldValidateAnswerExistsInMap() {
		Map<String, String> answers = new HashMap<>();
		answers.put("Valid", "valid-uuid");
		
		assertTrue(AnswerMapper.isValidAnswer(answers, "Valid"));
		assertFalse(AnswerMapper.isValidAnswer(answers, "Invalid"));
	}
	
	@Test
	public void isValidAnswer_shouldValidateAnswerExistsInList() {
		List<AnswerModel> answers = Arrays.asList(new AnswerModel("Option A", "a-uuid", "a-uuid"),
		    new AnswerModel("Option B", "b-uuid", "b-uuid"));
		
		assertTrue(AnswerMapper.isValidAnswer(answers, "Option A"));
		assertFalse(AnswerMapper.isValidAnswer(answers, "Option C"));
	}
	
	@Test
	public void getAnswerLabels_shouldGetAnswerLabelsFromMap() {
		Map<String, String> answers = new LinkedHashMap<>();
		answers.put("First", "uuid1");
		answers.put("Second", "uuid2");
		answers.put("Third", "uuid3");
		
		List<String> labels = AnswerMapper.getAnswerLabels(answers);
		
		assertNotNull(labels);
		assertEquals(3, labels.size());
		assertTrue(labels.contains("First"));
		assertTrue(labels.contains("Second"));
		assertTrue(labels.contains("Third"));
	}
	
	@Test
	public void getAnswerLabels_shouldGetAnswerLabelsFromList() {
		List<AnswerModel> answers = Arrays.asList(new AnswerModel("Alpha", "a-uuid", "a-uuid"),
		    new AnswerModel("Beta", "b-uuid", "b-uuid"), new AnswerModel(null, "c-uuid", "c-uuid") // Should skip null
		);
		
		List<String> labels = AnswerMapper.getAnswerLabels(answers);
		
		assertNotNull(labels);
		assertEquals(2, labels.size());
		assertTrue(labels.contains("Alpha"));
		assertTrue(labels.contains("Beta"));
	}
	
	@Test
	public void mergeAnswers_shouldMergeAnswerListsWithoutDuplicates() {
		List<AnswerModel> primary = Arrays.asList(new AnswerModel("A", "uuid-a", "uuid-a"),
		    new AnswerModel("B", "uuid-b", "uuid-b"));
		
		List<AnswerModel> secondary = Arrays.asList(new AnswerModel("B", "uuid-b", "uuid-b"), // Duplicate
		    new AnswerModel("C", "uuid-c", "uuid-c"));
		
		List<AnswerModel> merged = AnswerMapper.mergeAnswers(primary, secondary);
		
		assertNotNull(merged);
		assertEquals(3, merged.size());
		assertEquals("A", merged.get(0).label);
		assertEquals("B", merged.get(1).label);
		assertEquals("C", merged.get(2).label);
	}
	
	@Test
	public void mergeAnswers_shouldHandleNullLists() {
		List<AnswerModel> list = Arrays.asList(new AnswerModel("Test", "test-uuid", "test-uuid"));
		
		List<AnswerModel> result1 = AnswerMapper.mergeAnswers(null, list);
		assertEquals(1, result1.size());
		
		List<AnswerModel> result2 = AnswerMapper.mergeAnswers(list, null);
		assertEquals(1, result2.size());
		
		List<AnswerModel> result3 = AnswerMapper.mergeAnswers(null, null);
		assertTrue(result3.isEmpty());
	}
	
	@Test
	public void toJson_shouldConvertMapToJsonCorrectly() {
		Map<String, String> answers = new LinkedHashMap<>();
		answers.put("Yes", "yes-uuid");
		answers.put("No", "no-uuid");
		
		String json = AnswerMapper.toJson(answers);
		
		assertNotNull(json);
		assertTrue(json.startsWith("{"));
		assertTrue(json.endsWith("}"));
		assertTrue(json.contains("\"Yes\":\"yes-uuid\""));
		assertTrue(json.contains("\"No\":\"no-uuid\""));
	}
	
	@Test
	public void toJson_shouldConvertListToJsonCorrectly() {
		List<AnswerModel> answers = Arrays.asList(new AnswerModel("Active", "active-uuid", "active-uuid"),
		    new AnswerModel("Inactive", "inactive-uuid", "inactive-uuid"));
		
		String json = AnswerMapper.toJson(answers);
		
		assertNotNull(json);
		// Labels are normalized to lowercase
		assertTrue(json.contains("active"));
		assertTrue(json.contains("active-uuid"));
	}
	
	@Test
	public void toJson_shouldHandleEmptyMapInJsonConversion() {
		String json = AnswerMapper.toJson(new HashMap<>());
		
		assertEquals("{}", json);
	}
	
	@Test
	public void toJson_shouldEscapeQuotesInJson() {
		Map<String, String> answers = new HashMap<>();
		answers.put("Test \"quoted\" value", "test-uuid");
		
		String json = AnswerMapper.toJson(answers);
		
		assertTrue(json.contains("\\\""));
		assertFalse(json.contains("\"quoted\""));
	}
	
	@Test
	public void toMap_shouldPreserveInsertionOrder() {
		List<AnswerModel> answers = Arrays.asList(new AnswerModel("First", "uuid1", "uuid1"),
		    new AnswerModel("Second", "uuid2", "uuid2"), new AnswerModel("Third", "uuid3", "uuid3"));
		
		Map<String, String> map = AnswerMapper.toMap(answers);
		
		List<String> keys = new ArrayList<>(map.keySet());
		assertEquals("first", keys.get(0));
		assertEquals("second", keys.get(1));
		assertEquals("third", keys.get(2));
	}
	
	@Test
	public void toMapAndToList_shouldHandleRoundTripConversion() {
		List<AnswerModel> original = Arrays.asList(new AnswerModel("A", "uuid-a", "uuid-a"),
		    new AnswerModel("B", "uuid-b", "uuid-b"), new AnswerModel("C", "uuid-c", "uuid-c"));
		
		Map<String, String> map = AnswerMapper.toMap(original);
		List<AnswerModel> converted = AnswerMapper.toList(map);
		
		assertNotNull(converted);
		assertEquals(original.size(), converted.size());
	}
	
	@Test
	public void getAnswerConceptUuid_shouldHandleLabelsWithSpecialCharacters() {
		Map<String, String> answers = new HashMap<>();
		answers.put("Test & Value", "test-uuid");
		answers.put("Another < Value", "another-uuid");
		
		String uuid1 = AnswerMapper.getAnswerConceptUuid(answers, "Test & Value");
		String uuid2 = AnswerMapper.getAnswerConceptUuid(answers, "Another < Value");
		
		assertEquals("test-uuid", uuid1);
		assertEquals("another-uuid", uuid2);
	}
}
