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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Metadata about a generated Excel template for import purposes.
 */
public class TemplateMeta {
	
	public int templateVersion = 1;
	
	public Instant generatedAt;
	
	public String locale = "en";
	
	public String formName;
	
	public String formUuid;
	
	public String encounterTypeUuid;
	
	// Map of sheet name to sheet metadata
	public final Map<String, SheetMeta> sheets = new HashMap<>();
	
	public SheetMeta getSheetMeta(String sheetName) {
		return sheets.get(sheetName);
	}
	
	public void addSheetMeta(String sheetName, SheetMeta sheetMeta) {
		sheets.put(sheetName, sheetMeta);
	}
}
