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

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Controller for the audit log view page (Legacy UI).
 */
@Controller("dataimport.AuditLogPageController")
@RequestMapping(value = "/module/dataimport/auditlog.form")
public class AuditLogPageController {
	
	private static final String VIEW = "/module/dataimport/auditlog";
	
	@RequestMapping(method = RequestMethod.GET)
	public String showPage(Model model) {
		return VIEW;
	}
}
