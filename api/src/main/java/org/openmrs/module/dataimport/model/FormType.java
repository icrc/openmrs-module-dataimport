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

/**
 * Enumeration of supported form types for Excel template generation. Each form type requires a
 * corresponding FormResolver implementation.
 */
@Getter
public enum FormType {
	
	/**
	 * OpenMRS 3.x forms with JSON schema (default)
	 */
	O3_JSON("o3-json", "O3 Form (JSON Schema)"),
	
	/**
	 * OpenMRS 2.x HTML Form Entry (HFE) XML forms
	 */
	HFE_XML("hfe-xml", "HTML Form Entry (XML)");
	
	private final String code;
	
	private final String displayName;
	
	FormType(String code, String displayName) {
		this.code = code;
		this.displayName = displayName;
	}
	
	/**
	 * Find FormType by code (case-insensitive)
	 *
	 * @param code The form type code
	 * @return The matching FormType
	 * @throws IllegalArgumentException if code is unknown
	 */
	public static FormType fromCode(String code) {
		if (code == null || code.trim().isEmpty()) {
			throw new IllegalArgumentException("Form type code cannot be null or empty");
		}
		
		for (FormType type : values()) {
			if (type.code.equalsIgnoreCase(code.trim())) {
				return type;
			}
		}
		
		throw new IllegalArgumentException("Unknown form type code: " + code);
	}
}
