/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.impl;

import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.dao.ImportAuditLogDao;
import org.openmrs.module.dataimport.api.model.ImportAuditError;
import org.openmrs.module.dataimport.api.model.ImportAuditLog;
import org.openmrs.module.dataimport.api.service.ImportAuditService;
import org.openmrs.module.dataimport.model.ImportPlan;
import org.openmrs.module.dataimport.model.ImportResult;
import org.openmrs.module.dataimport.model.ImportStats;
import org.openmrs.module.dataimport.model.QuarantineItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of ImportAuditService. Handles logging of import operations and cleanup of old
 * audit logs.
 */
@Transactional
public class ImportAuditServiceImpl implements ImportAuditService {
	
	private static final Logger log = LoggerFactory.getLogger(ImportAuditServiceImpl.class);
	
	private static final String GP_RETENTION_DAYS = "dataimport.auditlog.retentionDays";
	
	private static final int DEFAULT_RETENTION_DAYS = 90;
	
	private static final int MAX_ERRORS_TO_STORE = 100;
	
	private ImportAuditLogDao dao;
	
	public void setDao(ImportAuditLogDao dao) {
		this.dao = dao;
	}
	
	@Override
	public ImportAuditLog logDryRun(UUID batchId, String filename, ImportPlan plan, long durationMs) {
		ImportAuditLog auditLog = createBaseAuditLog(batchId, filename, ImportAuditLog.Action.DRY_RUN, durationMs);
		
		if (plan != null) {
			ImportStats stats = plan.getStats();
			if (stats != null) {
				auditLog.setTotalRows(stats.getTotalRows());
				auditLog.setSuccessfulRows(stats.getOkRows());
				auditLog.setQuarantinedRows(stats.getQuarantinedRows());
				auditLog.setPatientsCreated(stats.getPatientsToCreate());
				auditLog.setEncountersCreated(stats.getEncountersToCreate());
				auditLog.setObsCreated(stats.getObsToCreate());
			}
			
			// Determine status based on validation results
			if (plan.getIssues() == null || plan.getIssues().isEmpty()) {
				auditLog.setStatus(ImportAuditLog.Status.SUCCESS);
			} else if (plan.getRowsOk() != null && !plan.getRowsOk().isEmpty()) {
				auditLog.setStatus(ImportAuditLog.Status.VALIDATION_ERRORS);
			} else {
				auditLog.setStatus(ImportAuditLog.Status.FAILED);
			}
			
			// Store error details (limited to prevent DB bloat)
			if (plan.getIssues() != null && !plan.getIssues().isEmpty()) {
				storeErrors(auditLog, plan.getIssues());
				auditLog.setErrorSummary(buildErrorSummary(plan.getIssues()));
			}
		} else {
			auditLog.setStatus(ImportAuditLog.Status.FAILED);
		}
		
		return dao.saveOrUpdate(auditLog);
	}
	
	@Override
	public ImportAuditLog logCommit(UUID batchId, String filename, ImportResult result, long durationMs) {
		ImportAuditLog auditLog = createBaseAuditLog(batchId, filename, ImportAuditLog.Action.COMMIT, durationMs);
		
		if (result != null) {
			auditLog.setStatus(result.isSuccess() ? ImportAuditLog.Status.SUCCESS : ImportAuditLog.Status.FAILED);
			
			ImportStats stats = result.getStats();
			if (stats != null) {
				auditLog.setTotalRows(stats.getTotalRows());
				auditLog.setSuccessfulRows(stats.getOkRows());
				auditLog.setQuarantinedRows(stats.getQuarantinedRows());
				auditLog.setPatientsCreated(stats.getPatientsToCreate());
				auditLog.setEncountersCreated(stats.getEncountersToCreate());
				auditLog.setObsCreated(stats.getObsToCreate());
			}
			
			if (result.getErrors() != null && !result.getErrors().isEmpty()) {
				auditLog.setErrorSummary(String.join("; ", result.getErrors()));
			}
			
			// Check for partial success (some rows succeeded, some failed)
			if (result.isSuccess() && stats != null && stats.getQuarantinedRows() > 0) {
				auditLog.setStatus(ImportAuditLog.Status.PARTIAL_SUCCESS);
			}
		} else {
			auditLog.setStatus(ImportAuditLog.Status.FAILED);
		}
		
		return dao.saveOrUpdate(auditLog);
	}
	
