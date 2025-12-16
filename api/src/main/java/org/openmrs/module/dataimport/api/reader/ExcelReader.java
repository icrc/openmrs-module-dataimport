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

import lombok.Getter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.openmrs.module.dataimport.api.util.DataImportConstants;
import org.openmrs.module.dataimport.api.util.WorkbookUtil;
import org.openmrs.module.dataimport.model.ColumnMeta;
import org.openmrs.module.dataimport.model.EncounterBlueprint;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.ObsBlueprint;
import org.openmrs.module.dataimport.model.SheetMeta;
import org.openmrs.module.dataimport.model.TemplateMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelReader {
	
	private static final Logger log = LoggerFactory.getLogger(ExcelReader.class);
	
	public static TemplateMeta read(Workbook workbook) {
		return read(workbook, true);
	}
	
	public static TemplateMeta read(Workbook workbook, boolean strict) {
		Sheet metaSheet = workbook.getSheet(DataImportConstants.META_SHEET);
		if (metaSheet == null) {
			throw new IllegalArgumentException("Workbook does not contain required __meta sheet");
		}
		
		TemplateMeta meta = new TemplateMeta();
		
		Map<String, String> templateData = new HashMap<>();
		// Read up to 20 rows to accommodate new fields (allowedLocationUuids, restrictToAllowedLocations)
		for (int rowIndex = 1; rowIndex <= 20; rowIndex++) {
			Row row = metaSheet.getRow(rowIndex);
			if (row == null)
				continue;
			
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
		meta.templateName = templateData.getOrDefault("templateName", "Multi-Form Template");
		meta.defaultLocationUuid = templateData.getOrDefault("defaultLocationUuid", "");
		meta.formCount = Integer.parseInt(templateData.getOrDefault("formCount", "0"));
		
		// Parse multi-location fields (new in template version 2+)
		String allowedLocationsStr = templateData.getOrDefault("allowedLocationUuids", "");
		if (allowedLocationsStr != null && !allowedLocationsStr.trim().isEmpty()) {
			String[] uuids = allowedLocationsStr.split(",");
			for (String uuid : uuids) {
				String trimmed = uuid.trim();
				if (!trimmed.isEmpty()) {
					meta.allowedLocationUuids.add(trimmed);
				}
			}
		}
		
		String restrictStr = templateData.getOrDefault("restrictToAllowedLocations", "false");
		meta.restrictToAllowedLocations = "true".equalsIgnoreCase(restrictStr.trim());
		
		int columnTableStartRow = findColumnTableStart(metaSheet);
		if (columnTableStartRow == -1) {
			throw new IllegalArgumentException("Could not find column metadata table in __meta sheet");
		}
		
		readColumnMetadata(metaSheet, meta, columnTableStartRow, strict);
		readFormList(metaSheet, meta);
		return meta;
	}
	
	private static void readFormList(Sheet metaSheet, TemplateMeta meta) {
		int formListStartRow = -1;
		for (int rowIndex = 0; rowIndex < metaSheet.getLastRowNum(); rowIndex++) {
			Row row = metaSheet.getRow(rowIndex);
			if (row == null)
				continue;
			
			Cell firstCell = row.getCell(0);
			if (firstCell != null && "formUuid".equals(WorkbookUtil.Cells.getString(firstCell))) {
				formListStartRow = rowIndex;
				break;
			}
		}
		
		if (formListStartRow == -1) {
			return;
		}
		
		for (int rowIndex = formListStartRow + 1; rowIndex <= metaSheet.getLastRowNum(); rowIndex++) {
			Row row = metaSheet.getRow(rowIndex);
			if (row == null)
				continue;
			
			Cell formUuidCell = row.getCell(0);
			Cell formNameCell = row.getCell(1);
			Cell formVersionCell = row.getCell(2);
			Cell encounterTypeCell = row.getCell(3);
			Cell sheetNameCell = row.getCell(4);
			
			if (formUuidCell == null || WorkbookUtil.Cells.getString(formUuidCell) == null) {
				break;
			}
			
			TemplateMeta.FormInfo formInfo = new TemplateMeta.FormInfo();
			formInfo.formUuid = WorkbookUtil.Cells.getString(formUuidCell);
			formInfo.formName = WorkbookUtil.Cells.getString(formNameCell);
			formInfo.formVersion = WorkbookUtil.Cells.getString(formVersionCell);
			formInfo.encounterTypeUuid = WorkbookUtil.Cells.getString(encounterTypeCell);
			formInfo.sheetName = WorkbookUtil.Cells.getString(sheetNameCell);
			
			meta.addForm(formInfo);
			
			SheetMeta sheetMeta = meta.getSheetMeta(formInfo.sheetName);
			if (sheetMeta != null) {
				sheetMeta.formUuid = formInfo.formUuid;
				sheetMeta.formName = formInfo.formName;
				sheetMeta.encounterTypeUuid = formInfo.encounterTypeUuid;
			}
		}
	}
	
	private static boolean isPatientRegistrationColumn(ColumnMeta columnMeta) {
		return DataImportConstants.PATIENT_SHEET_NAME.equals(columnMeta.sheetName);
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
	
	private static void readColumnMetadata(Sheet metaSheet, TemplateMeta meta, int startRow, boolean strict) {
		for (int rowIndex = startRow + 1; rowIndex <= metaSheet.getLastRowNum(); rowIndex++) {
			Row row = metaSheet.getRow(rowIndex);
			if (row == null)
				continue;
			
			ColumnMeta columnMeta = parseColumnMeta(row);
			if (columnMeta == null)
				continue;
			
			if (!isPatientRegistrationColumn(columnMeta) && !columnMeta.isPatientRef()) {
				if (columnMeta.datatype == null) {
					throw new IllegalArgumentException("__meta validation failed: missing datatype for column '"
					        + columnMeta.label + "' on sheet '" + columnMeta.sheetName + "'");
				}
				if (columnMeta.conceptUuid == null || columnMeta.conceptUuid.trim().isEmpty()) {
					log.debug("Column '{}' on sheet '{}' has no conceptUuid — will be skipped during import",
					    columnMeta.label, columnMeta.sheetName);
				}
				if (columnMeta.datatype == FieldDatatype.CODED
				        && (columnMeta.answers == null || columnMeta.answers.isEmpty())) {
					if (strict) {
						throw new IllegalArgumentException(
						        "__meta validation failed: missing coded answers mapping for column '" + columnMeta.label
						                + "' (" + columnMeta.conceptUuid + ") on sheet '" + columnMeta.sheetName + "'");
					} else {
						log.warn(
						    "__meta warning: missing coded answers mapping for column '{}' ({}) on sheet '{}' — proceeding in lenient mode",
						    columnMeta.label, columnMeta.conceptUuid, columnMeta.sheetName);
					}
				}
			}
			
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
		
		String datatypeStr = WorkbookUtil.Cells.getString(datatypeCell);
		if (datatypeStr != null && !datatypeStr.trim().isEmpty()) {
			try {
				meta.datatype = FieldDatatype.valueOf(datatypeStr);
			}
			catch (IllegalArgumentException e) {
				meta.datatype = FieldDatatype.TEXT;
			}
		} else {
			meta.datatype = null;
		}
		
		meta.isRepeat = isRepeatCell != null && isRepeatCell.getBooleanCellValue();
		meta.isMulti = isMultiCell != null && isMultiCell.getBooleanCellValue();
		
		String answersJson = WorkbookUtil.Cells.getString(answersJsonCell);
		if (answersJson != null && !answersJson.trim().isEmpty() && !"{}".equals(answersJson)) {
			meta.answers = parseAnswersJson(answersJson);
		}
		
		return meta;
	}
	
	private static Map<String, String> parseAnswersJson(String json) {
		Map<String, String> answers = new HashMap<>();
		json = json.trim();
		if (!json.startsWith("{") || !json.endsWith("}")) {
			return answers;
		}
		
		json = json.substring(1, json.length() - 1);
		if (json.trim().isEmpty()) {
			return answers;
		}
		
		String[] pairs = json.split(",");
		for (String pair : pairs) {
			String[] keyValue = pair.split(":", 2);
			if (keyValue.length == 2) {
				String key = keyValue[0].trim().replaceAll("^\"|\"$", "");
				String value = keyValue[1].trim().replaceAll("^\"|\"$", "");
				answers.put(key, value);
			}
		}
		
		return answers;
	}
	
	public static ExcelReader.PatientIndex indexPatients(org.apache.poi.ss.usermodel.Workbook workbook) {
		Sheet regSheet = workbook.getSheet(DataImportConstants.PATIENT_SHEET_NAME);
		if (regSheet == null) {
			return new ExcelReader.PatientIndex();
		}
		
		Map<String, ExcelReader.PatientRow> patientRows = new HashMap<>();
		
		for (int rowIndex = 1; rowIndex <= WorkbookUtil.lastDataRow(regSheet); rowIndex++) {
			if (WorkbookUtil.isEmptyRow(regSheet, rowIndex))
				continue;
			
			try {
				ExcelReader.PatientRow patientRow = ExcelReader.PatientRow.from(regSheet, rowIndex);
				if (patientRow != null && patientRow.patientRef != null && !patientRow.patientRef.isEmpty()) {
					patientRows.put(patientRow.patientRef, patientRow);
				}
			}
			catch (Exception e) {
				log.warn("Failed to parse patient row {}: {}", rowIndex, e.getMessage());
			}
		}
		
		return new ExcelReader.PatientIndex(patientRows);
	}
	
	public static EncounterBlueprint buildEncounter(SheetMeta sheetMeta, String templateDefaultLocationUuid) {
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
		
		return builder.build();
	}
	
	public static List<ObsBlueprint> buildObs(Sheet sheet, int rowIndex, SheetMeta sheetMeta) {
		ObsBuildOutcome outcome = buildObsDetailed(sheet, rowIndex, sheetMeta);
		if (!outcome.getIssues().isEmpty()) {
			for (ObsBuildIssue issue : outcome.getIssues()) {
				ColumnMeta column = issue.getColumn();
				String columnLabel = describeColumn(column);
				String rawValue = issue.getRawValue();
				if (rawValue != null && !rawValue.trim().isEmpty()) {
					log.warn("Skipping value '{}' for column '{}' on sheet '{}' row {}: {}", rawValue, columnLabel,
					    sheet.getSheetName(), rowIndex, issue.getMessage());
				} else {
					log.warn("Skipping column '{}' on sheet '{}' row {}: {}", columnLabel, sheet.getSheetName(), rowIndex,
					    issue.getMessage());
				}
			}
		}
		return outcome.getObs();
	}
	
	public static ObsBuildOutcome buildObsDetailed(Sheet sheet, int rowIndex, SheetMeta sheetMeta) {
		List<ObsBlueprint> obs = new ArrayList<>();
		List<ObsBuildIssue> issues = new ArrayList<>();
		Row row = sheet.getRow(rowIndex);
		
		if (row == null) {
			return new ObsBuildOutcome(obs, issues);
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
				List<String> unknownValues = new ArrayList<>();
				for (String singleLabel : labels) {
					String trimmed = singleLabel.trim();
					String mapped = column.getAnswerConceptUuid(trimmed);
					if (mapped != null && !mapped.isEmpty()) {
						obs.add(ObsBlueprint.coded(column.conceptUuid, mapped));
					} else {
						unknownValues.add(trimmed);
					}
				}
				if (!unknownValues.isEmpty()) {
					String message = String.format("Unknown coded values %s for concept %s", unknownValues,
					    column.conceptUuid);
					issues.add(new ObsBuildIssue(column, raw, message, null));
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
				String rawValue = WorkbookUtil.Cells.getString(cell);
				String baseMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
				String message = String.format("Failed to parse value for column '%s': %s", describeColumn(column),
				    baseMessage);
				issues.add(new ObsBuildIssue(column, rawValue, message, e));
			}
		}
		
		return new ObsBuildOutcome(obs, issues);
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
		
		if (column.isMulti && label.contains("|")) {
			String[] labels = label.split("\\|");
			List<ObsBlueprint> multiObs = new ArrayList<>();
			for (String singleLabel : labels) {
				String conceptUuid = column.getAnswerConceptUuid(singleLabel.trim());
				if (conceptUuid != null) {
					multiObs.add(ObsBlueprint.coded(column.conceptUuid, conceptUuid));
				}
			}
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
			String raw = WorkbookUtil.Cells.getString(cell);
			if (raw == null && cell != null) {
				raw = cell.toString();
			}
			throw new IllegalArgumentException("Expected numeric value but found '" + (raw != null ? raw : "") + "'");
		}
		return ObsBlueprint.numeric(column.conceptUuid, value);
	}
	
	private static ObsBlueprint buildDateObs(Cell cell, ColumnMeta column) {
		LocalDate value = WorkbookUtil.Cells.getLocalDate(cell);
		if (value == null) {
			String raw = WorkbookUtil.Cells.getString(cell);
			if (raw == null && cell != null) {
				raw = cell.toString();
			}
			throw new IllegalArgumentException(
			        "Expected date value (YYYY-MM-DD) but found '" + (raw != null ? raw : "") + "'");
		}
		return ObsBlueprint.date(column.conceptUuid, value);
	}
	
	private static ObsBlueprint buildDateTimeObs(Cell cell, ColumnMeta column) {
		Instant value = WorkbookUtil.Cells.getInstant(cell);
		if (value == null) {
			String raw = WorkbookUtil.Cells.getString(cell);
			if (raw == null && cell != null) {
				raw = cell.toString();
			}
			throw new IllegalArgumentException(
			        "Expected datetime value (ISO-8601) but found '" + (raw != null ? raw : "") + "'");
		}
		return ObsBlueprint.datetime(column.conceptUuid, value);
	}
	
	private static ObsBlueprint buildBooleanObs(Cell cell, ColumnMeta column) {
		Boolean value = WorkbookUtil.Cells.getBoolean(cell);
		if (value == null) {
			String raw = WorkbookUtil.Cells.getString(cell);
			if (raw == null && cell != null) {
				raw = cell.toString();
			}
			throw new IllegalArgumentException(
			        "Expected boolean value (true/false/yes/no/1/0) but found '" + (raw != null ? raw : "") + "'");
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
	
	public static List<ExcelReader.RowData> readIfPresent(Workbook workbook) {
		Sheet sheet = workbook.getSheet(DataImportConstants.CONCEPTS_SHEET);
		if (sheet == null) {
			return new ArrayList<>();
		}
		
		List<ExcelReader.RowData> rows = new ArrayList<>();
		for (int i = 1; i <= sheet.getLastRowNum(); i++) {
			Row row = sheet.getRow(i);
			if (row == null)
				continue;
			
			ExcelReader.RowData rd = parse(row);
			if (rd != null) {
				rows.add(rd);
			}
		}
		log.debug("Read {} rows from __concepts", rows.size());
		return rows;
	}
	
	private static ExcelReader.RowData parse(Row row) {
		ExcelReader.RowData rd = new ExcelReader.RowData();
		rd.sheetName = get(row, 0);
		rd.fieldId = get(row, 1);
		rd.questionLabel = get(row, 2);
		rd.questionConceptUuid = get(row, 3);
		rd.questionDatatype = get(row, 4);
		rd.questionClass = get(row, 5);
		rd.isMulti = "true".equalsIgnoreCase(get(row, 6));
		rd.answerLabel = get(row, 7);
		rd.answerConceptUuid = get(row, 8);
		rd.answerDatatype = get(row, 9);
		rd.answerClass = get(row, 10);
		
		if (rd.sheetName == null) {
			return null;
		}
		return rd;
	}
	
	private static String get(Row row, int idx) {
		Cell c = row.getCell(idx);
		return WorkbookUtil.Cells.getString(c);
	}
	
	public static class RowData {
		
		public String sheetName;
		
		public String fieldId;
		
		public String questionLabel;
		
		public String questionConceptUuid;
		
		public String questionDatatype;
		
		public String questionClass;
		
		public boolean isMulti;
		
		public String answerLabel;
		
		public String answerConceptUuid;
		
		public String answerDatatype;
		
		public String answerClass;
	}
	
	public static String describeColumn(ColumnMeta column) {
		if (column == null) {
			return "unknown column";
		}
		if (column.label != null && !column.label.trim().isEmpty()) {
			return column.label;
		}
		if (column.fieldId != null && !column.fieldId.trim().isEmpty()) {
			return column.fieldId;
		}
		if (column.conceptUuid != null && !column.conceptUuid.trim().isEmpty()) {
			return column.conceptUuid;
		}
		return "col " + column.colIndex;
	}
	
	public static class PatientRow {
		
		public String givenName;
		
		public String familyName;
		
		public String gender;
		
		public LocalDate birthdate;
		
		public String addressLine1;
		
		public String cityVillage;
		
		public String country;
		
		public String phone;
		
		public String patientRef;
		
		public static ExcelReader.PatientRow from(Sheet sheet, int rowIndex) {
			Row row = sheet.getRow(rowIndex);
			if (row == null)
				return null;
			
			ExcelReader.PatientRow patientRow = new ExcelReader.PatientRow();
			patientRow.givenName = WorkbookUtil.Cells.getString(row.getCell(0));
			patientRow.familyName = WorkbookUtil.Cells.getString(row.getCell(1));
			patientRow.gender = WorkbookUtil.Cells.getString(row.getCell(2));
			patientRow.birthdate = WorkbookUtil.Cells.getLocalDate(row.getCell(3));
			patientRow.addressLine1 = WorkbookUtil.Cells.getString(row.getCell(4));
			patientRow.cityVillage = WorkbookUtil.Cells.getString(row.getCell(5));
			patientRow.country = WorkbookUtil.Cells.getString(row.getCell(6));
			patientRow.phone = WorkbookUtil.Cells.getString(row.getCell(7));
			patientRow.patientRef = WorkbookUtil.Cells.getString(row.getCell(8));
			
			return patientRow;
		}
		
		public String patientRef() {
			return patientRef;
		}
	}
	
	@Getter
	public static class PatientIndex {
		
		private final Map<String, ExcelReader.PatientRow> patients;
		
		public PatientIndex() {
			this.patients = new HashMap<>();
		}
		
		public PatientIndex(Map<String, ExcelReader.PatientRow> patients) {
			this.patients = patients;
		}
		
		public ExcelReader.PatientResolution resolve(String patientRef) {
			if (patientRef == null || patientRef.isEmpty()) {
				return ExcelReader.PatientResolution.unresolved("Missing patient reference");
			}
			
			ExcelReader.PatientRow patientRow = patients.get(patientRef);
			if (patientRow == null) {
				return ExcelReader.PatientResolution.unresolved("Patient reference not found: " + patientRef);
			}
			
			return ExcelReader.PatientResolution.createNew(patientRow);
		}
	}
	
	@Getter
	public static class PatientResolution {
		
		private final boolean resolved;
		
		private final String patientUuid;
		
		private final ExcelReader.PatientRow patientRow;
		
		private final String errorMessage;
		
		private PatientResolution(boolean resolved, String patientUuid, ExcelReader.PatientRow patientRow,
		    String errorMessage) {
			this.resolved = resolved;
			this.patientUuid = patientUuid;
			this.patientRow = patientRow;
			this.errorMessage = errorMessage;
		}
		
		public static ExcelReader.PatientResolution createNew(ExcelReader.PatientRow patientRow) {
			return new ExcelReader.PatientResolution(true, null, patientRow, null);
		}
		
		public static ExcelReader.PatientResolution existing(String patientUuid) {
			return new ExcelReader.PatientResolution(true, patientUuid, null, null);
		}
		
		public static ExcelReader.PatientResolution unresolved(String errorMessage) {
			return new ExcelReader.PatientResolution(false, null, null, errorMessage);
		}
		
		public boolean needsCreation() {
			return resolved && patientUuid == null && patientRow != null;
		}
	}
	
	@Getter
	public static class ObsBuildOutcome {
		
		private final List<ObsBlueprint> obs;
		
		private final List<ObsBuildIssue> issues;
		
		public ObsBuildOutcome(List<ObsBlueprint> obs, List<ObsBuildIssue> issues) {
			this.obs = obs;
			this.issues = issues;
		}
	}
	
	@Getter
	public static class ObsBuildIssue {
		
		private final ColumnMeta column;
		
		private final String rawValue;
		
		private final String message;
		
		private final Throwable cause;
		
		public ObsBuildIssue(ColumnMeta column, String rawValue, String message, Throwable cause) {
			this.column = column;
			this.rawValue = rawValue;
			this.message = message;
			this.cause = cause;
		}
	}
}
