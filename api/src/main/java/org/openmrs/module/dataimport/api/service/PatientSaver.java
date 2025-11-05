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
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.IdentifierSource;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	
	private PersonService getPersonService() {
		return Context.getPersonService();
	}
	
	private AdministrationService getAdministrationService() {
		return Context.getAdministrationService();
	}
	
	public Patient createPatientWithGeneratedIdentifier(String givenName, String familyName, String gender, Date birthdate,
	        String identifierTypeUuid, String locationUuid, String addressLine1, String cityVillage, String country,
	        String phone) {
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
		    locationUuid, addressLine1, cityVillage, country, phone);
	}
	
	private void addPhoneAttributeIfPresent(Person person, String phone) {
		if (phone == null || phone.trim().isEmpty()) {
			return;
		}
		PersonAttributeType pat = resolvePhoneAttributeType();
		if (pat == null) {
			log.warn("Phone PersonAttributeType not configured; skipping phone attribute");
			return;
		}
		PersonAttribute attr = new PersonAttribute();
		attr.setAttributeType(pat);
		attr.setValue(phone);
		person.addAttribute(attr);
	}
	
	private PersonAttributeType resolvePhoneAttributeType() {
		AdministrationService as = getAdministrationService();
		String gp = null;
		try {
			gp = as.getGlobalProperty("dataimport.phoneAttributeTypeUuid");
		}
		catch (Exception ignored) {}
		if (gp != null && !gp.trim().isEmpty()) {
			return getPersonService().getPersonAttributeTypeByUuid(gp);
		}
		return null;
	}
	
	public Patient createPatient(String givenName, String familyName, String gender, Date birthdate, String identifierValue,
	        String identifierTypeUuid, String locationUuid, String addressLine1, String cityVillage, String country,
	        String phone) {
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
		
		if ((addressLine1 != null && !addressLine1.trim().isEmpty())
		        || (cityVillage != null && !cityVillage.trim().isEmpty()) || (country != null && !country.trim().isEmpty())) {
			PersonAddress address = new PersonAddress();
			address.setAddress1(addressLine1);
			address.setCityVillage(cityVillage);
			address.setCountry(country);
			person.addAddress(address);
		}
		
		addPhoneAttributeIfPresent(person, phone);
		
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
		log.info("Created patient with address and phone: {} {} [{}]", givenName, familyName, identifierValue);
		return patient;
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
