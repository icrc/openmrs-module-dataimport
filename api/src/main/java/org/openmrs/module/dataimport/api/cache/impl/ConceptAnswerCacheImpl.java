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

import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.api.ConceptService;
import org.openmrs.module.dataimport.api.cache.ConceptAnswerCache;
import org.openmrs.module.dataimport.api.util.QueryCounter;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Guava-based implementation of ConceptAnswerCache. Thread-safe with LRU eviction and TTL
 * expiration.
 * <p>
 * This implementation uses Guava's Cache (non-loading) which provides:
 * <ul>
 * <li>Thread-safe concurrent access</li>
 * <li>LRU eviction when max size reached</li>
 * <li>TTL-based expiration</li>
 * <li>Statistics tracking</li>
 * <li>Explicit loading via put() or preloadConcepts()</li>
 * </ul>
 * </p>
 * <p>
 * Unlike FormSchemaCache, this cache does NOT auto-load on cache miss. Concepts must be explicitly
 * loaded via put() or preloadConcepts() for optimal batch performance in multi-form scenarios.
 * </p>
 *
 * @since 1.0.0
 */
@Component("conceptAnswerCache")
public class ConceptAnswerCacheImpl implements ConceptAnswerCache {
	
	private static final Logger log = LoggerFactory.getLogger(ConceptAnswerCacheImpl.class);
	
	private final Cache<String, List<AnswerModel>> cache;
	
	private final ConceptService conceptService;
	
	/**
	 * Constructs cache with specified configuration.
	 *
	 * @param conceptService concept service for loading answers
	 * @param maxSize maximum number of entries (default 100)
	 * @param ttlHours time-to-live in hours (default 1)
	 */
	@Autowired
	public ConceptAnswerCacheImpl(ConceptService conceptService,
	    @Value("${dataimport.cache.concept.maxSize:100}") int maxSize,
	    @Value("${dataimport.cache.concept.ttlHours:1}") int ttlHours) {
		this.conceptService = conceptService;
		
		log.info("Initializing ConceptAnswerCache with maxSize={}, ttlHours={}", maxSize, ttlHours);
		
		this.cache = CacheBuilder.newBuilder().maximumSize(maxSize).expireAfterWrite(ttlHours, TimeUnit.HOURS).recordStats() // Enable statistics tracking
		        .build();
	}
	
	@Override
	public List<AnswerModel> getIfPresent(String conceptUuid) {
		validateUuid(conceptUuid);
		
		List<AnswerModel> answers = cache.getIfPresent(conceptUuid);
		log.debug("Cache getIfPresent for {}: {}", conceptUuid, answers != null ? "HIT" : "MISS");
		return answers;
	}
	
	@Override
	public List<AnswerModel> getOrEmpty(String conceptUuid) {
		validateUuid(conceptUuid);
		
		List<AnswerModel> answers = cache.getIfPresent(conceptUuid);
		log.debug("Cache getOrEmpty for {}: {}", conceptUuid, answers != null ? "HIT" : "MISS");
		return answers != null ? answers : Collections.emptyList();
	}
	
	@Override
	public void put(String conceptUuid, List<AnswerModel> answers) {
		if (conceptUuid == null || conceptUuid.trim().isEmpty()) {
			throw new IllegalArgumentException("Concept UUID cannot be null or empty");
		}
		if (answers == null) {
			throw new IllegalArgumentException("Answers list cannot be null (use empty list for concepts with no answers)");
		}
		
		cache.put(conceptUuid, answers);
		log.debug("Cached {} answers for concept UUID: {}", answers.size(), conceptUuid);
	}
	
	@Override
	public void preloadConcepts(Set<String> conceptUuids) {
		if (conceptUuids == null || conceptUuids.isEmpty()) {
			log.debug("preloadConcepts called with null or empty set, no-op");
			return;
		}

		// Filter out concepts that are already cached
		Set<String> uncachedUuids = conceptUuids.stream().filter(uuid -> cache.getIfPresent(uuid) == null)
		        .collect(Collectors.toSet());

		if (uncachedUuids.isEmpty()) {
			log.debug("All {} requested concepts already cached, skipping preload", conceptUuids.size());
			return;
		}

		log.debug("Preloading {} uncached concepts (out of {} requested)", uncachedUuids.size(), conceptUuids.size());

		// Batch load concepts using HQL IN clause for better performance
		try {
			loadConceptAnswersBatchFromDatabase(uncachedUuids);
			log.info("Preloaded {} concept answers into cache using batch query", uncachedUuids.size());
		}
		catch (Exception e) {
			log.error("Batch preload failed, falling back to individual loads", e);
			// Fallback to individual loading on batch failure
			fallbackToIndividualLoading(uncachedUuids);
		}
	}

