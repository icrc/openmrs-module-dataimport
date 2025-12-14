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
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.openmrs.Concept;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renderer for html <obs conceptId=xxx conceptAnswer=xxx></obs> tag Renderer for html
 * <obs conceptId=xxx conceptAnswer=yyy></obs> tag Renderer for html
 * <obs conceptId=xxx conceptAnswer=zzz></obs> tag
 */
@Setter
@Getter
public class HtmlFormUngroupedCheckboxRenderer {
	
	private String conceptUuid;
	
	private List<Element> elements;
	
	private String getQuestionLabel;
	
	public HtmlFormUngroupedCheckboxRenderer(String conceptUuid, List<Element> elements, String getQuestionLabel) {
		this.conceptUuid = conceptUuid;
		this.elements = elements;
		this.getQuestionLabel = getQuestionLabel;
	}
	
	public ObjectNode render() {
		if (conceptUuid == null || elements.isEmpty())
			return null;
		
		ObjectNode obsStub = generateQuestionStub();
		ObjectNode optionsNode = generateOptionsStub();
		Map<String, String> ansList = generateAnswerList(elements);
		ArrayNode answersList = JsonNodeFactory.instance.arrayNode();
		
		if (!ansList.isEmpty()) {
			for (Map.Entry<String, String> entry : ansList.entrySet()) {
				ObjectNode answerObject = JsonNodeFactory.instance.objectNode();
				answerObject.put("concept", entry.getKey());
				answerObject.put("label", entry.getValue());
				answersList.add(answerObject);
			}
			optionsNode.set("answers", answersList);
		}
		obsStub.set("questionOptions", optionsNode);
		
		return obsStub;
	}
	
	public Map<String, String> generateAnswerList(List<Element> ansListElements) {
		
		Map<String, String> codedAnswerMap = new HashMap<String, String>();
		
		for (Element obsTag : ansListElements) {
			String answerConceptId = obsTag.attr("answerConceptId");
			String answerLabel = obsTag.attr("answerLabel");
			
			if (StringUtils.isNotBlank(answerConceptId)) {
				Concept ansConcept = HtmlFormConversionUtil.getConceptByUuidOrId(answerConceptId);
				
				if (ansConcept != null) {
					codedAnswerMap.put(ansConcept.getUuid(),
					    StringUtils.isNotBlank(answerLabel) ? answerLabel : ansConcept.getName().getName());
				} else {
					String label = StringUtils.isNotBlank(answerLabel) ? answerLabel : answerConceptId;
					codedAnswerMap.put(answerConceptId, label);
					System.out.println("Answer Concept UUID Invalid: " + answerConceptId + " - using as placeholder");
				}
			}
		}
		return codedAnswerMap;
	}
	
	private ObjectNode generateQuestionStub() {
		ObjectNode obsStub = JsonNodeFactory.instance.objectNode();
		obsStub.put("label", getQuestionLabel);
		obsStub.put("type", "obs");
		
		return obsStub;
	}
	
	private ObjectNode generateOptionsStub() {
		ObjectNode optionsNode = JsonNodeFactory.instance.objectNode();
		optionsNode.put("concept", getConceptUuid());
		optionsNode.put("rendering", "multiCheckbox");
		return optionsNode;
	}
}
