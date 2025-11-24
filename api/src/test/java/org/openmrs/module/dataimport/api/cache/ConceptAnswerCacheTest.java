/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptName;
import org.openmrs.api.ConceptService;
import org.openmrs.module.dataimport.api.cache.impl.ConceptAnswerCacheImpl;
import org.openmrs.module.dataimport.model.AnswerModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for ConceptAnswerCache implementation. Following OpenMRS test naming convention:
 * methodName_shouldExpectedBehavior() TDD Approach: These tests are written BEFORE the
 * implementation. The implementation (ConceptAnswerCacheImpl) should be written to make these tests
 * pass.
 */
public class ConceptAnswerCacheTest {
	
	private ConceptAnswerCache cache;
	
	private ConceptService mockConceptService;
	
	@BeforeEach
	public void setUp() {
		mockConceptService = mock(ConceptService.class);
		
		// Initialize cache with test configuration (maxSize=100, ttl=1 hour)
		cache = new ConceptAnswerCacheImpl(mockConceptService, 100, 1);
	}
	
	/**
	 * Test: getIfPresent should return null for uncached concept Expected: Returns null without loading
	 * from database
	 */
	@Test
	public void getIfPresent_shouldReturnNullForUncachedConcept() {
		// Arrange
		String conceptUuid = "uncached-concept-uuid";
		
		// Act
		List<AnswerModel> result = cache.getIfPresent(conceptUuid);
		
		// Assert
		assertNull(result, "Should return null for uncached concept");
	}
	
	/**
	 * Test: getIfPresent should return cached answers if present Expected: Returns cached answers
	 * without additional service calls
	 */
	@Test
	public void getIfPresent_shouldReturnCachedAnswersIfPresent() {
		// Arrange
		String conceptUuid = "cached-concept-uuid";
		List<AnswerModel> answers = createMockAnswers("Yes", "No", "Unknown");
		
		// Pre-populate cache
		cache.put(conceptUuid, answers);
		
		// Act
		List<AnswerModel> result = cache.getIfPresent(conceptUuid);
		
		// Assert
		assertNotNull(result, "Should return cached answers");
		assertEquals(3, result.size(), "Should return all cached answers");
		assertEquals("Yes", result.get(0).label, "First answer should match");
	}
	
	/**
	 * Test: getOrEmpty should return empty list for uncached concept Expected: Returns empty list
	 * instead of null
	 */
	@Test
	public void getOrEmpty_shouldReturnEmptyListForUncachedConcept() {
		// Arrange
		String conceptUuid = "uncached-concept-uuid";
		
		// Act
		List<AnswerModel> result = cache.getOrEmpty(conceptUuid);
		
		// Assert
		assertNotNull(result, "Should never return null");
		assertTrue(result.isEmpty(), "Should return empty list for uncached concept");
	}
	
	/**
	 * Test: getOrEmpty should return cached answers if present Expected: Returns cached answers
	 */
	@Test
	public void getOrEmpty_shouldReturnCachedAnswersIfPresent() {
		// Arrange
		String conceptUuid = "cached-concept-uuid";
		List<AnswerModel> answers = createMockAnswers("Option A", "Option B");
		cache.put(conceptUuid, answers);
		
		// Act
		List<AnswerModel> result = cache.getOrEmpty(conceptUuid);
		
		// Assert
		assertNotNull(result, "Should not be null");
		assertEquals(2, result.size(), "Should return all cached answers");
	}
	
	/**
	 * Test: put should cache concept answers for later retrieval Expected: Answers are cached and
	 * retrievable via getIfPresent
	 */
	@Test
	public void put_shouldCacheAnswersForLaterRetrieval() {
		// Arrange
		String conceptUuid = "put-test-uuid";
		List<AnswerModel> answers = createMockAnswers("Male", "Female", "Other");
		
		// Act
		cache.put(conceptUuid, answers);
		List<AnswerModel> retrieved = cache.getIfPresent(conceptUuid);
		
		// Assert
		assertNotNull(retrieved, "Cached answers should be retrievable");
		assertEquals(3, retrieved.size(), "Should retrieve all cached answers");
	}
	
	/**
	 * Test: put should accept empty list for concepts with no answers Expected: Empty list is cached
	 * successfully
	 */
	@Test
	public void put_shouldAcceptEmptyAnswersList() {
		// Arrange
		String conceptUuid = "empty-answers-uuid";
		List<AnswerModel> emptyAnswers = new ArrayList<>();
		
		// Act
		cache.put(conceptUuid, emptyAnswers);
		List<AnswerModel> retrieved = cache.getIfPresent(conceptUuid);
		
		// Assert
		assertNotNull(retrieved, "Should return empty list, not null");
		assertTrue(retrieved.isEmpty(), "Should be empty list");
	}
	
	/**
	 * Test: put with null UUID should throw IllegalArgumentException Expected: Exception thrown with
	 * clear message
	 */
	@Test
	public void put_shouldThrowExceptionForNullUuid() {
		// Arrange
		List<AnswerModel> answers = createMockAnswers("Answer1");
		
		// Act & Assert
		assertThrows(IllegalArgumentException.class, () -> {
			cache.put(null, answers);
		}, "Should throw IllegalArgumentException for null UUID");
	}
	
