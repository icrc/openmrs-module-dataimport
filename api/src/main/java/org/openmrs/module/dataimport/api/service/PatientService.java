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

import org.openmrs.Patient;
import org.openmrs.api.OpenmrsService;

import java.util.Date;

public interface PatientService extends OpenmrsService {
	
	Patient createPatientWithGeneratedIdentifier(String givenName, String familyName, String gender, Date birthdate,
	        String identifierTypeUuid, String locationUuid, String addressLine1, String cityVillage, String country,
	        String phone);
	
	Patient createPatient(String givenName, String familyName, String gender, Date birthdate, String identifierValue,
	        String identifierTypeUuid, String locationUuid, String addressLine1, String cityVillage, String country,
	        String phone);
}
