/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.impl;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.dataimport.api.service.VisitImportService;
import org.openmrs.module.dataimport.api.util.DataImportConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

public class VisitImportServiceImpl extends BaseOpenmrsService implements VisitImportService {
	
	private static final Logger log = LoggerFactory.getLogger(VisitImportServiceImpl.class);
	
	private static final long DEFAULT_VISIT_REUSE_WINDOW_MS = 24 * 60 * 60 * 1000L;
	
	private org.openmrs.api.PatientService getPatientService() {
		return Context.getPatientService();
	}
	
	private org.openmrs.api.VisitService getVisitService() {
		return Context.getVisitService();
	}
	
	private LocationService getLocationService() {
		return Context.getLocationService();
	}
	
	@Override
	public Visit createVisit(String patientUuid, String visitTypeUuid, String locationUuid, Date startDatetime) {
		PatientService patientService = getPatientService();
		VisitService visitService = getVisitService();
		LocationService locationService = getLocationService();
		
		Patient patient = patientService.getPatientByUuid(patientUuid);
		if (patient == null) {
			throw new IllegalArgumentException("Patient not found: " + patientUuid);
		}
		
		VisitType visitType = null;
		if (visitTypeUuid != null && !visitTypeUuid.trim().isEmpty()) {
			visitType = visitService.getVisitTypeByUuid(visitTypeUuid);
			if (visitType == null) {
				log.warn("VisitType not found: {}, falling back to default", visitTypeUuid);
			}
		}
		if (visitType == null) {
			visitType = getDefaultVisitType();
		}
		
		Location location = null;
		if (locationUuid != null && !locationUuid.trim().isEmpty()) {
			location = locationService.getLocationByUuid(locationUuid);
			if (location == null) {
				log.warn("Location not found: {}, falling back to default", locationUuid);
			}
		}
		if (location == null) {
			location = locationService.getDefaultLocation();
			if (location == null) {
				throw new IllegalStateException("No default location configured in system");
			}
		}
		
		Date visitStart = startDatetime != null ? startDatetime : new Date();
		Visit visit = new Visit();
		visit.setPatient(patient);
		visit.setVisitType(visitType);
		visit.setLocation(location);
		visit.setStartDatetime(visitStart);
		visit = visitService.saveVisit(visit);
		return visit;
	}
	
	@Override
	public Visit closeVisit(String visitUuid, Date endDatetime) {
		VisitService visitService = getVisitService();
		
		Visit visit = visitService.getVisitByUuid(visitUuid);
		if (visit == null) {
			throw new IllegalArgumentException("Visit not found: " + visitUuid);
		}
		
		if (endDatetime == null) {
			throw new IllegalArgumentException("End datetime cannot be null");
		}
		
		if (visit.getStopDatetime() == null) {
			visit.setStopDatetime(endDatetime);
			visit = visitService.saveVisit(visit);
			log.debug("Closed visit {} with end datetime {}", visitUuid, endDatetime);
		} else {
			log.debug("Visit {} is already closed with end datetime {}", visitUuid, visit.getStopDatetime());
		}
		
		return visit;
	}
	
	public Visit findReusableOpenVisit(String patientUuid, String locationUuid, Date encounterDatetime) {
		return findReusableOpenVisit(patientUuid, locationUuid, encounterDatetime, DEFAULT_VISIT_REUSE_WINDOW_MS);
	}
	
	public Visit findReusableOpenVisit(String patientUuid, String locationUuid, Date encounterDatetime, long windowMs) {
		PatientService patientService = getPatientService();
		VisitService visitService = getVisitService();
		LocationService locationService = getLocationService();
		
		Patient patient = patientService.getPatientByUuid(patientUuid);
		if (patient == null) {
			return null;
		}
		
		Location location = null;
		if (locationUuid != null && !locationUuid.trim().isEmpty()) {
			location = locationService.getLocationByUuid(locationUuid);
		}
		
		List<Visit> activeVisits = visitService.getActiveVisitsByPatient(patient);
		Date checkTime = encounterDatetime != null ? encounterDatetime : new Date();
		Date windowStart = new Date(checkTime.getTime() - windowMs);
		
		for (Visit visit : activeVisits) {
			if (visit.getStartDatetime() != null && visit.getStartDatetime().before(windowStart)) {
				continue;
			}
			
			if (location != null && visit.getLocation() != null) {
				if (visit.getLocation().equals(location)) {
					return visit;
				}
			} else {
				log.debug("Found reusable open visit: {} for patient: {} (no location match required)", visit.getUuid(),
				    patientUuid);
				return visit;
			}
		}
		
		if (location != null) {
			for (Visit visit : activeVisits) {
				if (visit.getStartDatetime() != null && !visit.getStartDatetime().before(windowStart)) {
					return visit;
				}
			}
		}
		
		return null;
	}
	
	public Visit ensureVisit(String patientUuid, String visitTypeUuid, String locationUuid, Date encounterDatetime) {
		Visit openVisit = findReusableOpenVisit(patientUuid, locationUuid, encounterDatetime);
		if (openVisit != null) {
			return openVisit;
		}
		
		return createVisit(patientUuid, visitTypeUuid, locationUuid, encounterDatetime);
	}
	
	private VisitType getDefaultVisitType() {
		org.openmrs.api.AdministrationService adminService = Context.getAdministrationService();
		String gp = adminService.getGlobalProperty("dataimport.defaultVisitTypeUuid");
		
		if (gp != null && !gp.trim().isEmpty()) {
			VisitType type = getVisitService().getVisitTypeByUuid(gp);
			if (type != null) {
				return type;
			}
		}
		
		VisitType dataImportVisitType = getVisitService()
		        .getVisitTypeByUuid(DataImportConstants.DATA_IMPORT_VISIT_TYPE_UUID);
		if (dataImportVisitType != null) {
			return dataImportVisitType;
		}
		
		List<VisitType> allTypes = getVisitService().getAllVisitTypes();
		if (allTypes.isEmpty()) {
			throw new IllegalStateException("No VisitTypes configured in system");
		}
		
		return allTypes.get(0);
	}
}
