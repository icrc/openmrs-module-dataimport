/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.service;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ObsService;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.ObsBlueprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class ObsSaver {
	
	private static final Logger log = LoggerFactory.getLogger(ObsSaver.class);
	
	private ConceptService getConceptService() {
		return Context.getConceptService();
	}
	
	private ObsService getObsService() {
		return Context.getObsService();
	}
	
	/**
	 * Creates a text observation.
	 */
	public Obs addTextObs(Encounter enc, String conceptUuid, String value) {
		if (value == null || value.trim().isEmpty()) {
			return null; // Skip empty values
		}
		
		Concept c = getConceptService().getConceptByUuid(conceptUuid);
		if (c == null) {
			throw new IllegalArgumentException("Concept not found: " + conceptUuid);
		}
		
		Obs o = base(enc, c);
		o.setValueText(value.trim());
		return getObsService().saveObs(o, null);
	}
	
	/**
	 * Creates a numeric observation.
	 */
	public Obs addNumericObs(Encounter enc, String conceptUuid, Double value) {
		if (value == null) {
			return null; // Skip null values
		}
		
		Concept c = getConceptService().getConceptByUuid(conceptUuid);
		if (c == null) {
			throw new IllegalArgumentException("Concept not found: " + conceptUuid);
		}
		
		Obs o = base(enc, c);
		o.setValueNumeric(value);
		return getObsService().saveObs(o, null);
	}
	
	/**
	 * Creates a coded observation.
	 */
	public Obs addCodedObs(Encounter enc, String questionConceptUuid, String answerConceptUuid) {
		if (answerConceptUuid == null || answerConceptUuid.trim().isEmpty()) {
			return null; // Skip empty values
		}
		
		Concept q = getConceptService().getConceptByUuid(questionConceptUuid);
		if (q == null) {
			throw new IllegalArgumentException("Question concept not found: " + questionConceptUuid);
		}
		
		Concept a = getConceptService().getConceptByUuid(answerConceptUuid);
		if (a == null) {
			throw new IllegalArgumentException("Answer concept not found: " + answerConceptUuid);
		}
		
		Obs o = base(enc, q);
		o.setValueCoded(a);
		return getObsService().saveObs(o, null);
	}
	
	/**
	 * Creates a date observation.
	 */
	public Obs addDateObs(Encounter enc, String conceptUuid, Date date) {
		if (date == null) {
			return null; // Skip null values
		}
		
		Concept c = getConceptService().getConceptByUuid(conceptUuid);
		if (c == null) {
			throw new IllegalArgumentException("Concept not found: " + conceptUuid);
		}
		
		Obs o = base(enc, c);
		o.setValueDate(date);
		return getObsService().saveObs(o, null);
	}
	
	/**
	 * Creates a datetime observation.
	 */
	public Obs addDateTimeObs(Encounter enc, String conceptUuid, Date dateTime) {
		if (dateTime == null) {
			return null; // Skip null values
		}
		
		Concept c = getConceptService().getConceptByUuid(conceptUuid);
		if (c == null) {
			throw new IllegalArgumentException("Concept not found: " + conceptUuid);
		}
		
		Obs o = base(enc, c);
		o.setValueDatetime(dateTime);
		return getObsService().saveObs(o, null);
	}
	
	/**
	 * Creates a boolean observation (as coded true/false).
	 */
	public Obs addBooleanObs(Encounter enc, String conceptUuid, Boolean value, String trueConceptUuid,
	        String falseConceptUuid) {
		if (value == null) {
			return null; // Skip null values
		}
		
		String answerConceptUuid = value ? trueConceptUuid : falseConceptUuid;
		return addCodedObs(enc, conceptUuid, answerConceptUuid);
	}
	
	/**
	 * Creates an observation from ObsBlueprint.
	 */
	public Obs createObsFromBlueprint(Encounter enc, ObsBlueprint blueprint) {
		if (enc == null) {
			throw new IllegalArgumentException("Encounter cannot be null");
		}
		if (blueprint == null) {
			throw new IllegalArgumentException("ObsBlueprint cannot be null");
		}
		if (blueprint.getConceptUuid() == null) {
			throw new IllegalArgumentException("Concept UUID cannot be null");
		}
		
		ObsBlueprint.ObsValue value = blueprint.getValue();
		if (value == null) {
			return null; // Skip empty observations
		}
		
		// Create obs based on value type
		if (value instanceof ObsBlueprint.ObsValue.TextValue) {
			String textValue = ((ObsBlueprint.ObsValue.TextValue) value).getValue();
			return addTextObs(enc, blueprint.getConceptUuid(), textValue);
			
		} else if (value instanceof ObsBlueprint.ObsValue.NumericValue) {
			Double numericValue = ((ObsBlueprint.ObsValue.NumericValue) value).getValue();
			return addNumericObs(enc, blueprint.getConceptUuid(), numericValue);
			
		} else if (value instanceof ObsBlueprint.ObsValue.CodedValue) {
			String answerConceptUuid = ((ObsBlueprint.ObsValue.CodedValue) value).getAnswerConceptUuid();
			return addCodedObs(enc, blueprint.getConceptUuid(), answerConceptUuid);
			
		} else if (value instanceof ObsBlueprint.ObsValue.DateValue) {
			Date dateValue = Date.from(((ObsBlueprint.ObsValue.DateValue) value).getValue()
			        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
			return addDateObs(enc, blueprint.getConceptUuid(), dateValue);
			
		} else if (value instanceof ObsBlueprint.ObsValue.DateTimeValue) {
			Date dateTimeValue = Date.from(((ObsBlueprint.ObsValue.DateTimeValue) value).getValue());
			return addDateTimeObs(enc, blueprint.getConceptUuid(), dateTimeValue);
			
		} else if (value instanceof ObsBlueprint.ObsValue.BooleanValue) {
			Boolean booleanValue = ((ObsBlueprint.ObsValue.BooleanValue) value).getValue();
			// For boolean values, we'll use coded concepts
			// In a real implementation, you'd configure these UUIDs
			String trueConceptUuid = getBooleanTrueConceptUuid();
			String falseConceptUuid = getBooleanFalseConceptUuid();
			return addBooleanObs(enc, blueprint.getConceptUuid(), booleanValue, trueConceptUuid, falseConceptUuid);
		}
		
		log.warn("Unknown observation value type: {}", value.getClass().getSimpleName());
		return null;
	}
	
	/**
	 * Creates base observation with common fields.
	 */
	private Obs base(Encounter enc, Concept questionConcept) {
		Obs o = new Obs();
		o.setEncounter(enc);
		o.setPerson(enc.getPatient());
		o.setConcept(questionConcept);
		o.setObsDatetime(enc.getEncounterDatetime());
		o.setLocation(enc.getLocation());
		return o;
	}
	
	/**
	 * Gets the UUID for boolean true concept. In a real implementation, this would come from Global
	 * Properties.
	 */
	private String getBooleanTrueConceptUuid() {
		// This is a placeholder - in production you'd configure this
		Concept trueConcept = getConceptService().getConceptByName("YES");
		if (trueConcept != null) {
			return trueConcept.getUuid();
		}
		
		// Fallback - this would need to be configured properly
		throw new IllegalStateException("Boolean true concept not configured");
	}
	
	/**
	 * Gets the UUID for boolean false concept. In a real implementation, this would come from Global
	 * Properties.
	 */
	private String getBooleanFalseConceptUuid() {
		// This is a placeholder - in production you'd configure this
		Concept falseConcept = getConceptService().getConceptByName("NO");
		if (falseConcept != null) {
			return falseConcept.getUuid();
		}
		
		// Fallback - this would need to be configured properly
		throw new IllegalStateException("Boolean false concept not configured");
	}
	
	/**
	 * Validates observation data before creation.
	 */
	public void validateObsData(String conceptUuid, ObsBlueprint.ObsValue value) {
		if (conceptUuid == null || conceptUuid.trim().isEmpty()) {
			throw new IllegalArgumentException("Concept UUID is required");
		}
		
		Concept concept = getConceptService().getConceptByUuid(conceptUuid);
		if (concept == null) {
			throw new IllegalArgumentException("Concept not found: " + conceptUuid);
		}
		
		if (value == null) {
			return; // Null values are allowed (will be skipped)
		}
		
		// Type-specific validation
		if (value instanceof ObsBlueprint.ObsValue.TextValue) {
			String textValue = ((ObsBlueprint.ObsValue.TextValue) value).getValue();
			if (textValue != null && textValue.length() > 1000) {
				throw new IllegalArgumentException("Text value too long: " + textValue.length() + " characters");
			}
		}
	}
}
