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

import static java.util.Objects.requireNonNull;

import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.RuleModel;
import org.openmrs.module.dataimport.model.SheetModel;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.Locale;

@Component
public class O3FormParser {
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	/**
	 * Parses O3 form schema JSON into the form model.
	 *
	 * @param schemaJson The O3 form schema JSON string
	 * @param locale The locale for parsing (reserved for future localization)
	 * @return FormModel object representing the parsed form
	 */
	public FormModel parse(String schemaJson, Locale locale) {
		try {
			JsonNode root = mapper.readTree(schemaJson);
			FormModel form = new FormModel();
			form.name = getTextOrNull(root, "name");
			form.formUuid = getTextOrNull(root, "uuid");
			form.version = getTextOrNull(root, "version");
			form.encounterTypeUuid = getTextOrNull(root, "encounterType");
			if (form.encounterTypeUuid == null && root.has("encounter")) {
				form.encounterTypeUuid = getTextOrNull(root.path("encounter"), "encounterType");
			}
			
			SheetModel primary = new SheetModel();
			primary.sheetName = sanitizeSheetName(form.name != null && !form.name.trim().isEmpty() ? form.name : "Form");
			form.sheets.add(primary);
			
			ArrayNode pages = getArrayOrNull(root, "pages");
			if (pages != null) {
				for (JsonNode page : pages) {
					ArrayNode sections = getArrayOrNull(page, "sections");
					if (sections == null)
						continue;
					for (JsonNode section : sections) {
						ArrayNode questions = getArrayOrNull(section, "questions");
						if (questions == null)
							continue;
						walkQuestions(form, primary, questions, "");
					}
				}
			}
			return form;
		}
		catch (IOException | RuntimeException e) {
			throw new IllegalArgumentException("Failed to parse O3 schema", e);
		}
	}
	
	private void walkQuestions(FormModel form, SheetModel current, ArrayNode questions, String path) {
		for (JsonNode q : questions) {
			String qid = getTextOrNull(q, "id");
			String label = getTextOrNull(q, "label");
			JsonNode questionOptions = q.path("questionOptions");
			String rendering = getTextOrNull(questionOptions, "rendering");
			boolean isRepeat = "repeating".equalsIgnoreCase(rendering) || questionOptions.has("repeatOptions");
			boolean isGroup = "group".equalsIgnoreCase(getTextOrNull(q, "type")) || q.has("questions");
			String hideExpr = getTextOrNull(q.path("hide"), "hideWhenExpression");
			
			if ("markdown".equals(rendering)) {
				continue;
			}
			
			if (isRepeat) {
				SheetModel repeat = new SheetModel();
				repeat.isRepeat = true;
				repeat.repeatGroupId = qid;
				repeat.sheetName = sanitizeSheetName((label != null ? label : qid) + " (repeat)");
				form.sheets.add(repeat);
				ArrayNode child = getArrayOrNull(q, "questions");
				if (child != null)
					walkQuestions(form, repeat, child, path + "/" + qid);
				continue;
			}
			
			if (isGroup) {
				ArrayNode child = getArrayOrNull(q, "questions");
				if (child != null)
					walkQuestions(form, current, child, path + "/" + qid);
				continue;
			}
			
			FieldModel field = new FieldModel();
			field.id = requireNonNull(qid, "form field id is required");
			field.label = label != null ? label : field.id;
			field.conceptUuid = getTextOrNull(questionOptions, "concept");
			field.hideExpr = hideExpr;
			
			inferDatatypeAndAnswers(field, questionOptions);
			
			JsonNode req = q.get("required");
			if (req != null) {
				if (req.isBoolean())
					field.requiredAlways = req.asBoolean();
				else if (req.isTextual())
					field.requiredExpr = req.asText();
				else if (req.isObject()) {
					field.requiredExpr = req.path("referenceQuestionId").asText() + " IN "
					        + req.path("referenceQuestionAnswers").toString();
				}
			}
			
			if (questionOptions.has("min"))
				field.min = parseDoubleOrNull(questionOptions.get("min"));
			if (questionOptions.has("max"))
				field.max = parseDoubleOrNull(questionOptions.get("max"));
			if (questionOptions.has("minLength"))
				field.minLength = parseIntOrNull(questionOptions.get("minLength"));
			if (questionOptions.has("maxLength"))
				field.maxLength = parseIntOrNull(questionOptions.get("maxLength"));
			
			current.fields.add(field);
			
			if (field.requiredExpr != null) {
				RuleModel rule = new RuleModel();
				rule.fieldId = field.id;
				rule.type = "required-if";
				rule.expr = field.requiredExpr;
				rule.message = "This field is required by schema";
				form.rules.add(rule);
			}
			if (field.hideExpr != null) {
				RuleModel rule = new RuleModel();
				rule.fieldId = field.id;
				rule.type = "relevance";
				rule.expr = field.hideExpr;
				rule.message = "Hidden when expression is true";
				form.rules.add(rule);
			}
			if (field.min != null || field.max != null || field.minLength != null || field.maxLength != null) {
				RuleModel rule = new RuleModel();
				rule.fieldId = field.id;
				rule.type = "constraint";
				rule.expr = String.format("min=%s,max=%s,minLen=%s,maxLen=%s", field.min, field.max, field.minLength,
				    field.maxLength);
				rule.message = "Value must satisfy constraints";
				form.rules.add(rule);
			}
		}
	}
	
