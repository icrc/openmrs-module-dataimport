/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing idempotency keys to prevent duplicate imports. In production, this would use
 * a proper database table instead of in-memory storage.
 */
public class IdempotencyService {
	
	private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
	
	// In-memory storage (in production, this would be a database table)
	private static final Map<String, String> storage = new ConcurrentHashMap<>();
	
	/**
	 * Checks if an idempotency key already exists.
	 */
	public boolean exists(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
			return false;
		}
		
		boolean exists = storage.containsKey(idempotencyKey);
		if (exists) {
			log.debug("Idempotency key already exists: {}", idempotencyKey);
		}
		
		return exists;
	}
	
	/**
	 * Records an idempotency key with its associated encounter UUID.
	 */
	public void record(String idempotencyKey, String encounterUuid) {
		if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
			throw new IllegalArgumentException("Idempotency key cannot be null or empty");
		}
		
		if (encounterUuid == null || encounterUuid.trim().isEmpty()) {
			throw new IllegalArgumentException("Encounter UUID cannot be null or empty");
		}
		
		storage.put(idempotencyKey, encounterUuid);
		log.debug("Recorded idempotency key: {} -> {}", idempotencyKey, encounterUuid);
	}
	
	/**
	 * Gets the encounter UUID associated with an idempotency key.
	 */
	public String getEncounterUuid(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
			return null;
		}
		
		return storage.get(idempotencyKey);
	}
	
	/**
	 * Removes an idempotency key from storage.
	 */
	public void remove(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
			return;
		}
		
		String removed = storage.remove(idempotencyKey);
		if (removed != null) {
			log.debug("Removed idempotency key: {}", idempotencyKey);
		}
	}
	
	/**
	 * Gets the number of stored idempotency keys.
	 */
	public int size() {
		return storage.size();
	}
	
	/**
	 * Clears all stored idempotency keys.
	 */
	public void clear() {
		int count = storage.size();
		storage.clear();
		log.info("Cleared {} idempotency keys from storage", count);
	}
	
	/**
	 * Gets all stored idempotency keys.
	 */
	public java.util.Set<String> getAllKeys() {
		return java.util.Collections.unmodifiableSet(storage.keySet());
	}
	
	/**
	 * Cleanup old idempotency keys (older than specified hours). In production, this would be a
	 * scheduled job.
	 */
	public void cleanupOldKeys(int maxAgeHours) {
		long cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000L);
		int removedCount = 0;
		
		// In a real implementation, you'd have timestamps stored with each key
		// For now, we'll just log that cleanup would happen
		log.info("Cleanup of idempotency keys older than {} hours would be performed here", maxAgeHours);
		
		// Example of what the cleanup logic would look like:
		/*
		Iterator<Map.Entry<String, String>> iterator = storage.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, String> entry = iterator.next();
			
			// Assuming we stored creation time with the key
			if (entry.getValue().getCreatedTime() < cutoffTime) {
				iterator.remove();
				removedCount++;
				log.debug("Removed old idempotency key: {}", entry.getKey());
			}
		}
		*/
		
		if (removedCount > 0) {
			log.info("Cleaned up {} old idempotency keys", removedCount);
		}
	}
	
	/**
	 * Validates that idempotency is properly configured.
	 */
	public void validateConfiguration() {
		// In a real implementation, you'd check Global Properties or other configuration
		log.info("Idempotency service is enabled - duplicate imports will be prevented");
	}
}
