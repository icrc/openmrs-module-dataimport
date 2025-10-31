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

import org.openmrs.module.dataimport.api.ImportPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage service for ImportPlan persistence. In production, this would use a proper database table
 * instead of in-memory storage.
 */
public class ImportPlanStorageService {
	
	private static final Logger log = LoggerFactory.getLogger(ImportPlanStorageService.class);
	
	// In-memory storage (in production, this would be a database table)
	private static final Map<UUID, ImportPlan> storage = new ConcurrentHashMap<>();
	
	/**
	 * Stores an ImportPlan for later retrieval.
	 */
	public void storePlan(ImportPlan plan) {
		if (plan == null) {
			throw new IllegalArgumentException("ImportPlan cannot be null");
		}
		
		storage.put(plan.getBatchId(), plan);
		log.info("Stored ImportPlan with batch ID: {} ({} rows OK, {} issues)", plan.getBatchId(), plan.getRowsOk().size(),
		    plan.getIssues().size());
	}
	
	/**
	 * Retrieves an ImportPlan by batch ID.
	 */
	public ImportPlan loadPlan(UUID batchId) {
		if (batchId == null) {
			throw new IllegalArgumentException("Batch ID cannot be null");
		}
		
		ImportPlan plan = storage.get(batchId);
		if (plan == null) {
			log.warn("ImportPlan not found for batch ID: {}", batchId);
			return null;
		}
		
		log.debug("Loaded ImportPlan with batch ID: {} ({} rows OK, {} issues)", batchId, plan.getRowsOk().size(),
		    plan.getIssues().size());
		return plan;
	}
	
	/**
	 * Removes an ImportPlan from storage.
	 */
	public void removePlan(UUID batchId) {
		if (batchId == null) {
			throw new IllegalArgumentException("Batch ID cannot be null");
		}
		
		ImportPlan removed = storage.remove(batchId);
		if (removed != null) {
			log.info("Removed ImportPlan with batch ID: {}", batchId);
		} else {
			log.warn("ImportPlan not found for removal: {}", batchId);
		}
	}
	
	/**
	 * Checks if an ImportPlan exists for the given batch ID.
	 */
	public boolean exists(UUID batchId) {
		return batchId != null && storage.containsKey(batchId);
	}
	
	/**
	 * Gets the number of stored ImportPlans.
	 */
	public int size() {
		return storage.size();
	}
	
	/**
	 * Clears all stored ImportPlans.
	 */
	public void clear() {
		int count = storage.size();
		storage.clear();
		log.info("Cleared {} ImportPlans from storage", count);
	}
	
	/**
	 * Gets all stored batch IDs.
	 */
	public java.util.Set<UUID> getAllBatchIds() {
		return java.util.Collections.unmodifiableSet(storage.keySet());
	}
	
	/**
	 * Cleanup old ImportPlans (older than specified hours). In production, this would be a scheduled
	 * job.
	 */
	public void cleanupOldPlans(int maxAgeHours) {
		long cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000L);
		int removedCount = 0;
		
		// In a real implementation, you'd have timestamps stored with each plan
		// For now, we'll just log that cleanup would happen
		log.info("Cleanup of ImportPlans older than {} hours would be performed here", maxAgeHours);
		
		// Example of what the cleanup logic would look like:
		/*
		Iterator<Map.Entry<UUID, ImportPlan>> iterator = storage.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, ImportPlan> entry = iterator.next();
			ImportPlan plan = entry.getValue();
			
			// Assuming we stored creation time with the plan
			if (plan.getCreatedTime() < cutoffTime) {
				iterator.remove();
				removedCount++;
				log.debug("Removed old ImportPlan: {}", entry.getKey());
			}
		}
		*/
		
		if (removedCount > 0) {
			log.info("Cleaned up {} old ImportPlans", removedCount);
		}
	}
}