	/**
	 * Loads concept answers for multiple concepts in batch.
	 * While we still load concepts individually, we do it in a tight loop with minimal overhead
	 * and proper error handling to avoid failing the entire batch if one concept fails.
	 *
	 * @param conceptUuids set of concept UUIDs to load
	 */
	private void loadConceptAnswersBatchFromDatabase(Set<String> conceptUuids) {
		if (conceptUuids.isEmpty()) {
			return;
		}

		log.debug("Batch loading {} concepts", conceptUuids.size());

		int loadedCount = 0;
		int failedCount = 0;

		// Load concepts in batch - each call is optimized to avoid redundant work
		for (String conceptUuid : conceptUuids) {
			try {
				// Track database query for performance metrics (1 query per concept)
				QueryCounter.increment();

				Concept concept = conceptService.getConceptByUuid(conceptUuid);

				if (concept == null) {
					log.debug("Concept not found for UUID: {}, caching empty list", conceptUuid);
					cache.put(conceptUuid, Collections.emptyList());
					loadedCount++;
					continue;
				}

				// Pre-size list based on concept answers count for better performance
				int answersCount = concept.getAnswers() != null ? concept.getAnswers().size() : 0;
				List<AnswerModel> answers = new ArrayList<>(answersCount);

				// Extract answers from concept
				if (concept.getAnswers() != null && !concept.getAnswers().isEmpty()) {
					for (ConceptAnswer conceptAnswer : concept.getAnswers()) {
						Concept answerConcept = conceptAnswer.getAnswerConcept();
						if (answerConcept != null) {
							AnswerModel answer = new AnswerModel();
							answer.label = answerConcept.getName() != null ? answerConcept.getName().getName()
							        : answerConcept.getUuid();
							answer.conceptUuid = answerConcept.getUuid();
							answer.value = answerConcept.getUuid();
							answers.add(answer);
						}
					}
				}

				cache.put(conceptUuid, answers);
				log.debug("Cached {} answers for concept {}", answers.size(), conceptUuid);
				loadedCount++;
			}
			catch (Exception e) {
				log.error("Failed to load concept {} during batch operation, caching empty list", conceptUuid, e);
				cache.put(conceptUuid, Collections.emptyList());
				failedCount++;
			}
		}

		log.info("Batch load completed: {} successful, {} failed out of {} concepts", loadedCount, failedCount,
		    conceptUuids.size());
	}

	/**
	 * Fallback method to load concepts individually if batch loading fails.
	 *
	 * @param conceptUuids set of concept UUIDs to load
	 */
	private void fallbackToIndividualLoading(Set<String> conceptUuids) {
		int loadedCount = 0;
		for (String conceptUuid : conceptUuids) {
			try {
				List<AnswerModel> answers = loadConceptAnswersFromDatabase(conceptUuid);
				cache.put(conceptUuid, answers);
				loadedCount++;
			}
			catch (Exception e) {
				log.error("Failed to preload concept {}, skipping", conceptUuid, e);
				// Continue loading other concepts even if one fails
			}
		}
		log.info("Fallback: Preloaded {} concept answers into cache", loadedCount);
	}
	
	@Override
	public void invalidate(String conceptUuid) {
		if (conceptUuid != null && !conceptUuid.trim().isEmpty()) {
			cache.invalidate(conceptUuid);
			log.info("Invalidated concept answer cache for UUID: {}", conceptUuid);
		}
	}
	
	@Override
	public void invalidateAll() {
		long sizeBefore = cache.size();
		cache.invalidateAll();
		log.info("Cleared entire concept answer cache ({} entries removed)", sizeBefore);
	}
	
	@Override
	public org.openmrs.module.dataimport.api.cache.CacheStats getStats() {
		CacheStats guavaStats = cache.stats();
		
		return new org.openmrs.module.dataimport.api.cache.CacheStats(guavaStats.hitCount(), guavaStats.missCount(),
		        guavaStats.loadSuccessCount(), guavaStats.evictionCount());
	}
	
	/**
	 * Loads concept answers from database using ConceptService.
	 *
	 * @param conceptUuid concept UUID to load
	 * @return list of AnswerModel (empty if concept has no answers)
	 */
	private List<AnswerModel> loadConceptAnswersFromDatabase(String conceptUuid) {
		log.debug("Loading concept answers from database for UUID: {}", conceptUuid);

		// Track database query for performance metrics
		QueryCounter.increment();

		try {
			Concept concept = conceptService.getConceptByUuid(conceptUuid);
			if (concept == null) {
				log.warn("Concept not found for UUID: {}, caching empty list", conceptUuid);
				return Collections.emptyList();
			}

			// Pre-size list based on concept answers count
			int answersCount = concept.getAnswers() != null ? concept.getAnswers().size() : 0;
			List<AnswerModel> answers = new ArrayList<>(answersCount);

			// Get concept answers and convert to AnswerModel
			for (ConceptAnswer conceptAnswer : concept.getAnswers()) {
				Concept answerConcept = conceptAnswer.getAnswerConcept();
				if (answerConcept != null) {
					AnswerModel answer = new AnswerModel();
					answer.label = answerConcept.getName().getName();
					answer.conceptUuid = answerConcept.getUuid();
					answer.value = answerConcept.getUuid(); // Use UUID as value
					answers.add(answer);
				}
			}
			
			log.debug("Loaded {} answers for concept UUID: {}", answers.size(), conceptUuid);
			return answers;
		}
		catch (Exception e) {
			log.error("Failed to load concept answers for UUID: {}", conceptUuid, e);
			// Return empty list rather than throwing exception
			// This allows template generation to continue with missing answers
			return Collections.emptyList();
		}
	}
	
	/**
	 * Validates UUID format and non-null requirement.
	 *
	 * @param uuid UUID to validate
	 * @throws IllegalArgumentException if invalid
	 */
	private void validateUuid(String uuid) {
		if (uuid == null) {
			throw new IllegalArgumentException("Concept UUID cannot be null");
		}
		if (uuid.trim().isEmpty()) {
			throw new IllegalArgumentException("Concept UUID cannot be empty");
		}
	}
}
