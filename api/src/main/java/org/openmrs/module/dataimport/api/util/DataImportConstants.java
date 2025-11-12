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

public final class DataImportConstants {
	
	private DataImportConstants() {
	}
	
	public static final String PATIENT_SHEET_NAME = "Patient Registration";
	
	public static final String PATIENT_REF_HEADER = "Patient Ref";
	
	public static final String META_SHEET = "__meta";
	
	public static final String CONCEPTS_SHEET = "__concepts";
	
	public static final String LIST_SHEET = "__lists";
	
	public static final String LONG_STRING_SHEET = "__strings";
	
	public static final String LONG_STRING_PREFIX = "__LONGSTR__:";
	
	public static final int MAX_CELL_TEXT_LENGTH = 32767;
	
	public static final int FIRST_DATA_ROW = 1;
	
	public static final int LAST_DATA_ROW = 1000;
	
	public static final String DATA_IMPORT_VISIT_TYPE_UUID = "0f631b77-4f1a-4c51-9e31-3d8b8f39ccbd";
	
	public static final String DATA_IMPORT_VISIT_TYPE_NAME = "Data Import Visit";
	
	public static final String DATA_IMPORT_VISIT_TYPE_DESCRIPTION = "Visit type automatically created by the Data Import module to group imported encounters";
	
	public static String normalizeLabel(String label) {
		if (label == null) {
			return null;
		}
		return label.trim().toLowerCase().replaceAll("\\s+", " ");
	}
}
