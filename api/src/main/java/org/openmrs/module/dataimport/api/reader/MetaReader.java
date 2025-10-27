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
import org.apache.poi.ss.usermodel.Workbook;
import org.openmrs.module.dataimport.api.util.WorkbookUtil;
import org.openmrs.module.dataimport.model.ColumnMeta;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.SheetMeta;
import org.openmrs.module.dataimport.model.TemplateMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads metadata from the __meta sheet in Excel workbooks.
 */
public class MetaReader {
	
	private static final Logger log = LoggerFactory.getLogger(MetaReader.class);
	
	private static final String META_SHEET = "__meta";
	
	/**
	 * Reads template metadata from the workbook.
	 */
	public static TemplateMeta read(Workbook workbook) {
		Sheet metaSheet = workbook.getSheet(META_SHEET);
		if (metaSheet == null) {
			throw new IllegalArgumentException("Workbook does not contain required __meta sheet");
		}
		
		TemplateMeta meta = new TemplateMeta();
		
		// Read template metadata (first table)
		Map<String, String> templateData = new HashMap<>();
		for (int rowIndex = 1; rowIndex <= 10; rowIndex++) { // Read first 10 rows
			Row row = metaSheet.getRow(rowIndex);
			if (row == null)
				break;
			
			Cell keyCell = row.getCell(0);
			Cell valueCell = row.getCell(1);
			
			if (keyCell != null && valueCell != null) {
				String key = WorkbookUtil.Cells.getString(keyCell);
				String value = WorkbookUtil.Cells.getString(valueCell);
				if (key != null && value != null) {
					templateData.put(key, value);
				}
			}
		}
		
		meta.templateVersion = Integer.parseInt(templateData.getOrDefault("templateVersion", "1"));
		meta.generatedAt = Instant.parse(templateData.getOrDefault("generatedAt", Instant.now().toString()));
		meta.locale = templateData.getOrDefault("locale", "en");
		meta.formName = templateData.getOrDefault("formName", "Unknown Form");
		meta.formUuid = templateData.getOrDefault("formUuid", "");
		meta.encounterTypeUuid = templateData.getOrDefault("encounterTypeUuid", "");
		
		int columnTableStartRow = findColumnTableStart(metaSheet);
		if (columnTableStartRow == -1) {
			throw new IllegalArgumentException("Could not find column metadata table in __meta sheet");
		}
		
		// Read column metadata
		readColumnMetadata(metaSheet, meta, columnTableStartRow);
		
		log.debug("Read template metadata: version={}, form={}, {} sheets", meta.templateVersion, meta.formName,
		    meta.sheets.size());
		
		return meta;
	}
	
	private static int findColumnTableStart(Sheet metaSheet) {
		for (int rowIndex = 0; rowIndex < metaSheet.getLastRowNum(); rowIndex++) {
			Row row = metaSheet.getRow(rowIndex);
			if (row == null)
				continue;
			
			Cell firstCell = row.getCell(0);
			if (firstCell != null && "sheetName".equals(WorkbookUtil.Cells.getString(firstCell))) {
				return rowIndex;
			}
		}
		return -1;
	}
	
	private static void readColumnMetadata(Sheet metaSheet, TemplateMeta meta, int startRow) {
		for (int rowIndex = startRow + 1; rowIndex <= metaSheet.getLastRowNum(); rowIndex++) {
			Row row = metaSheet.getRow(rowIndex);
			if (row == null)
				continue;
			
			ColumnMeta columnMeta = parseColumnMeta(row);
			if (columnMeta == null)
				continue;
			
			SheetMeta sheetMeta = meta.getSheetMeta(columnMeta.sheetName);
			if (sheetMeta == null) {
				sheetMeta = new SheetMeta();
				sheetMeta.sheetName = columnMeta.sheetName;
				meta.addSheetMeta(columnMeta.sheetName, sheetMeta);
			}
			
			sheetMeta.columns.add(columnMeta);
		}
	}
	
	private static ColumnMeta parseColumnMeta(Row row) {
		ColumnMeta meta = new ColumnMeta();
		
		Cell sheetNameCell = row.getCell(0);
		Cell colIndexCell = row.getCell(1);
		Cell fieldIdCell = row.getCell(2);
		Cell labelCell = row.getCell(3);
		Cell conceptUuidCell = row.getCell(4);
		Cell datatypeCell = row.getCell(5);
		Cell isRepeatCell = row.getCell(6);
		Cell isMultiCell = row.getCell(7);
		Cell answersJsonCell = row.getCell(8);
		
		if (sheetNameCell == null || colIndexCell == null) {
			return null; // Skip incomplete rows
		}
		
		meta.sheetName = WorkbookUtil.Cells.getString(sheetNameCell);
		meta.colIndex = (int) colIndexCell.getNumericCellValue();
		meta.fieldId = WorkbookUtil.Cells.getString(fieldIdCell);
		meta.label = WorkbookUtil.Cells.getString(labelCell);
		meta.conceptUuid = WorkbookUtil.Cells.getString(conceptUuidCell);
		
		// Parse datatype
		String datatypeStr = WorkbookUtil.Cells.getString(datatypeCell);
		if (datatypeStr != null) {
			try {
				meta.datatype = FieldDatatype.valueOf(datatypeStr);
			}
			catch (IllegalArgumentException e) {
				meta.datatype = FieldDatatype.TEXT;
			}
		} else {
			meta.datatype = FieldDatatype.TEXT;
		}
		
		meta.isRepeat = isRepeatCell != null && isRepeatCell.getBooleanCellValue();
		meta.isMulti = isMultiCell != null && isMultiCell.getBooleanCellValue();
		
		// Parse answers JSON
		String answersJson = WorkbookUtil.Cells.getString(answersJsonCell);
		if (answersJson != null && !answersJson.trim().isEmpty() && !"{}".equals(answersJson)) {
			meta.answers = parseAnswersJson(answersJson);
		}
		
		return meta;
	}
	
	private static Map<String, String> parseAnswersJson(String json) {
		Map<String, String> answers = new HashMap<>();
		
		// Simple JSON parsing for answers mapping
		// Expected format: {"label1":"uuid1","label2":"uuid2"}
		json = json.trim();
		if (!json.startsWith("{") || !json.endsWith("}")) {
			return answers;
		}
		
		json = json.substring(1, json.length() - 1); // Remove braces
		if (json.trim().isEmpty()) {
			return answers;
		}
		
		String[] pairs = json.split(",");
		for (String pair : pairs) {
			String[] keyValue = pair.split(":", 2);
			if (keyValue.length == 2) {
				String key = keyValue[0].trim().replaceAll("^\"|\"$", ""); // Remove quotes
				String value = keyValue[1].trim().replaceAll("^\"|\"$", ""); // Remove quotes
				answers.put(key, value);
			}
		}
		
		return answers;
	}
}
