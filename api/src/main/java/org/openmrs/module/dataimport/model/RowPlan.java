/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.model;

import lombok.Getter;

import java.util.List;

@Getter
public final class RowPlan {
	
	private final String sheetName;
	
	private final int rowIndex;
	
	private final String patientRef;
	
	private final String patientUuid;
	
	private final EncounterBlueprint encounter;
	
	private final List<ObsBlueprint> obs;
	
	private final String idempotencyKey;
	
	public RowPlan(String sheetName, int rowIndex, String patientRef, String patientUuid, EncounterBlueprint encounter,
	    List<ObsBlueprint> obs, String idempotencyKey) {
		this.sheetName = sheetName;
		this.rowIndex = rowIndex;
		this.patientRef = patientRef;
		this.patientUuid = patientUuid;
		this.encounter = encounter;
		this.obs = obs;
		this.idempotencyKey = idempotencyKey;
	}
	
}
