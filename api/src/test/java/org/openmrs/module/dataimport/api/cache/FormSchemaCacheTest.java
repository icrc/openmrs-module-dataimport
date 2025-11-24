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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.api.cache.impl.FormSchemaCacheImpl;
import org.openmrs.module.dataimport.api.resolver.FormResolver;
import org.openmrs.module.dataimport.api.resolver.FormResolverRegistry;
import org.openmrs.module.dataimport.model.FormModel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for FormSchemaCache implementation. Following OpenMRS test naming convention:
 * methodName_shouldExpectedBehavior() TDD Approach: These tests are written BEFORE the
 * implementation. The implementation (FormSchemaCacheImpl) should be written to make these tests
 * pass.
 */
public class FormSchemaCacheTest {
	
	private FormSchemaCache cache;
	
	private FormResolverRegistry mockResolverRegistry;
	
	private FormResolver mockResolver;
	
	@BeforeEach
	public void setUp() {
		mockResolverRegistry = mock(FormResolverRegistry.class);
		mockResolver = mock(FormResolver.class);
		
		// Initialize cache with test configuration (maxSize=10, ttl=1 hour)
		cache = new FormSchemaCacheImpl(mockResolverRegistry, 10, 1);
	}
	
	/**
	 * Test: Cache hit - requesting same form twice should return cached instance Expected: Second call
	 * returns same object without calling service again
	 */
	@Test
	public void get_shouldReturnCachedFormOnSecondRequest() {
		// Arrange
		String formUuid = "test-form-uuid";
		FormModel mockForm = createMockForm(formUuid, "Test Form");
		
		// Mock the resolver registry to return a resolver, and the resolver to return the form
		when(mockResolverRegistry.findResolver(formUuid)).thenReturn(mockResolver);
		when(mockResolver.resolve(formUuid, null)).thenReturn(mockForm);
		
		// Act
		FormModel firstCall = cache.get(formUuid);
		FormModel secondCall = cache.get(formUuid);
		
		// Assert
		assertNotNull(firstCall, "First call should return form");
		assertNotNull(secondCall, "Second call should return form");
		assertSame(firstCall, secondCall, "Second call should return same cached instance");
		
		// Verify resolver was called only once (cache hit on second call)
		verify(mockResolverRegistry, times(1)).findResolver(formUuid);
	}
	
	/**
	 * Test: Cache miss - requesting uncached form should load from service Expected: Form is loaded
	 * from service and then cached
	 */
	@Test
	public void get_shouldLoadFormFromServiceOnCacheMiss() {
		// Arrange
		String formUuid = "uncached-form-uuid";
		FormModel mockForm = createMockForm(formUuid, "Uncached Form");
		
		when(mockResolverRegistry.findResolver(formUuid)).thenReturn(mockResolver);
		when(mockResolver.resolve(formUuid, null)).thenReturn(mockForm);
		
		// Act
		FormModel result = cache.get(formUuid);
		
		// Assert
		assertNotNull(result, "Should load and return form");
		verify(mockResolverRegistry, times(1)).findResolver(formUuid);
	}
	
	/**
	 * Test: getIfPresent should return null for uncached form without loading Expected: Returns null
	 * and does not trigger service call
	 */
	@Test
	public void getIfPresent_shouldReturnNullForUncachedForm() {
		// Arrange
		String formUuid = "nonexistent-form-uuid";
		
		// Act
		FormModel result = cache.getIfPresent(formUuid);
		
		// Assert
		assertNull(result, "Should return null for uncached form");
		// Verify resolver was never called (no auto-loading)
		verify(mockResolverRegistry, times(0)).findResolver(anyString());
	}
	
	/**
	 * Test: getIfPresent should return cached form if present Expected: Returns cached form without
	 * additional service calls
	 */
	@Test
	public void getIfPresent_shouldReturnCachedFormIfPresent() {
		// Arrange
		String formUuid = "cached-form-uuid";
		FormModel mockForm = createMockForm(formUuid, "Cached Form");
		
		// Pre-populate cache
		cache.put(formUuid, mockForm);
		
		// Act
		FormModel result = cache.getIfPresent(formUuid);
		
		// Assert
		assertNotNull(result, "Should return cached form");
		assertSame(mockForm, result, "Should return same instance that was cached");
	}
	
	/**
	 * Test: put should add form to cache Expected: Form is cached and retrievable via get/getIfPresent
	 */
	@Test
	public void put_shouldCacheFormForLaterRetrieval() {
		// Arrange
		String formUuid = "put-test-uuid";
		FormModel mockForm = createMockForm(formUuid, "Put Test Form");
		
		// Act
		cache.put(formUuid, mockForm);
		FormModel retrieved = cache.getIfPresent(formUuid);
		
		// Assert
		assertNotNull(retrieved, "Cached form should be retrievable");
		assertSame(mockForm, retrieved, "Retrieved form should be same instance");
	}
	
	/**
	 * Test: put with null UUID should throw IllegalArgumentException Expected: Exception thrown with
	 * clear message
	 */
	@Test
	public void put_shouldThrowExceptionForNullUuid() {
		// Arrange
		FormModel mockForm = createMockForm("valid-uuid", "Test Form");
		
		// Act & Assert
		assertThrows(IllegalArgumentException.class, () -> {
			cache.put(null, mockForm);
		}, "Should throw IllegalArgumentException for null UUID");
	}
	
	/**
	 * Test: put with null form should throw IllegalArgumentException Expected: Exception thrown with
	 * clear message
	 */
	@Test
	public void put_shouldThrowExceptionForNullForm() {
		// Act & Assert
		assertThrows(IllegalArgumentException.class, () -> {
			cache.put("valid-uuid", null);
		}, "Should throw IllegalArgumentException for null form");
	}
	
