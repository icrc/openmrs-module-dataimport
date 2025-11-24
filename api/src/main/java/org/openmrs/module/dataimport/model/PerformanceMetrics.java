/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Captures timing and resource utilization data for template generation operations.
 * <p>
 * This class collects performance metrics during template generation to enable monitoring,
 * optimization analysis, and performance regression detection.
 * </p>
 * <p>
 * <b>Usage:</b> Create an instance at the start of generation, populate metrics during processing,
 * and log at completion.
 * </p>
 *
 * @since 1.0.0
 */
public class PerformanceMetrics {
	
	private String operationId;
	
	private List<String> formUuids;
	
	private int totalForms;
	
	private int totalFields;
	
	private Instant startTime;
	
	private Instant endTime;
	
	private long formResolutionMillis;
	
	private long conceptLoadingMillis;
	
	private long excelGenerationMillis;
	
	private double cacheHitRate;
	
	private int databaseQueries;
	
	private long memoryUsedMB;
	
	private boolean success;
	
	private String errorMessage;
	
	/**
	 * Constructs performance metrics with a unique operation ID.
	 */
	public PerformanceMetrics() {
		this.operationId = UUID.randomUUID().toString();
		this.formUuids = new ArrayList<>();
		this.startTime = Instant.now();
		this.success = false;
	}
	
	/**
	 * Gets the unique operation identifier.
	 *
	 * @return operation ID
	 */
	public String getOperationId() {
		return operationId;
	}
	
	/**
	 * Sets the operation ID (primarily for testing).
	 *
	 * @param operationId operation identifier
	 */
	public void setOperationId(String operationId) {
		this.operationId = operationId;
	}
	
	/**
	 * Gets the list of form UUIDs involved in this operation.
	 *
	 * @return form UUIDs list
	 */
	public List<String> getFormUuids() {
		return formUuids;
	}
	
	/**
	 * Sets the form UUIDs for this operation.
	 *
	 * @param formUuids list of form UUIDs
	 */
	public void setFormUuids(List<String> formUuids) {
		this.formUuids = formUuids != null ? formUuids : new ArrayList<>();
		this.totalForms = this.formUuids.size();
	}
	
	/**
	 * Gets the total number of forms processed.
	 *
	 * @return form count
	 */
	public int getTotalForms() {
		return totalForms;
	}
	
	/**
	 * Sets the total number of forms processed.
	 *
	 * @param totalForms form count
	 */
	public void setTotalForms(int totalForms) {
		this.totalForms = totalForms;
	}
	
	/**
	 * Gets the total number of fields across all forms.
	 *
	 * @return field count
	 */
	public int getTotalFields() {
		return totalFields;
	}
	
	/**
	 * Sets the total field count.
	 *
	 * @param totalFields field count
	 */
	public void setTotalFields(int totalFields) {
		this.totalFields = totalFields;
	}
	
	/**
	 * Gets the operation start timestamp.
	 *
	 * @return start time
	 */
	public Instant getStartTime() {
		return startTime;
	}
	
	/**
	 * Sets the start timestamp.
	 *
	 * @param startTime start time
	 */
	public void setStartTime(Instant startTime) {
		this.startTime = startTime;
	}
	
	/**
	 * Gets the operation end timestamp.
	 *
	 * @return end time
	 */
	public Instant getEndTime() {
		return endTime;
	}
	
	/**
	 * Sets the end timestamp and marks operation as complete.
	 *
	 * @param endTime end time
	 */
	public void setEndTime(Instant endTime) {
		this.endTime = endTime;
	}
	
	/**
	 * Gets the total duration in milliseconds.
	 *
	 * @return duration in ms, or 0 if not yet completed
	 */
	public long getDurationMillis() {
		if (startTime == null || endTime == null) {
			return 0;
		}
		return endTime.toEpochMilli() - startTime.toEpochMilli();
	}
	
	/**
	 * Gets time spent resolving form schemas (ms).
	 *
	 * @return form resolution time
	 */
	public long getFormResolutionMillis() {
		return formResolutionMillis;
	}
	