	private void inferDatatypeAndAnswers(FieldModel field, JsonNode questionOptions) {
		String rendering = questionOptions != null ? getTextOrNull(questionOptions, "rendering") : null;
		if ("date".equals(rendering))
			field.datatype = FieldDatatype.DATE;
		else if ("datetime".equals(rendering))
			field.datatype = FieldDatatype.DATETIME;
		else if ("number".equals(rendering))
			field.datatype = FieldDatatype.NUMERIC;
		else if ("checkbox".equals(rendering) || "toggle".equals(rendering))
			field.datatype = FieldDatatype.BOOLEAN;
		else if ("radio".equals(rendering) || "select".equals(rendering) || "select-concept-answers".equals(rendering)
		        || "multiCheckbox".equals(rendering) || "content-switcher".equals(rendering)
		        || "checkbox-searchable".equals(rendering) || "ui-select-extended".equals(rendering)
		        || "encounter-location".equals(rendering) || "encounter-provider".equals(rendering)
		        || "encounter-role".equals(rendering) || "problem".equals(rendering)
		        || "workspace-launcher".equals(rendering))
			field.datatype = FieldDatatype.CODED;
		else
			field.datatype = FieldDatatype.TEXT;
		
		if (field.datatype == FieldDatatype.CODED && questionOptions.has("answers")) {
			JsonNode answersNode = questionOptions.get("answers");
			if (answersNode != null && answersNode.isArray()) {
				ArrayNode answersArray = (ArrayNode) answersNode;
				for (JsonNode answerNode : answersArray) {
					String answerLabel = getTextOrNull(answerNode, "label");
					String answerConceptUuid = getTextOrNull(answerNode, "concept");
					String answerValue = answerConceptUuid != null ? answerConceptUuid
					        : (answerLabel != null ? answerLabel : "");
					field.answers.add(new AnswerModel(answerLabel, answerConceptUuid, answerValue));
				}
			}
		}
	}
	
	/**
	 * Extracts text value from a JSON node field, returning null if not found.
	 */
	private static String getTextOrNull(JsonNode node, String key) {
		return node != null && node.has(key) && !node.get(key).isNull() ? node.get(key).asText() : null;
	}
	
	/**
	 * Extracts array from a JSON node field, returning null if not an array.
	 */
	private static ArrayNode getArrayOrNull(JsonNode node, String key) {
		return (node != null && node.has(key) && node.get(key).isArray()) ? (ArrayNode) node.get(key) : null;
	}
	
	/**
	 * Sanitizes string for use as Excel sheet name (max 28 chars, removes invalid chars).
	 */
	private static String sanitizeSheetName(String name) {
		return name.replaceAll("[\\\\/*?\\[\\]:]", "_").substring(0, Math.min(28, name.length()));
	}
	
	/**
	 * Safely parses Double from JsonNode, returning null on failure instead of throwing exception.
	 */
	private static Double parseDoubleOrNull(JsonNode node) {
		if (node == null)
			return null;
		try {
			if (node.isTextual()) {
				return Double.parseDouble(node.asText());
			} else if (node.isNumber()) {
				return node.asDouble();
			}
			return null;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}
	
	/**
	 * Safely parses Integer from JsonNode, returning null on failure instead of throwing exception.
	 */
	private static Integer parseIntOrNull(JsonNode node) {
		if (node == null)
			return null;
		try {
			if (node.isTextual()) {
				return Integer.parseInt(node.asText());
			} else if (node.isInt()) {
				return node.asInt();
			}
			return null;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}
}
