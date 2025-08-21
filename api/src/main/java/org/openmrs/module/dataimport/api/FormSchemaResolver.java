/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api;

public interface FormSchemaResolver {
	
	/**
	 * Fetches the O3 form JSON schema for a given form UUID.
	 * 
	 * @param formUuid The form UUID
	 * @return JSON schema string
	 * @throws org.openmrs.api.APIException if form not found or has no schema
	 */
	String fetchSchemaJsonByUuid(String formUuid);
	
	/**
	 * Fetches the O3 form JSON schema with optional translation support.
	 */
	String fetchSchemaJsonByUuid(String formUuid, String languageCode);
	
	/**
	 * Checks if a form has a schema resource.
	 * 
	 * @param formUuid The form UUID
	 * @return true if schema resource exists, false otherwise
	 */
	boolean hasSchemaResource(String formUuid);
	
	/**
	 * Gets the form name for a given form UUID.
	 * 
	 * @param formUuid The form UUID
	 * @return Form name
	 * @throws org.openmrs.api.APIException if form not found
	 */
	String getFormName(String formUuid);
	
	/**
	 * Gets the form version for a given form UUID.
	 * 
	 * @param formUuid The form UUID
	 * @return Form version
	 * @throws org.openmrs.api.APIException if form not found
	 */
	String getFormVersion(String formUuid);
}
