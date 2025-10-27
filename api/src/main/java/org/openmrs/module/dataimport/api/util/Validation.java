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

import java.util.ArrayList;
import java.util.List;

/**
 * Validates observation data and creates quarantine items for issues.
 */
public class Validation {
	
	/**
	 * Validates a row's observations and adds quarantine items for issues.
	 */
	public static void checkRow(List<ObsBlueprint> obs, SheetMeta sheetMeta, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		
		for (ObsBlueprint obsBlueprint : obs) {
			ColumnMeta column = findColumnForObs(obsBlueprint, sheetMeta);
			if (column == null)
				continue;
			
			validateObs(obsBlueprint, column, quarantineItems, sheetName, rowIndex);
		}
	}
	
	/**
	 * Checks if there are any blocking issues for a row.
	 */
	public static boolean hasBlocking(List<QuarantineItem> quarantineItems, String sheetName, int rowIndex) {
		return quarantineItems.stream().anyMatch(item -> sheetName.equals(item.getSheet()) && item.getRow() == rowIndex);
	}
	
	private static ColumnMeta findColumnForObs(ObsBlueprint obs, SheetMeta sheetMeta) {
		return sheetMeta.columns.stream().filter(col -> obs.getConceptUuid().equals(col.conceptUuid)).findFirst()
		        .orElse(null);
	}
	
	private static void validateObs(ObsBlueprint obs, ColumnMeta column, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		
		// Check required fields
		if (column.requiredAlways && obs.getValue() == null) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "REQUIRED_FIELD_MISSING",
			    "Required field is missing"));
			return;
		}
		
		if (obs.getValue() == null)
			return; // Skip validation for empty values
			
		// Type-specific validation
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
		
		// Check max length
		if (column.maxLength != null && value.length() > column.maxLength) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "TEXT_TOO_LONG",
			    "Text exceeds maximum length of " + column.maxLength + " characters"));
		}
		
		// Check min length
		if (column.minLength != null && value.length() < column.minLength) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "TEXT_TOO_SHORT",
			    "Text is shorter than minimum length of " + column.minLength + " characters"));
		}
	}
	
	private static void validateNumericObs(ObsBlueprint obs, ColumnMeta column, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		Double value = ((ObsBlueprint.ObsValue.NumericValue) obs.getValue()).getValue();
		
		// Check min value
		if (column.min != null && value < column.min) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "VALUE_TOO_SMALL",
			    "Value " + value + " is less than minimum " + column.min));
		}
		
		// Check max value
		if (column.max != null && value > column.max) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "VALUE_TOO_LARGE",
			    "Value " + value + " is greater than maximum " + column.max));
		}
	}
	
	private static void validateCodedObs(ObsBlueprint obs, ColumnMeta column, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		String answerConceptUuid = ((ObsBlueprint.ObsValue.CodedValue) obs.getValue()).getAnswerConceptUuid();
		
		// Check if the answer concept UUID is valid
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
		// Date validation could include range checks, but for now we'll keep it simple
		// In a real implementation, you might check against min/max date constraints
	}
}
