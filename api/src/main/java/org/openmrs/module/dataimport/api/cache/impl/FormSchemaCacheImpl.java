/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.cache.impl;

import org.openmrs.api.APIException;
import org.openmrs.module.dataimport.api.cache.FormSchemaCache;
import org.openmrs.module.dataimport.api.resolver.FormResolver;
import org.openmrs.module.dataimport.api.resolver.FormResolverRegistry;
import org.openmrs.module.dataimport.api.util.QueryCounter;
import org.openmrs.module.dataimport.model.FormModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Guava-based implementation of FormSchemaCache. Thread-safe with LRU eviction and TTL expiration.
 * <p>
 * This implementation uses Guava's LoadingCache which provides:
 * <ul>
 * <li>Automatic loading on cache miss</li>
 * <li>Thread-safe concurrent access</li>
 * <li>LRU eviction when max size reached</li>
 * <li>TTL-based expiration</li>
 * <li>Statistics tracking</li>
 * </ul>
 * </p>
 *
 * @since 1.0.0
 */
@Component("formSchemaCache")
public class FormSchemaCacheImpl implements FormSchemaCache {
	
	private static final Logger log = LoggerFactory.getLogger(FormSchemaCacheImpl.class);
	
	private final LoadingCache<String, FormModel> cache;
	
	private final FormResolverRegistry resolverRegistry;
	
	/**
	 * Constructs cache with specified configuration.
	 *
	 * @param resolverRegistry form resolver registry for loading forms
	 * @param maxSize maximum number of entries (default 100)
	 * @param ttlHours time-to-live in hours (default 1)
	 */
	@Autowired
	public FormSchemaCacheImpl(FormResolverRegistry resolverRegistry,
	    @Value("${dataimport.cache.form.maxSize:100}") int maxSize,
	    @Value("${dataimport.cache.form.ttlHours:1}") int ttlHours) {
		this.resolverRegistry = resolverRegistry;
		
		log.info("Initializing FormSchemaCache with maxSize={}, ttlHours={}", maxSize, ttlHours);
		
		this.cache = CacheBuilder.newBuilder().maximumSize(maxSize).expireAfterWrite(ttlHours, TimeUnit.HOURS).recordStats() // Enable statistics tracking
		        .build(new CacheLoader<String, FormModel>() {
			        
			        @Override
			        public FormModel load(String formUuid) throws Exception {
				        return loadFormFromDatabase(formUuid);
			        }
		        });
	}
	
	/**
	 * Loads form from database using FormResolverRegistry. This method is called automatically on cache
	 * miss.
	 *
	 * @param formUuid form UUID to load
	 * @return resolved FormModel
	 * @throws APIException if form cannot be loaded
	 */
	private FormModel loadFormFromDatabase(String formUuid) {
		log.debug("Loading form schema from database for UUID: {}", formUuid);

		// Track database queries (form resolution typically involves 1-3 queries)
		QueryCounter.increment();

		try {
			FormResolver resolver = resolverRegistry.findResolver(formUuid);
			if (resolver == null) {
				throw new APIException("No compatible form resolver found for form " + formUuid);
			}

			FormModel form = resolver.resolve(formUuid, null);
			log.debug("Successfully loaded form schema for UUID: {}", formUuid);
			return form;
		}
		catch (Exception e) {
			log.error("Failed to load form schema for UUID: {}", formUuid, e);
			throw new APIException("Failed to load form schema for " + formUuid, e);
		}
	}
	
	@Override
	public FormModel get(String formUuid) {
		validateUuid(formUuid);
		
		try {
			FormModel form = cache.get(formUuid);
			log.debug("Cache get for {}: {}", formUuid, form != null ? "HIT" : "MISS");
			return form;
		}
		catch (ExecutionException e) {
			log.error("Cache load failed for form {}, falling back to direct load", formUuid, e);
			// Fallback to direct loading on cache failure
			return loadFormFromDatabase(formUuid);
		}
	}
	
	@Override
	public FormModel getIfPresent(String formUuid) {
		validateUuid(formUuid);
		
		FormModel form = cache.getIfPresent(formUuid);
		log.debug("Cache getIfPresent for {}: {}", formUuid, form != null ? "HIT" : "MISS");
		return form;
	}
	
	@Override
	public void put(String formUuid, FormModel formModel) {
		if (formUuid == null || formUuid.trim().isEmpty()) {
			throw new IllegalArgumentException("Form UUID cannot be null or empty");
		}
		if (formModel == null) {
			throw new IllegalArgumentException("FormModel cannot be null");
		}
		if (formModel.formUuid != null && !formUuid.equals(formModel.formUuid)) {
			throw new IllegalArgumentException(
			        "Form UUID mismatch: key=" + formUuid + ", model.formUuid=" + formModel.formUuid);
		}
		
		cache.put(formUuid, formModel);
		log.debug("Cached form schema for UUID: {}", formUuid);
	}
	
	@Override
	public void invalidate(String formUuid) {
		if (formUuid != null && !formUuid.trim().isEmpty()) {
			cache.invalidate(formUuid);
			log.info("Invalidated form schema cache for UUID: {}", formUuid);
		}
	}
	
	@Override
	public void invalidateAll() {
		long sizeBefore = cache.size();
		cache.invalidateAll();
		log.info("Cleared entire form schema cache ({} entries removed)", sizeBefore);
	}
	
	@Override
	public org.openmrs.module.dataimport.api.cache.CacheStats getStats() {
		CacheStats guavaStats = cache.stats();
		
		return new org.openmrs.module.dataimport.api.cache.CacheStats(guavaStats.hitCount(), guavaStats.missCount(),
		        guavaStats.loadSuccessCount(), guavaStats.evictionCount());
	}
	
	@Override
	public double getHitRate() {
		CacheStats stats = cache.stats();
		long requests = stats.hitCount() + stats.missCount();
		return requests == 0 ? 0.0 : (double) stats.hitCount() / requests;
	}
	
	/**
	 * Validates UUID format and non-null requirement.
	 *
	 * @param uuid UUID to validate
	 * @throws IllegalArgumentException if invalid
	 */
	private void validateUuid(String uuid) {
		if (uuid == null) {
			throw new IllegalArgumentException("Form UUID cannot be null");
		}
		if (uuid.trim().isEmpty()) {
			throw new IllegalArgumentException("Form UUID cannot be empty");
		}
		// Note: We don't validate UUID format strictly here to allow flexibility
		// The actual UUID validation happens in FormResolverRegistry
	}
}
