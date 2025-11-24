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

import org.openmrs.Concept;
import org.openmrs.ConceptDatatype;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.openmrs.module.dataimport.api.util.ConceptManager;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementation of ConceptAnswersService that acts as a thin wrapper around ConceptManager. All
 * concept operations must go through this service to ensure centralized caching and management.
 */
@Component
public class CoreConceptAnswersServiceImpl extends BaseOpenmrsService implements ConceptAnswersService {
	
	@Override
	public List<AnswerModel> loadAnswersForConcept(String conceptUuid) {
		return ConceptManager.loadAnswersForConcept(conceptUuid);
	}
	
	@Override
	public AnswerModel loadSingleConcept(String conceptUuid) {
		return ConceptManager.loadSingleConceptAsAnswer(conceptUuid);
	}
	
	@Override
	public String resolveConceptUuid(String conceptId) {
		return ConceptManager.resolveConceptUuid(conceptId);
	}
	
	@Override
	public String getConceptName(String conceptUuid) {
		return ConceptManager.getConceptName(conceptUuid);
	}
	
	@Override
	public Concept getConceptByUuid(String conceptUuid) {
		return ConceptManager.getConceptByUuid(conceptUuid);
	}
	
	@Override
	public ConceptDatatype getConceptDatatype(String conceptUuid) {
		return ConceptManager.getConceptDatatype(conceptUuid);
	}
	
	@Override
	public FieldDatatype convertToFieldDatatype(ConceptDatatype conceptDatatype) {
		return ConceptManager.convertToFieldDatatype(conceptDatatype);
	}
}
