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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.api.FormService;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.resolver.O3JsonFormResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.List;

@Controller("dataimport.DataImportController")
@RequestMapping(value = "/module/dataimport/dataimport.form")
public class DataImportController {
	
	private static final Log log = LogFactory.getLog(DataImportController.class);
	
	private static final String VIEW = "/module/dataimport/dataimport";
	
	@Autowired
	private O3JsonFormResolver o3JsonFormResolver;
	
	@RequestMapping(method = RequestMethod.GET)
	public String showPage(Model model) {
		FormService formService = Context.getFormService();
		LocationService locationService = Context.getLocationService();
		
		List<Form> allForms = formService.getAllForms(false);
		List<Form> o3Forms = filterO3Forms(allForms);
		List<Location> locations = locationService.getAllLocations(false);
		
		log.info("DataImportController - Fetched " + (allForms != null ? allForms.size() : 0) + " total forms, filtered to "
		        + o3Forms.size() + " O3 forms");
		log.info("DataImportController - Fetched " + (locations != null ? locations.size() : 0) + " locations");
		
		model.addAttribute("forms", o3Forms);
		model.addAttribute("locations", locations);
		
		return VIEW;
	}
	
	private List<Form> filterO3Forms(List<Form> allForms) {
		List<Form> o3Forms = new ArrayList<>();
		
		if (allForms == null || allForms.isEmpty()) {
			return o3Forms;
		}
		
		for (Form form : allForms) {
			try {
				if (o3JsonFormResolver.canResolve(form.getUuid())) {
					o3Forms.add(form);
					log.debug("Including O3 form: " + form.getName() + " (" + form.getUuid() + ")");
				} else {
					log.debug("Excluding non-O3 form: " + form.getName() + " (" + form.getUuid() + ")");
				}
			}
			catch (Exception e) {
				log.debug("Form " + form.getUuid() + " is not an O3 form: " + e.getMessage());
			}
		}
		
		return o3Forms;
	}
}
