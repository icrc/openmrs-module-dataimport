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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-local utility for tracking database query counts during template generation.
 * <p>
 * This is a lightweight counter that estimates database queries by tracking OpenMRS service calls.
 * While not 100% accurate (doesn't track Hibernate internals), it provides a good approximation
 * for performance monitoring.
 * </p>
 * <p>
 * <b>Usage:</b>
 * </p>
 *
 * <pre>
 * QueryCounter.start();
 * try {
 * 	// ... perform operations that query database ...
 * 	int count = QueryCounter.getCount();
 * } finally {
 * 	QueryCounter.clear();
 * }
 * </pre>
 * <p>
 * <b>Thread Safety:</b> Uses ThreadLocal, so each thread has its own counter.
 * </p>
 *
 * @since 1.0.0
 */
public class QueryCounter {

	private static final Logger log = LoggerFactory.getLogger(QueryCounter.class);

	private static final ThreadLocal<Integer> queryCount = new ThreadLocal<>();

	private static final ThreadLocal<Boolean> enabled = new ThreadLocal<>();

	/**
	 * Start query counting for the current thread.
	 */
	public static void start() {
		queryCount.set(0);
		enabled.set(true);
		log.debug("Query counter started for thread {}", Thread.currentThread().getName());
	}

	/**
	 * Increment the query counter.
	 */
	public static void increment() {
		if (Boolean.TRUE.equals(enabled.get())) {
			Integer current = queryCount.get();
			queryCount.set(current != null ? current + 1 : 1);
		}
	}

	/**
	 * Increment the query counter by a specific amount.
	 *
	 * @param count number to add to counter
	 */
	public static void increment(int count) {
		if (Boolean.TRUE.equals(enabled.get())) {
			Integer current = queryCount.get();
			queryCount.set(current != null ? current + count : count);
		}
	}

	/**
	 * Get the current query count for this thread.
	 *
	 * @return query count, or 0 if not started
	 */
	public static int getCount() {
		Integer current = queryCount.get();
		return current != null ? current : 0;
	}

	/**
	 * Clear the query counter for the current thread. Should be called in finally block.
	 */
	public static void clear() {
		int finalCount = getCount();
		queryCount.remove();
		enabled.remove();
		log.debug("Query counter cleared for thread {} (final count: {})", Thread.currentThread().getName(), finalCount);
	}

	/**
	 * Check if query counting is enabled for the current thread.
	 *
	 * @return true if enabled
	 */
	public static boolean isEnabled() {
		return Boolean.TRUE.equals(enabled.get());
	}
}
