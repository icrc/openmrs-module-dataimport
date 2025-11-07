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

import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.dataimport.model.EncounterBlueprint;

import java.util.Date;

public interface EncounterService extends OpenmrsService {
	
	Encounter createEncounter(String patientUuid, String encounterTypeUuid, Date when, String locationUuid, String formUuid,
	        String providerUuid, String encounterRoleUuid, Visit visit);
	
	Encounter createEncounterFromBlueprint(Patient patient, EncounterBlueprint blueprint, Visit visit);
	
	String getDefaultLocationUuid();
	
	String getDefaultEncounterTypeUuid();
	
	String getDefaultEncounterRoleUuid();
}
