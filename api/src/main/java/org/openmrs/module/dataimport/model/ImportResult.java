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
import java.util.UUID;

@Getter
public final class ImportResult {
	
	private final boolean success;
	
	private final String message;
	
	private final UUID batchId;
	
	private final ImportStats stats;
	
	private final List<String> createdEncounterUuids;
	
	private final List<String> errors;
	
	public ImportResult(boolean success, String message, UUID batchId, ImportStats stats, List<String> createdEncounterUuids,
	    List<String> errors) {
		this.success = success;
		this.message = message;
		this.batchId = batchId;
		this.stats = stats;
		this.createdEncounterUuids = createdEncounterUuids;
		this.errors = errors;
	}
	
	public static ImportResult success(UUID batchId, ImportStats stats, List<String> createdEncounterUuids) {
		return new ImportResult(true, "Import completed successfully", batchId, stats, createdEncounterUuids, null);
	}
	
	public static ImportResult failure(UUID batchId, String message, List<String> errors) {
		return new ImportResult(false, message, batchId, null, null, errors);
	}
}
