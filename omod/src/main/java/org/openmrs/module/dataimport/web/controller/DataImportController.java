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

import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.api.FormService;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

@Controller("dataimport.DataImportController")
@RequestMapping(value = "/module/dataimport/dataimport.form")
public class DataImportController {
	
	private static final String VIEW = "/module/dataimport/dataimport";
	
	@RequestMapping(method = RequestMethod.GET)
	public String showPage(Model model) {
		return VIEW;
	}
	
	@ModelAttribute("forms")
	protected List<Form> getForms() {
		FormService formService = Context.getFormService();
		return formService.getAllForms(false);
	}
	
	@ModelAttribute("locations")
	protected List<Location> getLocations() {
		LocationService locationService = Context.getLocationService();
		return locationService.getAllLocations(false);
	}
}
