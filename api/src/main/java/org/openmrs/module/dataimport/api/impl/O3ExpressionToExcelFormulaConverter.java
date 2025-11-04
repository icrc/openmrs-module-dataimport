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

import org.apache.poi.ss.util.CellReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts simple O3 JavaScript expressions to Excel formulas for conditional formatting.
 * 
 * IMPORTANT: This converter uses pattern matching, NOT JavaScript execution.
 * It does NOT evaluate JavaScript functions or complex expressions.
 * 
 * Only handles simple patterns:
 * - Field comparisons: `fieldId == "value"`, `fieldId !== "value"`
 * - IN expressions: `fieldId IN ["value1", "value2"]`
 * - isEmpty checks: `isEmpty(fieldId)`, `!isEmpty(fieldId)`
 * - Date comparisons: `isDateBefore(myValue, '1980-01-01')`, `isDateBefore(useFieldValue('fieldId'), 'date')`
 * - Field references: `useFieldValue('fieldId')` (used in date comparisons)
 * - Simple AND/OR combinations
 * 
 * CANNOT handle:
 * - Complex calculations: `calcTimeDifference()`, etc.
 * - Other functions: `arrayContains()`, etc.
 * - References to non-field variables (e.g., `age`, `sex`)
 * - Deeply nested function calls
 * 
 * Returns null for complex expressions that cannot be converted.
 * The importer will still enforce the real hideWhenExpression during import.
 */
public class O3ExpressionToExcelFormulaConverter {
	
	private static final Logger log = LoggerFactory.getLogger(O3ExpressionToExcelFormulaConverter.class);
	
	// Pattern to match fieldId == "value" or fieldId !== "value"
	private static final Pattern EQUALITY_PATTERN = Pattern.compile("(\\w+)\\s*(===|==|!==|!=)\\s*([\"'])([^\"']+)\\3");
	
	// Pattern to match fieldId IN ["value1", "value2", ...]
	private static final Pattern IN_PATTERN = Pattern.compile("(\\w+)\\s+IN\\s+\\[([^\\]]+)\\]");
	
	// Pattern to match isEmpty(fieldId) or !isEmpty(fieldId)
	private static final Pattern IS_EMPTY_PATTERN = Pattern.compile("(!?)isEmpty\\s*\\(\\s*(\\w+)\\s*\\)");
	
	// Pattern to match isDateBefore(arg1, arg2) - where args can be myValue, useFieldValue('fieldId'), or date strings
	private static final Pattern IS_DATE_BEFORE_PATTERN = Pattern.compile("isDateBefore\\s*\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)");
	
	// Pattern to match useFieldValue('fieldId')
	private static final Pattern USE_FIELD_VALUE_PATTERN = Pattern.compile("useFieldValue\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*\\)");
	
	// Pattern to match myValue (special variable referring to current field)
	private static final Pattern MY_VALUE_PATTERN = Pattern.compile("myValue");
	
	// Pattern to match quoted strings
	private static final Pattern QUOTED_STRING = Pattern.compile("[\"']([^\"']+)[\"']");
	
	// Pattern to match date strings in YYYY-MM-DD format
	private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
	
