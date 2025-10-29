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
import org.openmrs.module.idgen.IdentifierSource;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class PatientSaver {
	
	private static final Logger log = LoggerFactory.getLogger(PatientSaver.class);
	
	private PatientService getPatientService() {
		return Context.getPatientService();
	}
	
	private LocationService getLocationService() {
		return Context.getLocationService();
	}
	
	public Patient createPatient(String givenName, String familyName, String gender, Date birthdate, String identifierValue,
	        String identifierTypeUuid, String locationUuid) {
		
		PatientService ps = getPatientService();
		LocationService ls = getLocationService();
		
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
		log.info("Created patient: {} {} with identifier: {}", givenName, familyName, identifierValue);
		return patient;
	}
	
	/**
	 * Creates a patient and automatically generates a valid identifier using IdGen for the given
	 * identifier type. This avoids using non-identifier values (like patientRef) as identifiers and
	 * ensures validators (e.g., Luhn Mod-30) are satisfied.
	 */
	public Patient createPatientWithGeneratedIdentifier(String givenName, String familyName, String gender, Date birthdate,
	        String identifierTypeUuid, String locationUuid) {
		PatientService ps = getPatientService();
		LocationService ls = getLocationService();
		
		PatientIdentifierType idType = ps.getPatientIdentifierTypeByUuid(identifierTypeUuid);
		if (idType == null) {
			throw new IllegalArgumentException("PatientIdentifierType not found: " + identifierTypeUuid);
		}
		
		Location idLocation = ls.getLocationByUuid(locationUuid);
		if (idLocation == null) {
			throw new IllegalArgumentException("Location not found: " + locationUuid);
		}
		
		IdentifierSourceService idSourceService = null;
		try {
			idSourceService = Context.getService(IdentifierSourceService.class);
		}
		catch (Exception e) {
			log.error("IdentifierSourceService not available. Is the idgen module installed?", e);
			throw new IllegalStateException(
			        "Identifier generation service not available; please install and configure idgen module");
		}
		
		org.openmrs.module.idgen.AutoGenerationOption option = idSourceService.getAutoGenerationOption(idType);
		IdentifierSource source = option != null ? option.getSource() : null;
		if (source == null) {
			throw new IllegalStateException("No IdentifierSource configured for identifier type: " + idType.getName()
			        + "; configure AutoGenerationOption in idgen");
		}
		
		String generatedIdentifier = idSourceService.generateIdentifier(source, null);
		if (generatedIdentifier == null || generatedIdentifier.trim().isEmpty()) {
			throw new IllegalStateException("Failed to generate identifier for type: " + idType.getName());
		}
		
		return createPatient(givenName, familyName, gender, birthdate, generatedIdentifier, identifierTypeUuid,
		    locationUuid);
	}
	
	public Patient getOrCreateByIdentifier(String identifierValue, String identifierTypeUuid, String locationUuid,
	        String givenName, String familyName, String gender, Date birthdate) {
		
		PatientService ps = getPatientService();
		PatientIdentifierType type = ps.getPatientIdentifierTypeByUuid(identifierTypeUuid);
		
		if (type == null) {
			throw new IllegalArgumentException("PatientIdentifierType not found: " + identifierTypeUuid);
		}
		
		List<Patient> existingPatients = ps.getPatients(null, identifierValue, Collections.singletonList(type), true);
		Patient existing = existingPatients.stream().findFirst().orElse(null);
		
		if (existing != null) {
			log.debug("Found existing patient with identifier: {}", identifierValue);
			return existing;
		}
		return createPatient(givenName, familyName, gender, birthdate, identifierValue, identifierTypeUuid, locationUuid);
	}
	
	public String getDefaultIdentifierTypeUuid() {
		PatientService ps = getPatientService();
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
	
	public String getDefaultLocationUuid() {
		LocationService ls = getLocationService();
		Location defaultLocation = ls.getDefaultLocation();
		
		if (defaultLocation == null) {
			throw new IllegalStateException("No default location configured in system");
		}
		
		return defaultLocation.getUuid();
	}
}
