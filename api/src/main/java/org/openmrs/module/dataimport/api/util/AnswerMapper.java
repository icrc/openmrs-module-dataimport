/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.util;

import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for standardized answer mapping operations. Provides conversion between
 * List&lt;AnswerModel&gt; and Map&lt;String, String&gt; representations, and consistent label
 * normalization for answer lookups.
 */
public class AnswerMapper {
	
	private static final Logger log = LoggerFactory.getLogger(AnswerMapper.class);
	
	/**
	 * Converts a list of AnswerModel objects to a Map (label -> conceptUuid). Preserves insertion order
	 * using LinkedHashMap.
	 *
	 * @param answers List of AnswerModel objects
	 * @return Map with normalized labels as keys and concept UUIDs as values
	 */
	public static Map<String, String> toMap(List<AnswerModel> answers) {
		if (answers == null || answers.isEmpty()) {
			return new HashMap<>();
		}
		
		Map<String, String> map = new LinkedHashMap<>();
		
		for (AnswerModel answer : answers) {
			if (answer.label != null && answer.conceptUuid != null) {
				String normalizedLabel = DataImportConstants.normalizeLabel(answer.label);
				map.put(normalizedLabel, answer.conceptUuid);
			}
		}
		
		return map;
	}
	
	/**
	 * Converts a Map (label -> conceptUuid) to a list of AnswerModel objects.
	 *
	 * @param answersMap Map with labels as keys and concept UUIDs as values
	 * @return List of AnswerModel objects
	 */
	public static List<AnswerModel> toList(Map<String, String> answersMap) {
		if (answersMap == null || answersMap.isEmpty()) {
			return new ArrayList<>();
		}
		
		List<AnswerModel> answers = new ArrayList<>();
		
		for (Map.Entry<String, String> entry : answersMap.entrySet()) {
			String label = entry.getKey();
			String conceptUuid = entry.getValue();
			answers.add(new AnswerModel(label, conceptUuid, conceptUuid));
		}
		
		return answers;
	}
	
	/**
	 * Finds the concept UUID for a given answer label with normalization fallback.
	 * <p>
	 * Lookup strategy: 1. Direct match (exact label) 2. Normalized match (case-insensitive, trimmed)
	 *
	 * @param answersMap Map of label -> concept UUID
	 * @param label The answer label to look up
	 * @return The concept UUID, or null if not found
	 */
	public static String getAnswerConceptUuid(Map<String, String> answersMap, String label) {
		if (answersMap == null || label == null) {
			return null;
		}
		
		String direct = answersMap.get(label);
		if (direct != null) {
			return direct;
		}
		
		String normalizedTarget = DataImportConstants.normalizeLabel(label);
		for (Map.Entry<String, String> entry : answersMap.entrySet()) {
			String normalizedKey = DataImportConstants.normalizeLabel(entry.getKey());
			if (normalizedKey.equals(normalizedTarget)) {
				return entry.getValue();
			}
		}
		
		return null;
	}
	