	/**
	 * Converts an O3 hideWhenExpression to an Excel formula.
	 * 
	 * @param expression The O3 JavaScript expression
	 * @param fieldIdToColumnMap Map of field IDs to Excel column indices (0-based)
	 * @param targetColumnIndex The column index for which this conditional formatting applies
	 * @param dataRowStart The first data row (typically 1, after header row 0)
	 * @return Excel formula string, or null if expression is too complex
	 */
	public String convertToExcelFormula(String expression, Map<String, Integer> fieldIdToColumnMap, 
	                                     int targetColumnIndex, int dataRowStart) {
		if (expression == null || expression.trim().isEmpty()) {
			return null;
		}
		
		expression = expression.trim();
		
		// If expression is "false", the field should never be hidden, so no conditional formatting needed
		if ("false".equalsIgnoreCase(expression)) {
			return null;
		}
		
		try {
			// Handle isDateBefore() function calls
			Matcher dateBeforeMatcher = IS_DATE_BEFORE_PATTERN.matcher(expression);
			if (dateBeforeMatcher.find()) {
				String arg1 = dateBeforeMatcher.group(1).trim();
				String arg2 = dateBeforeMatcher.group(2).trim();
				String formula = buildDateBeforeFormula(arg1, arg2, fieldIdToColumnMap, targetColumnIndex, dataRowStart);
				if (formula != null) {
					return formula;
				}
			}
			
			// Handle simple isEmpty checks
			Matcher isEmptyMatcher = IS_EMPTY_PATTERN.matcher(expression);
			if (isEmptyMatcher.matches()) {
				String not = isEmptyMatcher.group(1);
				String fieldId = isEmptyMatcher.group(2);
				Integer columnIndex = fieldIdToColumnMap.get(fieldId);
				if (columnIndex != null) {
					String columnLetter = CellReference.convertNumToColString(columnIndex);
					String formula = "ISBLANK($" + columnLetter + dataRowStart + ")";
					return "!".equals(not) ? "NOT(" + formula + ")" : formula;
				}
			}
			
			// Handle IN expressions: fieldId IN ["value1", "value2"]
			Matcher inMatcher = IN_PATTERN.matcher(expression);
			if (inMatcher.find()) {
				String fieldId = inMatcher.group(1);
				String valuesStr = inMatcher.group(2);
				Integer columnIndex = fieldIdToColumnMap.get(fieldId);
				if (columnIndex != null) {
					return buildInFormula(columnIndex, valuesStr, dataRowStart);
				}
			}
			
			// Handle simple equality: fieldId == "value" or fieldId !== "value"
			Matcher equalityMatcher = EQUALITY_PATTERN.matcher(expression);
			if (equalityMatcher.find()) {
				String fieldId = equalityMatcher.group(1);
				String operator = equalityMatcher.group(2);
				String value = equalityMatcher.group(4);
				Integer columnIndex = fieldIdToColumnMap.get(fieldId);
				if (columnIndex != null) {
					return buildEqualityFormula(columnIndex, operator, value, dataRowStart);
				}
			}
			
			// Handle simple AND/OR combinations
			if (expression.contains("||") || expression.contains("&&")) {
				return buildCompoundFormula(expression, fieldIdToColumnMap, targetColumnIndex, dataRowStart);
			}
			
			// For complex expressions, return null (will fall back to header note)
			log.debug("Expression too complex for Excel formula conversion: {}", expression);
			return null;
		}
		catch (Exception e) {
			log.warn("Failed to convert expression to Excel formula: {}", expression, e);
			return null;
		}
	}
	
	private String buildEqualityFormula(int columnIndex, String operator, String value, int dataRowStart) {
		String columnLetter = CellReference.convertNumToColString(columnIndex);
		String cellRef = "$" + columnLetter + dataRowStart;
		
		// Excel uses = for equality, <> for inequality
		// Important: We need to handle empty cells properly. When a cell is empty, we don't want
		// to apply formatting based on inequality comparisons (empty != value is always true).
		if ("===".equals(operator) || "==".equals(operator)) {
			// For equality: only TRUE when cell equals value (and is not empty)
			return "AND(NOT(ISBLANK(" + cellRef + "))," + cellRef + "=\"" + escapeExcelString(value) + "\")";
		} else if ("!==".equals(operator) || "!=".equals(operator)) {
			// For inequality: only TRUE when cell is not empty AND not equal to value
			// This prevents empty cells from triggering the condition
			return "AND(NOT(ISBLANK(" + cellRef + "))," + cellRef + "<>\"" + escapeExcelString(value) + "\")";
		}
		return null;
	}
	
