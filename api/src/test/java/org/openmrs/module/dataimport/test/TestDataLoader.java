/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for loading test data from resources
 */
public class TestDataLoader {
	
	/**
	 * Load a JSON test form from the testdata resources folder
	 * 
	 * @param filename The filename (e.g., "form-with-radio-buttons.json")
	 * @return The JSON content as a string
	 * @throws IOException if the file cannot be read
	 */
	public static String loadTestForm(String filename) throws IOException {
		String resourcePath = "/testdata/" + filename;
		try (InputStream is = TestDataLoader.class.getResourceAsStream(resourcePath)) {
			if (is == null) {
				throw new IOException("Test data file not found: " + resourcePath);
			}
			
			StringBuilder result = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					result.append(line).append("\n");
				}
			}
			return result.toString();
		}
	}
	
	/**
	 * Load the radio buttons test form
	 */
	public static String loadRadioButtonsForm() throws IOException {
		return loadTestForm("form-with-radio-buttons.json");
	}
	
	/**
	 * Load the mixed fields test form
	 */
	public static String loadMixedFieldsForm() throws IOException {
		return loadTestForm("form-with-mixed-fields.json");
	}
	
	/**
	 * Load the repeat groups test form
	 */
	public static String loadRepeatGroupsForm() throws IOException {
		return loadTestForm("form-with-repeat-groups.json");
	}
	
	/**
	 * Load the advanced features test form
	 */
	public static String loadAdvancedFeaturesForm() throws IOException {
		return loadTestForm("form-with-advanced-features.json");
	}
}
