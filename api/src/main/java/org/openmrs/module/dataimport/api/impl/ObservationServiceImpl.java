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

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.dataimport.api.service.ObservationService;
import org.openmrs.module.dataimport.model.ObsBlueprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class ObservationServiceImpl extends BaseOpenmrsService implements ObservationService {
	
	private static final Logger log = LoggerFactory.getLogger(ObservationServiceImpl.class);
	
	private ConceptService getConceptService() {
		return Context.getConceptService();
	}

	private org.openmrs.api.ObsService getObsService() {
		return Context.getObsService();
	}

	private Concept validateAndGetConcept(String conceptUuid) {
		Concept concept = getConceptService().getConceptByUuid(conceptUuid);
		if (concept == null) {
			throw new IllegalArgumentException("Concept not found: " + conceptUuid);
		}
		return concept;
	}

	public Obs addTextObs(Encounter enc, String conceptUuid, String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}

		Concept c = validateAndGetConcept(conceptUuid);
		if (c.getDatatype() != null && (c.getDatatype().isCoded() || c.getDatatype().isNumeric() || c.getDatatype().isDate()
		        || c.getDatatype().isDateTime() || c.getDatatype().isBoolean())) {
			throw new IllegalArgumentException("Concept '" + c.getName() + "' (" + c.getUuid()
			        + ") is not a text datatype; update template to CODED/NUMERIC/DATE/DATETIME/BOOLEAN as appropriate");
		}
		
		Obs o = base(enc, c);
		o.setValueText(value.trim());
		return getObsService().saveObs(o, null);
	}
	
	public Obs addNumericObs(Encounter enc, String conceptUuid, Double value) {
		if (value == null) {
			return null;
		}

		Concept c = validateAndGetConcept(conceptUuid);
		if (c.getDatatype() != null && !c.getDatatype().isNumeric()) {
			throw new IllegalArgumentException(
			        "Concept '" + c.getName() + "' (" + c.getUuid() + ") is not NUMERIC; update template/datatype");
		}
		
		Obs o = base(enc, c);
		o.setValueNumeric(value);
		return getObsService().saveObs(o, null);
	}
	
	public Obs addCodedObs(Encounter enc, String questionConceptUuid, String answerConceptUuid) {
		if (answerConceptUuid == null || answerConceptUuid.trim().isEmpty()) {
			return null;
		}

		Concept q = validateAndGetConcept(questionConceptUuid);
		if (q.getDatatype() != null && !q.getDatatype().isCoded()) {
			throw new IllegalArgumentException(
			        "Concept '" + q.getName() + "' (" + q.getUuid() + ") is not CODED; update template/datatype");
		}

		Concept a = validateAndGetConcept(answerConceptUuid);
		
		Obs o = base(enc, q);
		o.setValueCoded(a);
		return getObsService().saveObs(o, null);
	}
	
	public Obs addDateObs(Encounter enc, String conceptUuid, Date date) {
		if (date == null) {
			return null;
		}

		Concept c = validateAndGetConcept(conceptUuid);
		if (c.getDatatype() != null && !(c.getDatatype().isDate() || c.getDatatype().isDateTime())) {
			throw new IllegalArgumentException(
			        "Concept '" + c.getName() + "' (" + c.getUuid() + ") is not DATE/DATETIME; update template/datatype");
		}
		
		Obs o = base(enc, c);
		o.setValueDate(date);
		return getObsService().saveObs(o, null);
	}
	
	public Obs addDateTimeObs(Encounter enc, String conceptUuid, Date dateTime) {
		if (dateTime == null) {
			return null;
		}

		Concept c = validateAndGetConcept(conceptUuid);
		if (c.getDatatype() != null && !(c.getDatatype().isDateTime() || c.getDatatype().isDate())) {
			throw new IllegalArgumentException(
			        "Concept '" + c.getName() + "' (" + c.getUuid() + ") is not DATETIME/DATE; update template/datatype");
		}
		
		Obs o = base(enc, c);
		o.setValueDatetime(dateTime);
		return getObsService().saveObs(o, null);
	}
	
	public Obs addBooleanObs(Encounter enc, String conceptUuid, Boolean value, String trueConceptUuid,
	        String falseConceptUuid) {
		if (value == null) {
			return null;
		}

		String answerConceptUuid = value ? trueConceptUuid : falseConceptUuid;
		Concept q = validateAndGetConcept(conceptUuid);
		if (q.getDatatype() != null && !(q.getDatatype().isCoded() || q.getDatatype().isBoolean())) {
			throw new IllegalArgumentException(
			        "Concept '" + q.getName() + "' (" + q.getUuid() + ") is not BOOLEAN/CODED; update template/datatype");
		}
		return addCodedObs(enc, conceptUuid, answerConceptUuid);
	}
	
	@Override
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
			return null;
		}
		
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
			Date dateValue = Date.from(
			    ((ObsBlueprint.ObsValue.DateValue) value).getValue().atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
			return addDateObs(enc, blueprint.getConceptUuid(), dateValue);
			
		} else if (value instanceof ObsBlueprint.ObsValue.DateTimeValue) {
			Date dateTimeValue = Date.from(((ObsBlueprint.ObsValue.DateTimeValue) value).getValue());
			return addDateTimeObs(enc, blueprint.getConceptUuid(), dateTimeValue);
			
		} else if (value instanceof ObsBlueprint.ObsValue.BooleanValue) {
			Boolean booleanValue = ((ObsBlueprint.ObsValue.BooleanValue) value).getValue();
			String trueConceptUuid = getBooleanTrueConceptUuid();
			String falseConceptUuid = getBooleanFalseConceptUuid();
			return addBooleanObs(enc, blueprint.getConceptUuid(), booleanValue, trueConceptUuid, falseConceptUuid);
		}
		
		return null;
	}
	
	private Obs base(Encounter enc, Concept questionConcept) {
		Obs o = new Obs();
		o.setEncounter(enc);
		o.setPerson(enc.getPatient());
		o.setConcept(questionConcept);
		o.setObsDatetime(enc.getEncounterDatetime());
		o.setLocation(enc.getLocation());
		return o;
	}
	
	private String getBooleanTrueConceptUuid() {
		Concept trueConcept = getConceptService().getConceptByName("YES");
		if (trueConcept != null) {
			return trueConcept.getUuid();
		}
		
		throw new IllegalStateException("Boolean true concept not configured");
	}
	
	private String getBooleanFalseConceptUuid() {
		Concept falseConcept = getConceptService().getConceptByName("NO");
		if (falseConcept != null) {
			return falseConcept.getUuid();
		}
		
		throw new IllegalStateException("Boolean false concept not configured");
	}
}
