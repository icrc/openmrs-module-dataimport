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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Options for Excel template generation. Supports multi-location selection for encounter location
 * dropdowns.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateOptions {
	
	public boolean includePatient = true;
	
	/**
	 * Default location UUID for encounters. Used when encounterLocation cell is empty.
	 */
	public String locationUuid = "";
	
	/**
	 * List of allowed location UUIDs for the encounterLocation dropdown. When empty, all non-retired
	 * locations are included.
	 */
	public List<String> locationUuids = new ArrayList<>();
	
	/**
	 * Filter locations by tag name (e.g., "Visit Location"). Applied during template generation to
	 * limit dropdown options.
	 */
	public String locationTagFilter = null;
	
	/**
	 * If true, import validation will reject locations not in the allowed list. If false, any valid
	 * location is accepted (backward compatible behavior).
	 */
	public boolean restrictToAllowedLocations = false;
	
	/**
	 * If true, visits created during import will be automatically closed after all encounters are
	 * imported. The visit end datetime will be set to the datetime of the last encounter in the visit.
	 * Default is false (visits remain open).
	 */
	public boolean autoCloseVisits = false;
	
	public String filename = "";
	
	/**
	 * Get the effective default location UUID. Returns locationUuid if set, otherwise returns the first
	 * item from locationUuids, or null if no locations are configured.
	 *
	 * @return the effective default location UUID, or null if none configured
	 */
	public String getEffectiveDefaultLocation() {
		if (locationUuid != null && !locationUuid.isEmpty()) {
			return locationUuid;
		}
		if (locationUuids != null && !locationUuids.isEmpty()) {
			return locationUuids.get(0);
		}
		return null;
	}
	
	/**
	 * Get all allowed locations including both locationUuid and locationUuids. Uses LinkedHashSet to
	 * maintain order while removing duplicates. locationUuid is added first to preserve its priority as
	 * the default.
	 *
	 * @return list of all allowed location UUIDs without duplicates
	 */
	public List<String> getAllAllowedLocations() {
		Set<String> all = new LinkedHashSet<>();
		if (locationUuid != null && !locationUuid.isEmpty()) {
			all.add(locationUuid);
		}
		if (locationUuids != null) {
			all.addAll(locationUuids);
		}
		return new ArrayList<>(all);
	}
}
