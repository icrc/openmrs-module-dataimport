/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.impl;

import org.openmrs.Form;
import org.openmrs.FormResource;
import org.openmrs.api.APIException;
import org.openmrs.api.DatatypeService;
import org.openmrs.api.FormService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.ClobDatatypeStorage;
import org.openmrs.module.dataimport.api.FormSchemaResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class CoreFormSchemaResolver implements FormSchemaResolver {
	
	private static final Logger log = LoggerFactory.getLogger(CoreFormSchemaResolver.class);
	
	private static final String[] ALTERNATIVE_SCHEMA_NAMES = { "JSON schema", "schema", "json", "formSchema", "o3Schema",
	        "O3 Form schema" };
	
	@Override
	public String fetchSchemaJsonByUuid(String formUuid) {
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
		
		FormResource schemaRes = findSchemaResourceDirect(resources, form);
		if (schemaRes == null) {
			throw new APIException(
			        "No JSON schema resource found for form " + formUuid + ". Available resources: "
			                + resources.stream()
			                        .map(r -> String.format("%s | datatype=%s | ref=%s", r.getName(),
			                            safeClass(r.getDatatypeClassname()), r.getValueReference()))
			                        .collect(Collectors.toList()));
		}
		
		String json = extractViaDatatypeService(schemaRes);
		if (json == null || json.trim().isEmpty()) {
			throw new APIException("JSON schema resource is empty or could not be resolved for form " + formUuid);
		}
		
		return json;
	}
	
	private FormResource findSchemaResourceDirect(Collection<FormResource> resources, Form form) {
		for (String schemaName : ALTERNATIVE_SCHEMA_NAMES) {
			for (FormResource fr : resources) {
				if (schemaName.equalsIgnoreCase(fr.getName())) {
					String json = extractViaDatatypeService(fr);
					if (looksJson(json)) {
						return fr;
					}
				}
			}
		}
		
		for (FormResource fr : resources) {
			String name = fr.getName() == null ? "" : fr.getName().toLowerCase();
			if (name.contains("json") && name.contains("schema")) {
				String json = extractViaDatatypeService(fr);
				if (looksJson(json)) {
					return fr;
				}
			}
		}
		
		for (FormResource fr : resources) {
			String name = fr.getName() == null ? "" : fr.getName().toLowerCase();
			if (name.contains("schema")) {
				String json = extractViaDatatypeService(fr);
				if (looksJson(json)) {
					return fr;
				}
			}
		}
		
		log.warn("No schema resource found by heuristics. Available resources for form {}: {}", form.getUuid(),
		    resources.stream().map(r -> String.format("%s | datatype=%s | ref=%s", r.getName(),
		        safeClass(r.getDatatypeClassname()), r.getValueReference())).collect(Collectors.toList()));
		
		return null;
	}
	
	private String extractViaDatatypeService(FormResource fr) {
		if (fr == null)
			return null;
		
		String ref = fr.getValueReference();
		if (ref == null || ref.trim().isEmpty()) {
			return null;
		}
		
		try {
			DatatypeService ds = Context.getDatatypeService();
			ClobDatatypeStorage clob = ds.getClobDatatypeStorageByUuid(ref);
			
			if (clob != null && clob.getValue() != null) {
				String s = clob.getValue().trim();
				if (!s.isEmpty()) {
					return s;
				}
			} else {
				log.warn("CLOB not found or empty for resource '{}' (ref: {})", fr.getName(), ref);
			}
		}
		catch (RuntimeException e) {
			log.error("DatatypeService failed for resource '{}' (ref: {}): {}", fr.getName(), ref, e.getMessage(), e);
		}
		
		return null;
	}
	
	private static boolean looksJson(String s) {
		if (s == null)
			return false;
		String t = s.trim();
		return t.startsWith("{") || t.startsWith("[");
	}
	
	private static String safeClass(String s) {
		return s == null ? "n/a" : s.substring(s.lastIndexOf('.') + 1);
	}
	
	@Override
	public boolean hasSchemaResource(String formUuid) {
		try {
			fetchSchemaJsonByUuid(formUuid);
			return true;
		}
		catch (APIException e) {
			return false;
		}
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
	
	@Override
	public String fetchSchemaJsonByUuid(String formUuid, String languageCode) {
		return fetchSchemaJsonByUuid(formUuid);
	}
}