	private String buildInFormula(int columnIndex, String valuesStr, int dataRowStart) {
		String columnLetter = CellReference.convertNumToColString(columnIndex);
		String cellRef = "$" + columnLetter + dataRowStart;
		
		// Extract quoted values from the IN array
		java.util.List<String> values = new java.util.ArrayList<>();
		Matcher valueMatcher = QUOTED_STRING.matcher(valuesStr);
		while (valueMatcher.find()) {
			values.add(valueMatcher.group(1));
		}
		
		if (values.isEmpty()) {
			return null;
		}
		
		// Build OR formula: =OR($C2="value1", $C2="value2", ...)
		StringBuilder formula = new StringBuilder("OR(");
		boolean first = true;
		for (String value : values) {
			if (!first) {
				formula.append(",");
			}
			formula.append(cellRef).append("=\"").append(escapeExcelString(value)).append("\"");
			first = false;
		}
		formula.append(")");
		return formula.toString();
	}
	
	/**
	 * Builds an Excel formula for isDateBefore() function.
	 * Handles patterns like:
	 * - isDateBefore(myValue, '1980-01-01')
	 * - isDateBefore(useFieldValue('fieldId'), '1980-01-01')
	 * - isDateBefore(useFieldValue('field1'), useFieldValue('field2'))
	 */
	private String buildDateBeforeFormula(String arg1, String arg2, Map<String, Integer> fieldIdToColumnMap,
	                                       int targetColumnIndex, int dataRowStart) {
		String leftRef = resolveFieldReference(arg1, fieldIdToColumnMap, targetColumnIndex, dataRowStart);
		String rightRef = resolveFieldReference(arg2, fieldIdToColumnMap, targetColumnIndex, dataRowStart);
		
		if (leftRef != null && rightRef != null) {
			// Excel date comparison: left < right
			return leftRef + "<" + rightRef;
		}
		
		return null;
	}
	
	/**
	 * Resolves a field reference to an Excel cell reference.
	 * Handles:
	 * - myValue -> current field column
	 * - useFieldValue('fieldId') -> column for that field
	 * - 'YYYY-MM-DD' -> Excel DATE() function
	 * - fieldId -> direct field reference (for backward compatibility)
	 */
	private String resolveFieldReference(String reference, Map<String, Integer> fieldIdToColumnMap,
	                                       int targetColumnIndex, int dataRowStart) {
		reference = reference.trim();
		
		// Handle myValue - refers to current field
		if (MY_VALUE_PATTERN.matcher(reference).matches()) {
			String columnLetter = CellReference.convertNumToColString(targetColumnIndex);
			return "$" + columnLetter + dataRowStart;
		}
		
		// Handle useFieldValue('fieldId')
		Matcher useFieldMatcher = USE_FIELD_VALUE_PATTERN.matcher(reference);
		if (useFieldMatcher.find()) {
			String fieldId = useFieldMatcher.group(1);
			Integer columnIndex = fieldIdToColumnMap.get(fieldId);
			if (columnIndex != null) {
				String columnLetter = CellReference.convertNumToColString(columnIndex);
				return "$" + columnLetter + dataRowStart;
			}
			return null;
		}
		
		// Handle date strings like '1980-01-01' (remove quotes first)
		String unquoted = reference;
		if ((unquoted.startsWith("'") && unquoted.endsWith("'")) || 
		    (unquoted.startsWith("\"") && unquoted.endsWith("\""))) {
			unquoted = unquoted.substring(1, unquoted.length() - 1);
		}
		Matcher dateMatcher = DATE_PATTERN.matcher(unquoted);
		if (dateMatcher.find()) {
			String year = dateMatcher.group(1);
			String month = dateMatcher.group(2);
			String day = dateMatcher.group(3);
			// Excel DATE function: DATE(year, month, day)
			return "DATE(" + year + "," + month + "," + day + ")";
		}
		
		// Try direct field ID reference (for backward compatibility)
		Integer columnIndex = fieldIdToColumnMap.get(reference);
		if (columnIndex != null) {
			String columnLetter = CellReference.convertNumToColString(columnIndex);
			return "$" + columnLetter + dataRowStart;
		}
		
		return null;
	}
	
