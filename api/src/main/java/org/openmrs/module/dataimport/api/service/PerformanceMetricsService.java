/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.service;

import org.openmrs.module.dataimport.model.PerformanceMetrics;

/**
 * Service for logging and tracking template generation performance metrics.
 * <p>
 * This service collects performance data during template generation to enable:
 * <ul>
 * <li>Performance monitoring and alerting</li>
 * <li>Optimization validation (comparing before/after metrics)</li>
 * <li>Regression detection (performance degradation over time)</li>
 * <li>Capacity planning (resource usage trends)</li>
 * </ul>
 * </p>
 * <p>
 * <b>Logging Format:</b> Metrics are logged at INFO level in structured format for easy parsing by
 * monitoring tools.
 * </p>
 * 
 * @since 1.0.0
 */
public interface PerformanceMetricsService {
	
	/**
	 * Log performance metrics for a template generation operation.
	 * <p>
	 * Metrics are logged at INFO level with the following format:
	 * </p>
	 * 
	 * <pre>
	 * Template generation completed:
	 *   Request ID: {operationId}
	 *   Forms: {totalForms} ({totalFields} total fields)
	 *   Duration: {duration}ms
	 *     - Form resolution: {formResolution}ms
	 *     - Concept loading: {conceptLoading}ms
	 *     - Excel generation: {excelGeneration}ms
	 *   Cache hit rate: {cacheHitRate} ({percentage}%)
	 *   Database queries: {queryCount}
	 *   Peak memory: {memory}MB
	 *   Status: {SUCCESS|FAILURE}
	 * </pre>
	 * 
	 * @param metrics PerformanceMetrics instance to log (must not be null)
	 * @throws IllegalArgumentException if metrics is null
	 */
	void log(PerformanceMetrics metrics);
	
	/**
	 * Create a new metrics builder with current timestamp.
	 * <p>
	 * Convenience method to start tracking metrics at the beginning of a template generation
	 * operation.
	 * </p>
	 * <p>
	 * Usage:
	 * </p>
	 * 
	 * <pre>
	 * PerformanceMetrics metrics = performanceMetricsService.startMetrics();
	 * metrics.setFormUuids(formUuids);
	 * // ... perform operations ...
	 * metrics.setEndTime(Instant.now());
	 * metrics.setSuccess(true);
	 * performanceMetricsService.log(metrics);
	 * </pre>
	 * 
	 * @return new PerformanceMetrics instance with startTime initialized
	 */
	PerformanceMetrics startMetrics();
}
