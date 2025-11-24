/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.resolver;

import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.FormType;

import java.util.Locale;

/**
 * Service Provider Interface (SPI) for resolving and parsing different form types into the unified
 * Excel Template Model (ETM). Implementations should handle: - Detecting if a form is compatible
 * with this resolver - Fetching the form definition (JSON schema, HFE XML, etc.) - Parsing the form
 * into FormModel (ETM) - Extracting metadata (name, version, encounter type)
 */
public interface FormResolver {
	
	/**
	 * @return The form type this resolver handles
	 */
	FormType getFormType();
	
	/**
	 * Check if a form has a compatible resource that this resolver can handle.
	 * 
	 * @param formUuid The form UUID
	 * @return true if this resolver can handle this form
	 */
	boolean canResolve(String formUuid);
	
	/**
	 * Fetch and parse the form into the Excel Template Model (ETM).
	 * 
	 * @param formUuid The form UUID
	 * @param locale Optional locale for label resolution (can be null)
	 * @return Parsed FormModel (ETM)
	 * @throws org.openmrs.api.APIException if form cannot be resolved or parsed
	 */
	FormModel resolve(String formUuid, Locale locale);
	
	/**
	 * Get the form name without fully resolving/parsing the form.
	 * 
	 * @param formUuid The form UUID
	 * @return The form name, or null if not found
	 */
	String getFormName(String formUuid);
	
	/**
	 * Get the form version without fully resolving/parsing the form.
	 * 
	 * @param formUuid The form UUID
	 * @return The form version, or null if not found
	 */
	String getFormVersion(String formUuid);
}
