/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.model;

/**
 * Rule representation in the form model.
 */
public class RuleModel {
	
	public String fieldId;
	
	public String type; // "required-if", "relevance", "constraint"
	
	public String expr; // expression as string (from schema), evaluated server-side on import
	
	public String message;
}
