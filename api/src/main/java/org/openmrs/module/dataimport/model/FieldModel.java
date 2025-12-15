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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FieldModel {
	
	public String id;

	public String label;

	public String sectionLabel;

	public String conceptUuid;
	
	public FieldDatatype datatype = FieldDatatype.TEXT;
	
	public final List<AnswerModel> answers = new ArrayList<>();
	
	public boolean requiredAlways;
	
	public String requiredExpr;
	
	public String hideExpr;
	
	public Integer minLength, maxLength;
	
	public Double min, max;
	
	public boolean isMulti = false;
	
	public boolean isRepeat = false;
	
	public String uiSelectSourceType;
	
	public String uiSelectDatasourceName;
	
	public final Map<String, String> uiSelectDatasourceConfig = new HashMap<>();
	
	FieldModel copyHeaderOnly() {
		FieldModel f = new FieldModel();
		f.id = id;
		f.label = label;
		f.sectionLabel = sectionLabel;
		f.conceptUuid = conceptUuid;
		f.datatype = datatype;
		f.answers.addAll(answers);
		f.isMulti = isMulti;
		f.isRepeat = isRepeat;
		f.uiSelectSourceType = uiSelectSourceType;
		f.uiSelectDatasourceName = uiSelectDatasourceName;
		f.uiSelectDatasourceConfig.putAll(uiSelectDatasourceConfig);
		return f;
	}

	public boolean isConditional() {
		return hideExpr != null || requiredExpr != null;
	}

	public String getConditionalDescription() {
		if (hideExpr != null && requiredExpr != null) {
			return "Conditional: " + hideExpr + " | Required when: " + requiredExpr;
		} else if (hideExpr != null) {
			return "Show only when: " + hideExpr;
		} else if (requiredExpr != null) {
			return "Required when: " + requiredExpr;
		}
		return null;
	}
}
