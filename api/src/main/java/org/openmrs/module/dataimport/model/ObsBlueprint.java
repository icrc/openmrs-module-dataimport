/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.model;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
public final class ObsBlueprint {
	
	private final String conceptUuid;
	
	private final ObsValue value;
	
	private final String groupUuid;
	
	public ObsBlueprint(String conceptUuid, ObsValue value, String groupUuid) {
		this.conceptUuid = conceptUuid;
		this.value = value;
		this.groupUuid = groupUuid;
	}
	
	public static ObsBlueprint text(String conceptUuid, String value) {
		return new ObsBlueprint(conceptUuid, new ObsValue.TextValue(value), null);
	}
	
	public static ObsBlueprint numeric(String conceptUuid, Double value) {
		return new ObsBlueprint(conceptUuid, new ObsValue.NumericValue(value), null);
	}
	
	public static ObsBlueprint coded(String conceptUuid, String answerConceptUuid) {
		return new ObsBlueprint(conceptUuid, new ObsValue.CodedValue(answerConceptUuid), null);
	}
	
	public static ObsBlueprint date(String conceptUuid, LocalDate value) {
		return new ObsBlueprint(conceptUuid, new ObsValue.DateValue(value), null);
	}
	
	public static ObsBlueprint datetime(String conceptUuid, Instant value) {
		return new ObsBlueprint(conceptUuid, new ObsValue.DateTimeValue(value), null);
	}
	
	public static ObsBlueprint bool(String conceptUuid, Boolean value) {
		return new ObsBlueprint(conceptUuid, new ObsValue.BooleanValue(value), null);
	}
	
	public static ObsBlueprint group(String conceptUuid, String groupUuid) {
		return new ObsBlueprint(conceptUuid, null, groupUuid);
	}
	
	public abstract static class ObsValue {
		
		@Getter
		public static class TextValue extends ObsValue {
			
			private final String value;
			
			public TextValue(String value) {
				this.value = value;
			}
			
		}
		
		@Getter
		public static class NumericValue extends ObsValue {
			
			private final Double value;
			
			public NumericValue(Double value) {
				this.value = value;
			}
			
		}
		
		@Getter
		public static class CodedValue extends ObsValue {
			
			private final String answerConceptUuid;
			
			public CodedValue(String answerConceptUuid) {
				this.answerConceptUuid = answerConceptUuid;
			}
			
		}
		
		@Getter
		public static class DateValue extends ObsValue {
			
			private final LocalDate value;
			
			public DateValue(LocalDate value) {
				this.value = value;
			}
			
		}
		
		@Getter
		public static class DateTimeValue extends ObsValue {
			
			private final Instant value;
			
			public DateTimeValue(Instant value) {
				this.value = value;
			}
			
		}
		
		@Getter
		public static class BooleanValue extends ObsValue {
			
			private final Boolean value;
			
			public BooleanValue(Boolean value) {
				this.value = value;
			}
			
		}
	}
}
