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

@Getter
public final class ImportStats {
	
	private final int totalRows;
	
	private final int okRows;
	
	private final int quarantinedRows;
	
	private final int patientsToCreate;
	
	private final int encountersToCreate;
	
	private final int obsToCreate;
	
	public ImportStats(int totalRows, int okRows, int quarantinedRows, int patientsToCreate, int encountersToCreate,
	    int obsToCreate) {
		this.totalRows = totalRows;
		this.okRows = okRows;
		this.quarantinedRows = quarantinedRows;
		this.patientsToCreate = patientsToCreate;
		this.encountersToCreate = encountersToCreate;
		this.obsToCreate = obsToCreate;
	}
	
	public static ImportStats of(java.util.List<RowPlan> okRows, java.util.List<QuarantineItem> issues) {
		int totalRows = okRows.size() + issues.size();
		int encountersToCreate = okRows.size();
		int obsToCreate = okRows.stream().mapToInt(r -> r.getObs().size()).sum();
		// TODO: Calculate patientsToCreate based on patient resolution
		int patientsToCreate = 0;
		
		return new ImportStats(totalRows, okRows.size(), issues.size(), patientsToCreate, encountersToCreate, obsToCreate);
	}
}
