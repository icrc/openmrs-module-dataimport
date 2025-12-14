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
import org.jsoup.nodes.Element;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;

import java.util.HashMap;
import java.util.Map;

public class HtmlObsTagExtractor {
	
	public static HtmlFormDataPoint extractObsTag(Element obsTag) {
		String conceptUUId = obsTag.attr("conceptId");
		Concept concept = HtmlFormConversionUtil.getConceptByUuidOrId(conceptUUId);
		
		String conceptUuidToUse;
		if (concept == null) {
			System.out.println("Concept UUID Invalid: " + conceptUUId + " - creating field anyway with placeholder");
			conceptUuidToUse = conceptUUId;
		} else {
			conceptUuidToUse = concept.getUuid();
		}
		
		HtmlFormDataPoint dataPoint = new HtmlFormDataPoint();
		String requiredField = obsTag.attr("required");
		String idAttr = obsTag.attr("id");
		String obsLabel = HtmlFormConversionUtil.extractQuestionLabel(obsTag);
		
		if (StringUtils.isBlank(obsLabel) && concept == null && StringUtils.isNotBlank(idAttr)) {
			obsLabel = convertIdToLabel(idAttr);
		}
		
		dataPoint.setQuestionLabel(obsLabel);
		
		if (StringUtils.isNotBlank(requiredField) && requiredField.equals("true")) {
			dataPoint.setRequiredField(true);
		}
		
		if (StringUtils.isNotBlank(idAttr)) {
			dataPoint.setFormFieldId(idAttr);
		}
		
		dataPoint.setConceptUUID(conceptUuidToUse);
		dataPoint.setDataType("obs");
		
		String rendering = obsTag.attr("style");
		String hasRowsAttr = obsTag.attr("rows");
		String hasColsAttr = obsTag.attr("cols");
		
		if (concept != null) {
			dataPoint.setConceptId(concept.getConceptId());
			dataPoint.setConceptName(concept.getName().getName());
			
			if (concept.getDatatype().isCoded()) {
				
				if (StringUtils.isNotBlank(rendering)) {
					if (rendering.equals("radio")) {
						dataPoint.setRendering(rendering);
					} else if (rendering.equals("checkbox")) {
						dataPoint.setRendering("multiCheckbox");
					} else if (rendering.equals("dropdown")) {
						dataPoint.setRendering("select");
					}
				} else {
					dataPoint.setRendering("select");
				}
				
				String answerConceptId = obsTag.attr("answerConceptId");
				String answerConceptIds = obsTag.attr("answerConceptIds");
				String answers = obsTag.attr("answers");
				String answerClasses = obsTag.attr("answerClasses");
				
				String answerLabel = obsTag.attr("answerLabel");
				String answerLabels = obsTag.attr("answerLabels");
				String answerCodes = obsTag.attr("answerCodes");
				Map<String, String> codedAnswerMap = new HashMap<String, String>();
				
				if (StringUtils.isNotBlank(answerConceptId)) {
					Concept ansConcept = HtmlFormConversionUtil.getConceptByUuidOrId(answerConceptId);
					
					if (ansConcept != null) {
						codedAnswerMap.put(ansConcept.getUuid(),
						    StringUtils.isNotBlank(answerLabel) ? answerLabel : ansConcept.getName().getName());
					}
				} else if (StringUtils.isNotBlank(answers)) {
					String[] answersArray = answers.split(",");
					String[] codesArray = null;
					if (StringUtils.isNotBlank(answerCodes)) {
						codesArray = answerCodes.split(",");
					} else if (StringUtils.isNotBlank(answerLabels)) {
						codesArray = answerLabels.split(",");
					}
					
					boolean hasLabels = codesArray != null && codesArray.length > 0;
					for (int i = 0; i < answersArray.length; i++) {
						Concept ansConcept = HtmlFormConversionUtil.getConceptByUuidOrId(answersArray[i].trim());
						if (ansConcept != null) {
							String ansLabel = hasLabels && i < codesArray.length ? codesArray[i].trim()
							        : ansConcept.getName().getName();
							codedAnswerMap.put(ansConcept.getUuid(), ansLabel);
						} else {
							String ansId = answersArray[i].trim();
							String ansLabel = hasLabels && i < codesArray.length ? codesArray[i].trim() : ansId;
							codedAnswerMap.put(ansId, ansLabel);
						}
					}
				} else if (StringUtils.isNotBlank(answerConceptIds)) {
					String[] conceptArray = answerConceptIds.split(",");
					String[] labelsArray = null;
					if (StringUtils.isNotBlank(answerLabels)) {
						labelsArray = answerLabels.split(",");
					}
					
					boolean hasCustomLabels = labelsArray != null && labelsArray.length > 0;
					for (int i = 0; i < conceptArray.length; i++) {
						Concept ansConcept = HtmlFormConversionUtil.getConceptByUuidOrId(conceptArray[i].trim());
						if (ansConcept != null) {
							String ansConceptUuid = ansConcept.getUuid();
							String ansLabel = hasCustomLabels ? labelsArray[i] : ansConcept.getName().getName();
							codedAnswerMap.put(ansConceptUuid, ansLabel.trim());
						}
					}
				} else {
					for (ConceptAnswer conceptAnswer : concept.getAnswers()) {
						codedAnswerMap.put(conceptAnswer.getAnswerConcept().getUuid(),
						    conceptAnswer.getAnswerConcept().getName().getName());
					}
				}
				
				if (StringUtils.isNotBlank(answerClasses) && answerClasses.equals("Drug")) {
					dataPoint.setRendering("drug");
				}
				dataPoint.setAnswersList(codedAnswerMap);
			} else if (concept.getDatatype().isNumeric()) {
				dataPoint.setRendering("number");
			} else if (concept.getDatatype().isDate() || concept.getDatatype().isDateTime()) {
				dataPoint.setRendering("date");
				
			} else if (concept.getDatatype().isText()) {
				
				if (StringUtils.isNotBlank(rendering)) {
					if (rendering.equals("textarea")) {
						dataPoint.setRendering(rendering);
					}
				} else if (StringUtils.isNotBlank(hasRowsAttr) || StringUtils.isNotBlank(hasColsAttr)) {
					dataPoint.setRendering("textarea");
				} else {
					dataPoint.setRendering("text");
				}
			} else if (concept.getDatatype().isBoolean()) {
				dataPoint.setRendering("select");
			}
		} else {
			dataPoint.setConceptId(null);
			dataPoint.setConceptName(StringUtils.isNotBlank(obsLabel) ? obsLabel : conceptUUId);
			
			if (StringUtils.isNotBlank(rendering)) {
				switch (rendering) {
					case "radio":
						dataPoint.setRendering("radio");
						break;
					case "checkbox":
						dataPoint.setRendering("multiCheckbox");
						break;
					case "dropdown":
						dataPoint.setRendering("select");
						break;
					case "textarea":
						dataPoint.setRendering("textarea");
						break;
					default:
						dataPoint.setRendering("text");
						break;
				}
			} else if (StringUtils.isNotBlank(hasRowsAttr) || StringUtils.isNotBlank(hasColsAttr)) {
				dataPoint.setRendering("textarea");
			} else {
				dataPoint.setRendering("text");
			}
			
			String answerConceptIds = obsTag.attr("answerConceptIds");
			String answers = obsTag.attr("answers");
			String answerLabels = obsTag.attr("answerLabels");
			String answerCodes = obsTag.attr("answerCodes");
			Map<String, String> codedAnswerMap = new HashMap<String, String>();
			
			if (StringUtils.isNotBlank(answers)) {
				String[] answersArray = answers.split(",");
				String[] codesArray = null;
				if (StringUtils.isNotBlank(answerCodes)) {
					codesArray = answerCodes.split(",");
				} else if (StringUtils.isNotBlank(answerLabels)) {
					codesArray = answerLabels.split(",");
				}
				
				boolean hasLabels = codesArray != null && codesArray.length > 0;
				for (int i = 0; i < answersArray.length; i++) {
					String ansId = answersArray[i].trim();
					String ansLabel = hasLabels && i < codesArray.length ? codesArray[i].trim() : ansId;
					codedAnswerMap.put(ansId, ansLabel);
				}
				dataPoint.setAnswersList(codedAnswerMap);
			} else if (StringUtils.isNotBlank(answerConceptIds)) {
				String[] conceptArray = answerConceptIds.split(",");
				String[] labelsArray = null;
				if (StringUtils.isNotBlank(answerLabels)) {
					labelsArray = answerLabels.split(",");
				}
				
				boolean hasCustomLabels = labelsArray != null && labelsArray.length > 0;
				for (int i = 0; i < conceptArray.length; i++) {
					String ansConceptId = conceptArray[i].trim();
					String ansLabel = hasCustomLabels && i < labelsArray.length ? labelsArray[i].trim() : ansConceptId;
					codedAnswerMap.put(ansConceptId, ansLabel);
				}
				dataPoint.setAnswersList(codedAnswerMap);
			}
		}
		
		return dataPoint;
	}
	
	/**
	 * Converts an id attribute to a human-readable label. E.g., "patient_weight_kg" -> "Patient Weight
	 * Kg", "assessSupineToSideLying" -> "Assess Supine To Side Lying"
	 */
	private static String convertIdToLabel(String id) {
		if (StringUtils.isBlank(id)) {
			return "";
		}
		String label = id.replaceAll("([a-z])([A-Z])", "$1 $2");
		label = label.replaceAll("[_-]", " ");
		String[] words = label.split("\\s+");
		StringBuilder result = new StringBuilder();
		for (String word : words) {
			if (!word.isEmpty()) {
				result.append(Character.toUpperCase(word.charAt(0)));
				if (word.length() > 1) {
					result.append(word.substring(1).toLowerCase());
				}
				result.append(" ");
			}
		}
		return result.toString().trim();
	}
}
