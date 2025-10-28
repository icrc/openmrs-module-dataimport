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
import org.openmrs.module.dataimport.api.ObsBlueprint;
import org.openmrs.module.dataimport.api.util.WorkbookUtil;
import org.openmrs.module.dataimport.model.ColumnMeta;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.SheetMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds observation blueprints from Excel sheet data.
 */
public class ObsReader {
	
	private static final Logger log = LoggerFactory.getLogger(ObsReader.class);
	
	/**
	 * Builds observation blueprints from a sheet row.
	 */
	public static List<ObsBlueprint> buildObs(Sheet sheet, int rowIndex, SheetMeta sheetMeta) {
		List<ObsBlueprint> obs = new ArrayList<>();
		Row row = sheet.getRow(rowIndex);
		
		if (row == null) {
			return obs;
		}
		
		for (ColumnMeta column : sheetMeta.columns) {
			if (column.isPatientRef())
				continue;
			if (column.conceptUuid == null || column.conceptUuid.isEmpty())
				continue;
			
			Cell cell = row.getCell(column.colIndex);
			if (WorkbookUtil.Cells.isBlank(cell))
				continue;
			
			if (column.datatype == FieldDatatype.CODED && column.isMulti) {
				String raw = WorkbookUtil.Cells.getString(cell);
				if (raw == null || raw.trim().isEmpty()) {
					continue;
				}
				String[] labels = raw.split("\\|");
				for (String singleLabel : labels) {
					String mapped = column.getAnswerConceptUuid(singleLabel.trim());
					if (mapped != null && !mapped.isEmpty()) {
						obs.add(ObsBlueprint.coded(column.conceptUuid, mapped));
					} else {
						log.warn("Unknown coded multi-select value '{}' for concept {} on sheet {} row {}", singleLabel,
						    column.conceptUuid, sheet.getSheetName(), rowIndex);
					}
				}
				continue;
			}
			
			try {
				ObsBlueprint obsBlueprint = buildObsFromCell(cell, column);
				if (obsBlueprint != null) {
					obs.add(obsBlueprint);
				}
			}
			catch (Exception e) {
				log.warn("Failed to build obs for column {} in row {}: {}", column.label, rowIndex, e.getMessage());
			}
		}
		
		return obs;
	}
	
	private static ObsBlueprint buildObsFromCell(Cell cell, ColumnMeta column) {
		switch (column.datatype) {
			case CODED:
				return buildCodedObs(cell, column);
			case NUMERIC:
				return buildNumericObs(cell, column);
			case DATE:
				return buildDateObs(cell, column);
			case DATETIME:
				return buildDateTimeObs(cell, column);
			case BOOLEAN:
				return buildBooleanObs(cell, column);
			case TEXT:
			default:
				return buildTextObs(cell, column);
		}
	}
	
	private static ObsBlueprint buildCodedObs(Cell cell, ColumnMeta column) {
		String label = WorkbookUtil.Cells.getString(cell);
		if (label == null || label.isEmpty()) {
			return null;
		}
		
		// Handle multi-select (pipe-separated values)
		if (column.isMulti && label.contains("|")) {
			// For multi-select, we'll create multiple obs blueprints
			// This will be handled by the caller
			String[] labels = label.split("\\|");
			List<ObsBlueprint> multiObs = new ArrayList<>();
			for (String singleLabel : labels) {
				String conceptUuid = column.getAnswerConceptUuid(singleLabel.trim());
				if (conceptUuid != null) {
					multiObs.add(ObsBlueprint.coded(column.conceptUuid, conceptUuid));
				}
			}
			// Return the first one, caller should handle multiple
			return multiObs.isEmpty() ? null : multiObs.get(0);
		}
		
		String conceptUuid = column.getAnswerConceptUuid(label);
		if (conceptUuid == null) {
			throw new IllegalArgumentException("Unknown coded value: " + label + " for concept " + column.conceptUuid);
		}
		
		return ObsBlueprint.coded(column.conceptUuid, conceptUuid);
	}
	
	private static ObsBlueprint buildNumericObs(Cell cell, ColumnMeta column) {
		Double value = WorkbookUtil.Cells.getDouble(cell);
		if (value == null) {
			return null;
		}
		return ObsBlueprint.numeric(column.conceptUuid, value);
	}
	
	private static ObsBlueprint buildDateObs(Cell cell, ColumnMeta column) {
		LocalDate value = WorkbookUtil.Cells.getLocalDate(cell);
		if (value == null) {
			return null;
		}
		return ObsBlueprint.date(column.conceptUuid, value);
	}
	
	private static ObsBlueprint buildDateTimeObs(Cell cell, ColumnMeta column) {
		Instant value = WorkbookUtil.Cells.getInstant(cell);
		if (value == null) {
			return null;
		}
		return ObsBlueprint.datetime(column.conceptUuid, value);
	}
	
	private static ObsBlueprint buildBooleanObs(Cell cell, ColumnMeta column) {
		Boolean value = WorkbookUtil.Cells.getBoolean(cell);
		if (value == null) {
			return null;
		}
		return ObsBlueprint.bool(column.conceptUuid, value);
	}
	
	private static ObsBlueprint buildTextObs(Cell cell, ColumnMeta column) {
		String value = WorkbookUtil.Cells.getString(cell);
		if (value == null || value.isEmpty()) {
			return null;
		}
		return ObsBlueprint.text(column.conceptUuid, value);
	}
}
