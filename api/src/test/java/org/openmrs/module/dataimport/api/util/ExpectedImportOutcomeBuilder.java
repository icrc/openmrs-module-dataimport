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

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for creating expected import outcome assertions. Used to define what should be in the
 * database after a successful import.
 */
public class ExpectedImportOutcomeBuilder {
	
	private int expectedPatientCount;
	
	private int expectedEncounterCount;
	
	private int expectedObsCount;
	
	private int expectedQuarantinedRows;
	
	private List<String> expectedErrorMessages;
	
	private List<ObsAssertion> obsValidations;
	
	public ExpectedImportOutcomeBuilder() {
		this.expectedErrorMessages = new ArrayList<>();
		this.obsValidations = new ArrayList<>();
	}
	
	/**
	 * Creates a new builder.
	 *
	 * @return a new builder instance
	 */
	public static ExpectedImportOutcomeBuilder create() {
		return new ExpectedImportOutcomeBuilder();
	}
	
	/**
	 * Sets the expected number of patients.
	 *
	 * @param count expected patient count
	 * @return this builder
	 */
	public ExpectedImportOutcomeBuilder expectPatients(int count) {
		this.expectedPatientCount = count;
		return this;
	}
	
	/**
	 * Sets the expected number of encounters.
	 *
	 * @param count expected encounter count
	 * @return this builder
	 */
	public ExpectedImportOutcomeBuilder expectEncounters(int count) {
		this.expectedEncounterCount = count;
		return this;
	}
	
	/**
	 * Sets the expected number of observations.
	 *
	 * @param count expected observation count
	 * @return this builder
	 */
	public ExpectedImportOutcomeBuilder expectObservations(int count) {
		this.expectedObsCount = count;
		return this;
	}
	
	/**
	 * Sets the expected number of quarantined rows.
	 *
	 * @param count expected quarantined row count
	 * @return this builder
	 */
	public ExpectedImportOutcomeBuilder expectQuarantinedRows(int count) {
		this.expectedQuarantinedRows = count;
		return this;
	}
	
	/**
	 * Adds an expected error message pattern.
	 *
	 * @param errorPattern the error message pattern
	 * @return this builder
	 */
	public ExpectedImportOutcomeBuilder expectError(String errorPattern) {
		this.expectedErrorMessages.add(errorPattern);
		return this;
	}
	
	/**
	 * Adds an observation assertion.
	 *
	 * @param conceptUuid the concept UUID
	 * @param patientRef the patient reference
	 * @param expectedValue the expected value
	 * @return this builder
	 */
	public ExpectedImportOutcomeBuilder assertObs(String conceptUuid, String patientRef, Object expectedValue) {
		this.obsValidations.add(new ObsAssertion(conceptUuid, patientRef, expectedValue, 0));
		return this;
	}
	
	/**
	 * Adds an observation assertion with encounter index.
	 *
	 * @param conceptUuid the concept UUID
	 * @param patientRef the patient reference
	 * @param expectedValue the expected value
	 * @param encounterIndex the encounter index for this patient
	 * @return this builder
	 */
	public ExpectedImportOutcomeBuilder assertObs(String conceptUuid, String patientRef, Object expectedValue,
	        int encounterIndex) {
		this.obsValidations.add(new ObsAssertion(conceptUuid, patientRef, expectedValue, encounterIndex));
		return this;
	}
	
	// Getters
	
	public int getExpectedPatientCount() {
		return expectedPatientCount;
	}
	
	public int getExpectedEncounterCount() {
		return expectedEncounterCount;
	}
	
	public int getExpectedObsCount() {
		return expectedObsCount;
	}
	
	public int getExpectedQuarantinedRows() {
		return expectedQuarantinedRows;
	}
	
	public List<String> getExpectedErrorMessages() {
		return expectedErrorMessages;
	}
	
	public List<ObsAssertion> getObsValidations() {
		return obsValidations;
	}
	
	/**
	 * Represents an assertion about a specific observation.
	 */
	public static class ObsAssertion {
		
		private final String conceptUuid;
		
		private final String patientRef;
		
		private final Object expectedValue;
		
		private final int encounterIndex;
		
		public ObsAssertion(String conceptUuid, String patientRef, Object expectedValue, int encounterIndex) {
			this.conceptUuid = conceptUuid;
			this.patientRef = patientRef;
			this.expectedValue = expectedValue;
			this.encounterIndex = encounterIndex;
		}
		
		public String getConceptUuid() {
			return conceptUuid;
		}
		
		public String getPatientRef() {
			return patientRef;
		}
		
		public Object getExpectedValue() {
			return expectedValue;
		}
		
		public int getEncounterIndex() {
			return encounterIndex;
		}
	}
}