	private String buildCompoundFormula(String expression, Map<String, Integer> fieldIdToColumnMap, 
	                                     int targetColumnIndex, int dataRowStart) {
		// For now, handle simple cases with || (OR)
		if (expression.contains("||")) {
			java.util.List<String> orParts = java.util.Arrays.asList(expression.split("\\s*\\|\\|\\s*"));
			java.util.List<String> formulas = new java.util.ArrayList<>();
			
			for (String part : orParts) {
				part = part.trim();
				String formula = convertSimplePart(part, fieldIdToColumnMap, targetColumnIndex, dataRowStart);
				if (formula != null) {
					formulas.add(formula);
				} else {
					// If any part is too complex, return null
					return null;
				}
			}
			
			if (!formulas.isEmpty()) {
				return "OR(" + String.join(",", formulas) + ")";
			}
		}
		
		// Handle && (AND)
		if (expression.contains("&&")) {
			java.util.List<String> andParts = java.util.Arrays.asList(expression.split("\\s*&&\\s*"));
			java.util.List<String> formulas = new java.util.ArrayList<>();
			
			for (String part : andParts) {
				part = part.trim();
				String formula = convertSimplePart(part, fieldIdToColumnMap, targetColumnIndex, dataRowStart);
				if (formula != null) {
					formulas.add(formula);
				} else {
					return null;
				}
			}
			
			if (!formulas.isEmpty()) {
				return "AND(" + String.join(",", formulas) + ")";
			}
		}
		
		return null;
	}
	
	private String convertSimplePart(String part, Map<String, Integer> fieldIdToColumnMap, 
	                                   int targetColumnIndex, int dataRowStart) {
		part = part.trim();
		
		// Remove outer parentheses if present
		if (part.startsWith("(") && part.endsWith(")")) {
			part = part.substring(1, part.length() - 1).trim();
		}
		
		// Try isDateBefore
		Matcher dateBeforeMatcher = IS_DATE_BEFORE_PATTERN.matcher(part);
		if (dateBeforeMatcher.find()) {
			String arg1 = dateBeforeMatcher.group(1).trim();
			String arg2 = dateBeforeMatcher.group(2).trim();
			String formula = buildDateBeforeFormula(arg1, arg2, fieldIdToColumnMap, targetColumnIndex, dataRowStart);
			if (formula != null) {
				return formula;
			}
		}
		
		// Try isEmpty
		Matcher isEmptyMatcher = IS_EMPTY_PATTERN.matcher(part);
		if (isEmptyMatcher.matches()) {
			String not = isEmptyMatcher.group(1);
			String fieldId = isEmptyMatcher.group(2);
			Integer columnIndex = fieldIdToColumnMap.get(fieldId);
			if (columnIndex != null) {
				String columnLetter = CellReference.convertNumToColString(columnIndex);
				String formula = "ISBLANK($" + columnLetter + dataRowStart + ")";
				return "!".equals(not) ? "NOT(" + formula + ")" : formula;
			}
		}
		
		// Try IN
		Matcher inMatcher = IN_PATTERN.matcher(part);
		if (inMatcher.find()) {
			String fieldId = inMatcher.group(1);
			String valuesStr = inMatcher.group(2);
			Integer columnIndex = fieldIdToColumnMap.get(fieldId);
			if (columnIndex != null) {
				return buildInFormula(columnIndex, valuesStr, dataRowStart);
			}
		}
		
		// Try equality
		Matcher equalityMatcher = EQUALITY_PATTERN.matcher(part);
		if (equalityMatcher.find()) {
			String fieldId = equalityMatcher.group(1);
			String operator = equalityMatcher.group(2);
			String value = equalityMatcher.group(4);
			Integer columnIndex = fieldIdToColumnMap.get(fieldId);
			if (columnIndex != null) {
				return buildEqualityFormula(columnIndex, operator, value, dataRowStart);
			}
		}
		
		return null;
	}
	
	private String escapeExcelString(String value) {
		// Escape quotes in Excel formulas
		return value.replace("\"", "\"\"");
	}
}

