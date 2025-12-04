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
import java.util.Map;
import java.util.Set;

/**
 * Service for creating patients from import data.
 */
public interface PatientImportService {
	
	/**
	 * Creates patients from the registration sheet in the import plan.
	 *
	 * @param plan the import plan containing patient data
	 * @return outcome of patient creation including mappings and errors
	 */
	PatientCreationOutcome createPatientsFromPlan(ImportPlan plan);
	
	/**
	 * Outcome of patient creation process.
	 */
	class PatientCreationOutcome {
		
		public final Map<String, String> refToPatientUuid;
		
		public final Set<String> failedPatientRefs;
		
		public final List<String> errors;
		
		public PatientCreationOutcome(Map<String, String> refToPatientUuid, Set<String> failedPatientRefs,
		    List<String> errors) {
			this.refToPatientUuid = refToPatientUuid;
			this.failedPatientRefs = failedPatientRefs;
			this.errors = errors;
		}
	}
}