	/**
	 * Sets form resolution time.
	 *
	 * @param formResolutionMillis time in milliseconds
	 */
	public void setFormResolutionMillis(long formResolutionMillis) {
		this.formResolutionMillis = formResolutionMillis;
	}
	
	/**
	 * Adds time to form resolution duration.
	 *
	 * @param millis milliseconds to add
	 */
	public void addFormResolutionTime(long millis) {
		this.formResolutionMillis += millis;
	}
	
	/**
	 * Gets time spent loading concept answers (ms).
	 *
	 * @return concept loading time
	 */
	public long getConceptLoadingMillis() {
		return conceptLoadingMillis;
	}
	
	/**
	 * Sets concept loading time.
	 *
	 * @param conceptLoadingMillis time in milliseconds
	 */
	public void setConceptLoadingMillis(long conceptLoadingMillis) {
		this.conceptLoadingMillis = conceptLoadingMillis;
	}
	
	/**
	 * Adds time to concept loading duration.
	 *
	 * @param millis milliseconds to add
	 */
	public void addConceptLoadingTime(long millis) {
		this.conceptLoadingMillis += millis;
	}
	
	/**
	 * Gets time spent generating Excel workbook (ms).
	 *
	 * @return Excel generation time
	 */
	public long getExcelGenerationMillis() {
		return excelGenerationMillis;
	}
	
	/**
	 * Sets Excel generation time.
	 *
	 * @param excelGenerationMillis time in milliseconds
	 */
	public void setExcelGenerationMillis(long excelGenerationMillis) {
		this.excelGenerationMillis = excelGenerationMillis;
	}
	
	/**
	 * Gets cache hit rate (0.0 to 1.0).
	 *
	 * @return cache hit rate
	 */
	public double getCacheHitRate() {
		return cacheHitRate;
	}
	
	/**
	 * Sets cache hit rate.
	 *
	 * @param cacheHitRate hit rate between 0.0 and 1.0
	 */
	public void setCacheHitRate(double cacheHitRate) {
		this.cacheHitRate = cacheHitRate;
	}
	
	/**
	 * Gets the number of database queries executed.
	 *
	 * @return query count
	 */
	public int getDatabaseQueries() {
		return databaseQueries;
	}
	
	/**
	 * Sets database query count.
	 *
	 * @param databaseQueries query count
	 */
	public void setDatabaseQueries(int databaseQueries) {
		this.databaseQueries = databaseQueries;
	}
	
	/**
	 * Gets peak memory usage in megabytes.
	 *
	 * @return memory usage in MB
	 */
	public long getMemoryUsedMB() {
		return memoryUsedMB;
	}
	
	/**
	 * Sets memory usage.
	 *
	 * @param memoryUsedMB memory in megabytes
	 */
	public void setMemoryUsedMB(long memoryUsedMB) {
		this.memoryUsedMB = memoryUsedMB;
	}
	
	/**
	 * Checks if operation completed successfully.
	 *
	 * @return true if successful
	 */
	public boolean isSuccess() {
		return success;
	}
	
	/**
	 * Sets operation success status.
	 *
	 * @param success true if successful
	 */
	public void setSuccess(boolean success) {
		this.success = success;
	}
	
	/**
	 * Gets error message if operation failed.
	 *
	 * @return error message or null if successful
	 */
	public String getErrorMessage() {
		return errorMessage;
	}
	
	/**
	 * Sets error message for failed operations.
	 *
	 * @param errorMessage error description
	 */
	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
	
	/**
	 * Returns a formatted summary of performance metrics.
	 *
	 * @return formatted metrics string
	 */
	@Override
	public String toString() {
		return String.format(
		    "PerformanceMetrics[id=%s, forms=%d (%d fields), " + "duration=%dms (resolve=%dms, concepts=%dms, excel=%dms), "
		            + "cacheHitRate=%.2f, queries=%d, memory=%dMB, success=%s]",
		    operationId, totalForms, totalFields, getDurationMillis(), formResolutionMillis, conceptLoadingMillis,
		    excelGenerationMillis, cacheHitRate, databaseQueries, memoryUsedMB, success);
	}
}
