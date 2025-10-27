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

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class PatientSaver {
	
	private static final Logger log = LoggerFactory.getLogger(PatientSaver.class);
	
	public Patient createPatient(String givenName, String familyName, String gender, Date birthdate, String identifierValue,
	        String identifierTypeUuid, String locationUuid) {
		
		PatientService ps = Context.getPatientService();
		LocationService ls = Context.getLocationService();
		
		Person person = new Person();
		person.setGender(gender);
		person.setBirthdate(birthdate);
		
		PersonName name = new PersonName();
		name.setGivenName(givenName);
		name.setFamilyName(familyName);
		name.setPreferred(true);
		person.addName(name);
		
		Patient patient = new Patient(person);
		PatientIdentifierType idType = ps.getPatientIdentifierTypeByUuid(identifierTypeUuid);
		if (idType == null) {
			throw new IllegalArgumentException("PatientIdentifierType not found: " + identifierTypeUuid);
		}
		
		Location idLocation = ls.getLocationByUuid(locationUuid);
		if (idLocation == null) {
			throw new IllegalArgumentException("Location not found: " + locationUuid);
		}
		
		PatientIdentifier pid = new PatientIdentifier(identifierValue, idType, idLocation);
		pid.setPreferred(true);
		patient.addIdentifier(pid);
		
		patient = ps.savePatient(patient);
		log.info("Created new patient: {} {} with identifier: {}", givenName, familyName, identifierValue);
		return patient;
	}
	
	public Patient getOrCreateByIdentifier(String identifierValue, String identifierTypeUuid, String locationUuid,
	        String givenName, String familyName, String gender, Date birthdate) {
		
		PatientService ps = Context.getPatientService();
		PatientIdentifierType type = ps.getPatientIdentifierTypeByUuid(identifierTypeUuid);
		
		if (type == null) {
			throw new IllegalArgumentException("PatientIdentifierType not found: " + identifierTypeUuid);
		}
		
		List<Patient> existingPatients = ps.getPatients(null, identifierValue, Collections.singletonList(type), true);
		Patient existing = existingPatients.stream().findFirst().orElse(null);
		
		if (existing != null) {
			log.info("Found existing patient with identifier: {}", identifierValue);
			return existing;
		}
		return createPatient(givenName, familyName, gender, birthdate, identifierValue, identifierTypeUuid, locationUuid);
	}
	
	public String getDefaultIdentifierTypeUuid() {
		try {
			PatientService ps = Context.getPatientService();
			PatientIdentifierType openmrsIdType = ps.getPatientIdentifierTypeByName("OpenMRS ID");
			if (openmrsIdType != null) {
				return openmrsIdType.getUuid();
			}
			
			List<PatientIdentifierType> types = ps.getAllPatientIdentifierTypes();
			if (types.isEmpty()) {
				throw new IllegalStateException("No PatientIdentifierTypes configured in system");
			}
			
			return types.get(0).getUuid();
		}
		catch (Exception e) {
			log.warn("Failed to get default identifier type UUID: {}", e.getMessage());
			return "PLACEHOLDER_IDENTIFIER_TYPE_UUID";
		}
	}
	
	public String getDefaultLocationUuid() {
		try {
			LocationService ls = Context.getLocationService();
			Location defaultLocation = ls.getDefaultLocation();
			
			if (defaultLocation == null) {
				throw new IllegalStateException("No default location configured in system");
			}
			
			return defaultLocation.getUuid();
		}
		catch (Exception e) {
			log.warn("Failed to get default location UUID: {}", e.getMessage());
			return "PLACEHOLDER_LOCATION_UUID";
		}
	}
}
