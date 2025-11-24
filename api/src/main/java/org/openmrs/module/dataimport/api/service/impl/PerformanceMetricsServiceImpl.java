/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.service.impl;

import org.openmrs.module.dataimport.api.service.PerformanceMetricsService;
import org.openmrs.module.dataimport.model.PerformanceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of PerformanceMetricsService for logging template generation metrics.
 * <p>
 * This service logs structured performance data at INFO level for monitoring and analysis. The log
 * format is designed to be easily parseable by monitoring tools and log aggregators.
 * </p>
 * <p>
 * <b>Log Format Example:</b>
 * </p>
 * <pre>
 * Template generation completed:
 *   Request ID: abc123-def456-789
 *   Forms: 5 (245 total fields)
 *   Duration: 2847ms
 *     - Form resolution: 423ms
 *     - Concept loading: 1204ms
 *     - Excel generation: 1220ms
 *   Cache hit rate: 0.85 (85%)
 *   Database queries: 42
 *   Peak memory: 128MB
 *   Status: SUCCESS
 * </pre>
 *
 * @since 1.0.0
 */
@Service("performanceMetricsService")
public class PerformanceMetricsServiceImpl implements PerformanceMetricsService {
	
	private static final Logger log = LoggerFactory.getLogger(PerformanceMetricsServiceImpl.class);
	
	@Override
	public void log(PerformanceMetrics metrics) {
		if (metrics == null) {
			throw new IllegalArgumentException("PerformanceMetrics cannot be null");
		}
		
		// Build structured log message
		StringBuilder logMessage = new StringBuilder();
		logMessage.append("\nTemplate generation completed:");
		logMessage.append("\n  Request ID: ").append(metrics.getOperationId());
		logMessage.append("\n  Forms: ").append(metrics.getTotalForms()).append(" (").append(metrics.getTotalFields())
		        .append(" total fields)");
		logMessage.append("\n  Duration: ").append(metrics.getDurationMillis()).append("ms");
		logMessage.append("\n    - Form resolution: ").append(metrics.getFormResolutionMillis()).append("ms");
		logMessage.append("\n    - Concept loading: ").append(metrics.getConceptLoadingMillis()).append("ms");
		logMessage.append("\n    - Excel generation: ").append(metrics.getExcelGenerationMillis()).append("ms");
		logMessage.append("\n  Cache hit rate: ").append(String.format("%.2f", metrics.getCacheHitRate())).append(" (")
		        .append(String.format("%.0f%%", metrics.getCacheHitRate() * 100)).append(")");
		logMessage.append("\n  Database queries: ").append(metrics.getDatabaseQueries());
		logMessage.append("\n  Peak memory: ").append(metrics.getMemoryUsedMB()).append("MB");
		logMessage.append("\n  Status: ").append(metrics.isSuccess() ? "SUCCESS" : "FAILURE");
		
		if (!metrics.isSuccess() && metrics.getErrorMessage() != null) {
			logMessage.append("\n  Error: ").append(metrics.getErrorMessage());
		}
		
		// Log at INFO level for operational visibility
		if (metrics.isSuccess()) {
			log.info(logMessage.toString());
		} else {
			// Log failures at WARN level for better alerting
			log.warn(logMessage.toString());
		}
		
		// Also log in compact format for easier parsing by monitoring tools
		log.info(
		    "PERF_METRIC: id={}, forms={}, fields={}, duration={}ms, cacheHitRate={}, queries={}, memory={}MB, success={}",
		    metrics.getOperationId(), metrics.getTotalForms(), metrics.getTotalFields(), metrics.getDurationMillis(),
		    metrics.getCacheHitRate(), metrics.getDatabaseQueries(), metrics.getMemoryUsedMB(), metrics.isSuccess());
	}
	
	@Override
	public PerformanceMetrics startMetrics() {
		PerformanceMetrics metrics = new PerformanceMetrics();
		log.debug("Started performance metrics tracking with ID: {}", metrics.getOperationId());
		return metrics;
	}
}
