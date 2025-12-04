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

import org.openmrs.Patient;
import org.openmrs.module.dataimport.api.reader.ExcelReader;
import org.openmrs.module.dataimport.api.service.PatientImportService;
import org.openmrs.module.dataimport.model.ImportPlan;
import org.openmrs.module.dataimport.model.RowPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PatientImportServiceImpl implements PatientImportService {

	private static final Logger log = LoggerFactory.getLogger(PatientImportServiceImpl.class);

	private final PatientServiceImpl patientService;

	@Autowired
	public PatientImportServiceImpl(PatientServiceImpl patientService) {
		this.patientService = patientService;
	}

	@Override
	public PatientCreationOutcome createPatientsFromPlan(ImportPlan plan) {
		Map<String, String> refToPatientUuid = new HashMap<>();
		Set<String> failedPatientRefs = new HashSet<>();
		List<String> errors = new ArrayList<>();

		String defaultIdentifierTypeUuid = patientService.getDefaultIdentifierTypeUuid();
		String defaultLocationUuid = patientService.getDefaultLocationUuid();
		Set<String> blockedRefs = plan.getBlockedPatientRefs() != null ? plan.getBlockedPatientRefs()
		        : Collections.emptySet();

		Map<String, ExcelReader.PatientRow> patientsToCreate = extractPatientsToCreate(plan);

		for (ExcelReader.PatientRow patientRow : patientsToCreate.values()) {
			if (blockedRefs.contains(patientRow.patientRef())) {
				String errorMsg = "Skipped creation for patient '" + patientRow.patientRef()
				        + "' because related rows were quarantined";
				failedPatientRefs.add(patientRow.patientRef());
				errors.add(errorMsg);
				continue;
			}

			try {
				Patient patient = createPatient(patientRow, defaultIdentifierTypeUuid, defaultLocationUuid);
				refToPatientUuid.put(patientRow.patientRef(), patient.getUuid());
			}
			catch (Exception e) {
				String errorMsg = "Failed to create patient '" + patientRow.patientRef() + "': " + e.getMessage();
				failedPatientRefs.add(patientRow.patientRef());
				errors.add(errorMsg);
			}
		}

		return new PatientCreationOutcome(refToPatientUuid, failedPatientRefs, errors);
	}

	private Patient createPatient(ExcelReader.PatientRow patientRow, String defaultIdentifierTypeUuid,
	        String defaultLocationUuid) {
		Date birthdate = null;
		if (patientRow.birthdate != null) {
			birthdate = Date.from(patientRow.birthdate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
		}

		return patientService.createPatientWithGeneratedIdentifier(patientRow.givenName, patientRow.familyName,
		    patientRow.gender, birthdate, defaultIdentifierTypeUuid, defaultLocationUuid, patientRow.addressLine1,
		    patientRow.cityVillage, patientRow.country, patientRow.phone);
	}

	private Map<String, ExcelReader.PatientRow> extractPatientsToCreate(ImportPlan plan) {
		Map<String, ExcelReader.PatientRow> patientsToCreate = new HashMap<>();
		Set<String> blockedRefs = plan.getBlockedPatientRefs() != null ? plan.getBlockedPatientRefs()
		        : Collections.emptySet();

		for (RowPlan rowPlan : plan.getRowsOk()) {
			String patientRef = rowPlan.getPatientRef();
			if (patientRef != null && !blockedRefs.contains(patientRef) && !patientsToCreate.containsKey(patientRef)) {
				ExcelReader.PatientRow patientRow = getPatientFromRegistrationSheet(patientRef, plan);
				if (patientRow != null) {
					patientsToCreate.put(patientRef, patientRow);
				}
			}
		}

		return patientsToCreate;
	}

	private ExcelReader.PatientRow getPatientFromRegistrationSheet(String patientRef, ImportPlan plan) {
		if (plan.getPatientIndex() == null) {
			return null;
		}

		ExcelReader.PatientResolution resolution = plan.getPatientIndex().resolve(patientRef);
		if (resolution.isResolved() && resolution.needsCreation()) {
			return resolution.getPatientRow();
		}

		return null;
	}
}