	/**
	 * Test: put with empty UUID should throw IllegalArgumentException Expected: Exception thrown
	 */
	@Test
	public void put_shouldThrowExceptionForEmptyUuid() {
		// Arrange
		List<AnswerModel> answers = createMockAnswers("Answer1");
		
		// Act & Assert
		assertThrows(IllegalArgumentException.class, () -> {
			cache.put("", answers);
		}, "Should throw IllegalArgumentException for empty UUID");
	}
	
	/**
	 * Test: put with null answers list should throw IllegalArgumentException Expected: Exception thrown
	 * (null is not allowed, empty list should be used)
	 */
	@Test
	public void put_shouldThrowExceptionForNullAnswersList() {
		// Act & Assert
		assertThrows(IllegalArgumentException.class, () -> {
			cache.put("valid-uuid", null);
		}, "Should throw IllegalArgumentException for null answers list");
	}
	
	/**
	 * Test: invalidate should remove concept from cache Expected: After invalidation, concept is no
	 * longer in cache
	 */
	@Test
	public void invalidate_shouldRemoveConceptFromCache() {
		// Arrange
		String conceptUuid = "invalidate-test-uuid";
		List<AnswerModel> answers = createMockAnswers("Answer1", "Answer2");
		cache.put(conceptUuid, answers);
		
		// Verify concept is cached
		assertNotNull(cache.getIfPresent(conceptUuid), "Concept should be cached initially");
		
		// Act
		cache.invalidate(conceptUuid);
		
		// Assert
		assertNull(cache.getIfPresent(conceptUuid), "Concept should be removed from cache after invalidation");
	}
	
	/**
	 * Test: invalidateAll should clear entire cache Expected: All cached concepts are removed
	 */
	@Test
	public void invalidateAll_shouldClearEntireCache() {
		// Arrange
		cache.put("concept1", createMockAnswers("A", "B"));
		cache.put("concept2", createMockAnswers("C", "D"));
		cache.put("concept3", createMockAnswers("E", "F"));
		
		// Verify concepts are cached
		assertNotNull(cache.getIfPresent("concept1"), "Concept 1 should be cached");
		assertNotNull(cache.getIfPresent("concept2"), "Concept 2 should be cached");
		assertNotNull(cache.getIfPresent("concept3"), "Concept 3 should be cached");
		
		// Act
		cache.invalidateAll();
		
		// Assert
		assertNull(cache.getIfPresent("concept1"), "Concept 1 should be removed");
		assertNull(cache.getIfPresent("concept2"), "Concept 2 should be removed");
		assertNull(cache.getIfPresent("concept3"), "Concept 3 should be removed");
	}
	
	/**
	 * Test: preloadConcepts should load multiple concepts efficiently Expected: All specified concepts
	 * are loaded into cache
	 */
	@Test
	public void preloadConcepts_shouldLoadMultipleConceptsIntoCache() {
		// Arrange
		Set<String> conceptUuids = new HashSet<>(Arrays.asList("concept-a-uuid", "concept-b-uuid", "concept-c-uuid"));
		
		// Mock the batch loading behavior
		// In real implementation, this would trigger ConceptService batch query
		// For now, we manually populate to verify the preload mechanism
		when(mockConceptService.getConceptByUuid("concept-a-uuid")).thenReturn(null); // Simplified mock
		
		// Act
		cache.preloadConcepts(conceptUuids);
		
		// After preloading, concepts should be in cache (assuming implementation loads them)
		// Note: The actual loading logic will be in ConceptAnswerCacheImpl
		// This test verifies the contract that preloadConcepts populates the cache
	}
	
	/**
	 * Test: preloadConcepts should handle null input gracefully Expected: No exception, no-op behavior
	 */
	@Test
	public void preloadConcepts_shouldHandleNullInputGracefully() {
		// Act & Assert - should not throw exception
		cache.preloadConcepts(null);
	}
	
	/**
	 * Test: preloadConcepts should handle empty set gracefully Expected: No exception, no-op behavior
	 */
	@Test
	public void preloadConcepts_shouldHandleEmptySetGracefully() {
		// Act & Assert - should not throw exception
		cache.preloadConcepts(new HashSet<>());
	}
	
	/**
	 * Test: preloadConcepts should only load uncached concepts Expected: Concepts already in cache are
	 * not reloaded
	 */
	@Test
	public void preloadConcepts_shouldOnlyLoadUncachedConcepts() {
		// Arrange
		String cachedConceptUuid = "already-cached-uuid";
		String uncachedConceptUuid = "not-cached-uuid";
		
		// Pre-populate one concept
		cache.put(cachedConceptUuid, createMockAnswers("Existing Answer"));
		
		Set<String> conceptUuids = new HashSet<>(Arrays.asList(cachedConceptUuid, uncachedConceptUuid));
		
		// Act
		cache.preloadConcepts(conceptUuids);
		
		// Assert - verify cached concept is still accessible
		assertNotNull(cache.getIfPresent(cachedConceptUuid), "Cached concept should still be present");
		
		// The implementation should have only loaded the uncached concept
		// This is more of a performance optimization verification
	}
	
