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

import org.openmrs.module.dataimport.api.model.ImportAuditLog;
import org.openmrs.module.dataimport.model.ImportPlan;
import org.openmrs.module.dataimport.model.ImportResult;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing import audit logs. Provides methods to log import operations and query audit
 * history.
 */
public interface ImportAuditService {
	
	/**
	 * Logs a dry-run validation operation.
	 *
	 * @param batchId the batch ID for this import
	 * @param filename the uploaded filename
	 * @param plan the import plan from validation
	 * @param durationMs time taken in milliseconds
	 * @return the created audit log entry
	 */
	ImportAuditLog logDryRun(UUID batchId, String filename, ImportPlan plan, long durationMs);
	
	/**
	 * Logs a commit operation.
	 *
	 * @param batchId the batch ID for this import
	 * @param filename the uploaded filename
	 * @param result the import result
	 * @param durationMs time taken in milliseconds
	 * @return the created audit log entry
	 */
	ImportAuditLog logCommit(UUID batchId, String filename, ImportResult result, long durationMs);
	
	/**
	 * Logs a failed operation (either dry-run or commit).
	 *
	 * @param batchId the batch ID for this import
	 * @param filename the uploaded filename
	 * @param action the action that failed (DRY_RUN or COMMIT)
	 * @param errorMessage the error message
	 * @param durationMs time taken in milliseconds
	 * @return the created audit log entry
	 */
	ImportAuditLog logFailure(UUID batchId, String filename, ImportAuditLog.Action action, String errorMessage,
	        long durationMs);
	
	/**
	 * Gets an audit log by its UUID.
	 *
	 * @param uuid the UUID of the audit log
	 * @return the audit log, or null if not found
	 */
	ImportAuditLog getByUuid(String uuid);
	
	/**
	 * Gets an audit log by batch ID.
	 *
	 * @param batchId the batch ID
	 * @return the most recent audit log for this batch, or null if not found
	 */
	ImportAuditLog getByBatchId(String batchId);
	
	/**
	 * Gets all audit logs for a specific batch (dry-run + commit).
	 *
	 * @param batchId the batch ID
	 * @return list of audit logs for this batch
	 */
	List<ImportAuditLog> getAllByBatchId(String batchId);
	
	/**
	 * Gets audit logs with pagination and optional filters.
	 *
	 * @param status optional status filter
	 * @param action optional action filter
	 * @param creatorId optional creator user ID filter
	 * @param startDate optional start date filter (inclusive)
	 * @param endDate optional end date filter (inclusive)
	 * @param page page number (0-based)
	 * @param pageSize number of results per page
	 * @return list of audit logs matching criteria
	 */
	List<ImportAuditLog> getAuditLogs(ImportAuditLog.Status status, ImportAuditLog.Action action, Integer creatorId,
	        Date startDate, Date endDate, int page, int pageSize);
	
	/**
	 * Counts audit logs matching the given filters.
	 *
	 * @param status optional status filter
	 * @param action optional action filter
	 * @param creatorId optional creator user ID filter
	 * @param startDate optional start date filter
	 * @param endDate optional end date filter
	 * @return count of matching audit logs
	 */
	long countAuditLogs(ImportAuditLog.Status status, ImportAuditLog.Action action, Integer creatorId, Date startDate,
	        Date endDate);
	
	/**
	 * Gets the most recent audit logs.
	 *
	 * @param maxResults maximum number of results
	 * @return list of recent audit logs
	 */
	List<ImportAuditLog> getRecentAuditLogs(int maxResults);
	
	/**
	 * Deletes audit logs older than the specified number of days. This is the cleanup/retention policy
	 * enforcement method.
	 *
	 * @param retentionDays number of days to retain logs
	 * @return number of deleted records
	 */
	int cleanupOldLogs(int retentionDays);
	
	/**
	 * Gets the configured retention period in days. Default is 90 days if not configured.
	 *
	 * @return retention period in days
	 */
	int getRetentionDays();
}
