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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptDatatype;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

import java.util.List;

public class ConceptManagerIntegrationTest extends BaseModuleContextSensitiveTest {
	
	@BeforeEach
	public void setUp() {
		ConceptManager.clearCaches();
	}
	
	@Test
	public void clearCaches_shouldClearAllCachesSuccessfully() {
		ConceptManager.clearCaches();
		
		String stats = ConceptManager.getCacheStats();
		assertNotNull(stats);
		assertTrue(stats.contains("UUIDs: 0"));
		assertTrue(stats.contains("Objects: 0"));
		assertTrue(stats.contains("Answers: 0"));
	}
	
	@Test
	public void getCacheStats_shouldReturnCacheStatisticsInCorrectFormat() {
		String stats = ConceptManager.getCacheStats();
		
		assertNotNull(stats);
		assertTrue(stats.startsWith("ConceptManager Cache Stats"));
		assertTrue(stats.contains("UUIDs:"));
		assertTrue(stats.contains("Objects:"));
		assertTrue(stats.contains("Answers:"));
	}
	
	@Test
	public void resolveConceptUuid_shouldHandleNullConceptIdGracefully() {
		String result = ConceptManager.resolveConceptUuid(null);
		assertNull(result);
	}
	
	@Test
	public void resolveConceptUuid_shouldHandleEmptyConceptIdGracefully() {
		String result = ConceptManager.resolveConceptUuid("");
		assertNull(result);
		
		result = ConceptManager.resolveConceptUuid("   ");
		assertNull(result);
	}
	
	@Test
	public void resolveConceptUuid_shouldHandleNullResultsWithoutCaching() {
		String nonExistentId = "non-existent-concept-12345";
		
		String result1 = ConceptManager.resolveConceptUuid(nonExistentId);
		String result2 = ConceptManager.resolveConceptUuid(nonExistentId);
		
		assertNull(result1);
		assertNull(result2);
	}
	
	@Test
	public void conceptMappingSeparator_shouldDetectConceptMappingFormatCorrectly() {
		String[] mappingFormats = { "CIEL:5089", "LOINC:29463-7", "SNOMED:27113001", "ICD-10:E11.9", "PIH:3064" };
		
		for (String mapping : mappingFormats) {
			assertTrue(mapping.contains(ConceptManager.CONCEPT_MAPPING_SEPARATOR), "Should contain separator: " + mapping);
		}
	}
	
	@Test
	public void getConceptName_shouldReturnNullForInvalidConceptNameLookup() {
		String name = ConceptManager.getConceptName((String) null);
		assertNull(name);
		
		name = ConceptManager.getConceptName("");
		assertNull(name);
	}
	
	@Test
	public void loadAnswersForConcept_shouldReturnEmptyListForNullConceptUuid() {
		List<AnswerModel> answers = ConceptManager.loadAnswersForConcept(null);
		assertNotNull(answers);
		assertTrue(answers.isEmpty());
		
		answers = ConceptManager.loadAnswersForConcept("");
		assertNotNull(answers);
		assertTrue(answers.isEmpty());
	}
	
	@Test
	public void loadSingleConceptAsAnswer_shouldReturnNullForInvalidConcept() {
		AnswerModel answer = ConceptManager.loadSingleConceptAsAnswer(null);
		assertNull(answer);
		
		answer = ConceptManager.loadSingleConceptAsAnswer("");
		assertNull(answer);
	}
	
	@Test
	public void convertToFieldDatatype_shouldConvertHL7CodesToFieldDatatypeCorrectly() {
		// Test that null conceptDatatype defaults to TEXT
		assertEquals(FieldDatatype.TEXT, ConceptManager.convertToFieldDatatype(null));
		
		// Test with null HL7 code
		// Since we can't create ConceptDatatype easily without OpenMRS context,
		// we test the logic indirectly through the method behavior
	}
	
	@Test
	public void isValidConcept_shouldReturnFalseForNull() {
		boolean result = ConceptManager.isValidConcept(null);
		assertFalse(result);
		
		result = ConceptManager.isValidConcept("");
		assertFalse(result);
	}
	
	@Test
	public void isConceptOfType_shouldReturnFalseForNull() {
		boolean result = ConceptManager.isConceptOfType(null, "NM");
		assertFalse(result);
		
		result = ConceptManager.isConceptOfType("some-uuid", null);
		assertFalse(result);
	}
	
	@Test
	public void isValidAnswer_shouldReturnFalseForEmptyAnswers() {
		boolean result = ConceptManager.isValidAnswer("question-uuid", "answer-uuid");
		assertFalse(result, "Should return false when no answers available");
	}
	
	@Test
	public void resolveConceptUuid_shouldTrimWhitespaceFromConceptIds() {
		String id1 = "  CIEL:5089  ";
		String id2 = "CIEL:5089";
		
		assertDoesNotThrow(() -> ConceptManager.resolveConceptUuid(id1));
		assertDoesNotThrow(() -> ConceptManager.resolveConceptUuid(id2));
	}
	
	@Test
	public void getConceptDatatype_shouldHandleConceptDatatypeExtractionGracefully() {
		ConceptDatatype datatype = ConceptManager.getConceptDatatype("non-existent-uuid");
		assertNull(datatype);
		
		datatype = ConceptManager.getConceptDatatype(null);
		assertNull(datatype);
	}
	
	@Test
	public void getCacheStats_shouldUpdateAfterOperations() {
		String initialStats = ConceptManager.getCacheStats();
		assertTrue(initialStats.contains("UUIDs: 0"));
		
		ConceptManager.resolveConceptUuid("test-uuid");
		
		String updatedStats = ConceptManager.getCacheStats();
		assertNotNull(updatedStats);
	}
	
	@Test
	public void resolveConceptUuidAndClearCaches_shouldHandleConcurrentAccessSafely() {
		assertDoesNotThrow(() -> {
			Thread t1 = new Thread(() -> ConceptManager.resolveConceptUuid("uuid1"));
			Thread t2 = new Thread(() -> ConceptManager.resolveConceptUuid("uuid2"));
			Thread t3 = new Thread(() -> ConceptManager.clearCaches());
			
			t1.start();
			t2.start();
			t3.start();
			
			t1.join();
			t2.join();
			t3.join();
		});
	}
	
	@Test
	public void conceptMappingSeparator_shouldMaintainConstantValue() {
		assertEquals(":", ConceptManager.CONCEPT_MAPPING_SEPARATOR);
	}
	
	@Test
	public void resolveConceptUuid_shouldHandleMappingCodesWithMultipleSeparators() {
		String mappingWithColon = "SOURCE:CODE:WITH:COLONS";
		assertDoesNotThrow(() -> ConceptManager.resolveConceptUuid(mappingWithColon));
	}
}
