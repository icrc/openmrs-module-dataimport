/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.reader;

import org.apache.poi.ss.usermodel.Sheet;
import org.openmrs.module.dataimport.api.EncounterBlueprint;
import org.openmrs.module.dataimport.model.SheetMeta;

import java.time.Instant;

/**
 * Builds encounter blueprints from Excel sheet data.
 */
public class EncounterReader {
	
	public static EncounterBlueprint buildEncounter(Sheet sheet, int rowIndex, SheetMeta sheetMeta,
	        String templateDefaultLocationUuid) {
		EncounterBlueprint.Builder builder = EncounterBlueprint.builder();
		
		if (sheetMeta.formUuid != null && !sheetMeta.formUuid.isEmpty()) {
			builder.formUuid(sheetMeta.formUuid);
		}
		
		if (sheetMeta.encounterTypeUuid != null && !sheetMeta.encounterTypeUuid.isEmpty()) {
			builder.encounterTypeUuid(sheetMeta.encounterTypeUuid);
		}
		
		String locationUuid = sheetMeta.locationUuid;
		if (locationUuid == null || locationUuid.isEmpty()) {
			locationUuid = templateDefaultLocationUuid;
		}
		if (locationUuid != null && !locationUuid.isEmpty()) {
			builder.locationUuid(locationUuid);
		}
		
		builder.encounterDatetime(Instant.now());
		
		// Provider will be resolved at commit time using defaults
		// Form sheets don't contain these system-level fields
		
		return builder.build();
	}
}
