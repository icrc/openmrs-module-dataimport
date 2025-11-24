/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.util;

import org.apache.poi.ss.usermodel.Sheet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Builder for creating test patient data for import testing. Provides methods to generate filled
 * Excel rows with patient and encounter data.
 */
public class TestPatientDataBuilder {
	
	private String patientId;
	
	private String givenName;
	
	private String familyName;
	
	private String gender;
	
	private LocalDate birthdate;
	
	private LocalDateTime encounterDate;
	
	private String locationUuid;
	
	private String providerUuid;
	
	private Map<String, Object> observations;
	
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	
	private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	
	public TestPatientDataBuilder() {
		this.observations = new HashMap<>();
		// Set defaults
		this.gender = "M";
		this.encounterDate = LocalDateTime.now();
	}
	
	/**
	 * Creates a new test patient data builder.
	 *
	 * @return a new builder instance
	 */
	public static TestPatientDataBuilder create() {
		return new TestPatientDataBuilder();
	}
	
	/**
	 * Sets the patient identifier.
	 *
	 * @param patientId the patient ID
	 * @return this builder
	 */
	public TestPatientDataBuilder withPatientId(String patientId) {
		this.patientId = patientId;
		return this;
	}
	
	/**
	 * Sets the patient name.
	 *
	 * @param givenName the given name
	 * @param familyName the family name
	 * @return this builder
	 */
	public TestPatientDataBuilder withName(String givenName, String familyName) {
		this.givenName = givenName;
		this.familyName = familyName;
		return this;
	}
	
	/**
	 * Sets the patient gender.
	 *
	 * @param gender M, F, or U
	 * @return this builder
	 */
	public TestPatientDataBuilder withGender(String gender) {
		this.gender = gender;
		return this;
	}
	
	/**
	 * Sets the birthdate.
	 *
	 * @param birthdate the date of birth
	 * @return this builder
	 */
	public TestPatientDataBuilder withBirthdate(LocalDate birthdate) {
		this.birthdate = birthdate;
		return this;
	}
	
	/**
	 * Sets the encounter date.
	 *
	 * @param encounterDate the encounter datetime
	 * @return this builder
	 */
	public TestPatientDataBuilder withEncounterDate(LocalDateTime encounterDate) {
		this.encounterDate = encounterDate;
		return this;
	}
	
	/**
	 * Sets the location UUID.
	 *
	 * @param locationUuid the location UUID
	 * @return this builder
	 */
	public TestPatientDataBuilder withLocation(String locationUuid) {
		this.locationUuid = locationUuid;
		return this;
	}
	
	/**
	 * Sets the provider UUID.
	 *
	 * @param providerUuid the provider UUID
	 * @return this builder
	 */
	public TestPatientDataBuilder withProvider(String providerUuid) {
		this.providerUuid = providerUuid;
		return this;
	}
	
	/**
	 * Adds an observation value.
	 *
	 * @param conceptName the concept name or column name
	 * @param value the observation value
	 * @return this builder
	 */
	public TestPatientDataBuilder withObs(String conceptName, Object value) {
		this.observations.put(conceptName, value);
		return this;
	}
	
	/**
	 * Writes this patient data to an Excel sheet row.
	 *
	 * @param sheet the Excel sheet
	 * @param rowIndex the row index to write to
	 * @param columnOrder the ordered list of column names
	 */
	public void writeToSheet(Sheet sheet, int rowIndex, java.util.List<String> columnOrder) {
		for (int colIndex = 0; colIndex < columnOrder.size(); colIndex++) {
			String columnName = columnOrder.get(colIndex);
			Object value = getValueForColumn(columnName);
			ExcelTestHelper.setCellValue(sheet, rowIndex, colIndex, value);
		}
	}
	
	/**
	 * Gets the value for a specific column.
	 *
	 * @param columnName the column name
	 * @return the value to write
	 */
	private Object getValueForColumn(String columnName) {
		// Handle standard columns
		if ("Patient Ref".equals(columnName) || "patientId".equals(columnName)) {
			return patientId;
		}
		if ("Encounter Date *".equals(columnName) || "Encounter Date".equals(columnName)
		        || "encounterDate".equals(columnName)) {
			return encounterDate != null ? encounterDate.format(DATE_FORMATTER) : null;
		}
		if ("Given Name".equals(columnName)) {
			return givenName;
		}
		if ("Family Name".equals(columnName)) {
			return familyName;
		}
		if ("Gender".equals(columnName)) {
			return gender;
		}
		if ("Birthdate".equals(columnName)) {
			return birthdate != null ? birthdate.format(DATE_FORMATTER) : null;
		}
		if ("Location".equals(columnName)) {
			return locationUuid;
		}
		if ("Provider".equals(columnName)) {
			return providerUuid;
		}
		
		// Check if it's an observation
		if (observations.containsKey(columnName)) {
			return observations.get(columnName);
		}
		
		return null;
	}
	
	/**
	 * Converts this patient data to an array of values for writeRow.
	 *
	 * @param columnOrder the ordered list of column names
	 * @return array of values
	 */
	public Object[] toArray(java.util.List<String> columnOrder) {
		Object[] values = new Object[columnOrder.size()];
		for (int i = 0; i < columnOrder.size(); i++) {
			values[i] = getValueForColumn(columnOrder.get(i));
		}
		return values;
	}
	
	/**
	 * Creates a quick test patient with minimal data.
	 *
	 * @param patientId the patient ID
	 * @return a builder with basic defaults
	 */
	public static TestPatientDataBuilder quickPatient(String patientId) {
		return create().withPatientId(patientId).withName("Test", "Patient").withGender("M")
		        .withBirthdate(LocalDate.of(1980, 1, 1)).withEncounterDate(LocalDateTime.now());
	}
}
