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

import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.openmrs.module.dataimport.api.util.ExcelNameUtils;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.RuleModel;
import org.openmrs.module.dataimport.model.SheetModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.Locale;

public class O3FormParser {
	
	private static final Logger log = LoggerFactory.getLogger(O3FormParser.class);
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private final ConceptAnswersService conceptAnswersService;
	
	@Autowired
	public O3FormParser(ConceptAnswersService conceptAnswersService) {
		this.conceptAnswersService = conceptAnswersService;
	}
	
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
			primary.sheetName = ExcelNameUtils
			        .sanitizeSheetName(form.name != null && !form.name.trim().isEmpty() ? form.name : "Form");
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
						String sectionLabel = getTextOrNull(section, "label");
						walkQuestions(form, primary, questions, "", sectionLabel);
					}
				}
			}
			return form;
		}
		catch (IOException | RuntimeException e) {
			throw new IllegalArgumentException("Failed to parse O3 schema", e);
		}
	}
	
	private void walkQuestions(FormModel form, SheetModel current, ArrayNode questions, String path, String sectionLabel) {
		for (JsonNode q : questions) {
			String qid = getTextOrNull(q, "id");
			String label = getTextOrNull(q, "label");
			JsonNode questionOptions = q.path("questionOptions");
			String rendering = getTextOrNull(questionOptions, "rendering");
			boolean isGroup = "group".equalsIgnoreCase(getTextOrNull(q, "type")) || q.has("questions");
			String hideExpr = getTextOrNull(q.path("hide"), "hideWhenExpression");
			String questionType = getTextOrNull(q, "type");
			
			if ("markdown".equals(rendering)) {
				continue;
			}
			
			if (isGroup) {
				ArrayNode child = getArrayOrNull(q, "questions");
				if (child != null) {
					String groupId = (qid != null && !qid.trim().isEmpty()) ? qid
					        : generateFallbackId(label, path, current.fields.size());
					walkQuestions(form, current, child, path + "/" + groupId, sectionLabel);
				}
				continue;
			}
			
			FieldModel field = new FieldModel();
			if (qid == null || qid.trim().isEmpty()) {
				field.id = generateFallbackId(label, path, current.fields.size());
			} else {
				field.id = qid;
			}
			field.label = label != null ? label : field.id;
			field.sectionLabel = sectionLabel;

			String conceptIdOrUuid = getTextOrNull(questionOptions, "concept");
			if (conceptIdOrUuid != null && !conceptIdOrUuid.trim().isEmpty()) {
				String resolvedUuid = conceptAnswersService.resolveConceptUuid(conceptIdOrUuid);
				if (resolvedUuid != null) {
					field.conceptUuid = resolvedUuid;
				} else {
					log.warn("Could not resolve concept '{}' for question '{}' - using as-is", conceptIdOrUuid, field.id);
					field.conceptUuid = conceptIdOrUuid;
				}
			}
			
			field.hideExpr = hideExpr;
			
			inferDatatypeAndAnswers(field, questionOptions, questionType);
			
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
	
	private void inferDatatypeAndAnswers(FieldModel field, JsonNode questionOptions, String questionType) {
		String rendering = questionOptions != null ? getTextOrNull(questionOptions, "rendering") : null;
		if ("date".equals(rendering))
			field.datatype = FieldDatatype.DATE;
		else if ("datetime".equals(rendering))
			field.datatype = FieldDatatype.DATETIME;
		else if ("number".equals(rendering))
			field.datatype = FieldDatatype.NUMERIC;
		else if ("toggle".equals(rendering))
			field.datatype = FieldDatatype.BOOLEAN;
		else if ("radio".equals(rendering) || "select".equals(rendering) || "checkbox".equals(rendering)
		        || "multiCheckbox".equals(rendering))
			field.datatype = FieldDatatype.CODED;
		else if ("ui-select-extended".equals(rendering)) {
			field.datatype = FieldDatatype.CODED;
			field.uiSelectSourceType = questionOptions != null ? getTextOrNull(questionOptions, "type") : null;
			if ((field.uiSelectSourceType == null || field.uiSelectSourceType.trim().isEmpty()) && questionType != null
			        && !questionType.trim().isEmpty()) {
				field.uiSelectSourceType = questionType;
			}
			if (questionOptions != null && questionOptions.has("datasource")) {
				JsonNode datasource = questionOptions.get("datasource");
				if (datasource != null && datasource.isObject()) {
					field.uiSelectDatasourceName = getTextOrNull(datasource, "name");
					JsonNode configNode = datasource.get("config");
					if (configNode != null && configNode.isObject()) {
						copyObjectNodeToMap(configNode, field.uiSelectDatasourceConfig);
					}
				}
			}
		} else
			field.datatype = FieldDatatype.TEXT;
		
		if ("multiCheckbox".equals(rendering)) {
			field.isMulti = true;
		}
		
		if (field.datatype == FieldDatatype.CODED && questionOptions.has("answers")) {
			JsonNode answersNode = questionOptions.get("answers");
			if (answersNode != null && answersNode.isArray()) {
				ArrayNode answersArray = (ArrayNode) answersNode;
				for (JsonNode answerNode : answersArray) {
					String answerLabel;
					String answerConceptIdOrUuid;
					String answerConceptUuid;
					
					if (answerNode.isTextual()) {
						answerConceptIdOrUuid = answerNode.asText();
						answerLabel = null;
					} else {
						answerLabel = getTextOrNull(answerNode, "label");
						answerConceptIdOrUuid = getTextOrNull(answerNode, "concept");
					}
					
					// Resolve answer concept through ConceptAnswersService (supports UUIDs and mappings)
					if (answerConceptIdOrUuid != null && !answerConceptIdOrUuid.trim().isEmpty()) {
						answerConceptUuid = conceptAnswersService.resolveConceptUuid(answerConceptIdOrUuid);
						
						// If no label provided, fetch from concept
						if (answerLabel == null || answerLabel.trim().isEmpty()) {
							if (answerConceptUuid != null) {
								String conceptName = conceptAnswersService.getConceptName(answerConceptUuid);
								answerLabel = conceptName != null && !conceptName.trim().isEmpty() ? conceptName
								        : answerConceptUuid;
							} else {
								answerLabel = answerConceptIdOrUuid; // Fallback to original value
								log.warn("Could not resolve answer concept '{}' - using as-is", answerConceptIdOrUuid);
							}
						}
					} else {
						answerConceptUuid = null;
					}
					
					String answerValue = answerConceptUuid != null ? answerConceptUuid
					        : (answerLabel != null ? answerLabel : "");
					field.answers.add(new AnswerModel(answerLabel, answerConceptUuid, answerValue));
				}
			}
		}
	}
	
	private static void copyObjectNodeToMap(JsonNode node, java.util.Map<String, String> target) {
		if (node == null || target == null || !node.isObject()) {
			return;
		}
		node.fields().forEachRemaining(entry -> {
			JsonNode valueNode = entry.getValue();
			String value;
			if (valueNode == null || valueNode.isNull()) {
				value = null;
			} else if (valueNode.isValueNode()) {
				value = valueNode.asText();
			} else {
				value = valueNode.toString();
			}
			target.put(entry.getKey(), value);
		});
	}
	
	private static String getTextOrNull(JsonNode node, String key) {
		return node != null && node.has(key) && !node.get(key).isNull() ? node.get(key).asText() : null;
	}
	
	private static ArrayNode getArrayOrNull(JsonNode node, String key) {
		return (node != null && node.has(key) && node.get(key).isArray()) ? (ArrayNode) node.get(key) : null;
	}
	
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
	
	private static String generateFallbackId(String label, String path, int fieldIndex) {
		String candidate = null;
		
		if (label != null && !label.trim().isEmpty()) {
			candidate = ExcelNameUtils.generateFallbackId(label);
		}
		if (candidate == null && path != null && !path.trim().isEmpty()) {
			String sanitized = path.trim().replaceAll("[^a-zA-Z0-9_/]", "_").replaceAll("_+", "_").replaceAll("^/|/$", "");
			if (!sanitized.isEmpty()) {
				candidate = sanitized.replace("/", "_") + "_field";
			}
		}
		if (candidate == null) {
			candidate = "field_" + fieldIndex;
		}
		
		if (ExcelNameUtils.isExcelCellReference(candidate)) {
			candidate = "field_" + candidate;
		}
		
		return candidate;
	}
}
