/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.service;

import org.openmrs.annotation.Authorized;
import org.openmrs.module.dataimport.DataimportConfig;
import org.openmrs.module.dataimport.model.TemplateOptions;

import java.util.List;

public interface TemplateGenerationService {
	
	/**
	 * Generates Excel templates from a list of form UUIDs.
	 * 
	 * @param formUuids List of form UUIDs to include in the template
	 * @param opts Template generation options (locale, patient/visit sheets, etc.)
	 * @return Excel workbook as byte array
	 * @throws org.openmrs.api.APIException if form not found or has no schema
	 */
	@Authorized(DataimportConfig.MODULE_PRIVILEGE)
	byte[] generateFromFormUuids(List<String> formUuids, TemplateOptions opts);
	
}
