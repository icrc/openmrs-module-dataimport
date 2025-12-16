/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.util;

import org.openmrs.Location;
import org.openmrs.module.dataimport.model.QuarantineItem;

import java.util.Map;
import java.util.Set;

/**
 * Utility class for validating location UUIDs during import.
 * Supports the multi-location feature with per-row location validation,
 * allowed location enforcement, and retired location detection.
 */
public class LocationValidation {

	/** Error code for invalid or non-existent location */
	public static final String INVALID_LOCATION = "INVALID_LOCATION";

	/** Error code for location not in the allowed list */
	public static final String LOCATION_NOT_ALLOWED = "LOCATION_NOT_ALLOWED";

	/**
	 * Validates a location UUID from an Excel cell.
	 *
	 * @param locationUuid the location UUID from the cell (may be null or empty)
	 * @param locationCache map of location UUID to Location objects (pre-loaded)
	 * @param allowedLocationUuids set of allowed location UUIDs (empty means all allowed)
	 * @param restrictToAllowedLocations if true, reject locations not in allowed list
	 * @param defaultLocationUuid fallback location when cell is empty
	 * @return validation result containing status, resolved UUID, and error details
	 */
	public static LocationValidationResult validateLocation(
			String locationUuid,
			Map<String, Location> locationCache,
			Set<String> allowedLocationUuids,
			boolean restrictToAllowedLocations,
			String defaultLocationUuid) {

		// Determine the effective location UUID
		String effectiveUuid = locationUuid;
		if (effectiveUuid == null || effectiveUuid.trim().isEmpty()) {
			effectiveUuid = defaultLocationUuid;
		}

		// If still empty after default fallback, return valid with null UUID
		// (let downstream handle missing location requirement)
		if (effectiveUuid == null || effectiveUuid.trim().isEmpty()) {
			return LocationValidationResult.valid(null);
		}

		effectiveUuid = effectiveUuid.trim();

		// Check if location exists in cache
		Location location = locationCache.get(effectiveUuid);
		if (location == null) {
			String message = String.format("Location '%s' does not exist or could not be found", effectiveUuid);
			return LocationValidationResult.invalid(INVALID_LOCATION, message);
		}

		// Check if location is retired
		if (location.getRetired() != null && location.getRetired()) {
			String locationName = location.getName() != null ? location.getName() : effectiveUuid;
			String message = String.format("Location '%s' (%s) is retired and cannot be used", locationName, effectiveUuid);
			return LocationValidationResult.invalid(INVALID_LOCATION, message);
		}

		// Check allowed locations list (only if restriction is enabled and list is non-empty)
		if (restrictToAllowedLocations && allowedLocationUuids != null && !allowedLocationUuids.isEmpty()) {
			if (!allowedLocationUuids.contains(effectiveUuid)) {
				String locationName = location.getName() != null ? location.getName() : effectiveUuid;
				String message = String.format(
					"Location '%s' (%s) is not in the allowed location list for this template",
					locationName, effectiveUuid
				);
				return LocationValidationResult.invalid(LOCATION_NOT_ALLOWED, message);
			}
		}

		// Location is valid
		return LocationValidationResult.valid(effectiveUuid);
	}

	/**
	 * Creates a QuarantineItem from a failed location validation result.
	 *
	 * @param result the validation result (must be invalid)
	 * @param sheetName the sheet name for the quarantine item
	 * @param rowIndex the row index (1-based)
	 * @param colIndex the column index of the location field
	 * @return QuarantineItem for the validation error, or null if result is valid
	 */
	public static QuarantineItem createQuarantineItem(
			LocationValidationResult result,
			String sheetName,
			int rowIndex,
			int colIndex) {

		if (result.isValid()) {
			return null;
		}

		return QuarantineItem.error(
			sheetName,
			rowIndex,
			colIndex,
			result.getErrorCode(),
			result.getErrorMessage()
		);
	}
}
