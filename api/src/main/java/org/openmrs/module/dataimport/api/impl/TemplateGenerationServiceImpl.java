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
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.openmrs.module.dataimport.api.service.FormSchemaService;
import org.openmrs.module.dataimport.api.service.TemplateGenerationService;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.SheetModel;
import org.openmrs.module.dataimport.model.TemplateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TemplateGenerationServiceImpl implements TemplateGenerationService {
	
	private static final Logger log = LoggerFactory.getLogger(TemplateGenerationServiceImpl.class);
	
	private final FormSchemaService formSchemaService;
	
	private final O3FormParser parser;
	
	private final ExcelTemplateExporter exporter;
	
	private final ConceptAnswersService conceptAnswersService;
	
	@Autowired
	public TemplateGenerationServiceImpl(FormSchemaService resolver, O3FormParser parser, ExcelTemplateExporter exporter,
	    ConceptAnswersService conceptAnswersProvider) {
		this.formSchemaService = resolver;
		this.parser = parser;
		this.exporter = exporter;
		this.conceptAnswersService = conceptAnswersProvider;
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
				if (!formSchemaService.hasSchemaResource(uuid)) {
					throw new APIException("Form " + uuid + " not found or has no JSON schema resource");
				}
				
				String json = formSchemaService.fetchSchemaJsonByUuid(uuid);
				
				FormModel formModel = parser.parse(json, null);
				enrichFormWithConceptAnswers(formModel);
				
				String formVersion = formModel.version != null ? formModel.version : "1.0";
				formVersions.add(formVersion);
				
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
	
	private void enrichFormWithConceptAnswers(FormModel form) {
		for (SheetModel sheet : form.sheets) {
			for (FieldModel field : sheet.fields) {
				if (field.datatype == FieldDatatype.CODED) {
					if (!field.answers.isEmpty()) {
						for (AnswerModel answer : field.answers) {
							boolean missingLabel = (answer.label == null || answer.label.trim().isEmpty());
							if (missingLabel && answer.conceptUuid != null && !answer.conceptUuid.trim().isEmpty()) {
								AnswerModel conceptAnswer = conceptAnswersService.loadSingleConcept(answer.conceptUuid);
								if (conceptAnswer != null && conceptAnswer.label != null) {
									answer.label = conceptAnswer.label;
								} else {
									answer.label = answer.conceptUuid;
								}
							}
						}
					} else if (field.conceptUuid != null && !field.conceptUuid.trim().isEmpty()) {
						List<AnswerModel> loadedAnswers = conceptAnswersService.loadAnswersForConcept(field.conceptUuid);
						if (loadedAnswers != null && !loadedAnswers.isEmpty()) {
							field.answers.addAll(loadedAnswers);
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
	}
	
	private String createCompositeVersion(List<String> formVersions) {
		if (formVersions.isEmpty()) {
			return "1.0";
		}
		
		if (formVersions.size() == 1) {
			return formVersions.get(0);
		}
		
		return String.join(",", formVersions);
	}
}
