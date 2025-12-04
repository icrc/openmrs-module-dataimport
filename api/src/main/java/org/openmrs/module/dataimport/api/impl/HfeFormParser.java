/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.impl;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.openmrs.Concept;
import org.openmrs.ConceptDatatype;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.openmrs.module.dataimport.api.util.ExcelNameUtils;
import org.openmrs.module.dataimport.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HfeFormParser {
	
	private static final Logger log = LoggerFactory.getLogger(HfeFormParser.class);
	
	private final ConceptAnswersService conceptAnswersService;
	
	private org.openmrs.messagesource.MessageSourceService messageSourceService;
	
	@Autowired
	public HfeFormParser(ConceptAnswersService conceptAnswersService) {
		this.conceptAnswersService = conceptAnswersService;
	}
	
	@Autowired(required = false)
	public void setMessageSourceService(org.openmrs.messagesource.MessageSourceService messageSourceService) {
		this.messageSourceService = messageSourceService;
	}
	
	private static final String FEATURE_LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
	
	private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
	
	private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
	
	private static final String ELEMENT_OBS = "obs";
	
	private static final String ELEMENT_OBSGROUP = "obsgroup";
	
	private static final String ELEMENT_ENCOUNTER_DATE = "encounterdate";
	
	private static final String ELEMENT_ENCOUNTER_LOCATION = "encounterlocation";
	
	private static final String ELEMENT_ENCOUNTER_PROVIDER = "encounterprovider";
	
	private static final String ELEMENT_REPEAT = "repeat";
	
	private static final String ATTR_CONCEPT_ID = "conceptId";
	
	private static final String ATTR_GROUPING_CONCEPT_ID = "groupingConceptId";
	
	private static final String ATTR_LABEL_TEXT = "labelText";
	
	private static final String ATTR_LABEL_CODE = "labelCode";
	
	private static final String ATTR_STYLE = "style";
	
	private static final String ATTR_ANSWER_CONCEPT_IDS = "answerConceptIds";
	
	private static final String ATTR_ANSWER_LABELS = "answerLabels";
	
	private static final String ATTR_ANSWER_CONCEPT_ID = "answerConceptId";
	
	private static final String ATTR_ANSWER_LABEL = "answerLabel";
	
	private static final String ATTR_HIDE_WHEN_EXPRESSION = "hideWhenExpression";
	
	private static final String ATTR_WITH = "with";
	
	private static final String WIDGET_SELECT = "select";
	
	private static final String WIDGET_INPUT = "input";
	
	private static final String WIDGET_TEXTAREA = "textarea";
	
	private static final String INPUT_TYPE_CHECKBOX = "checkbox";
	
	private static final String INPUT_TYPE_RADIO = "radio";
	
	private static final String INPUT_TYPE_DATE = "date";
	
	private static final String INPUT_TYPE_NUMBER = "number";
	
	private static final String INPUT_ATTR_TYPE = "type";
	
	private static final String DATATYPE_NUMERIC = "Numeric";
	
	private static final String DATATYPE_CODED = "Coded";
	
	private static final String DATATYPE_TEXT = "Text";
	
	private static final String DATATYPE_DATE = "Date";
	
	private static final String DATATYPE_DATETIME = "Datetime";
	
	private static final String DATATYPE_BOOLEAN = "Boolean";
	
	private static final String STYLE_DATE = "date";
	
	private static final String STYLE_LOCATION = "location";
	
	private static final String CONCEPT_MAPPING_SEPARATOR = ":";
	
	private static final String FIELD_ID_SEPARATOR = "_";
	
	private static final String FIELD_ID_ENCOUNTER_DATE = "encounterDate";
	
	private static final String FIELD_ID_ENCOUNTER_LOCATION = "encounterLocation";
	
	private static final String FIELD_ID_ENCOUNTER_PROVIDER = "encounterProvider";
	
	private static final String DEFAULT_SHEET_NAME = "Main";
	
	private static final int MAX_SHEET_NAME_LENGTH = 31;
	
	private static final String EXCEL_CELL_REFERENCE_PATTERN = "^[A-Z]+[0-9]+$";
	
	private static final String ERROR_NULL_XML = "HFE XML cannot be null or empty";
	
	private static final String ERROR_NULL_FORM = "Form object cannot be null";
	
	private static final String ERROR_PARSE_FAILED = "Failed to parse HFE XML for form ";
	
	private static final String ERROR_REPEAT_NO_WITH = "Repeat element missing 'with' attribute - skipping";
	
	private static final String ERROR_REPEAT_NO_TUPLES = "No valid tuples found in repeat 'with' attribute: ";
	
	private static final String ERROR_CONCEPT_RESOLVE = "Error resolving concept '{}': {}";
	
	private static final String ERROR_CLONE_FAILED = "Failed to clone element for substitution: {}";
	
	/**
	 * Parse HFE XML into FormModel.
	 */
	public FormModel parse(String hfeXml, org.openmrs.Form form, Locale locale) {
		if (hfeXml == null || hfeXml.trim().isEmpty()) {
			throw new IllegalArgumentException(ERROR_NULL_XML);
		}
		
		if (form == null) {
			throw new IllegalArgumentException(ERROR_NULL_FORM);
		}
		
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			factory.setValidating(false);
			factory.setFeature(FEATURE_LOAD_EXTERNAL_DTD, false);
			factory.setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
			factory.setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
			
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(new ByteArrayInputStream(hfeXml.getBytes(StandardCharsets.UTF_8)));
			
			FormModel formModel = new FormModel();
			formModel.formUuid = form.getUuid();
			formModel.name = form.getName();
			formModel.version = form.getVersion();
			formModel.encounterTypeUuid = form.getEncounterType() != null ? form.getEncounterType().getUuid() : null;
			formModel.formType = FormType.HFE_XML;
			
			SheetModel sheet = new SheetModel();
			sheet.sheetName = ExcelNameUtils.sanitizeSheetName(formModel.name);
			sheet.formUuid = formModel.formUuid;
			sheet.formVersion = formModel.version;
			sheet.encounterTypeUuid = formModel.encounterTypeUuid;
			sheet.formType = FormType.HFE_XML;
			
			formModel.sheets.add(sheet);
			
			Locale effectiveLocale = locale != null ? locale : Context.getLocale();
			Element root = doc.getDocumentElement();
			walkHfeElements(root, sheet, formModel, effectiveLocale, "");
			
			log.info("Successfully parsed HFE form '{}' (v{}) with {} fields", formModel.name, formModel.version,
			    sheet.fields.size());
			
			return formModel;
			
		}
		catch (IllegalArgumentException e) {
			throw e;
		}
		catch (Exception e) {
			String errorMsg = ERROR_PARSE_FAILED + form.getName() + ": " + e.getMessage();
			log.error(errorMsg, e);
			throw new IllegalArgumentException(errorMsg, e);
		}
	}
	
	/**
	 * Recursively walk HFE DOM elements and extract data fields.
	 */
	private void walkHfeElements(Element element, SheetModel sheet, FormModel form, Locale locale, String path) {
		NodeList children = element.getChildNodes();
		
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			
			Element child = (Element) node;
			String tagName = child.getTagName().toLowerCase(Locale.ROOT);
			
			switch (tagName) {
				case ELEMENT_OBS:
					handleObs(child, sheet, form, locale, path);
					break;
				case ELEMENT_OBSGROUP:
					handleObsGroup(child, sheet, form, locale, path);
					break;
				case ELEMENT_ENCOUNTER_DATE:
					handleEncounterDate(child, sheet, form, locale);
					break;
				case ELEMENT_ENCOUNTER_LOCATION:
					handleEncounterLocation(child, sheet, form, locale);
					break;
				case ELEMENT_ENCOUNTER_PROVIDER:
					handleEncounterProvider(child, sheet, form, locale);
					break;
				case ELEMENT_REPEAT:
					handleRepeat(child, sheet, form, locale, path);
					break;
				default:
					walkHfeElements(child, sheet, form, locale, path);
					break;
			}
		}
	}
	
	/**
	 * Handle &lt;obs/&gt; element - creates an observation field.
	 */
	private void handleObs(Element obsElement, SheetModel sheet, FormModel form, Locale locale, String path) {
		String conceptId = getAttributeOrNull(obsElement, ATTR_CONCEPT_ID);
		String labelText = getAttributeOrNull(obsElement, ATTR_LABEL_TEXT);
		String labelCode = getAttributeOrNull(obsElement, ATTR_LABEL_CODE);
		String answerConceptIds = getAttributeOrNull(obsElement, ATTR_ANSWER_CONCEPT_IDS);
		String answerConceptId = getAttributeOrNull(obsElement, ATTR_ANSWER_CONCEPT_ID);
		String answerLabels = getAttributeOrNull(obsElement, ATTR_ANSWER_LABELS);
		String answerLabel = getAttributeOrNull(obsElement, ATTR_ANSWER_LABEL);
		String style = getAttributeOrNull(obsElement, ATTR_STYLE);
		String required = getAttributeOrNull(obsElement, "required");
		String id = getAttributeOrNull(obsElement, "id");
		
		String conceptUuid = resolveConceptUuid(conceptId);
		if (conceptUuid == null) {
			log.warn("Could not resolve concept ID '{}' for obs field - skipping", conceptId);
			return;
		}
		
		FieldModel field = new FieldModel();
		field.conceptUuid = conceptUuid;
		field.id = (id != null && !id.trim().isEmpty()) ? sanitizeFieldId(id)
		        : generateFieldId(conceptId, labelText, path, sheet.fields.size());
		field.label = resolveLabelText(labelText, labelCode, conceptUuid, locale);
		field.datatype = inferDatatypeAndRendering(obsElement, conceptUuid, style, answerConceptIds, answerConceptId);
		
		if (field.datatype == FieldDatatype.CODED) {
			if (answerConceptIds != null && !answerConceptIds.trim().isEmpty()) {
				parseAnswers(field, answerConceptIds, answerLabels, locale);
			} else if (answerConceptId != null && !answerConceptId.trim().isEmpty()) {
				parseSingleAnswer(field, answerConceptId, answerLabel, locale);
			}
		}
		
		if ("true".equalsIgnoreCase(required)) {
			field.requiredAlways = true;
		}
		
		if (hasConditionalLogic(obsElement)) {
			field.hideExpr = "HFE conditional logic (advisory - validated on server during import)";
		}
		
		if (path != null && path.contains("/repeat[")) {
			field.isRepeat = true;
		}
		
		sheet.fields.add(field);
		log.debug("Added obs field '{}' (concept={}, datatype={}, isRepeat={})", field.label, conceptUuid, field.datatype,
		    field.isRepeat);
	}
	
	/**
	 * Handle &lt;obsgroup/&gt; element - flattens observation group into sheet.
	 */
	private void handleObsGroup(Element groupElement, SheetModel sheet, FormModel form, Locale locale, String path) {
		String groupingConceptId = getAttributeOrNull(groupElement, ATTR_GROUPING_CONCEPT_ID);
		String labelText = getAttributeOrNull(groupElement, ATTR_LABEL_TEXT);
		String newPath = path + "/"
		        + (labelText != null && !labelText.trim().isEmpty() ? sanitize(labelText) : "group_" + sheet.fields.size());
		
		log.debug("Flattening obsgroup '{}' (concept={}) at path: {}", labelText, groupingConceptId, newPath);
		walkHfeElements(groupElement, sheet, form, locale, newPath);
	}
	
	/**
	 * Handle &lt;encounterDate/&gt; element - creates encounter date/time field.
	 */
	private void handleEncounterDate(Element element, SheetModel sheet, FormModel form, Locale locale) {
		if (sheet.fields.stream().anyMatch(f -> FIELD_ID_ENCOUNTER_DATE.equals(f.id))) {
			return;
		}
		
		FieldModel field = new FieldModel();
		field.id = FIELD_ID_ENCOUNTER_DATE;
		field.label = "Encounter Date";
		field.datatype = FieldDatatype.DATE;
		field.requiredAlways = true;
		sheet.fields.add(field);
		log.debug("Added encounterDate field");
	}
	
	/**
	 * Handle &lt;encounterLocation/&gt; element - creates location picker field.
	 */
	private void handleEncounterLocation(Element element, SheetModel sheet, FormModel form, Locale locale) {
		if (sheet.fields.stream().anyMatch(f -> FIELD_ID_ENCOUNTER_LOCATION.equals(f.id))) {
			return;
		}
		
		FieldModel field = new FieldModel();
		field.id = FIELD_ID_ENCOUNTER_LOCATION;
		field.label = "Encounter Location";
		field.datatype = FieldDatatype.CODED;
		field.uiSelectSourceType = "encounterLocation";
		field.requiredAlways = true;
		sheet.fields.add(field);
		log.debug("Added encounterLocation field");
	}
	
	/**
	 * Handle &lt;encounterProvider/&gt; element - creates provider picker field.
	 */
	private void handleEncounterProvider(Element element, SheetModel sheet, FormModel form, Locale locale) {
		if (sheet.fields.stream().anyMatch(f -> FIELD_ID_ENCOUNTER_PROVIDER.equals(f.id))) {
			return;
		}
		
		String role = getAttributeOrNull(element, "role");
		FieldModel field = new FieldModel();
		field.id = FIELD_ID_ENCOUNTER_PROVIDER;
		field.label = (role != null) ? "Encounter Provider (" + role + ")" : "Encounter Provider";
		field.datatype = FieldDatatype.CODED;
		field.uiSelectSourceType = "encounterProvider";
		field.requiredAlways = true;
		sheet.fields.add(field);
		log.debug("Added encounterProvider field");
	}
	
	/**
	 * Handle &lt;repeat/&gt; element with template expansion.
	 */
	private void handleRepeat(Element repeatElement, SheetModel sheet, FormModel form, Locale locale, String path) {
		String withAttr = getAttributeOrNull(repeatElement, ATTR_WITH);
		if (withAttr == null || withAttr.trim().isEmpty()) {
			log.warn(ERROR_REPEAT_NO_WITH);
			return;
		}
		
		List<List<String>> tuples = parseRepeatWith(withAttr);
		if (tuples.isEmpty()) {
			log.warn(ERROR_REPEAT_NO_TUPLES + "{}", withAttr);
			return;
		}
		
		log.debug("Expanding repeat with {} iterations", tuples.size());
		
		for (int i = 0; i < tuples.size(); i++) {
			String iterPath = path + "/repeat[" + i + "]";
			walkHfeElementsWithSubstitution(repeatElement, sheet, form, locale, iterPath, tuples.get(i));
		}
	}
	
	/**
	 * Parse HFE repeat 'with' attribute into list of tuples.
	 */
	private List<List<String>> parseRepeatWith(String withAttr) {
		List<List<String>> result = new ArrayList<>();
		
		try {
			List<String> tupleStrings = new ArrayList<>();
			int depth = 0;
			int start = 0;
			
			for (int i = 0; i < withAttr.trim().length(); i++) {
				char c = withAttr.charAt(i);
				if (c == '[') {
					if (depth == 0) {
						start = i;
					}
					depth++;
				} else if (c == ']') {
					depth--;
					if (depth == 0) {
						tupleStrings.add(withAttr.substring(start + 1, i));
					}
				}
			}
			
			for (String tupleStr : tupleStrings) {
				List<String> tuple = parseTuple(tupleStr);
				if (!tuple.isEmpty()) {
					result.add(tuple);
				}
			}
		}
		catch (Exception e) {
			log.error("Failed to parse repeat 'with' attribute: {}", withAttr, e);
		}
		
		return result;
	}
	
	/**
	 * Parse a single tuple string by splitting on commas while respecting quoted values.
	 */
	private List<String> parseTuple(String tupleStr) {
		List<String> values = new ArrayList<>();
		boolean inQuote = false;
		char quoteChar = 0;
		StringBuilder current = new StringBuilder();
		
		for (int i = 0; i < tupleStr.length(); i++) {
			char c = tupleStr.charAt(i);
			
			if (!inQuote && (c == '\'' || c == '"')) {
				inQuote = true;
				quoteChar = c;
			} else if (inQuote && c == quoteChar) {
				inQuote = false;
				values.add(current.toString());
				current = new StringBuilder();
			} else if (inQuote) {
				current.append(c);
			}
		}
		
		return values;
	}
	
	/**
	 * Walk HFE elements with placeholder substitution. Replaces {0}, {1}, {2}, etc. with values from
	 * tuple.
	 */
	private void walkHfeElementsWithSubstitution(Element element, SheetModel sheet, FormModel form, Locale locale,
	        String path, List<String> tuple) {
		NodeList children = element.getChildNodes();
		
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			
			Element child = (Element) node;
			String tagName = child.getTagName().toLowerCase();
			
			Element substituted = substituteElement(child, tuple);
			
			switch (tagName) {
				case "obs":
					handleObs(substituted, sheet, form, locale, path);
					break;
				case "obsgroup":
					handleObsGroup(substituted, sheet, form, locale, path);
					break;
				case "encounterdate":
					handleEncounterDate(substituted, sheet, form, locale);
					break;
				case "encounterlocation":
					handleEncounterLocation(substituted, sheet, form, locale);
					break;
				case "encounterprovider":
					handleEncounterProvider(substituted, sheet, form, locale);
					break;
				case "repeat":
					handleRepeat(substituted, sheet, form, locale, path);
					break;
				default:
					walkHfeElementsWithSubstitution(substituted, sheet, form, locale, path, tuple);
					break;
			}
		}
	}
	
	/**
	 * Clone element and substitute {0}, {1}, {2} placeholders with tuple values.
	 */
	private Element substituteElement(Element element, List<String> tuple) {
		try {
			Element cloned = (Element) element.cloneNode(true);
			substituteAttributesAndText(cloned, tuple);
			return cloned;
		}
		catch (Exception e) {
			log.warn("Failed to clone element for substitution: {}", e.getMessage());
			return element;
		}
	}
	
	/**
	 * Recursively substitute placeholders in attributes and text content.
	 */
	private void substituteAttributesAndText(Element element, List<String> tuple) {
		// Substitute attributes
		if (element.hasAttributes()) {
			for (int i = 0; i < element.getAttributes().getLength(); i++) {
				Node attr = element.getAttributes().item(i);
				String value = attr.getNodeValue();
				if (value != null) {
					attr.setNodeValue(substitutePlaceholders(value, tuple));
				}
			}
		}
		
		// Substitute text nodes
		NodeList children = element.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.TEXT_NODE && child.getNodeValue() != null) {
				child.setNodeValue(substitutePlaceholders(child.getNodeValue(), tuple));
			} else if (child.getNodeType() == Node.ELEMENT_NODE) {
				substituteAttributesAndText((Element) child, tuple);
			}
		}
	}
	
	/**
	 * Replace {0}, {1}, {2} with values from tuple.
	 */
	private String substitutePlaceholders(String text, List<String> tuple) {
		if (text == null || tuple == null) {
			return text;
		}
		
		String result = text;
		for (int i = 0; i < tuple.size(); i++) {
			String placeholder = "{" + i + "}";
			String value = tuple.get(i);
			result = result.replace(placeholder, value != null ? value : "");
		}
		
		return result;
	}
	
	private String resolveConceptUuid(String conceptId) {
		return conceptAnswersService.resolveConceptUuid(conceptId);
	}
	
	private String resolveLabelText(String labelText, String labelCode, String conceptUuid, Locale locale) {
		if (labelText != null && !labelText.trim().isEmpty()) {
			return labelText.trim();
		}
		
		if (labelCode != null && !labelCode.trim().isEmpty()) {
			if (messageSourceService != null) {
				try {
					String message = messageSourceService.getMessage(labelCode, null, locale);
					if (message != null && !message.trim().isEmpty()) {
						log.debug("Resolved labelCode '{}' to '{}'", labelCode, message);
						return message;
					}
				}
				catch (Exception e) {
					log.debug("Failed to resolve labelCode '{}' using MessageSourceService: {}", labelCode, e.getMessage());
				}
			}
			
			log.debug("Using labelCode '{}' as fallback text (MessageSourceService {} available)", labelCode,
			    messageSourceService != null ? "was" : "not");
			return labelCode;
		}
		
		if (conceptUuid != null) {
			try {
				String conceptName = conceptAnswersService.getConceptName(conceptUuid);
				if (conceptName != null && !conceptName.trim().isEmpty()) {
					return conceptName;
				}
			}
			catch (Exception e) {
				log.debug("Could not resolve concept name for {}", conceptUuid);
			}
		}
		
		return "Field";
	}
	
	private FieldDatatype inferDatatypeAndRendering(Element obsElement, String conceptUuid, String style,
	        String answerConceptIds, String answerConceptId) {
		
		FieldDatatype widgetType = inferFromWidgetStructure(obsElement);
		if (widgetType != null) {
			return widgetType;
		}
		
		if (style != null) {
			String s = style.toLowerCase();
			if (s.contains("date")) {
				return FieldDatatype.DATE;
			}
			if (s.contains("number") || s.contains("numeric")) {
				return FieldDatatype.NUMERIC;
			}
			if (s.contains("location") || s.contains("drug")) {
				return FieldDatatype.CODED;
			}
		}
		
		if ((answerConceptIds != null && !answerConceptIds.trim().isEmpty())
		        || (answerConceptId != null && !answerConceptId.trim().isEmpty())) {
			return FieldDatatype.CODED;
		}
		
		try {
			Concept concept = conceptAnswersService.getConceptByUuid(conceptUuid);
			if (concept != null) {
				if (concept.getConceptClass() != null) {
					String conceptClassName = concept.getConceptClass().getName();
					if (conceptClassName != null && conceptClassName.toLowerCase().contains("drug")) {
						return FieldDatatype.CODED;
					}
				}
				
				FieldDatatype fieldDatatype = conceptAnswersService.convertToFieldDatatype(concept.getDatatype());
				if (fieldDatatype != FieldDatatype.TEXT) {
					return fieldDatatype;
				}
				
				ConceptDatatype dt = concept.getDatatype();
				if (dt != null) {
					String dtName = dt.getHl7Abbreviation();
					if (dtName != null) {
						switch (dtName.toUpperCase()) {
							case "TM":
								return FieldDatatype.DATETIME;
							case "CNE":
								return FieldDatatype.CODED;
						}
					}
				}
			}
		}
		catch (Exception e) {
			log.debug("Could not infer datatype from concept {}", conceptUuid);
		}
		
		return FieldDatatype.TEXT;
	}
	
	private FieldDatatype inferFromWidgetStructure(Element obsElement) {
		if (hasChildElement(obsElement, "select")) {
			return FieldDatatype.CODED;
		}
		
		int checkboxCount = countInputsByType(obsElement, "checkbox");
		if (checkboxCount > 1) {
			return FieldDatatype.CODED;
		} else if (checkboxCount == 1) {
			return FieldDatatype.CODED;
		}
		
		if (countInputsByType(obsElement, "radio") > 0) {
			return FieldDatatype.CODED;
		}
		
		if (countInputsByType(obsElement, "date") > 0) {
			return FieldDatatype.DATE;
		}
		
		if (countInputsByType(obsElement, "number") > 0) {
			return FieldDatatype.NUMERIC;
		}
		
		if (hasChildElement(obsElement, "textarea")) {
			return FieldDatatype.TEXT;
		}
		
		return null;
	}
	
	private boolean hasChildElement(Element element, String tagName) {
		NodeList nodes = element.getElementsByTagName(tagName);
		return nodes != null && nodes.getLength() > 0;
	}
	
	private int countInputsByType(Element element, String inputType) {
		NodeList inputs = element.getElementsByTagName("input");
		if (inputs == null || inputs.getLength() == 0) {
			return 0;
		}
		
		int count = 0;
		for (int i = 0; i < inputs.getLength(); i++) {
			Node node = inputs.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element inputElement = (Element) node;
				String type = inputElement.getAttribute("type");
				if (type != null && type.equalsIgnoreCase(inputType)) {
					count++;
				}
			}
		}
		return count;
	}
	
	/**
	 * Parse answer concepts into AnswerModel list
	 */
	private void parseAnswers(FieldModel field, String answerConceptIds, String answerLabels, Locale locale) {
		String[] ids = answerConceptIds.split(",");
		String[] labels = answerLabels != null ? answerLabels.split(",") : new String[0];
		
		for (int i = 0; i < ids.length; i++) {
			String conceptId = ids[i].trim();
			String conceptUuid = resolveConceptUuid(conceptId);
			
			if (conceptUuid == null) {
				log.warn("Could not resolve answer concept '{}' - skipping", conceptId);
				continue;
			}
			
			String label = i < labels.length ? labels[i].trim() : null;
			
			if (label == null || label.isEmpty()) {
				try {
					String conceptName = conceptAnswersService.getConceptName(conceptUuid);
					label = conceptName != null && !conceptName.trim().isEmpty() ? conceptName : conceptUuid;
				}
				catch (Exception e) {
					label = conceptUuid;
				}
			}
			
			field.answers.add(new AnswerModel(label, conceptUuid, conceptUuid));
		}
	}
	
	/**
	 * Parse single answer concept.
	 */
	private void parseSingleAnswer(FieldModel field, String answerConceptId, String answerLabel, Locale locale) {
		String conceptUuid = resolveConceptUuid(answerConceptId);
		
		if (conceptUuid == null) {
			return;
		}
		
		String label = answerLabel;
		
		if (label == null || label.trim().isEmpty()) {
			try {
				String conceptName = conceptAnswersService.getConceptName(conceptUuid);
				label = conceptName != null && !conceptName.trim().isEmpty() ? conceptName : conceptUuid;
			}
			catch (Exception e) {
				label = conceptUuid;
			}
		}
		
		field.answers.add(new AnswerModel(label, conceptUuid, conceptUuid));
		log.debug("Added single answer: {} ({})", label, conceptUuid);
	}
	
	private boolean hasConditionalLogic(Element element) {
		return element.hasAttribute("showDate") || element.hasAttribute("hideDate") || element.hasAttribute("showId")
		        || element.hasAttribute("hideId") || element.hasAttribute("when");
	}
	
	private String generateFieldId(String conceptId, String labelText, String path, int index) {
		String candidate = null;
		
		if (conceptId != null && !conceptId.trim().isEmpty()) {
			candidate = "concept_" + sanitize(conceptId);
		} else if (labelText != null && !labelText.trim().isEmpty()) {
			candidate = sanitize(labelText);
		} else if (path != null && !path.trim().isEmpty()) {
			candidate = sanitize(path) + "_field_" + index;
		} else {
			candidate = "field_" + index;
		}
		
		if (ExcelNameUtils.isExcelCellReference(candidate)) {
			candidate = "field_" + candidate;
		}
		
		return candidate;
	}
	
	private String sanitizeFieldId(String id) {
		String sanitized = sanitize(id);
		if (ExcelNameUtils.isExcelCellReference(sanitized)) {
			return "field_" + sanitized;
		}
		return sanitized;
	}
	
	private String sanitize(String str) {
		if (str == null || str.trim().isEmpty()) {
			return "field";
		}
		return str.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "").toLowerCase();
	}
	
	
	private String getAttributeOrNull(Element element, String attributeName) {
		if (element.hasAttribute(attributeName)) {
			String value = element.getAttribute(attributeName);
			return value != null && !value.trim().isEmpty() ? value.trim() : null;
		}
		return null;
	}
}
