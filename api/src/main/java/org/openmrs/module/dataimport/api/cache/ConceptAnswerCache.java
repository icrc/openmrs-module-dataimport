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

import org.openmrs.module.dataimport.model.AnswerModel;

import java.util.List;
import java.util.Set;

/**
 * Cache service for concept answers to reduce repeated ConceptService queries. Thread-safe cache
 * with LRU eviction and TTL expiration.
 * <p>
 * This cache stores concept-to-answers mappings for coded fields to avoid repeated database queries
 * during template generation. Particularly beneficial for multi-form templates where the same
 * concepts appear across multiple forms.
 * </p>
 * <p>
 * <b>Batch Loading:</b> Supports pre-loading multiple concepts in a single database query for
 * optimal performance in multi-form scenarios.
 * </p>
 * <p>
 * <b>Thread Safety:</b> All operations are thread-safe and support concurrent access.
 * </p>
 *
 * @since 1.0.0
 */
public interface ConceptAnswerCache {
	
	/**
	 * Retrieve concept answers from cache if present.
	 * <p>
	 * This method only checks the cache - it does NOT load from database if absent. Explicit loading
	 * must be done separately using put() or preloadConcepts().
	 * </p>
	 *
	 * @param conceptUuid UUID of the concept
	 * @return List of AnswerModel if cached, null if not present in cache
	 * @throws IllegalArgumentException if conceptUuid is null or empty
	 */
	List<AnswerModel> getIfPresent(String conceptUuid);
	
	/**
	 * Retrieve concept answers from cache or return empty list if absent.
	 * <p>
	 * Convenience method that never returns null. If the concept is not cached, returns an empty list
	 * instead of null.
	 * </p>
	 *
	 * @param conceptUuid UUID of the concept
	 * @return List of AnswerModel (empty if not cached), never null
	 * @throws IllegalArgumentException if conceptUuid is null or empty
	 */
	List<AnswerModel> getOrEmpty(String conceptUuid);
	
	/**
	 * Add concept answers to the cache.
	 *
	 * @param conceptUuid UUID of the concept (must not be null or empty)
	 * @param answers List of answers (must not be null, can be empty for concepts with no answers)
	 * @throws IllegalArgumentException if conceptUuid is null/empty or answers is null
	 */
	void put(String conceptUuid, List<AnswerModel> answers);
	
	/**
	 * Batch pre-load concept answers for multiple concepts.
	 * <p>
	 * This method performs a single database query for all uncached concepts, which is significantly
	 * more efficient than loading them individually.
	 * </p>
	 * <p>
	 * <b>Use Case:</b> When generating multi-form templates, collect all concept UUIDs from all forms
	 * first, then call this method once before enriching forms with answers.
	 * </p>
	 * <p>
	 * <b>Behavior:</b> Only loads concepts that are not already in cache (cache-aware).
	 * </p>
	 *
	 * @param conceptUuids Set of concept UUIDs to pre-load (null or empty is tolerated, no-op)
	 */
	void preloadConcepts(Set<String> conceptUuids);
	
	/**
	 * Remove specific concept from cache.
	 * <p>
	 * Called when concept dictionary is updated to prevent serving stale data.
	 * </p>
	 *
	 * @param conceptUuid UUID of the concept to invalidate
	 */
	void invalidate(String conceptUuid);
	
	/**
	 * Clear all cached concept answers.
	 * <p>
	 * Used for administrative cache management or testing.
	 * </p>
	 */
	void invalidateAll();
	
	/**
	 * Get cache statistics including hit/miss counts.
	 *
	 * @return CacheStats object with current statistics, never null
	 */
	CacheStats getStats();
}
