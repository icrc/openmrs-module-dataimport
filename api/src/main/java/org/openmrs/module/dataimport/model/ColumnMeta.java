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

import org.openmrs.module.dataimport.api.util.DataImportConstants;

import java.util.HashMap;
import java.util.Map;

public class ColumnMeta {
	
	public int colIndex;
	
	public String sheetName;
	
	public String fieldId;
	
	public String label;
	
	public String conceptUuid;
	
	public FieldDatatype datatype;
	
	public boolean isRepeat;
	
	public boolean isMulti;
	
	public boolean requiredAlways;
	
	public Integer minLength, maxLength;
	
	public Double min, max;
	
	public Map<String, String> answers = new HashMap<>();
	
	public boolean isPatientRef() {
		return DataImportConstants.PATIENT_REF_HEADER.equals(label);
	}
	
	public String getAnswerConceptUuid(String label) {
		if (answers == null)
			return null;
		String direct = answers.get(label);
		if (direct != null)
			return direct;
		String target = DataImportConstants.normalizeLabel(label);
		for (Map.Entry<String, String> e : answers.entrySet()) {
			if (DataImportConstants.normalizeLabel(e.getKey()).equals(target)) {
				return e.getValue();
			}
		}
		return null;
	}
}
