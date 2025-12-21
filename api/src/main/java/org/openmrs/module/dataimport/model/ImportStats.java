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
public final class ImportStats {
	
	private final int totalRows;
	
	private final int okRows;
	
	private final int quarantinedRows;
	
	private final int patientsToCreate;
	
	private final int existingPatientsReferenced;
	
	private final int blockedPatients;
	
	private final int encountersToCreate;
	
	private final int obsToCreate;
	
	private final List<SheetStats> sheetSummaries;
	
	public ImportStats(int totalRows, int okRows, int quarantinedRows, int patientsToCreate, int existingPatientsReferenced,
	    int blockedPatients, int encountersToCreate, int obsToCreate, List<SheetStats> sheetSummaries) {
		this.totalRows = totalRows;
		this.okRows = okRows;
		this.quarantinedRows = quarantinedRows;
		this.patientsToCreate = patientsToCreate;
		this.existingPatientsReferenced = existingPatientsReferenced;
		this.blockedPatients = blockedPatients;
		this.encountersToCreate = encountersToCreate;
		this.obsToCreate = obsToCreate;
		this.sheetSummaries = sheetSummaries;
	}
	
	public static ImportStats of(List<RowPlan> okRows, List<QuarantineItem> issues,
	        java.util.Set<String> blockedPatientRefs) {
		// Count only ERROR severity issues as quarantined (warnings don't block rows)
		long errorCount = issues.stream().filter(QuarantineItem::isError).count();
		int quarantinedRows = (int) errorCount;
		
		int totalRows = okRows.size() + issues.size();
		int encountersToCreate = okRows.size();
		int obsToCreate = okRows.stream().mapToInt(r -> r.getObs().size()).sum();
		
		java.util.Set<String> newPatientRefs = new java.util.HashSet<>();
		java.util.Set<String> existingPatientUuids = new java.util.HashSet<>();
		for (RowPlan row : okRows) {
			String ref = row.getPatientRef();
			if (ref == null || ref.trim().isEmpty()) {
				continue;
			}
			String uuid = row.getPatientUuid();
			if (uuid == null || uuid.trim().isEmpty()) {
				newPatientRefs.add(ref);
			} else {
				existingPatientUuids.add(uuid);
			}
		}
		
		java.util.Map<String, SheetStats.Builder> sheetMap = new java.util.LinkedHashMap<>();
		for (RowPlan row : okRows) {
			sheetMap.computeIfAbsent(row.getSheetName(), SheetStats.Builder::new).incrementOk();
		}
		// Only count ERROR severity issues as quarantined in sheet stats
		for (QuarantineItem issue : issues) {
			if (issue.isError()) {
				sheetMap.computeIfAbsent(issue.getSheet(), SheetStats.Builder::new).incrementQuarantined();
			}
		}
		List<SheetStats> sheetSummaries = sheetMap.values().stream().map(SheetStats.Builder::build)
		        .sorted(java.util.Comparator.comparing(SheetStats::getSheetName))
		        .collect(java.util.stream.Collectors.toList());
		
		int blockedPatients = blockedPatientRefs != null ? blockedPatientRefs.size() : 0;
		
		int patientsToCreate = newPatientRefs.size();
		int existingPatientsReferenced = existingPatientUuids.size();
		
		return new ImportStats(totalRows, okRows.size(), quarantinedRows, patientsToCreate, existingPatientsReferenced,
		        blockedPatients, encountersToCreate, obsToCreate, sheetSummaries);
	}
}
