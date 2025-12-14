/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.hfeconverter;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Model for HTML form data point/tag i.e. <obs></obs> Adapted from hfe-o3-form-schema-converter
 */
@Setter
@Getter
public class HtmlFormDataPoint {
	
	private String conceptUUID;
	
	private Integer conceptId;
	
	private String conceptName;
	
	private String dataType; // i.e. obs, obsGroup etc
	
	private String questionLabel = "";
	
	private boolean requiredField = false;
	
	private String rendering = "";
	
	private String formFieldId = "";
	
	private Map<String, String> answersList = new HashMap<String, String>();
	
	public boolean getRequiredField() {
		return requiredField;
	}
	
	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (o == null)
			return false;
		if (getClass() != o.getClass())
			return false;
		HtmlFormDataPoint pn = (HtmlFormDataPoint) o;
		return pn.getConceptId().equals(this.getConceptId());
	}
	
	@Override
	public int hashCode() {
		return getConceptId().hashCode();
	}
	
	@Override
	public String toString() {
		return "HtmlFormDataPoint{" + "conceptUUID='" + conceptUUID + '\'' + ", conceptId=" + conceptId + ", conceptName='"
		        + conceptName + '\'' + ", dataType='" + dataType + '\'' + ", questionLabel='" + questionLabel + '\''
		        + ", requiredField=" + requiredField + ", rendering='" + rendering + '\'' + ", formFieldId='" + formFieldId
		        + '\'' + ", answersList=" + answersList + '}';
	}
}
