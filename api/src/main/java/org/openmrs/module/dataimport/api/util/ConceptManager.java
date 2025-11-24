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

import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptDatatype;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized utility for concept resolution, caching, and management. Provides a single point of
 * access for all concept-related operations across the module.
 */
public class ConceptManager {
	
	private static final Logger log = LoggerFactory.getLogger(ConceptManager.class);
	
	/**
	 * Default separator for concept mapping codes (e.g., "CIEL:5089")
	 */
	public static final String CONCEPT_MAPPING_SEPARATOR = ":";
	
	/**
	 * Module-wide concept UUID cache for performance Maps input identifier (UUID, ID, or mapping code)
	 * to resolved UUID
	 */
	private static final Map<String, String> conceptUuidCache = new ConcurrentHashMap<>();
	
	/**
	 * Module-wide concept object cache for performance Maps UUID to full Concept object
	 */
	private static final Map<String, Concept> conceptObjectCache = new ConcurrentHashMap<>();
	
	/**
	 * Module-wide concept answers cache Maps concept UUID to list of AnswerModel objects
	 */
	private static final Map<String, List<AnswerModel>> answersCache = new ConcurrentHashMap<>();
	
	/**
	 * Clears all caches. Use this when concept data may have changed.
	 */
	public static void clearCaches() {
		conceptUuidCache.clear();
		conceptObjectCache.clear();
		answersCache.clear();
		log.debug("ConceptManager caches cleared");
	}
	
	public static String resolveConceptUuid(String conceptId) {
		if (conceptId == null || conceptId.trim().isEmpty()) {
			return null;
		}
		
		String normalized = conceptId.trim();
		
		if (conceptUuidCache.containsKey(normalized)) {
			return conceptUuidCache.get(normalized);
		}
		
		String resolvedUuid = null;
		
		try {
			ConceptService cs = Context.getConceptService();
			
			Concept concept = cs.getConceptByUuid(normalized);
			if (concept != null) {
				resolvedUuid = concept.getUuid();
				conceptObjectCache.put(resolvedUuid, concept);
			}
			
			if (resolvedUuid == null) {
				try {
					int id = Integer.parseInt(normalized);
					concept = cs.getConcept(id);
					if (concept != null) {
						resolvedUuid = concept.getUuid();
						conceptObjectCache.put(resolvedUuid, concept);
					}
				}
				catch (NumberFormatException ignored) {
					// Not a numeric ID, continue to next strategy
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
						conceptObjectCache.put(resolvedUuid, concept);
						log.debug("Resolved concept mapping {}:{} to UUID {}", source, code, resolvedUuid);
					}
				}
			}
			
		}
		catch (Exception e) {
			log.warn("Error resolving concept identifier '{}': {}", conceptId, e.getMessage());
		}
		
