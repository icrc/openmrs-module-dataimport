/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.web.controller;

import org.openmrs.annotation.Authorized;
import org.openmrs.api.APIException;
import org.openmrs.module.dataimport.DataimportConfig;
import org.openmrs.module.dataimport.api.TemplateGenerationService;
import org.openmrs.module.dataimport.api.TemplateOptions;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/dataimport")
@SuppressWarnings("unused")
public class FormTemplateController extends BaseRestController {
	
	private static final Logger log = LoggerFactory.getLogger(FormTemplateController.class);
	
	@Autowired
	private TemplateGenerationService templateGenerationService;
	
	@PostMapping(value = "/forms/template.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	@Authorized(DataimportConfig.MODULE_PRIVILEGE)
	public ResponseEntity<byte[]> generateTemplateFromPost(@RequestBody TemplateRequest request) {
		if (request.formUuids == null || request.formUuids.isEmpty()) {
			throw new APIException("At least one formUuid is required");
		}
		
		TemplateOptions options = request.options != null ? request.options
		        : templateGenerationService.createDefaultOptions();
		
		byte[] bytes = templateGenerationService.generateFromFormUuids(request.formUuids, options);
		
		String filename = "o3_form_template.xlsx";
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
		        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
		        .contentLength(bytes.length).body(bytes);
	}
}
