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

import org.openmrs.ConceptDatatype;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
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
			    "Required field is missing for column '" + safeLabel(column) + "'"));
			return;
		}
		
		if (obs.getValue() == null)
			return;
		
		switch (obs.getValue().getClass().getSimpleName()) {
			case "TextValue":
				validateDatatype(sheetName, rowIndex, column, "TEXT", quarantineItems);
				validateTextObs(obs, column, quarantineItems, sheetName, rowIndex);
				break;
			case "NumericValue":
				validateDatatype(sheetName, rowIndex, column, "NUMERIC", quarantineItems);
				validateNumericObs(obs, column, quarantineItems, sheetName, rowIndex);
				break;
			case "CodedValue":
				validateDatatype(sheetName, rowIndex, column, "CODED", quarantineItems);
				validateCodedObs(obs, column, quarantineItems, sheetName, rowIndex);
				break;
			case "DateValue":
				validateDatatype(sheetName, rowIndex, column, "DATE|DATETIME", quarantineItems);
				validateDateObs(obs, column, quarantineItems, sheetName, rowIndex);
				break;
			case "DateTimeValue":
				validateDatatype(sheetName, rowIndex, column, "DATETIME|DATE", quarantineItems);
				break;
			case "BooleanValue":
				validateDatatype(sheetName, rowIndex, column, "BOOLEAN|CODED", quarantineItems);
				break;
		}
	}
	
	private static void validateDatatype(String sheetName, int rowIndex, ColumnMeta column, String expected,
	        List<QuarantineItem> quarantineItems) {
		try {
			ConceptService cs = Context.getConceptService();
			if (cs == null)
				return; // outside runtime; skip
			ConceptDatatype dt = cs.getConceptByUuid(column.conceptUuid).getDatatype();
			if (dt == null)
				return;
			
			boolean ok = matchesAnyDatatype(dt, expected);
			if (!ok) {
				String actual = describeDatatype(dt);
				String label = safeLabel(column);
				String concept = column.conceptUuid != null ? column.conceptUuid : "";
				String message = String.format(
				    "Column '%s' (concept %s) datatype mismatch: expected one of [%s] but found %s", label, concept,
				    expected, actual);
				quarantineItems
				        .add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "DATATYPE_MISMATCH", message));
			}
		}
		catch (Exception ignored) {
			// In dry-run validation we avoid failing the whole process if concept lookup fails
		}
	}
	
	private static boolean matchesAnyDatatype(ConceptDatatype dt, String expectedTypes) {
		String[] tokens = expectedTypes.split("\\|");
		for (String token : tokens) {
			String t = token.trim();
			switch (t) {
				case "TEXT":
					if (dt.isText())
						return true;
					break;
				case "NUMERIC":
					if (dt.isNumeric())
						return true;
					break;
				case "CODED":
					if (dt.isCoded())
						return true;
					break;
				case "DATE":
					if (dt.isDate())
						return true;
					break;
				case "DATETIME":
					if (dt.isDateTime())
						return true;
					break;
				case "BOOLEAN":
					if (dt.isBoolean())
						return true;
					break;
			}
		}
		return false;
	}
	
	private static String describeDatatype(ConceptDatatype dt) {
		if (dt == null)
			return "UNKNOWN";
		if (dt.isText())
			return "TEXT";
		if (dt.isNumeric())
			return "NUMERIC";
		if (dt.isCoded())
			return "CODED";
		if (dt.isDateTime())
			return "DATETIME";
		if (dt.isDate())
			return "DATE";
		if (dt.isBoolean())
			return "BOOLEAN";
		return dt.getName();
	}
	
	private static void validateTextObs(ObsBlueprint obs, ColumnMeta column, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		String value = ((ObsBlueprint.ObsValue.TextValue) obs.getValue()).getValue();
		
		if (column.maxLength != null && value.length() > column.maxLength) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "TEXT_TOO_LONG",
			    "Column '" + safeLabel(column) + "' exceeds maximum length of " + column.maxLength + " characters"));
		}
		
		if (column.minLength != null && value.length() < column.minLength) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "TEXT_TOO_SHORT",
			    "Column '" + safeLabel(column) + "' is shorter than minimum length of " + column.minLength + " characters"));
		}
	}
	
	private static void validateNumericObs(ObsBlueprint obs, ColumnMeta column, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		Double value = ((ObsBlueprint.ObsValue.NumericValue) obs.getValue()).getValue();
		
		if (column.min != null && value < column.min) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "VALUE_TOO_SMALL",
			    "Column '" + safeLabel(column) + "' value " + value + " is less than minimum " + column.min));
		}
		
		if (column.max != null && value > column.max) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "VALUE_TOO_LARGE",
			    "Column '" + safeLabel(column) + "' value " + value + " is greater than maximum " + column.max));
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
			    "Invalid coded value for column '" + safeLabel(column) + "': " + answerConceptUuid,
			    validAnswers.toArray(new String[0])));
		}
	}
	
	private static void validateDateObs(ObsBlueprint obs, ColumnMeta column, List<QuarantineItem> quarantineItems,
	        String sheetName, int rowIndex) {
		LocalDate value = ((ObsBlueprint.ObsValue.DateValue) obs.getValue()).getValue();
		
		if (column.min != null) {
			LocalDate minDate = LocalDate.ofEpochDay(column.min.longValue());
			if (value.isBefore(minDate)) {
				quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "DATE_TOO_EARLY",
				    "Column '" + safeLabel(column) + "' date " + value + " is before minimum date " + minDate));
			}
		}
		
		if (column.max != null) {
			LocalDate maxDate = LocalDate.ofEpochDay(column.max.longValue());
			if (value.isAfter(maxDate)) {
				quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "DATE_TOO_LATE",
				    "Column '" + safeLabel(column) + "' date " + value + " is after maximum date " + maxDate));
			}
		}
		
		LocalDate today = LocalDate.now();
		if (value.isAfter(today)) {
			quarantineItems.add(QuarantineItem.error(sheetName, rowIndex, column.colIndex, "FUTURE_DATE",
			    "Column '" + safeLabel(column) + "' date " + value + " is in the future"));
		}
	}
	
	private static String safeLabel(ColumnMeta column) {
		return column.label != null && !column.label.trim().isEmpty() ? column.label
		        : (column.fieldId != null ? column.fieldId
		                : (column.conceptUuid != null ? column.conceptUuid : ("col " + column.colIndex)));
	}
}
