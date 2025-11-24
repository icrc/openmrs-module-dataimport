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

import org.openmrs.Form;
import org.openmrs.FormResource;
import org.openmrs.api.APIException;
import org.openmrs.api.DatatypeService;
import org.openmrs.api.FormService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.ClobDatatypeStorage;
import org.openmrs.module.dataimport.api.impl.HfeFormParser;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.FormType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.Locale;

public class HfeFormResolver implements FormResolver {
	
	private static final Logger log = LoggerFactory.getLogger(HfeFormResolver.class);
	
	private static final String HFE_RESOURCE_NAME = "htmlform";
	
	private final HfeFormParser parser;
	
	@Autowired
	public HfeFormResolver(HfeFormParser parser) {
		this.parser = parser;
	}
	
	@Override
	public FormType getFormType() {
		return FormType.HFE_XML;
	}
	
	@Override
	public boolean canResolve(String formUuid) {
		try {
			String xml = fetchHfeXml(formUuid);
			return !xml.trim().isEmpty();
		}
		catch (APIException e) {
			log.debug("Form {} does not have HFE XML: {}", formUuid, e.getMessage());
			return false;
		}
	}
	
	@Override
	public FormModel resolve(String formUuid, Locale locale) {
		String hfeXml = fetchHfeXml(formUuid);
		FormService fs = Context.getFormService();
		Form form = fs.getFormByUuid(formUuid);
		
		if (form == null) {
			throw new APIException("Form not found for UUID: " + formUuid);
		}
		
		FormModel model = parser.parse(hfeXml, form, locale);
		model.formType = FormType.HFE_XML;
		return model;
	}
	
	@Override
	public String getFormName(String formUuid) {
		if (formUuid == null || formUuid.trim().isEmpty()) {
			throw new APIException("Form UUID cannot be null or empty");
		}
		
		FormService fs = Context.getFormService();
		Form form = fs.getFormByUuid(formUuid);
		if (form == null) {
			throw new APIException("Form not found for UUID: " + formUuid);
		}
		
		return form.getName();
	}
	
	@Override
	public String getFormVersion(String formUuid) {
		if (formUuid == null || formUuid.trim().isEmpty()) {
			throw new APIException("Form UUID cannot be null or empty");
		}
		
		FormService fs = Context.getFormService();
		Form form = fs.getFormByUuid(formUuid);
		if (form == null) {
			throw new APIException("Form not found for UUID: " + formUuid);
		}
		
		return form.getVersion();
	}
	
	/**
	 * Fetch HFE XML from form resources
	 *
	 * @param formUuid The form UUID
	 * @return The HFE XML string
	 * @throws APIException if form not found or no HFE resource exists
	 */
	private String fetchHfeXml(String formUuid) {
		if (formUuid == null || formUuid.trim().isEmpty()) {
			throw new APIException("Form UUID cannot be null or empty");
		}
		
		FormService fs = Context.getFormService();
		Form form = fs.getFormByUuid(formUuid);
		if (form == null) {
			throw new APIException("Form not found for UUID: " + formUuid);
		}
		
		Collection<FormResource> resources = fs.getFormResourcesForForm(form);
		if (resources == null || resources.isEmpty()) {
			throw new APIException("No form resources found for form " + formUuid);
		}
		
		for (FormResource fr : resources) {
			if (HFE_RESOURCE_NAME.equalsIgnoreCase(fr.getName())) {
				String xml = extractHfeXmlFromResource(fr);
				if (xml != null && !xml.trim().isEmpty()) {
					return xml;
				}
			}
		}
		
		throw new APIException(
		        "No HFE XML resource found for form " + formUuid + ". Expected resource named '" + HFE_RESOURCE_NAME + "'");
	}
	
	/**
	 * Extract HFE XML from form resource HFE XML can be stored either as: 1. A CLOB reference
	 * (valueReference pointing to ClobDatatypeStorage) 2. Directly as a string value (older
	 * implementations)
	 *
	 * @param fr The form resource
	 * @return The HFE XML string, or null if not found
	 */
	private String extractHfeXmlFromResource(FormResource fr) {
		if (fr == null) {
			return null;
		}
		
		String ref = fr.getValueReference();
		if (ref != null && !ref.trim().isEmpty()) {
			try {
				DatatypeService ds = Context.getDatatypeService();
				ClobDatatypeStorage clob = ds.getClobDatatypeStorageByUuid(ref);
				
				if (clob != null && clob.getValue() != null) {
					String xml = clob.getValue().trim();
					if (!xml.isEmpty() && looksLikeXml(xml)) {
						log.debug("Loaded HFE XML from CLOB reference '{}' ({} chars)", ref, xml.length());
						return xml;
					}
				} else {
					log.debug("CLOB not found or empty for resource '{}' (ref: {})", fr.getName(), ref);
				}
			}
			catch (RuntimeException e) {
				log.warn("DatatypeService failed for resource '{}' (ref: {}): {}", fr.getName(), ref, e.getMessage());
			}
		}
		
		if (ref != null && looksLikeXml(ref)) {
			log.debug("Using valueReference directly as HFE XML ({} chars)", ref.length());
			return ref;
		}
		
		log.warn("Could not extract HFE XML from resource '{}'", fr.getName());
		return null;
	}
	
	private boolean looksLikeXml(String s) {
		if (s == null) {
			return false;
		}
		String trimmed = s.trim();
		return trimmed.startsWith("<") && trimmed.contains("htmlform");
	}
}
