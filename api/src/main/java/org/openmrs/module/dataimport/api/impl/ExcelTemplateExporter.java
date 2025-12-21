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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openmrs.Concept;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.util.DataImportConstants;
import org.openmrs.module.dataimport.api.util.ExcelNameUtils;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.SheetModel;
import org.openmrs.module.dataimport.model.TemplateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExcelTemplateExporter {
	
	private static final Logger log = LoggerFactory.getLogger(ExcelTemplateExporter.class);
	
	private static final String PATIENT_REFS_RANGE = "PatientRefs";
	
	private static final ObjectMapper objectMapper = new ObjectMapper();
	
	private final Map<String, Integer> usedNames = new HashMap<>();
	
	public byte[] export(FormModel form, boolean includePatient) {
		return export(form, includePatient, null);
	}
	
	public byte[] export(FormModel form, boolean includePatient, String locationUuid) {
		TemplateOptions opts = new TemplateOptions();
		opts.includePatient = includePatient;
		opts.locationUuid = locationUuid != null ? locationUuid : "";
		return export(form, opts);
	}
	
	/**
	 * Exports a FormModel to an Excel workbook using the provided TemplateOptions. This overload
	 * supports multi-location templates with allowedLocationUuids and restrictToAllowedLocations.
	 *
	 * @param form the form model to export
	 * @param opts template options including location settings
	 * @return Excel workbook as byte array
	 */
	public byte[] export(FormModel form, TemplateOptions opts) {
		boolean includePatient = opts != null ? opts.includePatient : true;
		String locationUuid = opts != null ? opts.getEffectiveDefaultLocation() : null;
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			LongStringRegistry longStrings = new LongStringRegistry();
			DataFormat dataFormat = workbook.createDataFormat();
			CellStyle dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd"));
			CellStyle dateTimeStyle = workbook.createCellStyle();
			dateTimeStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd hh:mm"));
			
			XSSFSheet listSheet = getOrCreateListSheet(workbook);
			
			if (includePatient) {
				String[] genderOptions = { "Male", "Female", "Unknown" };
				int genderStartRow = addAnswersToListSheet(listSheet, "__GLOBAL__", "patientGender", genderOptions, null);
				createPatientSheet(workbook, listSheet, genderStartRow);
			}
			
			for (SheetModel sheetModel : form.sheets) {
				String uniqueSheetName = getUniqueSheetName(workbook, sheetModel.sheetName);
				sheetModel.sheetName = uniqueSheetName;
				XSSFSheet sheet = workbook.createSheet(uniqueSheetName);
				Row headerRow = sheet.createRow(0);
				CellStyle headerStyle = createHeaderStyle(workbook);
				int columnIndex = 0;
				
				Cell patientRefHeaderCell = headerRow.createCell(columnIndex);
				patientRefHeaderCell.setCellValue(DataImportConstants.PATIENT_REF_HEADER);
				patientRefHeaderCell.setCellStyle(headerStyle);
				sheet.setColumnWidth(columnIndex, DataImportConstants.PATIENT_REF_COLUMN_WIDTH);
				columnIndex++;
				
				for (FieldModel field : sheetModel.fields) {
					Cell headerCell = headerRow.createCell(columnIndex);
					headerCell.setCellValue(labelWithMarkers(field));
					
					headerCell.setCellStyle(headerStyle);
					
					if (field.datatype == FieldDatatype.DATE) {
						for (int rowIndex = DataImportConstants.FIRST_DATA_ROW; rowIndex <= DataImportConstants.LAST_DATA_ROW; rowIndex++) {
							Cell cell = getOrCreate(sheet, rowIndex, columnIndex);
							cell.setCellStyle(dateStyle);
						}
					} else if (field.datatype == FieldDatatype.DATETIME) {
						for (int rowIndex = DataImportConstants.FIRST_DATA_ROW; rowIndex <= DataImportConstants.LAST_DATA_ROW; rowIndex++) {
							Cell cell = getOrCreate(sheet, rowIndex, columnIndex);
							cell.setCellStyle(dateTimeStyle);
						}
					} else if (field.datatype == FieldDatatype.CODED) {
						handleCodedField(workbook, sheet, field, columnIndex);
					}
					
					sheet.setColumnWidth(columnIndex, DataImportConstants.STANDARD_COLUMN_WIDTH);
					columnIndex++;
				}
				sheet.createFreezePane(0, 1);
			}
			
			createMetaSheet(workbook, form, opts, longStrings);
			createConceptsSheet(workbook, form);
			if (longStrings.hasEntries()) {
				longStrings.writeSheet(workbook);
			}
			ensurePatientRefsNamedRange(workbook);
			for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
				XSSFSheet s = workbook.getSheetAt(i);
				if (!DataImportConstants.PATIENT_SHEET_NAME.equals(s.getSheetName())) {
					applyPatientRefDropdown(s, 0, DataImportConstants.FIRST_DATA_ROW, DataImportConstants.LAST_DATA_ROW);
				}
			}
			
			finalizeListSheet(workbook);
			
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			workbook.write(outputStream);
			return outputStream.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to export template due to I/O error", e);
		}
		catch (RuntimeException e) {
			throw new IllegalStateException("Failed to export template", e);
		}
	}
	
	private static void createPatientSheet(XSSFWorkbook workbook, XSSFSheet listSheet, int genderStartRow) {
		XSSFSheet sheet = workbook.createSheet(DataImportConstants.PATIENT_SHEET_NAME);
		CellStyle headerStyle = createHeaderStyle(workbook);
		DataFormat dataFormat = workbook.createDataFormat();
		String[] columnHeaders = { "Given Name", "Family Name", "Gender", "Birthdate (yyyy-mm-dd)", "Address Line 1",
		        "City/Village", "Country", "Phone", DataImportConstants.PATIENT_REF_HEADER };
		Row headerRow = sheet.createRow(0);
		for (int columnIndex = 0; columnIndex < columnHeaders.length; columnIndex++) {
			Cell headerCell = headerRow.createCell(columnIndex);
			headerCell.setCellValue(columnHeaders[columnIndex]);
			headerCell.setCellStyle(headerStyle);
			if (columnIndex != 8) {
				sheet.setColumnWidth(columnIndex, DataImportConstants.STANDARD_COLUMN_WIDTH);
			}
		}
		
		CellStyle readonlyStyle = workbook.createCellStyle();
		readonlyStyle.setLocked(true);
		readonlyStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
		readonlyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		CellStyle unlockedStyle = workbook.createCellStyle();
		unlockedStyle.setLocked(false);
		CellStyle textStyle = workbook.createCellStyle();
		textStyle.setLocked(false);
		textStyle.setDataFormat(dataFormat.getFormat("@"));
		
		createGenderDropdown(workbook, sheet, listSheet, genderStartRow);
		
		for (int r = DataImportConstants.FIRST_DATA_ROW; r <= DataImportConstants.LAST_DATA_ROW; r++) {
			Row dataRow = sheet.getRow(r);
			if (dataRow == null)
				dataRow = sheet.createRow(r);
			for (int c = 0; c <= 7; c++) {
				Cell editableCell = dataRow.getCell(c);
				if (editableCell == null)
					editableCell = dataRow.createCell(c);
				if (c == 7) {
					editableCell.setCellStyle(textStyle);
				} else {
					editableCell.setCellStyle(unlockedStyle);
				}
			}
			
			Cell patientRefCell = dataRow.getCell(8);
			if (patientRefCell == null)
				patientRefCell = dataRow.createCell(8);
			
			String dataRange = "A" + (r + 1) + ":H" + (r + 1);
			String formula = "IF(COUNTA(" + dataRange + ")=0,\"\",\"P\"&TEXT(ROW()-1,\"000\")&\" - \"&TRIM(A" + (r + 1)
			        + "&\" \"&B" + (r + 1) + ")&\" (\"&C" + (r + 1) + "&\", \"&TEXT(D" + (r + 1) + ",\"yyyy-mm-dd\")&\")\")";
			patientRefCell.setCellFormula(formula);
			patientRefCell.setCellStyle(readonlyStyle);
		}
		
		sheet.setColumnWidth(8, DataImportConstants.PATIENT_REF_COLUMN_WIDTH);
		sheet.createFreezePane(0, 1);
		sheet.protectSheet("dataimport");
	}
	
	private static void createGenderDropdown(XSSFWorkbook workbook, XSSFSheet sheet, XSSFSheet listSheet, int startRow) {
		String[] genderOptions = { "Male", "Female", "Unknown" };
		int endRow = startRow + genderOptions.length - 1;
		
		Name namedRange = workbook.createName();
		namedRange.setNameName("GenderOptions");
		String reference = String.format("'%s'!$C$%d:$C$%d", DataImportConstants.LIST_SHEET, startRow, endRow);
		namedRange.setRefersToFormula(reference);
		
		DataValidationHelper helper = sheet.getDataValidationHelper();
		DataValidationConstraint constraint = helper.createFormulaListConstraint("GenderOptions");
		CellRangeAddressList region = new CellRangeAddressList(DataImportConstants.FIRST_DATA_ROW,
		        DataImportConstants.LAST_DATA_ROW, 2, 2);
		DataValidation validation = helper.createValidation(constraint, region);
		sheet.addValidationData(validation);
	}
	
	private static Cell getOrCreate(Sheet sheet, int rowIndex, int columnIndex) {
		Row row = sheet.getRow(rowIndex);
		if (row == null)
			row = sheet.createRow(rowIndex);
		Cell cell = row.getCell(columnIndex);
		if (cell == null)
			cell = row.createCell(columnIndex);
		return cell;
	}
	
	private static String labelWithMarkers(FieldModel field) {
		String labelText = field.label != null ? field.label : field.id;
		
		// Prepend section label if available
		if (field.sectionLabel != null && !field.sectionLabel.trim().isEmpty()) {
			labelText = field.sectionLabel + ": " + labelText;
		}
		
		if (field.requiredAlways)
			labelText = labelText + " *";
		
		// Add indicator for conditional fields
		if (field.isConditional()) {
			labelText = labelText + " (?)";
		}
		
		return labelText;
	}
	
	private XSSFSheet getOrCreateListSheet(XSSFWorkbook workbook) {
		XSSFSheet listSheet = workbook.getSheet(DataImportConstants.LIST_SHEET);
		if (listSheet == null) {
			listSheet = workbook.createSheet(DataImportConstants.LIST_SHEET);
			workbook.setSheetVisibility(workbook.getSheetIndex(listSheet), SheetVisibility.VERY_HIDDEN);
			initializeListSheetHeaders(workbook, listSheet);
		}
		return listSheet;
	}
	
	private void initializeListSheetHeaders(XSSFWorkbook workbook, XSSFSheet listSheet) {
		CellStyle headerStyle = createHeaderStyle(workbook);
		Row headerRow = listSheet.createRow(0);
		String[] headers = { "fieldId", "sheetName", "answerLabel", "answerConceptUuid", "sortOrder" };
		for (int i = 0; i < headers.length; i++) {
			Cell cell = headerRow.createCell(i);
			cell.setCellValue(headers[i]);
			cell.setCellStyle(headerStyle);
		}
		listSheet.createFreezePane(0, 1);
	}
	
	private int addAnswersToListSheet(XSSFSheet listSheet, String sheetName, String fieldId, String[] answerLabels,
	        List<AnswerModel> answers) {
		int currentRow = listSheet.getLastRowNum() + 1;
		int startRow = currentRow;
		
		if (answerLabels != null) {
			for (int i = 0; i < answerLabels.length; i++) {
				Row row = listSheet.createRow(currentRow++);
				row.createCell(0).setCellValue(fieldId);
				row.createCell(1).setCellValue(sheetName);
				row.createCell(2).setCellValue(answerLabels[i]);
				row.createCell(3).setCellValue("");
				row.createCell(4).setCellValue(i + 1);
			}
		} else if (answers != null) {
			for (int i = 0; i < answers.size(); i++) {
				AnswerModel answer = answers.get(i);
				String displayValue = answer.label != null ? answer.label
				        : (answer.conceptUuid != null ? answer.conceptUuid : (answer.value != null ? answer.value : ""));
				Row row = listSheet.createRow(currentRow++);
				row.createCell(0).setCellValue(fieldId);
				row.createCell(1).setCellValue(sheetName);
				row.createCell(2).setCellValue(displayValue);
				row.createCell(3).setCellValue(answer.conceptUuid != null ? answer.conceptUuid : "");
				row.createCell(4).setCellValue(i + 1);
			}
		}
		
		return startRow + 1;
	}
	
	private void finalizeListSheet(XSSFWorkbook workbook) {
		XSSFSheet listSheet = workbook.getSheet(DataImportConstants.LIST_SHEET);
		if (listSheet != null && listSheet.getLastRowNum() > 0) {
			for (int i = 0; i < 5; i++) {
				listSheet.autoSizeColumn(i);
			}
		}
	}
	
	private String getUniqueSheetName(XSSFWorkbook workbook, String baseName) {
		String candidate = ExcelNameUtils.sanitizeSheetName(baseName);
		int counter = 1;
		
		while (workbook.getSheet(candidate) != null) {
			usedNames.put(baseName, counter);
			candidate = ExcelNameUtils.getUniqueSheetName(baseName, usedNames);
			counter++;
			
			if (counter > DataImportConstants.MAX_SHEET_NAME_ATTEMPTS) {
				candidate = "Sheet_" + System.currentTimeMillis();
				break;
			}
		}
		
		if (counter > 1) {
			log.debug("Sheet name '{}' already exists, using unique name '{}'", baseName, candidate);
		}
		
		return candidate;
	}
	
	private String makeSafeExcelName(String baseName) {
		if (baseName == null || baseName.trim().isEmpty())
			baseName = "List";
		String safeName = baseName.replaceAll("[^A-Za-z0-9_]", "_");
		if (!safeName.isEmpty() && !Character.isLetter(safeName.charAt(0)) && safeName.charAt(0) != '_') {
			safeName = "_" + safeName;
		}
		
		// Excel doesn't allow named ranges to have names that are valid cell references (e.g., "q1", "A1")
		// A valid cell reference is: one or more letters followed by one or more digits (nothing else)
		// Use regex to check if it matches this exact pattern
		if (ExcelNameUtils.isExcelCellReference(safeName)) {
			safeName = "field_" + safeName;
		}
		
		if (safeName.length() > DataImportConstants.MAX_EXCEL_NAME_LENGTH)
			safeName = safeName.substring(0, 240);
		return safeName;
	}
	
	private String makeUniqueExcelName(String baseName) {
		String safeName = makeSafeExcelName(baseName);
		Integer count = usedNames.get(safeName);
		if (count == null) {
			usedNames.put(safeName, 1);
			return safeName;
		}
		count++;
		usedNames.put(safeName, count);
		
		String uniqueName = safeName + "_" + count;
		if (uniqueName.length() > DataImportConstants.MAX_EXCEL_NAME_LENGTH) {
			int availableSpace = DataImportConstants.MAX_EXCEL_NAME_LENGTH - String.valueOf(count).length() - 1; // -1 for underscore
			uniqueName = safeName.substring(0, Math.max(1, availableSpace)) + "_" + count;
		}
		return uniqueName;
	}
	
	private void handleCodedField(XSSFWorkbook workbook, XSSFSheet sheet, FieldModel field, int columnIndex) {
		if (field.answers.isEmpty()) {
			return;
		}
		
		createSimpleDropdown(workbook, sheet, field, columnIndex);
	}
	
	private void createSimpleDropdown(XSSFWorkbook workbook, XSSFSheet sheet, FieldModel field, int columnIndex) {
		if (field.answers.isEmpty()) {
			return;
		}
		
		String fieldName = makeUniqueExcelName(field.label != null ? field.label : field.id);
		
		XSSFSheet listSheet = getOrCreateListSheet(workbook);
		
		String sheetName = sheet.getSheetName();
		int startRow = addAnswersToListSheet(listSheet, sheetName, field.id, null, field.answers);
		int endRow = startRow + field.answers.size() - 1;
		
		Name namedRange = workbook.createName();
		namedRange.setNameName(fieldName);
		String reference = String.format("'%s'!$C$%d:$C$%d", DataImportConstants.LIST_SHEET, startRow, endRow);
		namedRange.setRefersToFormula(reference);
		
		DataValidationHelper helper = sheet.getDataValidationHelper();
		DataValidationConstraint constraint = helper.createFormulaListConstraint(fieldName);
		CellRangeAddressList region = new CellRangeAddressList(DataImportConstants.FIRST_DATA_ROW,
		        DataImportConstants.LAST_DATA_ROW, columnIndex, columnIndex);
		DataValidation validation = helper.createValidation(constraint, region);
		
		sheet.addValidationData(validation);
	}
	
	private void ensurePatientRefsNamedRange(XSSFWorkbook workbook) {
		String regSheet = DataImportConstants.PATIENT_SHEET_NAME;
		String formula = "IF(COUNTA('" + regSheet + "'!$I:$I)<=1," + "'" + regSheet + "'!$I$2," + "OFFSET('" + regSheet
		        + "'!$I$2,0,0,COUNTA('" + regSheet + "'!$I:$I)-1,1))";
		Name name = workbook.getName(PATIENT_REFS_RANGE);
		if (name == null) {
			name = workbook.createName();
			name.setNameName(PATIENT_REFS_RANGE);
		}
		name.setRefersToFormula(formula);
	}
	
	private void applyPatientRefDropdown(XSSFSheet sheet, int targetColumnIndex, int firstDataRow, int lastDataRow) {
		DataValidationHelper helper = sheet.getDataValidationHelper();
		DataValidationConstraint constraint = helper.createFormulaListConstraint(PATIENT_REFS_RANGE);
		CellRangeAddressList region = new CellRangeAddressList(firstDataRow, lastDataRow, targetColumnIndex,
		        targetColumnIndex);
		DataValidation validation = helper.createValidation(constraint, region);
		sheet.addValidationData(validation);
	}
	
	private static CellStyle createHeaderStyle(XSSFWorkbook workbook) {
		CellStyle style = workbook.createCellStyle();
		Font font = workbook.createFont();
		font.setBold(true);
		style.setFont(font);
		style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		return style;
	}
	
	private static CellStyle createSectionHeaderStyle(XSSFWorkbook workbook) {
		CellStyle style = workbook.createCellStyle();
		Font font = workbook.createFont();
		font.setBold(true);
		font.setFontHeightInPoints((short) 12);
		style.setFont(font);
		style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		return style;
	}
	
	private void createMetaSheet(XSSFWorkbook workbook, FormModel form, TemplateOptions opts,
	        LongStringRegistry longStrings) {
		XSSFSheet metaSheet = workbook.createSheet(DataImportConstants.META_SHEET);
		workbook.setSheetHidden(workbook.getSheetIndex(metaSheet), true);
		
		CellStyle headerStyle = createHeaderStyle(workbook);
		CellStyle sectionHeaderStyle = createSectionHeaderStyle(workbook);
		
		// Extract location settings from options
		String locationUuid = opts != null ? opts.getEffectiveDefaultLocation() : null;
		List<String> allowedLocationUuids = opts != null ? opts.getAllAllowedLocations() : new ArrayList<>();
		boolean restrictToAllowedLocations = opts != null && opts.restrictToAllowedLocations;
		boolean autoCloseVisits = opts != null && opts.autoCloseVisits;
		
		int rowIndex = 0;
		
		Row sectionRow = metaSheet.createRow(rowIndex++);
		Cell sectionCell = sectionRow.createCell(0);
		sectionCell.setCellValue("TEMPLATE INFORMATION");
		sectionCell.setCellStyle(sectionHeaderStyle);
		metaSheet.addMergedRegion(new CellRangeAddress(rowIndex - 1, rowIndex - 1, 0, 1));
		
		Row headerRow = metaSheet.createRow(rowIndex++);
		Cell keyHeader = headerRow.createCell(0);
		keyHeader.setCellValue("key");
		keyHeader.setCellStyle(headerStyle);
		Cell valueHeader = headerRow.createCell(1);
		valueHeader.setCellValue("value");
		valueHeader.setCellStyle(headerStyle);
		
		metaSheet.createRow(rowIndex++).createCell(0).setCellValue("templateVersion");
		metaSheet.getRow(rowIndex - 1).createCell(1).setCellValue(2); // Version 2 for multi-location support
		
		metaSheet.createRow(rowIndex++).createCell(0).setCellValue("generatedAt");
		metaSheet.getRow(rowIndex - 1).createCell(1).setCellValue(Instant.now().toString());
		
		metaSheet.createRow(rowIndex++).createCell(0).setCellValue("locale");
		metaSheet.getRow(rowIndex - 1).createCell(1).setCellValue("en");
		
		metaSheet.createRow(rowIndex++).createCell(0).setCellValue("templateName");
		metaSheet.getRow(rowIndex - 1).createCell(1).setCellValue("Multi-Form Template");
		
		metaSheet.createRow(rowIndex++).createCell(0).setCellValue("defaultLocationUuid");
		metaSheet.getRow(rowIndex - 1).createCell(1).setCellValue(locationUuid != null ? locationUuid : "");
		
		metaSheet.createRow(rowIndex++).createCell(0).setCellValue("formCount");
		metaSheet.getRow(rowIndex - 1).createCell(1).setCellValue(form.sheets.size());
		
		// New multi-location fields (T039-T042)
		metaSheet.createRow(rowIndex++).createCell(0).setCellValue("allowedLocationUuids");
		metaSheet.getRow(rowIndex - 1).createCell(1).setCellValue(String.join(",", allowedLocationUuids));
		
		metaSheet.createRow(rowIndex++).createCell(0).setCellValue("restrictToAllowedLocations");
		metaSheet.getRow(rowIndex - 1).createCell(1).setCellValue(String.valueOf(restrictToAllowedLocations));
		
		metaSheet.createRow(rowIndex++).createCell(0).setCellValue("autoCloseVisits");
		metaSheet.getRow(rowIndex - 1).createCell(1).setCellValue(String.valueOf(autoCloseVisits));
		
		rowIndex += 2;
		
		sectionRow = metaSheet.createRow(rowIndex++);
		sectionCell = sectionRow.createCell(0);
		sectionCell.setCellValue("FORM DEFINITIONS");
		sectionCell.setCellStyle(sectionHeaderStyle);
		metaSheet.addMergedRegion(new CellRangeAddress(rowIndex - 1, rowIndex - 1, 0, 5));
		
		Row formHeaderRow = metaSheet.createRow(rowIndex++);
		String[] formHeaders = { "formUuid", "formName", "formVersion", "formType", "encounterTypeUuid", "sheetName" };
		for (int i = 0; i < formHeaders.length; i++) {
			Cell cell = formHeaderRow.createCell(i);
			cell.setCellValue(formHeaders[i]);
			cell.setCellStyle(headerStyle);
		}
		
		for (SheetModel sheetModel : form.sheets) {
			Row formRow = metaSheet.createRow(rowIndex++);
			String formUuid = sheetModel.formUuid != null ? sheetModel.formUuid : "";
			String formName = sheetModel.sheetName;
			String formVersion = sheetModel.formVersion != null ? sheetModel.formVersion : "1.0";
			String formType = sheetModel.formType != null ? sheetModel.formType.getCode() : "o3-json"; // Default to O3
			String encounterTypeUuid = sheetModel.encounterTypeUuid != null ? sheetModel.encounterTypeUuid : "";
			formRow.createCell(0).setCellValue(formUuid);
			formRow.createCell(1).setCellValue(formName);
			formRow.createCell(2).setCellValue(formVersion);
			formRow.createCell(3).setCellValue(formType);
			formRow.createCell(4).setCellValue(encounterTypeUuid);
			formRow.createCell(5).setCellValue(sheetModel.sheetName);
		}
		
		rowIndex += 2;
		
		sectionRow = metaSheet.createRow(rowIndex++);
		sectionCell = sectionRow.createCell(0);
		sectionCell.setCellValue("FIELD MAPPINGS");
		sectionCell.setCellStyle(sectionHeaderStyle);
		metaSheet.addMergedRegion(new CellRangeAddress(rowIndex - 1, rowIndex - 1, 0, 8));
		
		Row columnHeaderRow = metaSheet.createRow(rowIndex++);
		String[] columnHeaders = { "sheetName", "colIndex", "fieldId", "label", "conceptUuid", "datatype", "isRepeat",
		        "isMulti", "answersJson" };
		for (int i = 0; i < columnHeaders.length; i++) {
			Cell cell = columnHeaderRow.createCell(i);
			cell.setCellValue(columnHeaders[i]);
			cell.setCellStyle(headerStyle);
		}
		
		if (workbook.getSheet(DataImportConstants.PATIENT_SHEET_NAME) != null) {
			String[] patientHeaders = { "Given Name", "Family Name", "Gender", "Birthdate (yyyy-mm-dd)", "Address Line 1",
			        "City/Village", "Country", "Phone", DataImportConstants.PATIENT_REF_HEADER };
			String genderAnswersJson = "{\"Male\":\"Male\",\"Female\":\"Female\",\"Unknown\":\"Unknown\"}";
			for (int colIndex = 0; colIndex < patientHeaders.length; colIndex++) {
				Row row = metaSheet.createRow(rowIndex++);
				row.createCell(0).setCellValue(DataImportConstants.PATIENT_SHEET_NAME);
				row.createCell(1).setCellValue(colIndex);
				row.createCell(2).setCellValue("");
				row.createCell(3).setCellValue(patientHeaders[colIndex]);
				row.createCell(4).setCellValue("");
				row.createCell(5).setCellValue("TEXT");
				row.createCell(6).setCellValue(false);
				row.createCell(7).setCellValue(false);
				row.createCell(8).setCellValue(colIndex == 2 ? genderAnswersJson : "");
			}
		}
		
		for (SheetModel sheetModel : form.sheets) {
			int colIndex = 0;
			
			Row row = metaSheet.createRow(rowIndex++);
			row.createCell(0).setCellValue(sheetModel.sheetName);
			row.createCell(1).setCellValue(colIndex++);
			row.createCell(2).setCellValue("");
			row.createCell(3).setCellValue(DataImportConstants.PATIENT_REF_HEADER);
			row.createCell(4).setCellValue("");
			row.createCell(5).setCellValue("TEXT");
			row.createCell(6).setCellValue(false);
			row.createCell(7).setCellValue(false);
			row.createCell(8).setCellValue("");
			
			for (FieldModel field : sheetModel.fields) {
				row = metaSheet.createRow(rowIndex++);
				row.createCell(0).setCellValue(sheetModel.sheetName);
				row.createCell(1).setCellValue(colIndex++);
				row.createCell(2).setCellValue(field.id != null ? field.id : "");
				row.createCell(3).setCellValue(field.label != null ? field.label : "");
				row.createCell(4).setCellValue(field.conceptUuid != null ? field.conceptUuid : "");
				row.createCell(5).setCellValue(field.datatype != null ? field.datatype.name() : "TEXT");
				row.createCell(6).setCellValue(field.isRepeat);
				row.createCell(7).setCellValue(field.isMulti);
				
				String answersJson = (field.datatype == FieldDatatype.CODED && field.answers != null)
				        ? buildAnswersJson(field.answers)
				        : "";
				row.createCell(8).setCellValue(answersJson);
			}
		}
		
		for (int i = 0; i < columnHeaders.length; i++) {
			metaSheet.autoSizeColumn(i);
		}
	}
	
	private String buildAnswersJson(List<AnswerModel> answers) {
		if (answers == null || answers.isEmpty()) {
			return "";
		}
		StringBuilder json = new StringBuilder("{");
		for (int i = 0; i < answers.size(); i++) {
			AnswerModel answer = answers.get(i);
			String label = answer.label != null ? answer.label : answer.conceptUuid;
			String uuid = answer.conceptUuid != null ? answer.conceptUuid : "";
			if (i > 0) {
				json.append(",");
			}
			json.append(escapeJsonValue(label)).append(":").append(escapeJsonValue(uuid));
		}
		json.append("}");
		return json.toString();
	}
	
	private String escapeJsonValue(String value) {
		if (value == null) {
			return "\"\"";
		}
		value = value.replace("\"", "'");
		return "\"" + value + "\"";
	}
	
	private void createConceptsSheet(XSSFWorkbook workbook, FormModel form) {
		XSSFSheet conceptsSheet = workbook.createSheet(DataImportConstants.CONCEPTS_SHEET);
		workbook.setSheetHidden(workbook.getSheetIndex(conceptsSheet), true);
		CellStyle headerStyle = createHeaderStyle(workbook);
		Row header = conceptsSheet.createRow(0);
		String[] headers = { "level", "sheetName", "fieldId", "label", "conceptUuid", "datatype", "class", "isMulti" };
		for (int i = 0; i < headers.length; i++) {
			Cell cell = header.createCell(i);
			cell.setCellValue(headers[i]);
			cell.setCellStyle(headerStyle);
		}
		int rowIndex = 1;
		
		Map<String, Concept> conceptCache = loadConceptsForForm(form);
		
		for (SheetModel sheetModel : form.sheets) {
			for (FieldModel field : sheetModel.fields) {
				Row questionRow = conceptsSheet.createRow(rowIndex++);
				questionRow.createCell(0).setCellValue("QUESTION");
				questionRow.createCell(1).setCellValue(sheetModel.sheetName);
				questionRow.createCell(2).setCellValue(field.id != null ? field.id : "");
				questionRow.createCell(3).setCellValue(field.label != null ? field.label : "");
				questionRow.createCell(4).setCellValue(field.conceptUuid != null ? field.conceptUuid : "");
				
				String datatype = "";
				String conceptClass = "";
				if (field.conceptUuid != null && !field.conceptUuid.trim().isEmpty()) {
					Concept concept = conceptCache.get(field.conceptUuid);
					if (concept != null) {
						datatype = concept.getDatatype() != null ? concept.getDatatype().getName() : "";
						conceptClass = concept.getConceptClass() != null ? concept.getConceptClass().getName() : "";
					}
				}
				questionRow.createCell(5).setCellValue(datatype);
				questionRow.createCell(6).setCellValue(conceptClass);
				questionRow.createCell(7).setCellValue(field.isMulti);
				
				if (field.datatype == FieldDatatype.CODED && !field.answers.isEmpty()) {
					for (org.openmrs.module.dataimport.model.AnswerModel answer : field.answers) {
						Row answerRow = conceptsSheet.createRow(rowIndex++);
						answerRow.createCell(0).setCellValue("ANSWER");
						answerRow.createCell(1).setCellValue(sheetModel.sheetName);
						answerRow.createCell(2).setCellValue(field.id != null ? field.id : "");
						answerRow.createCell(3).setCellValue(answer.label != null ? answer.label : "");
						answerRow.createCell(4).setCellValue(answer.conceptUuid != null ? answer.conceptUuid : "");
						
						String answerDatatype = "";
						String answerClass = "";
						if (answer.conceptUuid != null && !answer.conceptUuid.trim().isEmpty()) {
							Concept answerConcept = conceptCache.get(answer.conceptUuid);
							if (answerConcept != null) {
								answerDatatype = answerConcept.getDatatype() != null ? answerConcept.getDatatype().getName()
								        : "";
								answerClass = answerConcept.getConceptClass() != null
								        ? answerConcept.getConceptClass().getName()
								        : "";
							}
						}
						answerRow.createCell(5).setCellValue(answerDatatype);
						answerRow.createCell(6).setCellValue(answerClass);
						answerRow.createCell(7).setCellValue(false);
					}
				}
			}
		}
		for (int i = 0; i < headers.length; i++) {
			conceptsSheet.autoSizeColumn(i);
		}
	}
	
	private String mapToJson(Map<String, String> map) {
		try {
			return objectMapper.writeValueAsString(map);
		}
		catch (JsonProcessingException e) {
			log.error("Failed to serialize answers map to JSON", e);
			return "{}";
		}
	}
	
	private void setString(Cell cell, String value, LongStringRegistry longStrings) {
		if (value == null) {
			cell.setCellValue("");
			return;
		}
		String registered = longStrings.register(value);
		cell.setCellValue(registered != null ? registered : "");
	}
	
	private Map<String, Concept> loadConceptsForForm(FormModel form) {
		Map<String, Concept> conceptCache = new HashMap<>();
		
		// Check if ConceptService is available
		ConceptService conceptService = getConceptServiceIfAvailable();
		if (conceptService == null) {
			return conceptCache;
		}
		
		// Collect all unique concept UUIDs
		Set<String> conceptUuids = new HashSet<>();
		for (SheetModel sheet : form.sheets) {
			for (FieldModel field : sheet.fields) {
				if (field.conceptUuid != null && !field.conceptUuid.trim().isEmpty()) {
					conceptUuids.add(field.conceptUuid.trim());
				}
				if (field.datatype == FieldDatatype.CODED) {
					for (AnswerModel answer : field.answers) {
						if (answer.conceptUuid != null && !answer.conceptUuid.trim().isEmpty()) {
							conceptUuids.add(answer.conceptUuid.trim());
						}
					}
				}
			}
		}
		
		// Load all concepts (with caching)
		for (String uuid : conceptUuids) {
			try {
				Concept concept = conceptService.getConceptByUuid(uuid);
				if (concept != null) {
					conceptCache.put(uuid, concept);
				}
			}
			catch (APIException e) {
				log.debug("Failed to load concept {}: {}", uuid, e.getMessage());
			}
		}
		
		return conceptCache;
	}
	
	private ConceptService getConceptServiceIfAvailable() {
		try {
			if (Context.isSessionOpen()) {
				try {
					return Context.getConceptService();
				}
				catch (APIException e) {
					log.debug("ConceptService not available (Context not initialized): {}", e.getMessage());
				}
			}
		}
		catch (Exception e) {
			log.debug("Context not available: {}", e.getMessage());
		}
		return null;
	}
	
}