	@Override
	public ImportAuditLog logFailure(UUID batchId, String filename, ImportAuditLog.Action action, String errorMessage,
	        long durationMs) {
		ImportAuditLog auditLog = createBaseAuditLog(batchId, filename, action, durationMs);
		auditLog.setStatus(ImportAuditLog.Status.FAILED);
		auditLog.setErrorSummary(errorMessage);
		return dao.saveOrUpdate(auditLog);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ImportAuditLog getByUuid(String uuid) {
		return dao.getByUuid(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ImportAuditLog getByBatchId(String batchId) {
		return dao.getByBatchId(batchId);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ImportAuditLog> getAllByBatchId(String batchId) {
		return dao.getAllByBatchId(batchId);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ImportAuditLog> getAuditLogs(ImportAuditLog.Status status, ImportAuditLog.Action action, Integer creatorId,
	        Date startDate, Date endDate, int page, int pageSize) {
		int startIndex = page * pageSize;
		return dao.getAuditLogs(status, action, creatorId, startDate, endDate, startIndex, pageSize);
	}
	
	@Override
	@Transactional(readOnly = true)
	public long countAuditLogs(ImportAuditLog.Status status, ImportAuditLog.Action action, Integer creatorId, Date startDate,
	        Date endDate) {
		return dao.countAuditLogs(status, action, creatorId, startDate, endDate);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ImportAuditLog> getRecentAuditLogs(int maxResults) {
		return dao.getRecentAuditLogs(maxResults);
	}
	
	@Override
	public int cleanupOldLogs(int retentionDays) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, -retentionDays);
		Date cutoffDate = cal.getTime();
		
		log.info("Cleaning up audit logs older than {} days (before {})", retentionDays, cutoffDate);
		int deleted = dao.deleteOlderThan(cutoffDate);
		log.info("Deleted {} old audit log entries", deleted);
		
		return deleted;
	}
	
	@Override
	@Transactional(readOnly = true)
	public int getRetentionDays() {
		String value = Context.getAdministrationService().getGlobalProperty(GP_RETENTION_DAYS);
		if (value != null && !value.trim().isEmpty()) {
			try {
				return Integer.parseInt(value.trim());
			}
			catch (NumberFormatException e) {
				log.warn("Invalid value for {}: {}. Using default: {}", GP_RETENTION_DAYS, value, DEFAULT_RETENTION_DAYS);
			}
		}
		return DEFAULT_RETENTION_DAYS;
	}
	
	private ImportAuditLog createBaseAuditLog(UUID batchId, String filename, ImportAuditLog.Action action, long durationMs) {
		ImportAuditLog auditLog = new ImportAuditLog();
		auditLog.setUuid(UUID.randomUUID().toString());
		auditLog.setBatchId(batchId != null ? batchId.toString() : UUID.randomUUID().toString());
		auditLog.setFilename(filename);
		auditLog.setAction(action);
		auditLog.setDurationMs(durationMs);
		auditLog.setCreator(Context.getAuthenticatedUser());
		auditLog.setDateCreated(new Date());
		return auditLog;
	}
	
	private void storeErrors(ImportAuditLog auditLog, List<QuarantineItem> issues) {
		// Limit the number of errors stored to prevent DB bloat
		List<QuarantineItem> limitedIssues = issues.stream().limit(MAX_ERRORS_TO_STORE).collect(Collectors.toList());
		
		for (QuarantineItem issue : limitedIssues) {
			ImportAuditError error = new ImportAuditError(issue.getSheet(), issue.getRow(), issue.getCol(), issue.getCode(),
			        issue.getMessage());
			auditLog.addError(error);
		}
		
		if (issues.size() > MAX_ERRORS_TO_STORE) {
			log.info("Truncated error list from {} to {} entries for audit log", issues.size(), MAX_ERRORS_TO_STORE);
		}
	}
	
	private String buildErrorSummary(List<QuarantineItem> issues) {
		// Group errors by error code and count
		java.util.Map<String, Long> errorCounts = issues.stream().collect(
		    java.util.stream.Collectors.groupingBy(QuarantineItem::getCode, java.util.stream.Collectors.counting()));
		
		StringBuilder summary = new StringBuilder();
		summary.append(issues.size()).append(" validation error(s): ");
		
		errorCounts.entrySet().stream().sorted(java.util.Map.Entry.<String, Long> comparingByValue().reversed()).limit(5)
		        .forEach(entry -> {
			        if (summary.length() > 30) {
				        summary.append(", ");
			        }
			        summary.append(entry.getKey()).append(" (").append(entry.getValue()).append(")");
		        });
		
		if (errorCounts.size() > 5) {
			summary.append(", and ").append(errorCounts.size() - 5).append(" more error types");
		}
		
		return summary.toString();
	}
}
