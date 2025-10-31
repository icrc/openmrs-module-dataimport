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

import org.openmrs.api.APIException;
import org.openmrs.module.dataimport.api.ConceptAnswersProvider;
import org.openmrs.module.dataimport.api.FormSchemaResolver;
import org.openmrs.module.dataimport.api.TemplateOptions;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.SheetModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TemplateGenerationServiceImpl implements org.openmrs.module.dataimport.api.TemplateGenerationService {
	
	private static final Logger log = LoggerFactory.getLogger(TemplateGenerationServiceImpl.class);
	
	private final FormSchemaResolver resolver;
	
	private final O3FormParser parser;
	
	private final ExcelTemplateExporter exporter;
	
	private final ConceptAnswersProvider conceptAnswersProvider;
	
	@Autowired
	public TemplateGenerationServiceImpl(FormSchemaResolver resolver, O3FormParser parser, ExcelTemplateExporter exporter,
	    ConceptAnswersProvider conceptAnswersProvider) {
		this.resolver = resolver;
		this.parser = parser;
		this.exporter = exporter;
		this.conceptAnswersProvider = conceptAnswersProvider;
	}
	
	@Override
	public byte[] generateFromFormUuids(List<String> formUuids, TemplateOptions opts) {
		if (formUuids == null || formUuids.isEmpty()) {
			throw new APIException("At least one form UUID is required");
		}
		
		if (opts == null) {
			throw new APIException("Template options cannot be null");
		}
		
		FormModel merged = new FormModel();
		merged.name = "Template";
		
		List<String> formVersions = new ArrayList<>();
		
		for (String uuid : formUuids) {
			try {
				if (!resolver.hasSchemaResource(uuid)) {
					throw new APIException("Form " + uuid + " not found or has no JSON schema resource");
				}
				
				String json = resolver.fetchSchemaJsonByUuid(uuid);
				
				FormModel formModel = parser.parse(json, null);
				enrichFormWithConceptAnswers(formModel);
				
				String formVersion = formModel.version != null ? formModel.version : "1.0";
				formVersions.add(formVersion);
				
				// Set form information on each sheet before adding to merged form
				for (SheetModel sheet : formModel.sheets) {
					sheet.formUuid = formModel.formUuid;
					sheet.formVersion = formVersion;
					sheet.encounterTypeUuid = formModel.encounterTypeUuid;
				}
				
				if (merged.encounterTypeUuid == null) {
					merged.encounterTypeUuid = formModel.encounterTypeUuid;
				}
				merged.sheets.addAll(formModel.sheets);
				merged.rules.addAll(formModel.rules);
				if (merged.formUuid == null) {
					merged.formUuid = formModel.formUuid;
				}
				
			}
			catch (APIException e) {
				log.error(
				    "Error processing form {}: {}. Tip: verify form resources under Administration → Forms or REST /form/{}?v=full",
				    uuid, e.getMessage(), uuid);
				throw e;
			}
			catch (Exception e) {
				log.error("Unexpected error processing form {}: {}", uuid, e.getMessage(), e);
				throw new APIException("Error processing form " + uuid + ": " + e.getMessage(), e);
			}
		}
		
		merged.version = createCompositeVersion(formVersions);
		
		try {
			return exporter.export(merged, opts.includePatient, opts.locationUuid);
		}
		catch (RuntimeException e) {
			throw new APIException("Failed to generate Excel template: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Enrich coded fields with concept answers loaded from ConceptService. This is critical for radio
	 * buttons and selects that don't have inline answers in the form JSON but instead reference a
	 * concept's answers.
	 * <p>
	 * This method ensures all CODED fields have their answers populated before Excel export.
	 * 
	 * @param form The form to enrich
	 */
	private void enrichFormWithConceptAnswers(FormModel form) {
		log.info("Enriching form with concept answers for CODED fields");
		
		int totalCodedFields = 0;
		int fieldsWithInlineAnswers = 0;
		int fieldsLoadedFromConcepts = 0;
		
		for (SheetModel sheet : form.sheets) {
			for (FieldModel field : sheet.fields) {
				if (field.datatype == FieldDatatype.CODED) {
					totalCodedFields++;
					log.info("CODED field: '{}', conceptUuid='{}', inline answers={}", field.label, field.conceptUuid,
					    field.answers.size());
					
					if (!field.answers.isEmpty()) {
						fieldsWithInlineAnswers++;
						log.info("Field '{}' already has {} inline answers", field.label, field.answers.size());
					} else if (field.conceptUuid != null && !field.conceptUuid.trim().isEmpty()) {
						log.info("Loading concept answers for '{}' from concept {}", field.label, field.conceptUuid);
						List<AnswerModel> loadedAnswers = conceptAnswersProvider.loadAnswersForConcept(field.conceptUuid);
						if (loadedAnswers != null && !loadedAnswers.isEmpty()) {
							field.answers.addAll(loadedAnswers);
							fieldsLoadedFromConcepts++;
							log.info("Loaded {} answers for field '{}' from concept", loadedAnswers.size(), field.label);
						} else {
							log.warn("No answers found for coded field '{}' with concept {}", field.label,
							    field.conceptUuid);
						}
					} else {
						log.warn("CODED field '{}' has no inline answers and no concept UUID", field.label);
					}
				}
			}
		}
		
		log.info("CONCEPT ENRICHMENT SUMMARY: {} CODED fields, {} with inline answers, {} loaded from concepts",
		    totalCodedFields, fieldsWithInlineAnswers, fieldsLoadedFromConcepts);
	}
	
	/**
	 * Creates a composite version string from individual form versions. For single form: returns the
	 * form version as-is For multiple forms: returns comma-separated versions (e.g., "1.0,2.1,1.5")
	 * 
	 * @param formVersions List of individual form versions
	 * @return Composite version string
	 */
	private String createCompositeVersion(List<String> formVersions) {
		if (formVersions.isEmpty()) {
			return "1.0";
		}
		
		if (formVersions.size() == 1) {
			return formVersions.get(0);
		}
		
		// Multiple forms: create comma-separated version string
		return String.join(",", formVersions);
	}
}
