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
import org.openmrs.Concept;
import org.openmrs.api.context.Context;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Renderer for html <obs></obs> tag
 */
@Setter
@Getter
public class HtmlFormObsRenderer {
	
	private HtmlFormDataPoint dataPoint;
	
	public HtmlFormObsRenderer(HtmlFormDataPoint dataPoint) {
		this.dataPoint = dataPoint;
	}
	
	public ObjectNode render() {
		if (dataPoint == null)
			return null;
		
		String rendering = dataPoint.getRendering();
		
		if (rendering == null || rendering.isEmpty()) {
			Concept concept = Context.getConceptService().getConceptByUuid(dataPoint.getConceptUUID());
			if (concept != null) {
				if (concept.getDatatype().isCoded()) {
					return renderCodedObs();
				} else if (concept.getDatatype().isBoolean()) {
					return renderBooleansObs();
				} else if (concept.getDatatype().isText()) {
					return renderTextObs();
				} else if (concept.getDatatype().isDateTime() || concept.getDatatype().isDate()) {
					return renderDateObs();
				} else if (concept.getDatatype().isNumeric()) {
					return renderNumericObs();
				}
			}
			return renderTextObs();
		}
		
		if (rendering.equals("select") || rendering.equals("radio") || rendering.equals("multiCheckbox")
		        || rendering.equals("drug")) {
			return renderCodedObs();
		} else if (rendering.equals("number")) {
			return renderNumericObs();
		} else if (rendering.equals("date")) {
			return renderDateObs();
		} else if (rendering.equals("text") || rendering.equals("textarea")) {
			return renderTextObs();
		}
		
		return renderTextObs();
	}
	
	private ObjectNode renderNumericObs() {
		ObjectNode obsStub = generateQuestionStub();
		ObjectNode optionsNode = generateOptionsStub();
		obsStub.set("questionOptions", optionsNode);
		return obsStub;
	}
	
	private ObjectNode renderDateObs() {
		ObjectNode obsStub = generateQuestionStub();
		ObjectNode optionsNode = generateOptionsStub();
		obsStub.set("questionOptions", optionsNode);
		return obsStub;
	}
	
	private ObjectNode renderTextObs() {
		ObjectNode obsStub = generateQuestionStub();
		ObjectNode optionsNode = generateOptionsStub();
		obsStub.set("questionOptions", optionsNode);
		return obsStub;
	}
	
	private ObjectNode renderBooleansObs() {
		ObjectNode obsStub = generateQuestionStub();
		ObjectNode optionsNode = generateOptionsStub();
		
		ArrayNode answersList = JsonNodeFactory.instance.arrayNode();
		
		ObjectNode yesAnswerObject = JsonNodeFactory.instance.objectNode();
		yesAnswerObject.put("concept", "1065AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		yesAnswerObject.put("label", "Yes");
		answersList.add(yesAnswerObject);
		
		ObjectNode noAnswerObject = JsonNodeFactory.instance.objectNode();
		noAnswerObject.put("concept", "1066AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		noAnswerObject.put("label", "No");
		answersList.add(noAnswerObject);
		
		optionsNode.set("answers", answersList);
		
		obsStub.set("questionOptions", optionsNode);
		
		return obsStub;
	}
	
	private ObjectNode generateQuestionStub() {
		ObjectNode obsStub = JsonNodeFactory.instance.objectNode();
		obsStub.put("label", dataPoint.getQuestionLabel());
		obsStub.put("type", "obs");
		if (dataPoint.getFormFieldId() != null) {
			obsStub.put("id", dataPoint.getFormFieldId());
		}
		
		return obsStub;
	}
	
	private ObjectNode generateOptionsStub() {
		ObjectNode optionsNode = JsonNodeFactory.instance.objectNode();
		optionsNode.put("concept", dataPoint.getConceptUUID());
		optionsNode.put("rendering", dataPoint.getRendering());
		return optionsNode;
	}
	
	private ObjectNode renderCodedObs() {
		ObjectNode obsStub = generateQuestionStub();
		ObjectNode optionsNode = generateOptionsStub();
		
		ArrayNode answersList = JsonNodeFactory.instance.arrayNode();
		
		if (!dataPoint.getAnswersList().isEmpty()) {
			for (Map.Entry<String, String> conceptLabelPair : dataPoint.getAnswersList().entrySet()) {
				ObjectNode answerObject = JsonNodeFactory.instance.objectNode();
				answerObject.put("concept", conceptLabelPair.getKey());
				answerObject.put("label", conceptLabelPair.getValue());
				answersList.add(answerObject);
			}
			optionsNode.set("answers", answersList);
		}
		obsStub.set("questionOptions", optionsNode);
		
		return obsStub;
	}
	
}
