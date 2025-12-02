/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.service;

import org.openmrs.Concept;
import org.openmrs.ConceptDatatype;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;

import java.util.List;

public interface ConceptAnswersService extends OpenmrsService {
	
	/**
	 * Load answers for a concept by its UUID
	 * 
	 * @param conceptUuid The UUID of the concept
	 * @return List of answers (label + conceptUuid + value)
	 */
	List<AnswerModel> loadAnswersForConcept(String conceptUuid);
	
	/**
	 * Load a single concept by its UUID and return it as an AnswerModel
	 * 
	 * @param conceptUuid The UUID of the concept
	 * @return AnswerModel with the concept's name as label, or null if concept not found
	 */
	AnswerModel loadSingleConcept(String conceptUuid);
	
	/**
	 * Resolves a concept identifier (UUID, numeric ID, or mapping like "CIEL:5089") to a concept UUID.
	 * Supports multiple identification strategies with caching.
	 * 
	 * @param conceptId The concept identifier (UUID, numeric ID, or mapping code)
	 * @return The resolved concept UUID, or null if not found
	 */
	String resolveConceptUuid(String conceptId);
	
	/**
	 * Gets the display name for a concept by UUID. Uses locale-aware name resolution with caching.
	 * 
	 * @param conceptUuid The concept UUID
	 * @return The concept name, or null if not found
	 */
	String getConceptName(String conceptUuid);
	
	/**
	 * Gets a Concept object by UUID with caching.
	 * 
	 * @param conceptUuid The concept UUID
	 * @return The Concept object, or null if not found
	 */
	Concept getConceptByUuid(String conceptUuid);
	
	/**
	 * Gets the datatype of a concept.
	 * 
	 * @param conceptUuid The concept UUID
	 * @return The ConceptDatatype, or null if not found
	 */
	ConceptDatatype getConceptDatatype(String conceptUuid);
	
	/**
	 * Converts OpenMRS ConceptDatatype to module FieldDatatype.
	 * 
	 * @param conceptDatatype The OpenMRS ConceptDatatype
	 * @return The corresponding FieldDatatype, or TEXT as default
	 */
	FieldDatatype convertToFieldDatatype(ConceptDatatype conceptDatatype);
}
