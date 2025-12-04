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

import java.util.List;

/**
 * Orchestrates encounter and observation creation during import commit.
 */
public interface EncounterImportOrchestrator {

	/**
	 * Processes encounters and observations for all rows in the import plan.
	 *
	 * @param plan the import plan
	 * @param patientCreationOutcome the outcome of patient creation
	 * @return outcome of encounter processing
	 */
	EncounterProcessingOutcome processEncounters(ImportPlan plan,
	        PatientImportService.PatientCreationOutcome patientCreationOutcome);

	/**
	 * Outcome of encounter processing.
	 */
	class EncounterProcessingOutcome {

		public final List<String> createdEncounterUuids;

		public final List<String> errors;

		public final int failedEncounters;

		public final int failedObs;

		public final int skippedRows;

		public EncounterProcessingOutcome(List<String> createdEncounterUuids, List<String> errors, int failedEncounters,
		        int failedObs, int skippedRows) {
			this.createdEncounterUuids = createdEncounterUuids;
			this.errors = errors;
			this.failedEncounters = failedEncounters;
			this.failedObs = failedObs;
			this.skippedRows = skippedRows;
		}
	}
}