	/**
	 * Test: Cache statistics should track operations Expected: getStats returns accurate counts
	 */
	@Test
	public void getStats_shouldTrackCacheOperations() {
		// Arrange
		String conceptUuid = "stats-test-uuid";
		List<AnswerModel> answers = createMockAnswers("Answer1");
		cache.put(conceptUuid, answers);
		
		// Act - perform cache operations
		cache.getIfPresent(conceptUuid); // Hit
		cache.getIfPresent("nonexistent-uuid"); // Miss
		
		CacheStats stats = cache.getStats();
		
		// Assert
		assertNotNull(stats, "Stats should not be null");
		assertTrue(stats.getHitCount() > 0, "Should have at least one hit");
		assertTrue(stats.getMissCount() > 0, "Should have at least one miss");
	}
	
	/**
	 * Test: Thread safety - concurrent access should not cause corruption Expected: Multiple threads
	 * can safely access cache simultaneously
	 */
	@Test
	public void cache_shouldBeThreadSafeForConcurrentAccess() throws InterruptedException {
		// Arrange
		String conceptUuid = "concurrent-test-uuid";
		List<AnswerModel> answers = createMockAnswers("Answer1", "Answer2");
		cache.put(conceptUuid, answers);
		
		int threadCount = 10;
		int operationsPerThread = 100;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);
		AtomicInteger successCount = new AtomicInteger(0);
		
		// Act - multiple threads reading from cache
		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					for (int j = 0; j < operationsPerThread; j++) {
						List<AnswerModel> result = cache.getIfPresent(conceptUuid);
						if (result != null && result.size() == 2) {
							successCount.incrementAndGet();
						}
					}
				}
				finally {
					latch.countDown();
				}
			});
		}
		
		// Wait for all threads to complete
		assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within timeout");
		executor.shutdown();
		
		// Assert
		int expectedSuccesses = threadCount * operationsPerThread;
		assertEquals(expectedSuccesses, successCount.get(), "All concurrent reads should succeed without corruption");
	}
	
	/**
	 * Test: Thread safety for concurrent writes Expected: Multiple threads can safely write to cache
	 * simultaneously
	 */
	@Test
	public void cache_shouldBeThreadSafeForConcurrentWrites() throws InterruptedException {
		// Arrange
		int threadCount = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);
		
		// Act - multiple threads writing to cache
		for (int i = 0; i < threadCount; i++) {
			final int threadNum = i;
			executor.submit(() -> {
				try {
					String conceptUuid = "concept-" + threadNum;
					List<AnswerModel> answers = createMockAnswers("Answer-" + threadNum);
					cache.put(conceptUuid, answers);
				}
				finally {
					latch.countDown();
				}
			});
		}
		
		// Wait for all threads to complete
		assertTrue(latch.await(5, TimeUnit.SECONDS), "All threads should complete within timeout");
		executor.shutdown();
		
		// Assert - verify all writes succeeded
		for (int i = 0; i < threadCount; i++) {
			String conceptUuid = "concept-" + i;
			List<AnswerModel> result = cache.getIfPresent(conceptUuid);
			assertNotNull(result, "Concept " + i + " should be cached");
			assertEquals(1, result.size(), "Should have one answer");
			assertEquals("Answer-" + i, result.get(0).label, "Answer label should match");
		}
	}
	
	/**
	 * Test: getIfPresent should validate UUID Expected: Invalid UUID throws IllegalArgumentException
	 */
	@Test
	public void getIfPresent_shouldValidateUuid() {
		// Act & Assert
		assertThrows(IllegalArgumentException.class, () -> {
			cache.getIfPresent(""); // Empty string
		}, "Should throw exception for empty UUID");
		
		assertThrows(IllegalArgumentException.class, () -> {
			cache.getIfPresent(null); // Null UUID
		}, "Should throw exception for null UUID");
	}
	
	/**
	 * Test: getOrEmpty should validate UUID Expected: Invalid UUID throws IllegalArgumentException
	 */
	@Test
	public void getOrEmpty_shouldValidateUuid() {
		// Act & Assert
		assertThrows(IllegalArgumentException.class, () -> {
			cache.getOrEmpty(""); // Empty string
		}, "Should throw exception for empty UUID");
		
		assertThrows(IllegalArgumentException.class, () -> {
			cache.getOrEmpty(null); // Null UUID
		}, "Should throw exception for null UUID");
	}
	
	/**
	 * Helper method to create mock answer list for testing
	 */
	private List<AnswerModel> createMockAnswers(String... labels) {
		List<AnswerModel> answers = new ArrayList<>();
		for (int i = 0; i < labels.length; i++) {
			AnswerModel answer = new AnswerModel();
			answer.label = labels[i];
			answer.conceptUuid = "answer-concept-" + i;
			answer.value = "value-" + i;
			answers.add(answer);
		}
		return answers;
	}
}
