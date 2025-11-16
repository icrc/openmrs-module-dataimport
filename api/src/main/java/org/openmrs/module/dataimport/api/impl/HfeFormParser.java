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
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses HTML Form Entry (HFE) XML into Excel Template Model (ETM).
 * <p>
 * Supported HFE elements:
 * <ul>
 * <li>&lt;obs conceptId="..." labelText="..."/&gt; - Observation fields</li>
 * <li>&lt;obsgroup groupingConceptId="..."/&gt; - Observation groups (flattened)</li>
 * <li>&lt;encounterDate/&gt;, &lt;encounterLocation/&gt;, &lt;encounterProvider/&gt;</li>
 * <li>&lt;repeat with="..."/&gt; - Template expansion with placeholder substitution</li>
 * </ul>
 * <p>
 * Features:
 * <ul>
 * <li>Widget type detection (date, number, text, radio, select, checkbox)</li>
 * <li>Concept resolution (UUID, numeric ID, mappings like "CIEL:5089")</li>
 * <li>4-tier datatype inference (widget → style → answers → concept)</li>
 * <li>Conditional display detection (mapped to gray cell styling)</li>
 * <li>Answer auto-loading from concept dictionary</li>
 * </ul>
 *
 * @see <a href="https://guide.openmrs.org/collecting-data/html-forms/">OpenMRS HTML Forms Guide</a>
 */
public class HfeFormParser {
	
	private static final Logger log = LoggerFactory.getLogger(HfeFormParser.class);
	
	// Performance: Cache concept resolutions within a single parse operation
	// Key: conceptId (UUID, numeric ID, or mapping), Value: resolved UUID
	private final Map<String, String> conceptCache = new HashMap<>();
	
	// XML Security Features
	private static final String FEATURE_LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
	
	private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
	
	private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
	
	// HFE Element Names
	private static final String ELEMENT_OBS = "obs";
	
	private static final String ELEMENT_OBSGROUP = "obsgroup";
	
	private static final String ELEMENT_ENCOUNTER_DATE = "encounterdate";
	
	private static final String ELEMENT_ENCOUNTER_LOCATION = "encounterlocation";
	
	private static final String ELEMENT_ENCOUNTER_PROVIDER = "encounterprovider";
	
	private static final String ELEMENT_REPEAT = "repeat";
	
	// HFE Attributes
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
	
	// HTML Widget Elements
	private static final String WIDGET_SELECT = "select";
	
	private static final String WIDGET_INPUT = "input";
	
	private static final String WIDGET_TEXTAREA = "textarea";
	
	// Input Types
	private static final String INPUT_TYPE_CHECKBOX = "checkbox";
	
	private static final String INPUT_TYPE_RADIO = "radio";
	
	private static final String INPUT_TYPE_DATE = "date";
	
	private static final String INPUT_TYPE_NUMBER = "number";
	
	private static final String INPUT_ATTR_TYPE = "type";
	
	// Concept Datatype Names
	private static final String DATATYPE_NUMERIC = "Numeric";
	
	private static final String DATATYPE_CODED = "Coded";
	
	private static final String DATATYPE_TEXT = "Text";
	
	private static final String DATATYPE_DATE = "Date";
	
	private static final String DATATYPE_DATETIME = "Datetime";
	
	private static final String DATATYPE_BOOLEAN = "Boolean";
	
	// Style Keywords
	private static final String STYLE_DATE = "date";
	
	private static final String STYLE_LOCATION = "location";
	
	// Concept Mapping Separator
	private static final String CONCEPT_MAPPING_SEPARATOR = ":";
	
	// Field ID Components
	private static final String FIELD_ID_SEPARATOR = "_";
	
	private static final String FIELD_ID_ENCOUNTER_DATE = "encounterDate";
	
	private static final String FIELD_ID_ENCOUNTER_LOCATION = "encounterLocation";
	
	private static final String FIELD_ID_ENCOUNTER_PROVIDER = "encounterProvider";
	
	// Default Values
	private static final String DEFAULT_SHEET_NAME = "Main";
	
	private static final int MAX_SHEET_NAME_LENGTH = 31;
	
	// Regex Patterns
	private static final String EXCEL_CELL_REFERENCE_PATTERN = "^[A-Z]+[0-9]+$";
	
	// Error Messages
	private static final String ERROR_NULL_XML = "HFE XML cannot be null or empty";
	
	private static final String ERROR_NULL_FORM = "Form object cannot be null";
	
