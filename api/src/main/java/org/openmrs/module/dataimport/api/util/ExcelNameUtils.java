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

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utilities for Excel naming conventions and validation.
 */
public class ExcelNameUtils {
	
	private static final Pattern EXCEL_CELL_REFERENCE_PATTERN = Pattern.compile("^[A-Za-z]+\\d+$");
	
	private static final Pattern INVALID_SHEET_NAME_CHARS = Pattern.compile("[\\\\/*?\\[\\]:]+");
	
	private static final int MAX_SHEET_NAME_LENGTH = 31;
	
	/**
	 * Checks if a string represents a valid Excel cell reference (e.g., "A1", "AB123").
	 *
	 * @param str the string to check
	 * @return true if the string is a valid Excel cell reference, false otherwise
	 */
	public static boolean isExcelCellReference(String str) {
		return str != null && EXCEL_CELL_REFERENCE_PATTERN.matcher(str).matches();
	}
	
	/**
	 * Sanitizes a string to be a valid Excel sheet name.
	 * <p>
	 * Excel sheet names must: - Not exceed 31 characters - Not contain: \ / * ? [ ] : - Not be empty or
	 * blank
	 *
	 * @param name the proposed sheet name
	 * @return sanitized sheet name
	 */
	public static String sanitizeSheetName(String name) {
		if (name == null || name.trim().isEmpty()) {
			return "Sheet";
		}
		
		String sanitized = INVALID_SHEET_NAME_CHARS.matcher(name).replaceAll("_");
		
		if (sanitized.length() > MAX_SHEET_NAME_LENGTH) {
			sanitized = sanitized.substring(0, MAX_SHEET_NAME_LENGTH);
		}
		
		return sanitized.trim();
	}
	
	/**
	 * Gets a unique sheet name by appending a number if needed.
	 *
	 * @param baseName the desired base name
	 * @param usedNames map of already used names (will be updated)
	 * @return unique sheet name
	 */
	public static String getUniqueSheetName(String baseName, Map<String, Integer> usedNames) {
		String sanitized = sanitizeSheetName(baseName);
		
		if (!usedNames.containsKey(sanitized)) {
			usedNames.put(sanitized, 1);
			return sanitized;
		}
		
		int count = usedNames.get(sanitized);
		usedNames.put(sanitized, count + 1);
		
		String suffix = "_" + count;
		int maxBaseLength = MAX_SHEET_NAME_LENGTH - suffix.length();
		
		if (sanitized.length() > maxBaseLength) {
			sanitized = sanitized.substring(0, maxBaseLength);
		}
		
		return sanitized + suffix;
	}
	
	/**
	 * Generates a fallback ID from a label when no explicit ID is provided.
	 * <p>
	 * Strategy: - Removes special characters - Converts to camelCase - Limits length to reasonable size
	 *
	 * @param label the label to generate an ID from
	 * @return generated fallback ID
	 */
	public static String generateFallbackId(String label) {
		if (label == null || label.trim().isEmpty()) {
			return "field_" + System.currentTimeMillis();
		}
		
		String cleaned = label.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
		
		if (cleaned.isEmpty()) {
			return "field_" + Math.abs(label.hashCode());
		}
		
		String[] words = cleaned.split("\\s+");
		StringBuilder id = new StringBuilder();
		
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			if (word.isEmpty())
				continue;
			
			if (i == 0) {
				id.append(word.toLowerCase());
			} else {
				id.append(Character.toUpperCase(word.charAt(0)));
				if (word.length() > 1) {
					id.append(word.substring(1).toLowerCase());
				}
			}
		}
		
		String result = id.toString();
		if (result.length() > 64) {
			result = result.substring(0, 64);
		}
		
		return result.isEmpty() ? "field_" + Math.abs(label.hashCode()) : result;
	}
}
