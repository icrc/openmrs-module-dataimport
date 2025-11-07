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
import org.openmrs.api.AdministrationService;
import org.openmrs.api.FormService;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.EncounterBlueprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class EncounterService {
	
	private static final Logger log = LoggerFactory.getLogger(EncounterService.class);
	
	private PatientService getPatientService() {
		return Context.getPatientService();
	}
	
	private org.openmrs.api.EncounterService getEncounterService() {
		return Context.getEncounterService();
	}
	
	private LocationService getLocationService() {
		return Context.getLocationService();
	}
	
	private FormService getFormService() {
		return Context.getFormService();
	}
	
	private ProviderService getProviderService() {
		return Context.getProviderService();
	}
	
	public Encounter createEncounter(String patientUuid, String encounterTypeUuid, Date when, String locationUuid,
	        String formUuid, String providerUuid, String encounterRoleUuid) {
		
		PatientService patientService = getPatientService();
		org.openmrs.api.EncounterService encounterService = getEncounterService();
		LocationService locationService = getLocationService();
		FormService formService = getFormService();
		ProviderService providerService = getProviderService();
		
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
				log.warn("Form not found, skipping form assignment: {}", formUuid);
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
				log.warn("Provider or EncounterRole not found, skipping provider assignment: provider={}, role={}",
				    providerUuid, encounterRoleUuid);
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
		
		String encounterTypeUuid = blueprint.getEncounterTypeUuid();
		if (encounterTypeUuid == null || encounterTypeUuid.trim().isEmpty()) {
			String formUuid = blueprint.getFormUuid();
			if (formUuid != null && !formUuid.trim().isEmpty()) {
				Form form = getFormService().getFormByUuid(formUuid);
				if (form != null && form.getEncounterType() != null) {
					encounterTypeUuid = form.getEncounterType().getUuid();
				}
			}
		}
		if (encounterTypeUuid == null || encounterTypeUuid.trim().isEmpty()) {
			encounterTypeUuid = getDefaultEncounterTypeUuid();
		}
		
		String encounterRoleUuid = getDefaultEncounterRoleUuid();
		String providerUuid = blueprint.getProviderUuid();
		if (providerUuid == null || providerUuid.trim().isEmpty()) {
			try {
				org.openmrs.User user = Context.getAuthenticatedUser();
				if (user != null && user.getPerson() != null) {
					java.util.Collection<Provider> providers = getProviderService().getProvidersByPerson(user.getPerson());
					Provider p = null;
					if (providers != null && !providers.isEmpty()) {
						p = providers.iterator().next();
					}
					if (p != null) {
						providerUuid = p.getUuid();
					}
				}
			}
			catch (Exception ignored) {
				// If no provider can be resolved, proceed without setting one
			}
		}
		return createEncounter(patient.getUuid(), encounterTypeUuid, encounterDate, locationUuid, blueprint.getFormUuid(),
		    providerUuid, encounterRoleUuid);
	}
	
	public String getDefaultLocationUuid() {
		LocationService ls = getLocationService();
		Location defaultLocation = ls.getDefaultLocation();
		
		if (defaultLocation == null) {
			throw new IllegalStateException("No default location configured in system");
		}
		
		return defaultLocation.getUuid();
	}
	
	public String getDefaultEncounterTypeUuid() {
		AdministrationService adminService = Context.getAdministrationService();
		String gp = adminService.getGlobalProperty("dataimport.defaultEncounterTypeUuid");
		if (gp != null && !gp.trim().isEmpty()) {
			EncounterType type = getEncounterService().getEncounterTypeByUuid(gp);
			if (type != null) {
				return type.getUuid();
			}
		}
		throw new IllegalArgumentException(
		        "EncounterType not found and no form encounter type provided; set GP 'dataimport.defaultEncounterTypeUuid'");
	}
	
	public String getDefaultEncounterRoleUuid() {
		org.openmrs.api.EncounterService es = getEncounterService();
		
		EncounterRole unknownRole = es.getEncounterRoleByName("Unknown");
		if (unknownRole != null) {
			return unknownRole.getUuid();
		}
		
		java.util.List<EncounterRole> roles = es.getAllEncounterRoles(false);
		if (roles.isEmpty()) {
			throw new IllegalStateException("No EncounterRoles configured in system");
		}
		
		return roles.get(0).getUuid();
	}
}