	private static final String ERROR_PARSE_FAILED = "Failed to parse HFE XML for form ";
	
	private static final String ERROR_REPEAT_NO_WITH = "Repeat element missing 'with' attribute - skipping";
	
	private static final String ERROR_REPEAT_NO_TUPLES = "No valid tuples found in repeat 'with' attribute: ";
	
	private static final String ERROR_CONCEPT_RESOLVE = "Error resolving concept '{}': {}";
	
	private static final String ERROR_CLONE_FAILED = "Failed to clone element for substitution: {}";
	
	/**
	 * Parse HFE XML into FormModel (ETM).
	 * <p>
	 * This method performs the following steps:
	 * <ol>
	 * <li>Validates input parameters</li>
	 * <li>Configures secure XML parsing (disables external entities)</li>
	 * <li>Parses XML into DOM structure</li>
	 * <li>Extracts form metadata from the Form object</li>
	 * <li>Walks the DOM tree to extract data fields</li>
	 * <li>Returns unified FormModel (ETM)</li>
	 * </ol>
	 *
	 * @param hfeXml The HFE XML string (required, non-empty)
	 * @param form The OpenMRS Form object for metadata (required)
	 * @param locale Optional locale for label resolution (uses Context.getLocale() if null)
	 * @return Parsed FormModel with all extracted fields
	 * @throws IllegalArgumentException if hfeXml is null/empty, form is null, or XML parsing fails
	 */
	public FormModel parse(String hfeXml, org.openmrs.Form form, Locale locale) {
		if (hfeXml == null || hfeXml.trim().isEmpty()) {
			throw new IllegalArgumentException(ERROR_NULL_XML);
		}
		
		if (form == null) {
			throw new IllegalArgumentException(ERROR_NULL_FORM);
		}
		
		conceptCache.clear();
		
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
			sheet.sheetName = sanitizeSheetName(formModel.name);
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
	 * <p>
	 * Recognized elements:
	 * <ul>
	 * <li>&lt;obs&gt; - Creates observation field</li>
	 * <li>&lt;obsgroup&gt; - Flattens group and processes children</li>
	 * <li>&lt;encounterDate&gt; - Creates encounter date field</li>
	 * <li>&lt;encounterLocation&gt; - Creates location picker</li>
	 * <li>&lt;encounterProvider&gt; - Creates provider picker</li>
	 * <li>&lt;repeat&gt; - Expands template with placeholder substitution</li>
	 * </ul>
	 * All other elements are recursively traversed but not processed.
	 *
	 * @param element Current DOM element
	 * @param sheet Sheet to add fields to
	 * @param form FormModel for metadata
	 * @param locale Locale for label resolution
	 * @param path Current path in DOM tree (for debugging/field IDs)
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
					// Recursively walk unknown elements
					walkHfeElements(child, sheet, form, locale, path);
					break;
			}
		}
	}
	
	/**
	 * Handle &lt;obs/&gt; element - creates an observation field.
	 * <p>
	 * Extraction process:
	 * <ol>
	 * <li>Resolve concept UUID from conceptId (supports UUID, numeric ID, or mapping)</li>
	 * <li>Generate field ID (from explicit 'id' attribute or auto-generated)</li>
	 * <li>Resolve label text (from labelText, labelCode, or concept name)</li>
	 * <li>Infer datatype using 4-tier priority (widget → style → answers → concept)</li>
	 * <li>Parse answers (supports multiple or single answer formats)</li>
	 * <li>Detect required and conditional logic</li>
	 * </ol>
	 * <p>
	 * Supported attributes:
	 * <ul>
	 * <li>conceptId - Concept identifier (UUID, ID, or mapping like "CIEL:5089")</li>
	 * <li>labelText - Explicit label text</li>
	 * <li>labelCode - Message code for i18n label lookup</li>
	 * <li>answerConceptIds - Comma-separated answer concept IDs (multi-select)</li>
	 * <li>answerConceptId - Single answer concept ID (checkbox)</li>
	 * <li>answerLabels - Comma-separated answer labels (optional)</li>
	 * <li>answerLabel - Single answer label (optional)</li>
	 * <li>style - Style hint (e.g., "date", "text", "textarea")</li>
	 * <li>required - "true" if field is required</li>
	 * <li>id - Explicit field ID</li>
	 * </ul>
	 *
	 * @param obsElement The &lt;obs&gt; DOM element
	 * @param sheet Sheet to add field to
	 * @param form FormModel for metadata (unused but kept for consistency)
	 * @param locale Locale for label resolution
	 * @param path Current DOM path for field ID generation
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
		
		sheet.fields.add(field);
		log.debug("Added obs field '{}' (concept={}, datatype={})", field.label, conceptUuid, field.datatype);
	}
	
	/**
	 * Handle &lt;obsgroup/&gt; element - flattens observation group into sheet.
	 * <p>
	 * For Excel templates, observation groups are flattened (not hierarchical). This means all child
	 * &lt;obs&gt; elements within the group are added as individual columns to the sheet.
	 * <p>
	 * The group's label is tracked in the path for field ID generation, which helps maintain context
	 * when generating unique field IDs.
	 *
	 * @param groupElement The &lt;obsgroup&gt; DOM element
	 * @param sheet Sheet to add child fields to
	 * @param form FormModel for metadata
	 * @param locale Locale for label resolution
	 * @param path Current DOM path (updated with group label)
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
	 * <p>
	 * Creates a single DATE field for encounter datetime. Prevents duplicate encounterDate fields if
	 * multiple &lt;encounterDate&gt; tags exist.
	 * <p>
	 * The field is always marked as required.
	 *
	 * @param element The &lt;encounterDate&gt; DOM element
	 * @param sheet Sheet to add field to
	 * @param form FormModel for metadata (unused)
	 * @param locale Locale for label resolution (unused)
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
	 * <p>
	 * The uiSelectSourceType triggers location loading during template enrichment.
	 *
	 * @param element The &lt;encounterLocation&gt; DOM element
	 * @param sheet Sheet to add field to
	 * @param form FormModel for metadata (unused)
	 * @param locale Locale for label resolution (unused)
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
	 * <p>
	 * The uiSelectSourceType triggers provider loading during template enrichment. Supports optional
	 * 'role' attribute for contextual labeling.
	 *
	 * @param element The &lt;encounterProvider&gt; DOM element
	 * @param sheet Sheet to add field to
	 * @param form FormModel for metadata (unused)
	 * @param locale Locale for label resolution (unused)
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
	 * <p>
	 * Parses the 'with' attribute containing tuples of values, then expands the template for each tuple
	 * by substituting placeholders ({0}, {1}, {2}, etc.) with actual values.
	 * <p>
	 * Example: <pre>
	 * &lt;repeat with="['label1','CONCEPT:1'], ['label2','CONCEPT:2']"&gt;
	 *   &lt;obs conceptId="{1}" labelText="{0}"/&gt;
	 * &lt;/repeat&gt;
	 * </pre> Expands to two obs fields with concepts CONCEPT:1 and CONCEPT:2.
	 *
	 * @param repeatElement The &lt;repeat&gt; element
	 * @param sheet Sheet to add expanded fields to
	 * @param form FormModel for metadata
	 * @param locale Locale for label resolution
	 * @param path Current path for debugging
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
	 * <p>
	 * Uses bracket depth tracking to find tuple boundaries, then parses each tuple.
	 * <p>
	 * Example: "['a','b'], ['c','d']" → [["a","b"], ["c","d"]]
	 *
	 * @param withAttr The 'with' attribute value
	 * @return List of tuples (each tuple is a list of strings)
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
	 * <p>
	 * Example: "'value1','value2','value3'" → ["value1", "value2", "value3"]
	 *
	 * @param tupleStr The tuple string to parse
	 * @return List of values extracted from the tuple
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
	
	/**
	 * Resolve concept ID to UUID with caching for performance.
	 * <p>
	 * Supports three formats:
	 * <ul>
	 * <li>UUID: "5089AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"</li>
	 * <li>Numeric ID: "5089"</li>
	 * <li>Mapping: "CIEL:5089" or "PIH:WEIGHT"</li>
	 * </ul>
	 * <p>
	 * Resolution order:
	 * <ol>
	 * <li>Check cache for previous resolution</li>
	 * <li>Try as UUID via ConceptService.getConceptByUuid()</li>
	 * <li>Try as numeric ID via ConceptService.getConcept(int)</li>
	 * <li>Try as mapping via ConceptService.getConceptByMapping()</li>
	 * </ol>
	 * <p>
	 * Results are cached per parse() invocation to avoid repeated lookups.
	 *
	 * @param conceptId The concept identifier (UUID, numeric ID, or mapping)
	 * @return Resolved concept UUID, or null if not found
	 */
	private String resolveConceptUuid(String conceptId) {
		if (conceptId == null || conceptId.trim().isEmpty()) {
			return null;
		}
		
		String normalized = conceptId.trim();
		
		// Check cache first (performance optimization)
		if (conceptCache.containsKey(normalized)) {
			return conceptCache.get(normalized);
		}
		
		String resolvedUuid = null;
		
		try {
			ConceptService cs = Context.getConceptService();
			Concept concept = cs.getConceptByUuid(normalized);
			if (concept != null) {
				resolvedUuid = concept.getUuid();
			}
			
			if (resolvedUuid == null) {
				try {
					int id = Integer.parseInt(normalized);
					concept = cs.getConcept(id);
					if (concept != null) {
						resolvedUuid = concept.getUuid();
					}
				}
				catch (NumberFormatException ignored) {
					// Not a numeric ID, continue
				}
			}
			
			if (resolvedUuid == null && normalized.contains(CONCEPT_MAPPING_SEPARATOR)) {
				String[] parts = normalized.split(CONCEPT_MAPPING_SEPARATOR, 2);
				if (parts.length == 2) {
					String source = parts[0].trim();
					String code = parts[1].trim();
					concept = cs.getConceptByMapping(code, source);
					if (concept != null) {
						resolvedUuid = concept.getUuid();
					}
				}
			}
			
		}
		catch (Exception e) {
			log.warn(ERROR_CONCEPT_RESOLVE, conceptId, e.getMessage());
		}
		
		conceptCache.put(normalized, resolvedUuid);
		
		return resolvedUuid;
	}
	
	/**
	 * Resolve label text from various sources Priority: labelText > labelCode (message lookup) >
	 * concept preferred name
	 */
	private String resolveLabelText(String labelText, String labelCode, String conceptUuid, Locale locale) {
		if (labelText != null && !labelText.trim().isEmpty()) {
			return labelText.trim();
		}
		
		// TODO: Implement message source lookup when available
		if (labelCode != null && !labelCode.trim().isEmpty()) {
			log.debug("labelCode '{}' found but message source lookup not implemented - using as fallback", labelCode);
			return labelCode;
		}
		
		if (conceptUuid != null) {
			try {
				ConceptService cs = Context.getConceptService();
				Concept concept = cs.getConceptByUuid(conceptUuid);
				if (concept != null && concept.getName(locale) != null) {
					return concept.getName(locale).getName();
				}
			}
			catch (Exception e) {
				log.debug("Could not resolve concept name for {}", conceptUuid);
			}
		}
		
		return "Field";
	}
	
	/**
	 * Infer datatype and rendering from multiple sources (ENHANCED with hfe-o3-converter insights)
	 * Priority: Widget structure > Style attribute > Answer attributes > Concept datatype
	 * 
	 * @param obsElement The obs DOM element (for widget structure inspection)
	 * @param conceptUuid The concept UUID
	 * @param style The style attribute value
	 * @param answerConceptIds Multiple answer concept IDs
	 * @param answerConceptId Single answer concept ID
	 * @return The inferred field datatype
	 */
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
			ConceptService cs = Context.getConceptService();
			Concept concept = cs.getConceptByUuid(conceptUuid);
			if (concept != null) {
				// Check if it's a drug concept
				if (concept.getConceptClass() != null) {
					String conceptClassName = concept.getConceptClass().getName();
					if (conceptClassName != null && conceptClassName.toLowerCase().contains("drug")) {
						return FieldDatatype.CODED;
					}
				}
				
				ConceptDatatype dt = concept.getDatatype();
				if (dt != null) {
					String dtName = dt.getHl7Abbreviation();
					if (dtName != null) {
						switch (dtName.toUpperCase()) {
							case "NM":
								return FieldDatatype.NUMERIC;
							case "DT":
								return FieldDatatype.DATE;
							case "TM":
							case "TS":
								return FieldDatatype.DATETIME;
							case "BIT":
								return FieldDatatype.BOOLEAN;
							case "CWE":
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
		
		return FieldDatatype.TEXT; // Default
	}
	
	/**
	 * Infer datatype from HTML widget structure (checkboxes, radios, selects, inputs) Uses standard DOM
	 * traversal (not jsoup selectors)
	 * 
	 * @param obsElement The obs DOM element
	 * @return The inferred datatype, or null if cannot determine
	 */
	private FieldDatatype inferFromWidgetStructure(Element obsElement) {
		if (hasChildElement(obsElement, "select")) {
			return FieldDatatype.CODED;
		}
		
		int checkboxCount = countInputsByType(obsElement, "checkbox");
		if (checkboxCount > 1) {
			return FieldDatatype.CODED; // Multi-select
		} else if (checkboxCount == 1) {
			return FieldDatatype.CODED; // Single checkbox (boolean-style coded)
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
	
	/**
	 * Check if element has child elements with given tag name (case-insensitive)
	 */
	private boolean hasChildElement(Element element, String tagName) {
		NodeList nodes = element.getElementsByTagName(tagName);
		return nodes != null && nodes.getLength() > 0;
	}
	
	/**
	 * Count input elements of a specific type within an element
	 */
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
			
			// If no label provided, fetch from concept
			if (label == null || label.isEmpty()) {
				try {
					ConceptService cs = Context.getConceptService();
					Concept concept = cs.getConceptByUuid(conceptUuid);
					if (concept != null && concept.getName(locale) != null) {
						label = concept.getName(locale).getName();
					} else {
						label = conceptUuid;
					}
				}
				catch (Exception e) {
					label = conceptUuid;
				}
			}
			
			field.answers.add(new AnswerModel(label, conceptUuid, conceptUuid));
		}
	}
	
	/**
	 * Parse single answer concept (for checkbox-style single answer fields)
	 * 
	 * @param field The field model to populate
	 * @param answerConceptId Single answer concept ID
	 * @param answerLabel Optional custom label
	 * @param locale Locale for concept name resolution
	 */
	private void parseSingleAnswer(FieldModel field, String answerConceptId, String answerLabel, Locale locale) {
		String conceptUuid = resolveConceptUuid(answerConceptId);
		
		if (conceptUuid == null) {
			log.warn("Could not resolve answer concept '{}' - skipping", answerConceptId);
			return;
		}
		
		String label = answerLabel;
		
		// If no label provided, fetch from concept
		if (label == null || label.trim().isEmpty()) {
			try {
				ConceptService cs = Context.getConceptService();
				Concept concept = cs.getConceptByUuid(conceptUuid);
				if (concept != null && concept.getName(locale) != null) {
					label = concept.getName(locale).getName();
				} else {
					label = conceptUuid;
				}
			}
			catch (Exception e) {
				label = conceptUuid;
			}
		}
		
		field.answers.add(new AnswerModel(label, conceptUuid, conceptUuid));
		log.debug("Added single answer: {} ({})", label, conceptUuid);
	}
	
	/**
	 * Check if element has conditional display logic
	 */
	private boolean hasConditionalLogic(Element element) {
		return element.hasAttribute("showDate") || element.hasAttribute("hideDate") || element.hasAttribute("showId")
		        || element.hasAttribute("hideId") || element.hasAttribute("when");
	}
	
	/**
	 * Generate field ID from available information
	 */
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
		
		// Prevent Excel cell reference conflicts
		if (isExcelCellReference(candidate)) {
			candidate = "field_" + candidate;
		}
		
		return candidate;
	}
	
	/**
	 * Sanitize field ID to remove special characters
	 */
	private String sanitizeFieldId(String id) {
		String sanitized = sanitize(id);
		if (isExcelCellReference(sanitized)) {
			return "field_" + sanitized;
		}
		return sanitized;
	}
	
	/**
	 * Sanitize string for use in IDs
	 */
	private String sanitize(String str) {
		if (str == null || str.trim().isEmpty()) {
			return "field";
		}
		return str.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_").replaceAll("^_|_$", "").toLowerCase();
	}
	
	/**
	 * Sanitize sheet name (Excel max 31 chars, no special chars)
	 */
	private String sanitizeSheetName(String name) {
		if (name == null || name.trim().isEmpty()) {
			return "Form";
		}
		String sanitized = name.replaceAll("[\\\\/*?\\[\\]:]", "_");
		return sanitized.substring(0, Math.min(28, sanitized.length()));
	}
	
	/**
	 * Check if string looks like an Excel cell reference (e.g., "A1", "Z99")
	 */
	private boolean isExcelCellReference(String str) {
		return str != null && str.matches("^[a-zA-Z]+\\d+$");
	}
	
	/**
	 * Get attribute value or null if not present
	 */
	private String getAttributeOrNull(Element element, String attributeName) {
		if (element.hasAttribute(attributeName)) {
			String value = element.getAttribute(attributeName);
			return value != null && !value.trim().isEmpty() ? value.trim() : null;
		}
		return null;
	}
}
