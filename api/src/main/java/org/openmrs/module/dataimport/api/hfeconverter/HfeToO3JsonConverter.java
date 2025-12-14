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

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openmrs.Concept;
import org.openmrs.Form;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts HFE XML to O3 JSON format. Adapted from hfe-o3-form-schema-converter.
 */
public class HfeToO3JsonConverter {
	
	private static final Logger log = LoggerFactory.getLogger(HfeToO3JsonConverter.class);
	
	private final ObjectMapper objectMapper;
	
	public HfeToO3JsonConverter() {
		this.objectMapper = new ObjectMapper();
	}
	
	public String convert(String hfeXml, Form form) {
		try {
			ObjectNode formJson = convertToObjectNode(hfeXml, form);
			return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(formJson);
		}
		catch (Exception e) {
			log.error("Failed to convert HFE XML to O3 JSON", e);
			throw new RuntimeException("Failed to convert HFE XML to O3 JSON: " + e.getMessage(), e);
		}
	}
	
	public ObjectNode convertToObjectNode(String hfeXml, Form form) {
		ObjectNode formJson = JsonNodeFactory.instance.objectNode();
		ArrayNode formPages = JsonNodeFactory.instance.arrayNode();
		
		ObjectNode defaultFormPage = JsonNodeFactory.instance.objectNode();
		ArrayNode formSections = JsonNodeFactory.instance.arrayNode();
		ObjectNode defaultFormSection = JsonNodeFactory.instance.objectNode();
		defaultFormSection.put("label", "Starter section");
		defaultFormSection.put("isExpanded", true);
		
		ArrayNode questionsList = JsonNodeFactory.instance.arrayNode();
		
		Document doc = Jsoup.parse(hfeXml, "", org.jsoup.parser.Parser.xmlParser());
		
		formJson.put("name", form.getName());
		formJson.put("description", form.getDescription() != null ? form.getDescription() : "");
		formJson.put("version", form.getVersion() != null ? form.getVersion() : "1.0");
		formJson.put("published", false);
		formJson.put("retired", false);
		formJson.put("processor", "EncounterFormProcessor");
		formJson.put("encounterType", form.getEncounterType() != null ? form.getEncounterType().getUuid() : "");
		formJson.set("referencedForms", JsonNodeFactory.instance.arrayNode());
		formJson.put("uuid", form.getUuid());
		
		log.debug("Converting HFE form: {}", form.getName());
		
		Element htmlform = doc.select("htmlform").first();
		if (htmlform == null) {
			log.warn("No <htmlform> element found in HFE XML");
			return createEmptyForm(form);
		}
		
		Elements elementTags = htmlform.select("obs,obsgroup");
		Set<String> processedUngroupedCheckedInputs = new HashSet<String>();
		
		for (Element element : elementTags) {
			if (element.normalName().equals("obs")) {
				boolean isGrouped = HtmlFormConversionUtil.obsIsInAGroup(element);
				
				if (isGrouped) {
					continue;
				}
				
				String conceptIdAttr = element.attr("conceptId");
				String styleAttr = element.attr("style");
				Concept questionConcept = HtmlFormConversionUtil.getConceptByUuidOrId(conceptIdAttr);
				
				if (StringUtils.isNotBlank(styleAttr) && styleAttr.equals("checkbox")
				        && StringUtils.isNotBlank(conceptIdAttr) && questionConcept != null
				        && !processedUngroupedCheckedInputs.contains(conceptIdAttr)) {
					String questionLabel = HtmlFormConversionUtil.extractQuestionLabel(element);
					String actualConceptUuid = questionConcept.getUuid();
					Map<String, List<Element>> relatedTags = HtmlUngroupedCheckboxExtractor
					        .getRelatedCheckboxes(conceptIdAttr, actualConceptUuid, htmlform);
					
					List<Element> allRelatedTags = relatedTags.get(actualConceptUuid);
					if (allRelatedTags == null || allRelatedTags.isEmpty()) {
						ObjectNode dataPointJson = generateJsonObjectForHtmlDataPoint(element);
						
						if (dataPointJson != null) {
							questionsList.add(dataPointJson);
						}
						
					} else {
						HtmlFormUngroupedCheckboxRenderer ungroupedCheckboxRenderer = new HtmlFormUngroupedCheckboxRenderer(
						        actualConceptUuid, allRelatedTags, questionLabel);
						ObjectNode jsonObj = ungroupedCheckboxRenderer.render();
						
						if (jsonObj != null) {
							questionsList.add(jsonObj);
						}
						processedUngroupedCheckedInputs.add(conceptIdAttr);
						
					}
					
				} else {
					
					ObjectNode dataPointJson = generateJsonObjectForHtmlDataPoint(element);
					
					if (dataPointJson != null) {
						questionsList.add(dataPointJson);
					}
				}
			} else if (element.normalName().equals("obsgroup")) {
				HtmlFormObsGroupRenderer obsGroupRenderer = new HtmlFormObsGroupRenderer(element);
				ObjectNode groupJson = obsGroupRenderer.render();
				if (groupJson != null) {
					questionsList.add(groupJson);
				}
			}
			
		}
		
		defaultFormSection.set("questions", questionsList);
		formSections.add(defaultFormSection);
		defaultFormPage.put("label", "Starter page");
		defaultFormPage.set("sections", formSections);
		formPages.add(defaultFormPage);
		
		formJson.set("pages", formPages);
		return formJson;
	}
	
	private ObjectNode generateJsonObjectForHtmlDataPoint(Element element) {
		HtmlFormDataPoint dataPoint = HtmlObsTagExtractor.extractObsTag(element);
		if (dataPoint == null) {
			log.debug("Encountered invalid concept UUID in the form schema. Ignoring the tag");
			return null;
		}
		HtmlFormObsRenderer renderer = new HtmlFormObsRenderer(dataPoint);
		ObjectNode obsJson = renderer.render();
		if (obsJson != null) {
			return obsJson;
		}
		return null;
	}
	
	private ObjectNode createEmptyForm(Form form) {
		ObjectNode formJson = JsonNodeFactory.instance.objectNode();
		formJson.put("name", form.getName());
		formJson.put("description", form.getDescription() != null ? form.getDescription() : "");
		formJson.put("version", form.getVersion() != null ? form.getVersion() : "1.0");
		formJson.put("published", false);
		formJson.put("retired", false);
		formJson.put("processor", "EncounterFormProcessor");
		formJson.put("encounterType", form.getEncounterType() != null ? form.getEncounterType().getUuid() : "");
		formJson.put("uuid", form.getUuid());
		formJson.set("pages", JsonNodeFactory.instance.arrayNode());
		formJson.set("referencedForms", JsonNodeFactory.instance.arrayNode());
		return formJson;
	}
}
