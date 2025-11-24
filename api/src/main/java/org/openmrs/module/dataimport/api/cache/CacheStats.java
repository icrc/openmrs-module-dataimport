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

/**
 * Immutable statistics for cache performance.
 * <p>
 * This class provides metrics for monitoring cache effectiveness including hit/miss ratios,
 * eviction counts, and load statistics.
 * </p>
 * <p>
 * <b>Thread Safety:</b> Immutable - all fields are final and cannot be modified after construction.
 * </p>
 *
 * @since 1.0.0
 */
public class CacheStats {
	
	private final long hitCount;
	
	private final long missCount;
	
	private final long loadSuccessCount;
	
	private final long evictionCount;
	
	/**
	 * Constructs cache statistics.
	 *
	 * @param hitCount number of cache hits (requests served from cache)
	 * @param missCount number of cache misses (requests that required loading)
	 * @param loadSuccessCount number of successful cache loads from database
	 * @param evictionCount number of entries evicted (LRU or TTL)
	 */
	public CacheStats(long hitCount, long missCount, long loadSuccessCount, long evictionCount) {
		this.hitCount = hitCount;
		this.missCount = missCount;
		this.loadSuccessCount = loadSuccessCount;
		this.evictionCount = evictionCount;
	}
	
	/**
	 * Gets the number of cache hits. A hit occurs when a requested entry is found in cache.
	 *
	 * @return number of cache hits
	 */
	public long getHitCount() {
		return hitCount;
	}
	
	/**
	 * Gets the number of cache misses. A miss occurs when a requested entry is not in cache and must be
	 * loaded.
	 *
	 * @return number of cache misses
	 */
	public long getMissCount() {
		return missCount;
	}
	
	/**
	 * Gets the number of successful cache loads from database.
	 *
	 * @return number of successful loads
	 */
	public long getLoadSuccessCount() {
		return loadSuccessCount;
	}
	
	/**
	 * Gets the number of cache entries evicted. Evictions occur due to LRU policy or TTL expiration.
	 *
	 * @return number of evicted entries
	 */
	public long getEvictionCount() {
		return evictionCount;
	}
	
	/**
	 * Gets the total number of cache requests (hits + misses).
	 *
	 * @return total request count
	 */
	public long requestCount() {
		return hitCount + missCount;
	}
	
	/**
	 * Gets the cache hit rate as a ratio between 0.0 and 1.0. Hit rate = hits / (hits + misses)
	 *
	 * @return hit rate (0.0 = no hits, 1.0 = all hits), or 0.0 if no requests yet
	 */
	public double hitRate() {
		long requests = requestCount();
		return requests == 0 ? 0.0 : (double) hitCount / requests;
	}
	
	/**
	 * Gets the cache miss rate as a ratio between 0.0 and 1.0. Miss rate = misses / (hits + misses) =
	 * 1.0 - hit rate
	 *
	 * @return miss rate (0.0 = no misses, 1.0 = all misses)
	 */
	public double missRate() {
		return 1.0 - hitRate();
	}
	
	/**
	 * Returns a human-readable summary of cache statistics.
	 *
	 * @return formatted string with all statistics
	 */
	@Override
	public String toString() {
		return String.format("CacheStats[requests=%d, hits=%d (%.1f%%), misses=%d (%.1f%%), " + "loads=%d, evictions=%d]",
		    requestCount(), hitCount, hitRate() * 100, missCount, missRate() * 100, loadSuccessCount, evictionCount);
	}
	
	/**
	 * Checks equality based on all statistics.
	 *
	 * @param obj object to compare
	 * @return true if all statistics match
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		CacheStats other = (CacheStats) obj;
		return hitCount == other.hitCount && missCount == other.missCount && loadSuccessCount == other.loadSuccessCount
		        && evictionCount == other.evictionCount;
	}
	
	/**
	 * Generates hash code based on all statistics.
	 *
	 * @return hash code
	 */
	@Override
	public int hashCode() {
		int result = (int) (hitCount ^ (hitCount >>> 32));
		result = 31 * result + (int) (missCount ^ (missCount >>> 32));
		result = 31 * result + (int) (loadSuccessCount ^ (loadSuccessCount >>> 32));
		result = 31 * result + (int) (evictionCount ^ (evictionCount >>> 32));
		return result;
	}
}
