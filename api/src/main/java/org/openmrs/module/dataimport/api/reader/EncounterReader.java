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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.openmrs.module.dataimport.api.EncounterBlueprint;
import org.openmrs.module.dataimport.api.ObsBlueprint;
import org.openmrs.module.dataimport.api.util.WorkbookUtil;
import org.openmrs.module.dataimport.model.ColumnMeta;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.SheetMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds encounter blueprints from Excel sheet data.
 */
public class EncounterReader {
	
	/**
	 * Builds an encounter blueprint from a sheet row.
	 */
	public static EncounterBlueprint buildEncounter(Sheet sheet, int rowIndex, SheetMeta sheetMeta) {
		Row row = sheet.getRow(rowIndex);
		if (row == null) {
			throw new IllegalArgumentException("Row " + rowIndex + " does not exist");
		}
		
		EncounterBlueprint.Builder builder = EncounterBlueprint.builder();
		if (sheetMeta.encounterTypeUuid != null && !sheetMeta.encounterTypeUuid.isEmpty()) {
			builder.encounterTypeUuid(sheetMeta.encounterTypeUuid);
		}
		
		builder.encounterDatetime(Instant.now());
		for (ColumnMeta column : sheetMeta.columns) {
			if (column.isPatientRef())
				continue;
			
			Cell cell = row.getCell(column.colIndex);
			if (WorkbookUtil.Cells.isBlank(cell))
				continue;
			
			String fieldId = column.fieldId;
			if (fieldId == null)
				continue;
			
			switch (fieldId.toLowerCase()) {
				case "encounterdate":
				case "encounter_date":
				case "date":
					if (column.datatype == FieldDatatype.DATE) {
						LocalDate date = WorkbookUtil.Cells.getLocalDate(cell);
						if (date != null) {
							builder.encounterDatetime(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
						}
					}
					break;
				case "encounterdatetime":
				case "encounter_datetime":
				case "datetime":
					if (column.datatype == FieldDatatype.DATETIME) {
						Instant datetime = WorkbookUtil.Cells.getInstant(cell);
						if (datetime != null) {
							builder.encounterDatetime(datetime);
						}
					}
					break;
				case "location":
					String locationUuid = WorkbookUtil.Cells.getString(cell);
					if (locationUuid != null && !locationUuid.isEmpty()) {
						builder.locationUuid(locationUuid);
					}
					break;
				case "provider":
				case "provider_uuid":
					String providerUuid = WorkbookUtil.Cells.getString(cell);
					if (providerUuid != null && !providerUuid.isEmpty()) {
						builder.providerUuid(providerUuid);
					}
					break;
			}
		}
		
		return builder.build();
	}
}
