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

import lombok.Getter;

/**
 * Result of location validation during import.
 * Contains validation status, resolved location UUID, and error details if invalid.
 */
@Getter
public class LocationValidationResult {

	private final boolean valid;
	private final String resolvedLocationUuid;
	private final String errorCode;
	private final String errorMessage;

	private LocationValidationResult(boolean valid, String resolvedLocationUuid, String errorCode, String errorMessage) {
		this.valid = valid;
		this.resolvedLocationUuid = resolvedLocationUuid;
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
	}

	/**
	 * Creates a valid result with the resolved location UUID.
	 *
	 * @param resolvedLocationUuid the validated location UUID (may be null if location not required)
	 * @return valid result
	 */
	public static LocationValidationResult valid(String resolvedLocationUuid) {
		return new LocationValidationResult(true, resolvedLocationUuid, null, null);
	}

	/**
	 * Creates an invalid result with error details.
	 *
	 * @param errorCode the error code (e.g., INVALID_LOCATION, LOCATION_NOT_ALLOWED)
	 * @param errorMessage human-readable error message
	 * @return invalid result
	 */
	public static LocationValidationResult invalid(String errorCode, String errorMessage) {
		return new LocationValidationResult(false, null, errorCode, errorMessage);
	}
}
