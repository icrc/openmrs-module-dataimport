/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.dao;

import org.openmrs.module.dataimport.api.model.ImportAuditLog;

import java.util.Date;
import java.util.List;

/**
 * Data Access Object for ImportAuditLog entities.
 */
public interface ImportAuditLogDao {
	
	/**
	 * Saves or updates an audit log entry.
	 *
	 * @param auditLog the audit log to save
	 * @return the saved audit log
	 */
	ImportAuditLog saveOrUpdate(ImportAuditLog auditLog);
	
	/**
	 * Gets an audit log by its internal ID.
	 *
	 * @param id the internal ID
	 * @return the audit log, or null if not found
	 */
	ImportAuditLog getById(Integer id);
	
	/**
	 * Gets an audit log by its UUID.
	 *
	 * @param uuid the UUID
	 * @return the audit log, or null if not found
	 */
	ImportAuditLog getByUuid(String uuid);
	
	/**
	 * Gets an audit log by batch ID.
	 *
	 * @param batchId the batch ID
	 * @return the audit log, or null if not found
	 */
	ImportAuditLog getByBatchId(String batchId);
	
	/**
	 * Gets all audit logs for a specific batch ID (may have multiple entries for dry-run and commit).
	 *
	 * @param batchId the batch ID
	 * @return list of audit logs for the batch
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
	 * @param startIndex pagination start index
	 * @param maxResults maximum number of results to return
	 * @return list of audit logs matching criteria
	 */
	List<ImportAuditLog> getAuditLogs(ImportAuditLog.Status status, ImportAuditLog.Action action, Integer creatorId,
	        Date startDate, Date endDate, Integer startIndex, Integer maxResults);
	
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
	Long countAuditLogs(ImportAuditLog.Status status, ImportAuditLog.Action action, Integer creatorId, Date startDate,
	        Date endDate);
	
	/**
	 * Deletes audit logs older than the specified date. Used for cleanup/retention policy enforcement.
	 *
	 * @param olderThan date threshold - logs older than this will be deleted
	 * @return number of deleted records
	 */
	int deleteOlderThan(Date olderThan);
	
	/**
	 * Deletes an audit log by ID.
	 *
	 * @param id the ID of the audit log to delete
	 */
	void delete(Integer id);
	
	/**
	 * Gets the most recent audit logs (for dashboard display).
	 *
	 * @param maxResults maximum number of results
	 * @return list of recent audit logs, ordered by date descending
	 */
	List<ImportAuditLog> getRecentAuditLogs(int maxResults);
}
