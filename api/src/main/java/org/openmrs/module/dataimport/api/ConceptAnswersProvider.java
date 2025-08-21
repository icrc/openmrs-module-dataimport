/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api;

import org.openmrs.api.OpenmrsService;
import org.openmrs.module.dataimport.model.AnswerModel;

import java.util.List;

/**
 * Service for loading concept answers from OpenMRS concepts
 */
public interface ConceptAnswersProvider extends OpenmrsService {
	
	/**
	 * Load answers for a concept by its UUID
	 * 
	 * @param conceptUuid The UUID of the concept
	 * @return List of answers (label + conceptUuid + value)
	 */
	List<AnswerModel> loadAnswersForConcept(String conceptUuid);
}
