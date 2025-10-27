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
import org.openmrs.EncounterProvider;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.EncounterService;
import org.openmrs.api.FormService;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.EncounterBlueprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class EncounterSaver {
	
	private static final Logger log = LoggerFactory.getLogger(EncounterSaver.class);
	
	public Encounter createEncounter(String patientUuid, String encounterTypeUuid, Date when, String locationUuid,
	        String formUuid, String providerUuid, String encounterRoleUuid) {
		
		PatientService patientService = Context.getPatientService();
		EncounterService encounterService = Context.getEncounterService();
		LocationService locationService = Context.getLocationService();
		FormService formService = Context.getFormService();
		ProviderService providerService = Context.getProviderService();
		
		Patient patient = patientService.getPatientByUuid(patientUuid);
		if (patient == null) {
			throw new IllegalArgumentException("Patient not found: " + patientUuid);
		}
		
		EncounterType encType = encounterService.getEncounterTypeByUuid(encounterTypeUuid);
		if (encType == null) {
			throw new IllegalArgumentException("EncounterType not found: " + encounterTypeUuid);
		}
		
		Location location = locationService.getLocationByUuid(locationUuid);
		if (location == null) {
			throw new IllegalArgumentException("Location not found: " + locationUuid);
		}
		
		Encounter enc = new Encounter();
		enc.setPatient(patient);
		enc.setEncounterDatetime(when != null ? when : new Date());
		enc.setLocation(location);
		enc.setEncounterType(encType);
		
		if (formUuid != null && !formUuid.trim().isEmpty()) {
			Form form = formService.getFormByUuid(formUuid);
			if (form != null) {
				enc.setForm(form);
			} else {
				log.warn("Form not found: {}, skipping form assignment", formUuid);
			}
		}
		
		if (providerUuid != null && encounterRoleUuid != null && !providerUuid.trim().isEmpty()
		        && !encounterRoleUuid.trim().isEmpty()) {
			
			Provider provider = providerService.getProviderByUuid(providerUuid);
			EncounterRole role = encounterService.getEncounterRoleByUuid(encounterRoleUuid);
			
			if (provider != null && role != null) {
				EncounterProvider ep = new EncounterProvider();
				ep.setEncounter(enc);
				ep.setProvider(provider);
				ep.setEncounterRole(role);
				enc.getEncounterProviders().add(ep);
			} else {
				log.warn("Provider or EncounterRole not found: provider={}, role={}", providerUuid, encounterRoleUuid);
			}
		}
		
		enc = encounterService.saveEncounter(enc);
		log.info("Created encounter for patient: {} at location: {}", patientUuid, location.getName());
		
		return enc;
	}
	
	public Encounter createEncounterFromBlueprint(Patient patient, EncounterBlueprint blueprint) {
		if (patient == null) {
			throw new IllegalArgumentException("Patient cannot be null");
		}
		if (blueprint == null) {
			throw new IllegalArgumentException("EncounterBlueprint cannot be null");
		}
		
		Date encounterDate = null;
		if (blueprint.getEncounterDatetime() != null) {
			encounterDate = Date.from(blueprint.getEncounterDatetime());
		}
		
		String locationUuid = blueprint.getLocationUuid();
		if (locationUuid == null || locationUuid.trim().isEmpty()) {
			locationUuid = getDefaultLocationUuid();
		}
		
		String encounterRoleUuid = getDefaultEncounterRoleUuid();
		return createEncounter(patient.getUuid(), blueprint.getEncounterTypeUuid(), encounterDate, locationUuid,
		    blueprint.getFormUuid(), blueprint.getProviderUuid(), encounterRoleUuid);
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
			// Return a placeholder that will be resolved later when Context is available
			return "PLACEHOLDER_LOCATION_UUID";
		}
	}
	
	public String getDefaultEncounterRoleUuid() {
		try {
			EncounterService es = Context.getEncounterService();
			
			// Try to get "Unknown" role first
			EncounterRole unknownRole = es.getEncounterRoleByName("Unknown");
			if (unknownRole != null) {
				return unknownRole.getUuid();
			}
			
			// Fallback to first available role
			java.util.List<EncounterRole> roles = es.getAllEncounterRoles(false);
			if (roles.isEmpty()) {
				throw new IllegalStateException("No EncounterRoles configured in system");
			}
			
			return roles.get(0).getUuid();
		}
		catch (Exception e) {
			log.warn("Failed to get default encounter role UUID: {}", e.getMessage());
			// Return a placeholder that will be resolved later when Context is available
			return "PLACEHOLDER_ENCOUNTER_ROLE_UUID";
		}
	}
}
