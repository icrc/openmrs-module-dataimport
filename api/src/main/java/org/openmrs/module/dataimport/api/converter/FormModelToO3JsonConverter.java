/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.converter;

import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.SheetModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Converts FormModel (from HFE parser) to O3 JSON format. This allows us to reuse the existing
 * O3FormParser for Excel template generation.
 */
public class FormModelToO3JsonConverter {
	
	private static final Logger log = LoggerFactory.getLogger(FormModelToO3JsonConverter.class);
	
	private final ObjectMapper objectMapper;
	
	public FormModelToO3JsonConverter() {
		this.objectMapper = new ObjectMapper();
	}
	
	/**
	 * Convert FormModel to O3 JSON string
	 */
	public String convert(FormModel formModel) {
		try {
			ObjectNode o3Form = convertToO3Json(formModel);
			return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o3Form);
		}
		catch (Exception e) {
			log.error("Failed to convert FormModel to O3 JSON", e);
			throw new RuntimeException("Failed to convert FormModel to O3 JSON", e);
		}
	}
	
	/**
	 * Convert FormModel to O3 JSON ObjectNode
	 */
	public ObjectNode convertToO3Json(FormModel formModel) {
		ObjectNode o3Form = objectMapper.createObjectNode();
		
		// Top-level form metadata
		o3Form.put("$schema", "http://json.openmrs.org/form.schema.json");
		o3Form.put("name", formModel.name != null ? formModel.name : "HFE Form");
		o3Form.put("uuid", formModel.formUuid != null ? formModel.formUuid : "generated-uuid");
		o3Form.put("version", formModel.version != null ? formModel.version : "1.0");
		o3Form.put("processor", "EncounterFormProcessor");
		o3Form.put("encounterType",
		    formModel.encounterTypeUuid != null ? formModel.encounterTypeUuid : "encounter-type-uuid");
		o3Form.put("published", false);
		o3Form.put("retired", false);
		
		// Convert sheets to pages
		ArrayNode pages = objectMapper.createArrayNode();
		for (SheetModel sheet : formModel.sheets) {
			ObjectNode page = convertSheetToPage(sheet);
			pages.add(page);
		}
		o3Form.set("pages", pages);
		
		// Empty referencedForms
		o3Form.set("referencedForms", objectMapper.createArrayNode());
		
		return o3Form;
	}
	
	/**
	 * Convert SheetModel to O3 Page
	 */
	private ObjectNode convertSheetToPage(SheetModel sheet) {
		ObjectNode page = objectMapper.createObjectNode();
		page.put("label", sheet.sheetName != null ? sheet.sheetName : "Main Page");
		
		// Create a single section containing all questions
		ObjectNode section = objectMapper.createObjectNode();
		section.put("label", sheet.sheetName != null ? sheet.sheetName + " Section" : "Main Section");
		section.put("isExpanded", true);
		
		// Convert fields to questions
		ArrayNode questions = objectMapper.createArrayNode();
		for (FieldModel field : sheet.fields) {
			ObjectNode question = convertFieldToQuestion(field);
			if (question != null) {
				questions.add(question);
			}
		}
		section.set("questions", questions);
		
		ArrayNode sections = objectMapper.createArrayNode();
		sections.add(section);
		page.set("sections", sections);
		
		return page;
	}
	
	/**
	 * Convert FieldModel to O3 Question
	 */
	private ObjectNode convertFieldToQuestion(FieldModel field) {
		ObjectNode question = objectMapper.createObjectNode();
		
		question.put("label", field.label != null ? field.label : "");
		question.put("type", "obs");
		question.put("id", field.id != null ? field.id : "field_" + field.label);
		
		// Question options
		ObjectNode questionOptions = objectMapper.createObjectNode();
		questionOptions.put("concept", field.conceptUuid != null ? field.conceptUuid : "");
		questionOptions.put("rendering", mapDatatypeToRendering(field.datatype));
		
		// Add answers for coded fields
		if (field.datatype == FieldDatatype.CODED && field.answers != null && !field.answers.isEmpty()) {
			ArrayNode answers = objectMapper.createArrayNode();
			for (AnswerModel answer : field.answers) {
				ObjectNode answerObj = objectMapper.createObjectNode();
				answerObj.put("concept", answer.conceptUuid != null ? answer.conceptUuid : answer.value);
				answerObj.put("label", answer.label != null ? answer.label : "");
				answers.add(answerObj);
			}
			questionOptions.set("answers", answers);
		}
		
		// Add validation for required fields
		if (field.requiredAlways) {
			ArrayNode validators = objectMapper.createArrayNode();
			ObjectNode requiredValidator = objectMapper.createObjectNode();
			requiredValidator.put("type", "required");
			validators.add(requiredValidator);
			question.set("validators", validators);
		}
		
		question.set("questionOptions", questionOptions);
		
		return question;
	}
	
	/**
	 * Map FieldDatatype to O3 rendering type
	 */
	private String mapDatatypeToRendering(FieldDatatype datatype) {
		if (datatype == null) {
			return "text";
		}
		
		switch (datatype) {
			case TEXT:
				return "text";
			case NUMERIC:
				return "number";
			case DATE:
				return "date";
			case DATETIME:
				return "datetime";
			case CODED:
				return "select";
			case BOOLEAN:
				return "checkbox";
			default:
				return "text";
		}
	}
}
