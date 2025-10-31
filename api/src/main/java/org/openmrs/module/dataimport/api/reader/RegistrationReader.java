/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.reader;

import lombok.Getter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.openmrs.module.dataimport.api.util.WorkbookUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class RegistrationReader {
	
	private static final Logger log = LoggerFactory.getLogger(RegistrationReader.class);
	
	private static final String PATIENT_SHEET_NAME = "Patient Registration";
	
	public static PatientIndex indexPatients(org.apache.poi.ss.usermodel.Workbook workbook) {
		Sheet regSheet = workbook.getSheet(PATIENT_SHEET_NAME);
		if (regSheet == null) {
			log.warn("No Patient Registration sheet found");
			return new PatientIndex();
		}
		
		Map<String, PatientRow> patientRows = new HashMap<>();
		
		for (int rowIndex = 1; rowIndex <= WorkbookUtil.lastDataRow(regSheet); rowIndex++) {
			if (WorkbookUtil.isEmptyRow(regSheet, rowIndex))
				continue;
			
			try {
				PatientRow patientRow = PatientRow.from(regSheet, rowIndex);
				if (patientRow != null && patientRow.patientRef != null && !patientRow.patientRef.isEmpty()) {
					patientRows.put(patientRow.patientRef, patientRow);
				}
			}
			catch (Exception e) {
				log.warn("Failed to parse patient row {}: {}", rowIndex, e.getMessage());
			}
		}
		
		log.debug("Indexed {} patients from registration sheet", patientRows.size());
		return new PatientIndex(patientRows);
	}
	
	/**
	 * Represents a patient row from the registration sheet.
	 */
	public static class PatientRow {
		
		public String givenName;
		
		public String familyName;
		
		public String gender;
		
		public LocalDate birthdate;
		
		public String addressLine1;
		
		public String cityVillage;
		
		public String country;
		
		public String phone;
		
		public String patientRef;
		
		public static PatientRow from(Sheet sheet, int rowIndex) {
			Row row = sheet.getRow(rowIndex);
			if (row == null)
				return null;
			
			PatientRow patientRow = new PatientRow();
			patientRow.givenName = WorkbookUtil.Cells.getString(row.getCell(0));
			patientRow.familyName = WorkbookUtil.Cells.getString(row.getCell(1));
			patientRow.gender = WorkbookUtil.Cells.getString(row.getCell(2));
			patientRow.birthdate = WorkbookUtil.Cells.getLocalDate(row.getCell(3));
			patientRow.addressLine1 = WorkbookUtil.Cells.getString(row.getCell(4));
			patientRow.cityVillage = WorkbookUtil.Cells.getString(row.getCell(5));
			patientRow.country = WorkbookUtil.Cells.getString(row.getCell(6));
			patientRow.phone = WorkbookUtil.Cells.getString(row.getCell(7));
			patientRow.patientRef = WorkbookUtil.Cells.getString(row.getCell(8));
			
			return patientRow;
		}
		
		public String patientRef() {
			return patientRef;
		}
	}
	
	/**
	 * Index for resolving patient references.
	 */
	@Getter
	public static class PatientIndex {
		
		private final Map<String, PatientRow> patients;
		
		public PatientIndex() {
			this.patients = new HashMap<>();
		}
		
		public PatientIndex(Map<String, PatientRow> patients) {
			this.patients = patients;
		}
		
		public PatientResolution resolve(String patientRef) {
			if (patientRef == null || patientRef.isEmpty()) {
				return PatientResolution.unresolved("Missing patient reference");
			}
			
			PatientRow patientRow = patients.get(patientRef);
			if (patientRow == null) {
				return PatientResolution.unresolved("Patient reference not found: " + patientRef);
			}
			
			// For now, we'll mark all patients as needing to be created
			// In a real implementation, you'd check against existing patients
			return PatientResolution.createNew(patientRow);
		}
	}
	
	/**
	 * Result of patient resolution.
	 */
	@Getter
	public static class PatientResolution {
		
		private final boolean resolved;
		
		private final String patientUuid;
		
		private final PatientRow patientRow;
		
		private final String errorMessage;
		
		private PatientResolution(boolean resolved, String patientUuid, PatientRow patientRow, String errorMessage) {
			this.resolved = resolved;
			this.patientUuid = patientUuid;
			this.patientRow = patientRow;
			this.errorMessage = errorMessage;
		}
		
		public static PatientResolution createNew(PatientRow patientRow) {
			return new PatientResolution(true, null, patientRow, null);
		}
		
		public static PatientResolution existing(String patientUuid) {
			return new PatientResolution(true, patientUuid, null, null);
		}
		
		public static PatientResolution unresolved(String errorMessage) {
			return new PatientResolution(false, null, null, errorMessage);
		}
		
		public boolean needsCreation() {
			return resolved && patientUuid == null && patientRow != null;
		}
	}
}