	/**
	 * Finds the concept UUID for a given answer label from a list of AnswerModel objects.
	 *
	 * @param answers List of AnswerModel objects
	 * @param label The answer label to look up
	 * @return The concept UUID, or null if not found
	 */
	public static String getAnswerConceptUuid(List<AnswerModel> answers, String label) {
		if (answers == null || label == null) {
			return null;
		}
		
		String normalizedTarget = DataImportConstants.normalizeLabel(label);
		
		for (AnswerModel answer : answers) {
			if (answer.label != null) {
				if (answer.label.equals(label)) {
					return answer.conceptUuid;
				}
				
				String normalizedLabel = DataImportConstants.normalizeLabel(answer.label);
				if (normalizedLabel.equals(normalizedTarget)) {
					return answer.conceptUuid;
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Validates that an answer label exists in the allowed answers.
	 *
	 * @param answersMap Map of allowed answers
	 * @param label The answer label to validate
	 * @return true if the answer is valid
	 */
	public static boolean isValidAnswer(Map<String, String> answersMap, String label) {
		return getAnswerConceptUuid(answersMap, label) != null;
	}
	
	/**
	 * Validates that an answer label exists in the allowed answers list.
	 *
	 * @param answers List of allowed answers
	 * @param label The answer label to validate
	 * @return true if the answer is valid
	 */
	public static boolean isValidAnswer(List<AnswerModel> answers, String label) {
		return getAnswerConceptUuid(answers, label) != null;
	}
	
	/**
	 * Gets all answer labels from a map.
	 *
	 * @param answersMap Map of answers
	 * @return List of answer labels
	 */
	public static List<String> getAnswerLabels(Map<String, String> answersMap) {
		if (answersMap == null) {
			return new ArrayList<>();
		}
		return new ArrayList<>(answersMap.keySet());
	}
	
	/**
	 * Gets all answer labels from a list of AnswerModel objects.
	 *
	 * @param answers List of AnswerModel objects
	 * @return List of answer labels
	 */
	public static List<String> getAnswerLabels(List<AnswerModel> answers) {
		if (answers == null) {
			return new ArrayList<>();
		}
		
		List<String> labels = new ArrayList<>();
		for (AnswerModel answer : answers) {
			if (answer.label != null) {
				labels.add(answer.label);
			}
		}
		return labels;
	}
	
	/**
	 * Enriches answers with concept labels from the ConceptAnswersService. Useful when you have UUIDs
	 * but missing labels.
	 *
	 * @param answers List of AnswerModel objects (may have null labels)
	 * @return New list with labels populated from concepts
	 */
	public static List<AnswerModel> enrichAnswerLabels(List<AnswerModel> answers) {
		if (answers == null || answers.isEmpty()) {
			return answers;
		}
		
		ConceptAnswersService conceptService = Context.getRegisteredComponent("coreConceptAnswersProvider",
		    ConceptAnswersService.class);
		if (conceptService == null) {
			// Service not available - return original list
			return answers;
		}
		
		List<AnswerModel> enriched = new ArrayList<>();
		
		for (AnswerModel answer : answers) {
			if (answer.label != null && !answer.label.trim().isEmpty()) {
				// Label already exists, keep as is
				enriched.add(answer);
			} else if (answer.conceptUuid != null) {
				// Label missing, try to load from concept via service
				AnswerModel loaded = conceptService.loadSingleConcept(answer.conceptUuid);
				if (loaded != null) {
					enriched.add(loaded);
					log.debug("Enriched answer label for concept {}: {}", answer.conceptUuid, loaded.label);
				} else {
					// Keep original even if enrichment failed
					enriched.add(answer);
					log.warn("Could not enrich answer label for concept {}", answer.conceptUuid);
				}
			} else {
				// No UUID, can't enrich
				enriched.add(answer);
			}
		}
		
		return enriched;
	}
	
	/**
	 * Merges two answer lists, preferring entries from the first list when duplicates exist.
	 *
	 * @param primary Primary list (takes precedence)
	 * @param secondary Secondary list (used for missing entries)
	 * @return Merged list with no duplicate concept UUIDs
	 */
	public static List<AnswerModel> mergeAnswers(List<AnswerModel> primary, List<AnswerModel> secondary) {
		if (primary == null || primary.isEmpty()) {
			return secondary != null ? new ArrayList<>(secondary) : new ArrayList<>();
		}
		
		if (secondary == null || secondary.isEmpty()) {
			return new ArrayList<>(primary);
		}
		
		Map<String, AnswerModel> merged = new LinkedHashMap<>();
		
		// Add primary answers first
		for (AnswerModel answer : primary) {
			if (answer.conceptUuid != null) {
				merged.put(answer.conceptUuid, answer);
			}
		}
		
		// Add secondary answers if not already present
		for (AnswerModel answer : secondary) {
			if (answer.conceptUuid != null && !merged.containsKey(answer.conceptUuid)) {
				merged.put(answer.conceptUuid, answer);
			}
		}
		
		return new ArrayList<>(merged.values());
	}
	
	/**
	 * Creates a JSON string representation of an answer map. Format:
	 * {"label1":"uuid1","label2":"uuid2",...}
	 *
	 * @param answersMap Map of answers
	 * @return JSON string
	 */
	public static String toJson(Map<String, String> answersMap) {
		if (answersMap == null || answersMap.isEmpty()) {
			return "{}";
		}
		
		StringBuilder json = new StringBuilder("{");
		boolean first = true;
		
		for (Map.Entry<String, String> entry : answersMap.entrySet()) {
			if (!first) {
				json.append(",");
			}
			// Escape quotes in labels
			String escapedLabel = entry.getKey().replace("\"", "\\\"");
			String escapedUuid = entry.getValue().replace("\"", "\\\"");
			json.append("\"").append(escapedLabel).append("\":\"").append(escapedUuid).append("\"");
			first = false;
		}
		
		json.append("}");
		return json.toString();
	}
	
	/**
	 * Creates a JSON string representation of an answer list.
	 *
	 * @param answers List of AnswerModel objects
	 * @return JSON string
	 */
	public static String toJson(List<AnswerModel> answers) {
		return toJson(toMap(answers));
	}
}