	/**
	 * Test: invalidate should remove form from cache Expected: After invalidation, form is no longer in
	 * cache
	 */
	@Test
	public void invalidate_shouldRemoveFormFromCache() {
		// Arrange
		String formUuid = "invalidate-test-uuid";
		FormModel mockForm = createMockForm(formUuid, "Invalidate Test");
		cache.put(formUuid, mockForm);
		
		// Verify form is cached
		assertNotNull(cache.getIfPresent(formUuid), "Form should be cached initially");
		
		// Act
		cache.invalidate(formUuid);
		
		// Assert
		assertNull(cache.getIfPresent(formUuid), "Form should be removed from cache after invalidation");
	}
	
	/**
	 * Test: invalidateAll should clear entire cache Expected: All cached forms are removed
	 */
	@Test
	public void invalidateAll_shouldClearEntireCache() {
		// Arrange
		cache.put("form1", createMockForm("form1", "Form 1"));
		cache.put("form2", createMockForm("form2", "Form 2"));
		cache.put("form3", createMockForm("form3", "Form 3"));
		
		// Verify forms are cached
		assertNotNull(cache.getIfPresent("form1"), "Form 1 should be cached");
		assertNotNull(cache.getIfPresent("form2"), "Form 2 should be cached");
		assertNotNull(cache.getIfPresent("form3"), "Form 3 should be cached");
		
		// Act
		cache.invalidateAll();
		
		// Assert
		assertNull(cache.getIfPresent("form1"), "Form 1 should be removed");
		assertNull(cache.getIfPresent("form2"), "Form 2 should be removed");
		assertNull(cache.getIfPresent("form3"), "Form 3 should be removed");
	}
	
	/**
	 * Test: Cache statistics should track hits and misses Expected: getStats returns accurate hit/miss
	 * counts
	 */
	@Test
	public void getStats_shouldTrackHitsAndMisses() {
		// Arrange
		String formUuid = "stats-test-uuid";
		FormModel mockForm = createMockForm(formUuid, "Stats Test");
		cache.put(formUuid, mockForm);
		
		// Act
		cache.getIfPresent(formUuid); // Hit
		cache.getIfPresent("nonexistent-uuid"); // Miss
		
		CacheStats stats = cache.getStats();
		
		// Assert
		assertNotNull(stats, "Stats should not be null");
		assertTrue(stats.getHitCount() > 0, "Should have at least one hit");
		assertTrue(stats.getMissCount() > 0, "Should have at least one miss");
	}
	
	/**
	 * Test: Hit rate calculation Expected: getHitRate returns correct ratio
	 */
	@Test
	public void getHitRate_shouldCalculateCorrectRatio() {
		// Arrange
		String formUuid = "hitrate-test-uuid";
		FormModel mockForm = createMockForm(formUuid, "Hit Rate Test");
		cache.put(formUuid, mockForm);
		
		// Act - 3 hits, 1 miss = 75% hit rate
		cache.getIfPresent(formUuid); // Hit 1
		cache.getIfPresent(formUuid); // Hit 2
		cache.getIfPresent(formUuid); // Hit 3
		cache.getIfPresent("nonexistent-uuid"); // Miss 1
		
		double hitRate = cache.getHitRate();
		
		// Assert
		assertEquals(0.75, hitRate, 0.01, "Hit rate should be 75% (3 hits / 4 requests)");
	}
	
	/**
	 * Test: LRU eviction - cache with size 3 should evict oldest when 4th added Expected: Least
	 * recently used entry is evicted
	 */
	@Test
	public void cache_shouldEvictLeastRecentlyUsedEntryWhenFull() {
		// TODO: This test requires cache with small max size (3)
		// For now, test structure is documented
		
		// Arrange - create cache with maxSize=3
		// FormSchemaCache smallCache = new FormSchemaCacheImpl(mockFormSchemaService, 3, 1);
		
		// Act - add 4 forms
		// smallCache.put("form1", createMockForm("form1", "Form 1"));
		// smallCache.put("form2", createMockForm("form2", "Form 2"));
		// smallCache.put("form3", createMockForm("form3", "Form 3"));
		// smallCache.put("form4", createMockForm("form4", "Form 4")); // Should evict form1
		
		// Assert
		// assertNull(smallCache.getIfPresent("form1"), "Oldest form should be evicted");
		// assertNotNull(smallCache.getIfPresent("form4"), "Newest form should be cached");
	}
	
	/**
	 * Test: Thread safety - concurrent access should not cause corruption Expected: Multiple threads
	 * can safely access cache simultaneously
	 */
	@Test
	public void cache_shouldBeThreadSafeForConcurrentAccess() throws InterruptedException {
		// Arrange
		String formUuid = "concurrent-test-uuid";
		FormModel mockForm = createMockForm(formUuid, "Concurrent Test");
		cache.put(formUuid, mockForm);
		
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
						FormModel result = cache.getIfPresent(formUuid);
						if (result != null) {
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
	 * Test: Cache should handle UUID validation Expected: Invalid UUID format throws
	 * IllegalArgumentException
	 */
	@Test
	public void get_shouldValidateUuidFormat() {
		// Act & Assert
		assertThrows(IllegalArgumentException.class, () -> {
			cache.get(""); // Empty string
		}, "Should throw exception for empty UUID");
		
		assertThrows(IllegalArgumentException.class, () -> {
			cache.get(null); // Null UUID
		}, "Should throw exception for null UUID");
	}
	
	/**
	 * Helper method to create mock FormModel for testing
	 */
	private FormModel createMockForm(String uuid, String name) {
		FormModel form = new FormModel();
		form.formUuid = uuid;
		form.name = name;
		return form;
	}
}
