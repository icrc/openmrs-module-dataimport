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

import org.openmrs.module.dataimport.model.FormModel;

/**
 * Cache service for form schemas to improve template generation performance. Thread-safe cache with
 * LRU (Least Recently Used) eviction and TTL (Time-To-Live) expiration.
 * <p>
 * This cache stores resolved FormModel objects to avoid repeated database queries and JSON parsing
 * during template generation. The cache is transparent to callers - failures fall back to direct
 * database access.
 * </p>
 * <p>
 * <b>Thread Safety:</b> All operations are thread-safe and support concurrent access.
 * </p>
 * <p>
 * <b>Eviction Policy:</b> Entries are evicted based on:
 * <ul>
 * <li>LRU - Least recently used entries are removed when cache is full</li>
 * <li>TTL - Entries expire after configured time-to-live duration</li>
 * <li>Explicit invalidation - When forms are updated in OpenMRS</li>
 * </ul>
 * </p>
 * 
 * @since 1.0.0
 */
public interface FormSchemaCache {
	
	/**
	 * Retrieve a form model from cache or load from database if not cached.
	 * <p>
	 * This method uses a loading cache pattern - if the form is not present, it will be
	 * automatically loaded from the database and cached.
	 * </p>
	 * 
	 * @param formUuid UUID of the form to retrieve (must be valid UUID format)
	 * @return FormModel instance, never null
	 * @throws IllegalArgumentException if formUuid is null, empty, or invalid format
	 * @throws org.openmrs.api.APIException if form loading fails after cache miss
	 */
	FormModel get(String formUuid);
	
	/**
	 * Retrieve a form model from cache without loading from database if absent.
	 * <p>
	 * This method only checks the cache - it will not trigger database access if the form is not
	 * present. Use this when you want to check cache presence without side effects.
	 * </p>
	 * 
	 * @param formUuid UUID of the form to retrieve
	 * @return FormModel instance if cached, null if not present
	 * @throws IllegalArgumentException if formUuid is null or empty
	 */
	FormModel getIfPresent(String formUuid);
	
	/**
	 * Manually add or update a form model in the cache.
	 * <p>
	 * This method is typically used to pre-populate the cache or update an entry after form
	 * modification.
	 * </p>
	 * 
	 * @param formUuid UUID of the form (must match formModel.formUuid)
	 * @param formModel FormModel instance to cache (must not be null)
	 * @throws IllegalArgumentException if either parameter is null or formUuid doesn't match model
	 */
	void put(String formUuid, FormModel formModel);
	
	/**
	 * Remove a specific form from the cache.
	 * <p>
	 * This method is called when a form is updated or deleted in OpenMRS to prevent serving stale
	 * cached data.
	 * </p>
	 * 
	 * @param formUuid UUID of the form to invalidate
	 */
	void invalidate(String formUuid);
	
	/**
	 * Clear all cached forms.
	 * <p>
	 * This method removes all entries from the cache. Used for administrative cache management,
	 * testing, or when cache corruption is suspected.
	 * </p>
	 */
	void invalidateAll();
	
	/**
	 * Get cache statistics including hit/miss counts and eviction metrics.
	 * 
	 * @return CacheStats object with current statistics, never null
	 */
	CacheStats getStats();
	
	/**
	 * Get cache hit rate as a ratio between 0.0 and 1.0.
	 * <p>
	 * Hit rate is calculated as: hits / (hits + misses)
	 * </p>
	 * <p>
	 * A rate of 0.70 (70%) means 70% of requests were served from cache.
	 * </p>
	 * 
	 * @return Hit rate between 0.0 (no hits) and 1.0 (all hits), or 0.0 if no requests yet
	 */
	double getHitRate();
}
