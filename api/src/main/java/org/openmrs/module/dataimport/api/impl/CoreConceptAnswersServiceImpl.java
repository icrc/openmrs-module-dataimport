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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
public class CoreConceptAnswersServiceImpl extends BaseOpenmrsService implements ConceptAnswersService {
	
	private static final Log log = LogFactory.getLog(CoreConceptAnswersServiceImpl.class);
	
	@Override
	public List<AnswerModel> loadAnswersForConcept(String conceptUuid) {
		if (conceptUuid == null || conceptUuid.trim().isEmpty()) {
			return Collections.emptyList();
		}
		
		try {
			ConceptService cs = Context.getConceptService();
			Concept concept = cs.getConceptByUuid(conceptUuid);
			
			if (concept == null) {
				return Collections.emptyList();
			}
			
			List<AnswerModel> answers = new ArrayList<>();
			
			if (concept.getAnswers() != null && !concept.getAnswers().isEmpty()) {
				for (ConceptAnswer ca : concept.getAnswers()) {
					Concept answerConcept = ca.getAnswerConcept();
					if (answerConcept != null) {
						String label = getConceptName(answerConcept);
						String uuid = answerConcept.getUuid();
						answers.add(new AnswerModel(label, uuid, uuid));
					}
				}
				return answers;
			}
			
			if ((concept.getSetMembers() != null) && !concept.getSetMembers().isEmpty()) {
				for (Concept member : concept.getSetMembers()) {
					String label = getConceptName(member);
					String uuid = member.getUuid();
					answers.add(new AnswerModel(label, uuid, uuid));
				}
				return answers;
			}
			
			return Collections.emptyList();
			
		}
		catch (RuntimeException e) {
			log.error("Error loading answers for concept " + conceptUuid, e);
			return Collections.emptyList();
		}
	}
	
	@Override
	public AnswerModel loadSingleConcept(String conceptUuid) {
		if (conceptUuid == null || conceptUuid.trim().isEmpty()) {
			return null;
		}
		
		try {
			ConceptService cs = Context.getConceptService();
			if (cs == null) {
				return null;
			}
			
			Concept concept = cs.getConceptByUuid(conceptUuid);
			
			if (concept == null) {
				log.debug("Concept not found for UUID: " + conceptUuid
				        + " (ConceptService is available, but concept doesn't exist in database)");
				try {
					int conceptId = Integer.parseInt(conceptUuid);
					Concept byId = cs.getConcept(conceptId);
					if (byId != null) {
						log.info("Found concept by ID " + conceptId + " instead of UUID - UUID is: " + byId.getUuid());
					}
				}
				catch (NumberFormatException e) {
					// Not a numeric ID, that's fine
				}
				return null;
			}
			
			if (concept.getRetired() != null && concept.getRetired()) {
				log.warn("Concept " + conceptUuid + " is retired, but using it anyway");
			}
			
			String label = getConceptName(concept);
			if (label == null || label.trim().isEmpty()) {
				log.warn("Concept " + conceptUuid + " found but has no name - using UUID as fallback");
				return null;
			}
			
			log.debug("Successfully loaded concept " + conceptUuid + " -> label: " + label);
			return new AnswerModel(label, conceptUuid, conceptUuid);
		}
		catch (RuntimeException e) {
			log.error("Error loading single concept " + conceptUuid + ": " + e.getMessage(), e);
			return null;
		}
	}
	
	private String getConceptName(Concept concept) {
		if (concept == null) {
			return null;
		}
		
		Locale locale = Context.getLocale();
		if (concept.getPreferredName(locale) != null) {
			return concept.getPreferredName(locale).getName();
		}
		
		if (concept.getName(locale) != null) {
			return concept.getName(locale).getName();
		}
		
		return concept.getDisplayString();
	}
}
