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

/**
 * Renderer for html <obsgroup></obsgroup> tag
 */
@Setter
@Getter
public class HtmlFormObsGroupRenderer {
	
	private Element obsGroupTag;
	
	public HtmlFormObsGroupRenderer(Element obsGroupTag) {
		
		this.obsGroupTag = obsGroupTag;
	}
	
	public ObjectNode render() {
		if (obsGroupTag == null)
			return null;
		
		String groupingConceptUuid = obsGroupTag.attr("groupingConceptId");
		
		if (StringUtils.isBlank(groupingConceptUuid))
			return null;
		
		Concept groupConcept = HtmlFormConversionUtil.getConceptByUuidOrId(groupingConceptUuid);
		String groupConceptUuidToUse;
		if (groupConcept == null) {
			groupConceptUuidToUse = groupingConceptUuid;
		} else {
			groupConceptUuidToUse = groupConcept.getUuid();
		}
		
		ArrayNode questionsList = JsonNodeFactory.instance.arrayNode();
		
		for (Element element : obsGroupTag.select("obs")) {
			HtmlFormDataPoint dataPoint = HtmlObsTagExtractor.extractObsTag(element);
			HtmlFormObsRenderer renderer = new HtmlFormObsRenderer(dataPoint);
			ObjectNode obsJson = renderer.render();
			if (obsJson != null) {
				questionsList.add(obsJson);
			}
		}
		ObjectNode obsGroupObject = generateGroupStub(groupConceptUuidToUse);
		obsGroupObject.set("questions", questionsList);
		return obsGroupObject;
	}
	
	private ObjectNode generateGroupStub(String groupingConceptUuid) {
		String groupLabel = obsGroupTag.attr("labelText");
		
		ObjectNode obsStub = JsonNodeFactory.instance.objectNode();
		obsStub.put("label", groupLabel);
		obsStub.put("type", "obsGroup");
		
		ObjectNode optionsNode = JsonNodeFactory.instance.objectNode();
		optionsNode.put("concept", groupingConceptUuid);
		optionsNode.put("rendering", HtmlFormConversionUtil.isRepeat(obsGroupTag) ? "repeat" : "group");
		obsStub.set("questionOptions", optionsNode);
		return obsStub;
	}
	
}