		if (resolvedUuid != null) {
			conceptUuidCache.put(normalized, resolvedUuid);
		}
		return resolvedUuid;
	}
	
	/**
	 * Gets a Concept object by UUID with caching.
	 *
	 * @param conceptUuid The concept UUID
	 * @return The Concept object, or null if not found
	 */
	public static Concept getConceptByUuid(String conceptUuid) {
		if (conceptUuid == null || conceptUuid.trim().isEmpty()) {
			return null;
		}
		
		String normalized = conceptUuid.trim();
		
		if (conceptObjectCache.containsKey(normalized)) {
			return conceptObjectCache.get(normalized);
		}
		
		try {
			ConceptService cs = Context.getConceptService();
			Concept concept = cs.getConceptByUuid(normalized);
			
			if (concept != null) {
				conceptObjectCache.put(normalized, concept);
			}
			
			return concept;
		}
		catch (Exception e) {
			log.warn("Error loading concept by UUID '{}': {}", conceptUuid, e.getMessage());
			return null;
		}
	}
	
	/**
	 * Gets the display name for a concept with locale support.
	 * <p>
	 * Priority: 1. Preferred name in current locale 2. Any name in current locale 3. Display string
	 * (fallback)
	 *
	 * @param concept The Concept object
	 * @return The concept name, or null if not available
	 */
	public static String getConceptName(Concept concept) {
		if (concept == null) {
			return null;
		}
		
		Locale locale = Context.getLocale();
		
		if (concept.getPreferredName(locale) != null) {
			return concept.getPreferredName(locale).getName();
		}
		
		if (concept.getName(locale) != null) {
			return concept.getName(locale).getName();
		}
		
		return concept.getDisplayString();
	}
	
	/**
	 * Gets the display name for a concept by UUID.
	 *
	 * @param conceptUuid The concept UUID
	 * @return The concept name, or null if not found
	 */
	public static String getConceptName(String conceptUuid) {
		Concept concept = getConceptByUuid(conceptUuid);
		return getConceptName(concept);
	}
	
	/**
	 * Loads answers for a coded concept (either concept answers or set members). Results are cached for
	 * performance.
	 *
	 * @param conceptUuid The concept UUID
	 * @return List of AnswerModel objects, or empty list if no answers
	 */
	public static List<AnswerModel> loadAnswersForConcept(String conceptUuid) {
		if (conceptUuid == null || conceptUuid.trim().isEmpty()) {
			return Collections.emptyList();
		}
		
		String normalized = conceptUuid.trim();
		if (answersCache.containsKey(normalized)) {
			return answersCache.get(normalized);
		}
		
		try {
			Concept concept = getConceptByUuid(normalized);
			
			if (concept == null) {
				answersCache.put(normalized, Collections.emptyList());
				return Collections.emptyList();
			}
			
			List<AnswerModel> answers = new ArrayList<>();
			
			if (concept.getAnswers() != null && !concept.getAnswers().isEmpty()) {
				for (ConceptAnswer ca : concept.getAnswers()) {
					Concept answerConcept = ca.getAnswerConcept();
					if (answerConcept != null) {
						String label = getConceptName(answerConcept);
						String uuid = answerConcept.getUuid();
						answers.add(new AnswerModel(label, uuid, uuid));
					}
				}
				answersCache.put(normalized, answers);
				return answers;
			}
			
			if (concept.getSetMembers() != null && !concept.getSetMembers().isEmpty()) {
				for (Concept member : concept.getSetMembers()) {
					String label = getConceptName(member);
					String uuid = member.getUuid();
					answers.add(new AnswerModel(label, uuid, uuid));
				}
				answersCache.put(normalized, answers);
				return answers;
			}
			
			answersCache.put(normalized, Collections.emptyList());
			return Collections.emptyList();
			
		}
		catch (RuntimeException e) {
			log.error("Error loading answers for concept {}: {}", conceptUuid, e.getMessage(), e);
			answersCache.put(normalized, Collections.emptyList());
			return Collections.emptyList();
		}
	}
	
	/**
	 * Loads a single concept and returns it as an AnswerModel. Useful for enriching answer labels from
	 * UUIDs.
	 *
	 * @param conceptUuid The concept UUID
	 * @return AnswerModel with label and UUID, or null if not found
	 */
	public static AnswerModel loadSingleConceptAsAnswer(String conceptUuid) {
		if (conceptUuid == null || conceptUuid.trim().isEmpty()) {
			return null;
		}
		
		try {
			Concept concept = getConceptByUuid(conceptUuid);
			
			if (concept == null) {
				log.debug("Concept not found for UUID: {}", conceptUuid);
				return null;
			}
			
			if (concept.getRetired() != null && concept.getRetired()) {
				log.warn("Concept {} is retired, but using it anyway", conceptUuid);
			}
			
			String label = getConceptName(concept);
			if (label == null || label.trim().isEmpty()) {
				log.warn("Concept {} found but has no name - using UUID as fallback", conceptUuid);
				return null;
			}
			
			return new AnswerModel(label, conceptUuid, conceptUuid);
		}
		catch (RuntimeException e) {
			log.error("Error loading single concept {}: {}", conceptUuid, e.getMessage(), e);
			return null;
		}
	}
	
	/**
	 * Gets the datatype of a concept.
	 *
	 * @param conceptUuid The concept UUID
	 * @return The ConceptDatatype, or null if not found
	 */
	public static ConceptDatatype getConceptDatatype(String conceptUuid) {
		Concept concept = getConceptByUuid(conceptUuid);
		return (concept != null) ? concept.getDatatype() : null;
	}
	
	/**
	 * Converts OpenMRS ConceptDatatype to module FieldDatatype.
	 *
	 * @param conceptDatatype The OpenMRS ConceptDatatype
	 * @return The corresponding FieldDatatype, or TEXT as default
	 */
	public static FieldDatatype convertToFieldDatatype(ConceptDatatype conceptDatatype) {
		if (conceptDatatype == null) {
			return FieldDatatype.TEXT;
		}
		
		String hl7Code = conceptDatatype.getHl7Abbreviation();
		
		if (hl7Code == null) {
			return FieldDatatype.TEXT;
		}
		
		switch (hl7Code.toUpperCase()) {
			case "NM": // Numeric
				return FieldDatatype.NUMERIC;
			case "DT": // Date
				return FieldDatatype.DATE;
			case "TS": // Timestamp/DateTime
				return FieldDatatype.DATETIME;
			case "BIT": // Boolean
				return FieldDatatype.BOOLEAN;
			case "CWE": // Coded with exceptions
			case "CE": // Coded element
				return FieldDatatype.CODED;
			case "ST": // String
			case "TX": // Text
			default:
				return FieldDatatype.TEXT;
		}
	}
	
	/**
	 * Validates that a concept exists and is not retired.
	 *
	 * @param conceptUuid The concept UUID
	 * @return true if concept exists and is not retired
	 */
	public static boolean isValidConcept(String conceptUuid) {
		Concept concept = getConceptByUuid(conceptUuid);
		return concept != null && (concept.getRetired() == null || !concept.getRetired());
	}
	
	/**
	 * Validates that a concept exists, is not retired, and has a specific datatype.
	 *
	 * @param conceptUuid The concept UUID
	 * @param expectedHl7Code The expected HL7 datatype code (e.g., "NM", "CWE", "DT")
	 * @return true if concept matches criteria
	 */
	public static boolean isConceptOfType(String conceptUuid, String expectedHl7Code) {
		Concept concept = getConceptByUuid(conceptUuid);
		if (concept == null || (concept.getRetired() != null && concept.getRetired())) {
			return false;
		}
		
		ConceptDatatype datatype = concept.getDatatype();
		if (datatype == null) {
			return false;
		}
		
		String hl7Code = datatype.getHl7Abbreviation();
		return expectedHl7Code.equalsIgnoreCase(hl7Code);
	}
	
	/**
	 * Checks if a given answer concept UUID is valid for a question concept.
	 *
	 * @param questionConceptUuid The question concept UUID
	 * @param answerConceptUuid The answer concept UUID
	 * @return true if the answer is valid for the question
	 */
	public static boolean isValidAnswer(String questionConceptUuid, String answerConceptUuid) {
		List<AnswerModel> validAnswers = loadAnswersForConcept(questionConceptUuid);
		
		for (AnswerModel answer : validAnswers) {
			if (answerConceptUuid.equals(answer.conceptUuid)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Gets cache statistics for monitoring and debugging.
	 *
	 * @return String with cache size information
	 */
	public static String getCacheStats() {
		return String.format("ConceptManager Cache Stats - UUIDs: %d, Objects: %d, Answers: %d", conceptUuidCache.size(),
		    conceptObjectCache.size(), answersCache.size());
	}
}
