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

import org.openmrs.module.dataimport.api.ObsBlueprint;
import org.openmrs.module.dataimport.api.QuarantineItem;
import org.openmrs.module.dataimport.model.ColumnMeta;
import org.openmrs.module.dataimport.model.SheetMeta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Validation {
	
	public static void checkRow(List<ObsBlueprint> obs, SheetMeta sheetMeta, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		
		for (ObsBlueprint obsBlueprint : obs) {
			ColumnMeta column = findColumnForObs(obsBlueprint, sheetMeta);
			if (column == null)
				continue;
			
			validateObs(obsBlueprint, column, quarantineItems, sheetName, rowIndex);
		}
	}
	
	public static boolean hasBlocking(List<QuarantineItem> quarantineItems, String sheetName, int rowIndex) {
		return quarantineItems.stream().anyMatch(item -> sheetName.equals(item.getSheet()) && item.getRow() == rowIndex);
	}
	
	private static ColumnMeta findColumnForObs(ObsBlueprint obs, SheetMeta sheetMeta) {
		return sheetMeta.columns.stream().filter(col -> obs.getConceptUuid().equals(col.conceptUuid)).findFirst()
		        .orElse(null);
	}
	
	private static void validateObs(ObsBlueprint obs, ColumnMeta column, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		
		if (column.requiredAlways && obs.getValue() == null) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "REQUIRED_FIELD_MISSING",
			    "Required field is missing"));
			return;
		}
		
		if (obs.getValue() == null)
			return;
		
		switch (obs.getValue().getClass().getSimpleName()) {
			case "TextValue":
				validateTextObs(obs, column, quarantineItems, sheetName, rowIndex);
				break;
			case "NumericValue":
				validateNumericObs(obs, column, quarantineItems, sheetName, rowIndex);
				break;
			case "CodedValue":
				validateCodedObs(obs, column, quarantineItems, sheetName, rowIndex);
				break;
			case "DateValue":
				validateDateObs(obs, column, quarantineItems, sheetName, rowIndex);
				break;
		}
	}
	
	private static void validateTextObs(ObsBlueprint obs, ColumnMeta column, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		String value = ((ObsBlueprint.ObsValue.TextValue) obs.getValue()).getValue();
		
		if (column.maxLength != null && value.length() > column.maxLength) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "TEXT_TOO_LONG",
			    "Text exceeds maximum length of " + column.maxLength + " characters"));
		}
		
		if (column.minLength != null && value.length() < column.minLength) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "TEXT_TOO_SHORT",
			    "Text is shorter than minimum length of " + column.minLength + " characters"));
		}
	}
	
	private static void validateNumericObs(ObsBlueprint obs, ColumnMeta column, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		Double value = ((ObsBlueprint.ObsValue.NumericValue) obs.getValue()).getValue();
		
		if (column.min != null && value < column.min) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "VALUE_TOO_SMALL",
			    "Value " + value + " is less than minimum " + column.min));
		}
		
		if (column.max != null && value > column.max) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "VALUE_TOO_LARGE",
			    "Value " + value + " is greater than maximum " + column.max));
		}
	}
	
	private static void validateCodedObs(ObsBlueprint obs, ColumnMeta column, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		String answerConceptUuid = ((ObsBlueprint.ObsValue.CodedValue) obs.getValue()).getAnswerConceptUuid();
		
		boolean isValidAnswer = column.answers != null && column.answers.containsValue(answerConceptUuid);
		if (!isValidAnswer) {
			List<String> validAnswers = new ArrayList<>();
			if (column.answers != null) {
				validAnswers.addAll(column.answers.keySet());
			}
			
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "INVALID_CODED_VALUE",
			    "Invalid coded value: " + answerConceptUuid, validAnswers.toArray(new String[0])));
		}
	}
	
	private static void validateDateObs(ObsBlueprint obs, ColumnMeta column, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		LocalDate value = ((ObsBlueprint.ObsValue.DateValue) obs.getValue()).getValue();
		
		// Check minimum date constraint
		if (column.min != null) {
			LocalDate minDate = LocalDate.ofEpochDay(column.min.longValue());
			if (value.isBefore(minDate)) {
				quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "DATE_TOO_EARLY",
				    "Date " + value + " is before minimum date " + minDate));
			}
		}
		
		// Check maximum date constraint
		if (column.max != null) {
			LocalDate maxDate = LocalDate.ofEpochDay(column.max.longValue());
			if (value.isAfter(maxDate)) {
				quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "DATE_TOO_LATE",
				    "Date " + value + " is after maximum date " + maxDate));
			}
		}
		
		// Check if date is in the future (common business rule)
		LocalDate today = LocalDate.now();
		if (value.isAfter(today)) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "FUTURE_DATE",
			    "Date " + value + " is in the future"));
		}
	}
}
