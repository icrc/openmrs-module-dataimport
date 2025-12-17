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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openmrs.module.dataimport.api.dao.ImportAuditLogDao;
import org.openmrs.module.dataimport.api.model.ImportAuditLog;
import org.openmrs.module.dataimport.api.reader.ExcelReader;
import org.openmrs.module.dataimport.model.ImportPlan;
import org.openmrs.module.dataimport.model.ImportResult;
import org.openmrs.module.dataimport.model.ImportStats;
import org.openmrs.module.dataimport.model.QuarantineItem;
import org.openmrs.module.dataimport.model.RowPlan;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ImportAuditServiceImplTest extends BaseModuleContextSensitiveTest {
	
	@Mock
	private ImportAuditLogDao dao;
	
	private ImportAuditServiceImpl service;
	
	@BeforeEach
	public void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new ImportAuditServiceImpl();
		service.setDao(dao);
	}
	
	@Test
	public void logDryRun_shouldCreateSuccessAuditLog_whenNoIssues() {
		when(dao.saveOrUpdate(any(ImportAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
		
		UUID batchId = UUID.randomUUID();
		String filename = "test.xlsx";
		ImportStats stats = ImportStats.of(Collections.emptyList(), Collections.emptyList(), Collections.emptySet());
		
		ImportPlan plan = ImportPlan.builder().batchId(batchId).rowsOk(Collections.emptyList())
		        .issues(Collections.emptyList()).stats(stats).patientIndex(new ExcelReader.PatientIndex())
		        .blockedPatientRefs(Collections.emptySet()).build();
		
		ImportAuditLog result = service.logDryRun(batchId, filename, plan, 1000L);
		
		assertNotNull(result);
		assertEquals(ImportAuditLog.Action.DRY_RUN, result.getAction());
		assertEquals(ImportAuditLog.Status.SUCCESS, result.getStatus());
		assertEquals(filename, result.getFilename());
		assertEquals(batchId.toString(), result.getBatchId());
		assertEquals(1000L, result.getDurationMs());
		assertNotNull(result.getCreator(), "Creator should be set from authenticated user");
		
		verify(dao).saveOrUpdate(any(ImportAuditLog.class));
	}
	
	@Test
	public void logDryRun_shouldCreateValidationErrorsStatus_whenHasIssuesButAlsoValidRows() {
		when(dao.saveOrUpdate(any(ImportAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
		
		UUID batchId = UUID.randomUUID();
		List<QuarantineItem> issues = new ArrayList<>();
		issues.add(QuarantineItem.error("Sheet1", 1, 1, "PARSE_ERROR", "Invalid value"));
		
		List<RowPlan> okRows = new ArrayList<>();
		okRows.add(new RowPlan("Sheet1", 2, "P001", null, null, Collections.emptyList(), null));
		
		ImportStats stats = ImportStats.of(okRows, issues, Collections.emptySet());
		
		ImportPlan plan = ImportPlan.builder().batchId(batchId).rowsOk(okRows).issues(issues).stats(stats)
		        .patientIndex(new ExcelReader.PatientIndex()).blockedPatientRefs(Collections.emptySet()).build();
		
		ImportAuditLog result = service.logDryRun(batchId, "test.xlsx", plan, 500L);
		
		assertEquals(ImportAuditLog.Status.VALIDATION_ERRORS, result.getStatus());
		assertNotNull(result.getErrorSummary());
		assertTrue(result.getErrorSummary().contains("1 validation error"));
	}
	
	@Test
	public void logDryRun_shouldCreateFailedStatus_whenNoValidRows() {
		when(dao.saveOrUpdate(any(ImportAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
		
		UUID batchId = UUID.randomUUID();
		List<QuarantineItem> issues = new ArrayList<>();
		issues.add(QuarantineItem.error("Sheet1", 1, 1, "PARSE_ERROR", "Invalid value"));
		
		ImportStats stats = ImportStats.of(Collections.emptyList(), issues, Collections.emptySet());
		
		ImportPlan plan = ImportPlan.builder().batchId(batchId).rowsOk(Collections.emptyList()).issues(issues).stats(stats)
		        .patientIndex(new ExcelReader.PatientIndex()).blockedPatientRefs(Collections.emptySet()).build();
		
		ImportAuditLog result = service.logDryRun(batchId, "test.xlsx", plan, 500L);
		
		assertEquals(ImportAuditLog.Status.FAILED, result.getStatus());
	}
	
	@Test
	public void logDryRun_shouldCreateFailedStatus_whenPlanIsNull() {
		when(dao.saveOrUpdate(any(ImportAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
		
		UUID batchId = UUID.randomUUID();
		
		ImportAuditLog result = service.logDryRun(batchId, "test.xlsx", null, 100L);
		
		assertEquals(ImportAuditLog.Status.FAILED, result.getStatus());
	}
	
	@Test
	public void logCommit_shouldCreateSuccessAuditLog_whenResultIsSuccess() {
		when(dao.saveOrUpdate(any(ImportAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
		
		UUID batchId = UUID.randomUUID();
		ImportStats stats = ImportStats.of(Collections.emptyList(), Collections.emptyList(), Collections.emptySet());
		
		ImportResult importResult = new ImportResult(true, "Success", batchId, stats, Collections.emptyList(),
		        Collections.emptyList());
		
		ImportAuditLog result = service.logCommit(batchId, "test.xlsx", importResult, 2000L);
		
		assertNotNull(result);
		assertEquals(ImportAuditLog.Action.COMMIT, result.getAction());
		assertEquals(ImportAuditLog.Status.SUCCESS, result.getStatus());
		assertEquals(2000L, result.getDurationMs());
	}
	
	@Test
	public void logCommit_shouldCreatePartialSuccessStatus_whenHasQuarantinedRows() {
		when(dao.saveOrUpdate(any(ImportAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
		
		UUID batchId = UUID.randomUUID();
		List<QuarantineItem> issues = new ArrayList<>();
		issues.add(QuarantineItem.error("Sheet1", 1, 1, "PARSE_ERROR", "Invalid value"));
		
		List<RowPlan> okRows = new ArrayList<>();
		okRows.add(new RowPlan("Sheet1", 2, "P001", null, null, Collections.emptyList(), null));
		
		ImportStats stats = ImportStats.of(okRows, issues, Collections.emptySet());
		
		ImportResult importResult = new ImportResult(true, "Partial success", batchId, stats, Collections.emptyList(),
		        Collections.emptyList());
		
		ImportAuditLog result = service.logCommit(batchId, "test.xlsx", importResult, 2000L);
		
		assertEquals(ImportAuditLog.Status.PARTIAL_SUCCESS, result.getStatus());
	}
	
	@Test
	public void logCommit_shouldCreateFailedStatus_whenResultIsFailed() {
		when(dao.saveOrUpdate(any(ImportAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
		
		UUID batchId = UUID.randomUUID();
		List<String> errors = Collections.singletonList("Database connection failed");
		
		ImportResult importResult = ImportResult.failure(batchId, "Import failed", errors);
		
		ImportAuditLog result = service.logCommit(batchId, "test.xlsx", importResult, 100L);
		
		assertEquals(ImportAuditLog.Status.FAILED, result.getStatus());
		assertNotNull(result.getErrorSummary());
		assertTrue(result.getErrorSummary().contains("Database connection failed"));
	}
	
	@Test
	public void logCommit_shouldCreateFailedStatus_whenResultIsNull() {
		when(dao.saveOrUpdate(any(ImportAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
		
		UUID batchId = UUID.randomUUID();
		
		ImportAuditLog result = service.logCommit(batchId, "test.xlsx", null, 100L);
		
		assertEquals(ImportAuditLog.Status.FAILED, result.getStatus());
	}
	
	@Test
	public void logFailure_shouldCreateFailedAuditLog() {
		when(dao.saveOrUpdate(any(ImportAuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
		
		UUID batchId = UUID.randomUUID();
		String errorMessage = "File parsing failed: Invalid format";
		
		ImportAuditLog result = service.logFailure(batchId, "bad.xlsx", ImportAuditLog.Action.DRY_RUN, errorMessage, 50L);
		
		assertEquals(ImportAuditLog.Status.FAILED, result.getStatus());
		assertEquals(ImportAuditLog.Action.DRY_RUN, result.getAction());
		assertEquals(errorMessage, result.getErrorSummary());
		assertEquals("bad.xlsx", result.getFilename());
	}
	
	@Test
	public void getRetentionDays_shouldReturnDefaultWhenGlobalPropertyNotSet() {
		// Default retention is 90 days when no global property is set
		int result = service.getRetentionDays();
		
		assertEquals(90, result);
	}
	
	@Test
	public void cleanupOldLogs_shouldCallDaoWithCorrectDate() {
		when(dao.deleteOlderThan(any())).thenReturn(5);
		
		int deleted = service.cleanupOldLogs(30);
		
		assertEquals(5, deleted);
		
		ArgumentCaptor<Date> dateCaptor = ArgumentCaptor.forClass(Date.class);
		verify(dao).deleteOlderThan(dateCaptor.capture());
		
		Date cutoffDate = dateCaptor.getValue();
		assertNotNull(cutoffDate);
		
		// Verify the cutoff date is approximately 30 days ago
		long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
		long actualDiff = System.currentTimeMillis() - cutoffDate.getTime();
		assertTrue(Math.abs(actualDiff - thirtyDaysMs) < 60000, "Cutoff date should be approximately 30 days ago");
	}
	
	@Test
	public void logDryRun_shouldLimitStoredErrors() {
		ArgumentCaptor<ImportAuditLog> captor = ArgumentCaptor.forClass(ImportAuditLog.class);
		when(dao.saveOrUpdate(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
		
		UUID batchId = UUID.randomUUID();
		
		// Create more than 100 errors
		List<QuarantineItem> issues = new ArrayList<>();
		for (int i = 0; i < 150; i++) {
			issues.add(QuarantineItem.error("Sheet1", i, 1, "ERROR_" + (i % 5), "Error message " + i));
		}
		
		ImportStats stats = ImportStats.of(Collections.emptyList(), issues, Collections.emptySet());
		
		ImportPlan plan = ImportPlan.builder().batchId(batchId).rowsOk(Collections.emptyList()).issues(issues).stats(stats)
		        .patientIndex(new ExcelReader.PatientIndex()).blockedPatientRefs(Collections.emptySet()).build();
		
		service.logDryRun(batchId, "test.xlsx", plan, 500L);
		
		ImportAuditLog savedLog = captor.getValue();
		// Should be limited to MAX_ERRORS_TO_STORE (100)
		assertEquals(100, savedLog.getErrors().size());
	}
	
	@Test
	public void logDryRun_shouldSetCorrectStatsFromPlan() {
		ArgumentCaptor<ImportAuditLog> captor = ArgumentCaptor.forClass(ImportAuditLog.class);
		when(dao.saveOrUpdate(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
		
		UUID batchId = UUID.randomUUID();
		
		List<RowPlan> okRows = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			okRows.add(new RowPlan("Sheet1", i, "P00" + i, null, null, Collections.emptyList(), null));
		}
		
		List<QuarantineItem> issues = new ArrayList<>();
		issues.add(QuarantineItem.error("Sheet1", 10, 1, "ERROR", "Error"));
		issues.add(QuarantineItem.error("Sheet1", 11, 1, "ERROR", "Error"));
		
		ImportStats stats = ImportStats.of(okRows, issues, Collections.emptySet());
		
		ImportPlan plan = ImportPlan.builder().batchId(batchId).rowsOk(okRows).issues(issues).stats(stats)
		        .patientIndex(new ExcelReader.PatientIndex()).blockedPatientRefs(Collections.emptySet()).build();
		
		service.logDryRun(batchId, "test.xlsx", plan, 500L);
		
		ImportAuditLog savedLog = captor.getValue();
		assertEquals(7, savedLog.getTotalRows()); // 5 ok + 2 quarantined
		assertEquals(5, savedLog.getSuccessfulRows());
		assertEquals(2, savedLog.getQuarantinedRows());
	}
	
	@Test
	public void getByUuid_shouldDelegateToDao() {
		String uuid = UUID.randomUUID().toString();
		ImportAuditLog expectedLog = new ImportAuditLog();
		expectedLog.setUuid(uuid);
		
		when(dao.getByUuid(uuid)).thenReturn(expectedLog);
		
		ImportAuditLog result = service.getByUuid(uuid);
		
		assertEquals(uuid, result.getUuid());
		verify(dao).getByUuid(uuid);
	}
	
	@Test
	public void getByBatchId_shouldDelegateToDao() {
		String batchId = UUID.randomUUID().toString();
		ImportAuditLog expectedLog = new ImportAuditLog();
		expectedLog.setBatchId(batchId);
		
		when(dao.getByBatchId(batchId)).thenReturn(expectedLog);
		
		ImportAuditLog result = service.getByBatchId(batchId);
		
		assertEquals(batchId, result.getBatchId());
		verify(dao).getByBatchId(batchId);
	}
	
	@Test
	public void getAllByBatchId_shouldDelegateToDao() {
		String batchId = UUID.randomUUID().toString();
		List<ImportAuditLog> expectedLogs = new ArrayList<>();
		expectedLogs.add(new ImportAuditLog());
		
		when(dao.getAllByBatchId(batchId)).thenReturn(expectedLogs);
		
		List<ImportAuditLog> result = service.getAllByBatchId(batchId);
		
		assertEquals(1, result.size());
		verify(dao).getAllByBatchId(batchId);
	}
	
	@Test
	public void getRecentAuditLogs_shouldDelegateToDao() {
		List<ImportAuditLog> expectedLogs = new ArrayList<>();
		expectedLogs.add(new ImportAuditLog());
		expectedLogs.add(new ImportAuditLog());
		
		when(dao.getRecentAuditLogs(10)).thenReturn(expectedLogs);
		
		List<ImportAuditLog> result = service.getRecentAuditLogs(10);
		
		assertEquals(2, result.size());
		verify(dao).getRecentAuditLogs(10);
	}
	
	@Test
	public void countAuditLogs_shouldDelegateToDao() {
		when(dao.countAuditLogs(null, null, null, null, null)).thenReturn(42L);
		
		long result = service.countAuditLogs(null, null, null, null, null);
		
		assertEquals(42L, result);
		verify(dao).countAuditLogs(null, null, null, null, null);
	}
}
