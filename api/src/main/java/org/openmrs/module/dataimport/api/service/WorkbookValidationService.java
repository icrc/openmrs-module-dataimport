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

import org.apache.poi.ss.usermodel.Sheet;
import org.openmrs.module.dataimport.api.reader.ExcelReader;
import org.openmrs.module.dataimport.model.QuarantineItem;
import org.openmrs.module.dataimport.model.RowPlan;
import org.openmrs.module.dataimport.model.TemplateMeta;

import java.util.List;
import java.util.Set;

/**
 * Service for validating workbook data during dry-run phase.
 */
public interface WorkbookValidationService {
	
	/**
	 * Validates workbook sheets and returns validation results.
	 *
	 * @param sheets the sheets to validate
	 * @param meta the template metadata
	 * @param patientIndex the patient index for resolving patient references
	 * @param templateDefaultLocationUuid the default location UUID
	 * @return validation results containing ok rows, quarantine items, and blocked patient refs
	 */
	ValidationResult validate(List<Sheet> sheets, TemplateMeta meta, ExcelReader.PatientIndex patientIndex,
	        String templateDefaultLocationUuid);
	
	/**
	 * Result of workbook validation.
	 */
	class ValidationResult {
		
		public final List<RowPlan> okRows;
		
		public final List<QuarantineItem> quarantineItems;
		
		public final Set<String> blockedPatientRefs;
		
		public ValidationResult(List<RowPlan> okRows, List<QuarantineItem> quarantineItems, Set<String> blockedPatientRefs) {
			this.okRows = okRows;
			this.quarantineItems = quarantineItems;
			this.blockedPatientRefs = blockedPatientRefs;
		}
	}
}
