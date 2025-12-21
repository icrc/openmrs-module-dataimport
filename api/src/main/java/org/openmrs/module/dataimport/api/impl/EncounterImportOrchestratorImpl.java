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

import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.service.EncounterImportOrchestrator;
import org.openmrs.module.dataimport.api.service.PatientImportService;
import org.openmrs.module.dataimport.api.storage.IdempotencyService;
import org.openmrs.module.dataimport.model.ImportPlan;
import org.openmrs.module.dataimport.model.ObsBlueprint;
import org.openmrs.module.dataimport.model.RowPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EncounterImportOrchestratorImpl implements EncounterImportOrchestrator {
	
	private static final Logger log = LoggerFactory.getLogger(EncounterImportOrchestratorImpl.class);
	
	private final IdempotencyService idempotencyService;
	
	private final EncounterServiceImpl encounterService;
	
	private final ObservationServiceImpl obsService;
	
	private final VisitImportServiceImpl visitImportService;
	
	@Autowired
	public EncounterImportOrchestratorImpl(IdempotencyService idempotencyService, EncounterServiceImpl encounterService,
	    ObservationServiceImpl obsService, VisitImportServiceImpl visitImportService) {
		this.idempotencyService = idempotencyService;
		this.encounterService = encounterService;
		this.obsService = obsService;
		this.visitImportService = visitImportService;
	}
	
	@Override
	public EncounterProcessingOutcome processEncounters(ImportPlan plan,
	        PatientImportService.PatientCreationOutcome patientCreationOutcome) {
		Map<String, String> refToPatientUuid = patientCreationOutcome.refToPatientUuid;
		Map<String, String> visitCache = new HashMap<>();
		Map<String, Date> visitLastEncounterDatetime = new HashMap<>();
		
		List<String> createdEncounterUuids = new ArrayList<>();
		List<String> errors = new ArrayList<>(patientCreationOutcome.errors);
		
		int failedEncounters = 0;
		int failedObs = 0;
		int skippedRows = 0;
		
		for (RowPlan rp : plan.getRowsOk()) {
			if (idempotencyService.exists(rp.getIdempotencyKey())) {
				continue;
			}
			
			Patient patient = resolvePatientForRow(rp, patientCreationOutcome, refToPatientUuid);
			if (patient == null) {
				skippedRows++;
				continue;
			}
			
			Visit visit = resolveOrCreateVisitForRow(patient, rp, visitCache, errors);
			Encounter enc = createEncounterForRow(patient, rp, visit, createdEncounterUuids, errors);
			if (enc == null) {
				failedEncounters++;
				continue;
			}
			
			// Track the last encounter datetime for this visit
			if (visit != null && enc.getEncounterDatetime() != null) {
				String visitUuid = visit.getUuid();
				Date currentLastDatetime = visitLastEncounterDatetime.get(visitUuid);
				if (currentLastDatetime == null || enc.getEncounterDatetime().after(currentLastDatetime)) {
					visitLastEncounterDatetime.put(visitUuid, enc.getEncounterDatetime());
				}
			}
			
			failedObs += processObsForEncounter(enc, rp);
			idempotencyService.record(rp.getIdempotencyKey(), enc.getUuid());
		}
		
		// Close visits if auto-close is enabled in template options
		closeVisitsIfEnabled(plan, visitLastEncounterDatetime);
		
		return new EncounterProcessingOutcome(createdEncounterUuids, errors, failedEncounters, failedObs, skippedRows);
	}
	
	private Patient resolvePatientForRow(RowPlan rp, PatientImportService.PatientCreationOutcome creationOutcome,
	        Map<String, String> refToPatientUuid) {
		if (creationOutcome.failedPatientRefs.contains(rp.getPatientRef())) {
			return null;
		}
		
		String patientUuid = refToPatientUuid.getOrDefault(rp.getPatientRef(), rp.getPatientUuid());
		if (patientUuid == null) {
			return null;
		}
		
		return Context.getPatientService().getPatientByUuid(patientUuid);
	}
	
	private Visit resolveOrCreateVisitForRow(Patient patient, RowPlan rp, Map<String, String> visitCache,
	        List<String> commitErrors) {
		try {
			String visitKey = generateVisitKey(rp);
			String cachedVisitUuid = visitCache.get(visitKey);
			if (cachedVisitUuid != null) {
				org.openmrs.api.VisitService visitService = Context.getVisitService();
				Visit cachedVisit = visitService.getVisitByUuid(cachedVisitUuid);
				if (cachedVisit != null) {
					return cachedVisit;
				}
				visitCache.remove(visitKey);
			}
			
			java.time.Instant encounterInstant = rp.getEncounter().getEncounterDatetime();
			Date encounterDate = encounterInstant != null ? Date.from(encounterInstant) : new Date();
			
			String locationUuid = rp.getEncounter().getLocationUuid();
			if (locationUuid == null || locationUuid.trim().isEmpty()) {
				locationUuid = encounterService.getDefaultLocationUuid();
			}
			
			Visit visit = visitImportService.findReusableOpenVisit(patient.getUuid(), locationUuid, encounterDate);
			if (visit == null) {
				visit = visitImportService.createVisit(patient.getUuid(), null, locationUuid, encounterDate);
			}
			
			visitCache.put(visitKey, visit.getUuid());
			
			return visit;
		}
		catch (Exception e) {
			commitErrors.add(String.format("Row %d (sheet %s): Visit creation failed: %s", rp.getRowIndex(),
			    rp.getSheetName(), e.getMessage()));
			return null;
		}
	}
	
	private String generateVisitKey(RowPlan rp) {
		String patientRef = rp.getPatientRef();
		String locationUuid = rp.getEncounter().getLocationUuid();
		if (locationUuid == null || locationUuid.trim().isEmpty()) {
			locationUuid = "default";
		}
		
		java.time.Instant encounterInstant = rp.getEncounter().getEncounterDatetime();
		String dateKey;
		if (encounterInstant != null) {
			java.time.LocalDate date = java.time.LocalDateTime.ofInstant(encounterInstant, java.time.ZoneId.systemDefault())
			        .toLocalDate();
			dateKey = date.toString();
		} else {
			dateKey = "today";
		}
		
		return patientRef + "|" + locationUuid + "|" + dateKey;
	}
	
	private Encounter createEncounterForRow(Patient patient, RowPlan rp, Visit visit, List<String> createdEncounterUuids,
	        List<String> commitErrors) {
		try {
			Encounter enc = encounterService.createEncounterFromBlueprint(patient, rp.getEncounter(), visit);
			createdEncounterUuids.add(enc.getUuid());
			return enc;
		}
		catch (Exception e) {
			commitErrors.add(String.format("Row %d (sheet %s): Encounter creation failed: %s", rp.getRowIndex(),
			    rp.getSheetName(), e.getMessage()));
			return null;
		}
	}
	
	private int processObsForEncounter(Encounter enc, RowPlan rp) {
		int failedObsCount = 0;
		for (ObsBlueprint obsBlueprint : rp.getObs()) {
			try {
				Obs obs = obsService.createObsFromBlueprint(enc, obsBlueprint);
				if (obs != null) {
					log.debug("Created obs: {} = {}", obs.getConcept().getName().getName(),
					    obs.getValueAsString(Context.getLocale()));
				}
			}
			catch (Exception e) {
				failedObsCount++;
				log.warn("Failed to create obs for row {}: {}", rp.getRowIndex(), e.getMessage());
			}
		}
		return failedObsCount;
	}
	
	private void closeVisitsIfEnabled(ImportPlan plan, Map<String, Date> visitLastEncounterDatetime) {
		if (visitLastEncounterDatetime.isEmpty()) {
			return;
		}
		
		// Check autoCloseVisits flag from template metadata
		boolean autoClose = plan.getTemplateMeta() != null && plan.getTemplateMeta().autoCloseVisits;
		
		if (!autoClose) {
			log.debug("Auto-close visits is disabled in template options. {} visit(s) will remain open.",
			    visitLastEncounterDatetime.size());
			return;
		}
		
		log.info("Auto-close visits is enabled in template options. Closing {} visit(s)...",
		    visitLastEncounterDatetime.size());
		
		for (Map.Entry<String, Date> entry : visitLastEncounterDatetime.entrySet()) {
			String visitUuid = entry.getKey();
			Date endDatetime = entry.getValue();
			
			try {
				visitImportService.closeVisit(visitUuid, endDatetime);
				log.debug("Successfully closed visit {} with end datetime {}", visitUuid, endDatetime);
			}
			catch (Exception e) {
				log.error("Failed to close visit {}: {}", visitUuid, e.getMessage(), e);
			}
		}
		
		log.info("Visit closing completed. Closed {} visit(s).", visitLastEncounterDatetime.size());
	}
}
