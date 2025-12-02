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

import org.openmrs.module.dataimport.model.ImportPlan;
import org.openmrs.module.dataimport.model.ImportResult;

import java.io.InputStream;
import java.util.UUID;

public interface WorkbookImportService {
	
	/**
	 * Performs a dry-run validation of the workbook without committing any data.
	 * 
	 * @param xlsx InputStream containing the Excel workbook
	 * @return ImportPlan containing validation results and planned operations
	 */
	ImportPlan dryRun(InputStream xlsx);
	
	/**
	 * Commits the validated import plan to the database.
	 * 
	 * @param batchId UUID of the batch to commit
	 * @return ImportResult containing execution results
	 */
	ImportResult commit(UUID batchId);
	
	/**
	 * Generates a color-coded Excel report with quarantined rows highlighted.
	 * 
	 * @param originalWorkbook The original workbook as an InputStream
	 * @param batchId UUID of the batch to get the import plan for
	 * @return Excel file as byte array with error rows highlighted
	 */
	byte[] generateQuarantineReport(InputStream originalWorkbook, UUID batchId);
}
