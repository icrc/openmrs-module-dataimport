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
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.SheetModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ExcelTemplateExporter {
	
	private static final Logger log = LoggerFactory.getLogger(ExcelTemplateExporter.class);
	
	private static final String LIST_SHEET = "__lists";
	
	private static final String PATIENT_SHEET_NAME = "Patient Registration";
	
	private static final String REG_SHEET = PATIENT_SHEET_NAME;
	
	private static final String PATIENT_REF_HEADER = "Patient Ref";
	
	private static final String PATIENT_REFS_RANGE = "PatientRefs";
	
	private static final int FIRST_DATA_ROW = 1;
	
	private static final int LAST_DATA_ROW = 1000;
	
	public byte[] export(FormModel form, boolean includePatient) {
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			DataFormat dataFormat = workbook.createDataFormat();
			CellStyle dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(dataFormat.getFormat("dd/mm/yyyy"));
			CellStyle dateTimeStyle = workbook.createCellStyle();
			dateTimeStyle.setDataFormat(dataFormat.getFormat("dd/mm/yyyy hh:mm"));
			CellStyle numberStyle = workbook.createCellStyle();
			numberStyle.setDataFormat(dataFormat.getFormat("0"));
			
			if (includePatient)
				createPatientSheet(workbook);
			
			getOrCreateListSheet(workbook);
			
			for (SheetModel sheetModel : form.sheets) {
				XSSFSheet sheet = workbook.createSheet(sheetModel.sheetName);
				Row headerRow = sheet.createRow(0);
				CellStyle headerStyle = createHeaderStyle(workbook);
				int columnIndex = 0;
				
				Cell patientRefHeaderCell = headerRow.createCell(columnIndex);
				patientRefHeaderCell.setCellValue(PATIENT_REF_HEADER);
				patientRefHeaderCell.setCellStyle(headerStyle);
				sheet.setColumnWidth(columnIndex, 60 * 256);
				columnIndex++;
				
				for (FieldModel field : sheetModel.fields) {
					Cell headerCell = headerRow.createCell(columnIndex);
					headerCell.setCellValue(labelWithMarkers(field));
					headerCell.setCellStyle(headerStyle);
					
					if (field.datatype == FieldDatatype.DATE) {
						for (int rowIndex = 1; rowIndex <= 1000; rowIndex++) {
							Cell cell = getOrCreate(sheet, rowIndex, columnIndex);
							cell.setCellStyle(dateStyle);
						}
						handleDateField(workbook, sheet, field, columnIndex);
					} else if (field.datatype == FieldDatatype.DATETIME) {
						for (int rowIndex = 1; rowIndex <= 1000; rowIndex++) {
							Cell cell = getOrCreate(sheet, rowIndex, columnIndex);
							cell.setCellStyle(dateTimeStyle);
						}
					} else if (field.datatype == FieldDatatype.NUMERIC) {
						for (int rowIndex = 1; rowIndex <= 1000; rowIndex++) {
							Cell cell = getOrCreate(sheet, rowIndex, columnIndex);
							cell.setCellStyle(numberStyle);
						}
						handleNumericField(workbook, sheet, field, columnIndex);
					} else if (field.datatype == FieldDatatype.CODED) {
						handleCodedField(workbook, sheet, field, columnIndex);
					}
					sheet.autoSizeColumn(columnIndex);
					columnIndex++;
				}
				sheet.createFreezePane(0, 1);
			}
			
			ensurePatientRefsNamedRange(workbook);
			for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
				Sheet s = workbook.getSheetAt(i);
				if (!PATIENT_SHEET_NAME.equals(s.getSheetName())) {
					applyPatientRefDropdown((XSSFSheet) s, 0, FIRST_DATA_ROW, LAST_DATA_ROW);
				}
			}
			
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			workbook.write(outputStream);
			int totalSheets = workbook.getNumberOfSheets();
			int namedRanges = workbook.getNumberOfNames();
			log.info("EXCEL EXPORT COMPLETE: {} sheets, {} named ranges, {} bytes", totalSheets, namedRanges,
			    outputStream.size());
			
			return outputStream.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to export template due to I/O error", e);
		}
		catch (RuntimeException e) {
			throw new IllegalStateException("Failed to export template", e);
		}
	}
	
	private static void createPatientSheet(Workbook workbook) {
		XSSFWorkbook wb = (XSSFWorkbook) workbook;
		XSSFSheet sheet = wb.createSheet(PATIENT_SHEET_NAME);
		CellStyle headerStyle = createHeaderStyle(wb);
		String[] columnHeaders = { "Given Name", "Family Name", "Gender", "Birthdate (yyyy-mm-dd)", "Address Line 1",
		        "City/Village", "Country", "Phone", PATIENT_REF_HEADER };
		Row headerRow = sheet.createRow(0);
		for (int columnIndex = 0; columnIndex < columnHeaders.length; columnIndex++) {
			Cell headerCell = headerRow.createCell(columnIndex);
			headerCell.setCellValue(columnHeaders[columnIndex]);
			headerCell.setCellStyle(headerStyle);
			sheet.autoSizeColumn(columnIndex);
		}
		
		CellStyle readonlyStyle = wb.createCellStyle();
		readonlyStyle.setLocked(true);
		readonlyStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
		readonlyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		CellStyle unlockedStyle = wb.createCellStyle();
		unlockedStyle.setLocked(false);
		
		for (int r = FIRST_DATA_ROW; r <= LAST_DATA_ROW; r++) {
			Row dataRow = sheet.getRow(r);
			if (dataRow == null)
				dataRow = sheet.createRow(r);
			for (int c = 0; c <= 7; c++) {
				Cell editableCell = dataRow.getCell(c);
				if (editableCell == null)
					editableCell = dataRow.createCell(c);
				editableCell.setCellStyle(unlockedStyle);
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
		
		sheet.setColumnWidth(8, 60 * 256);
		sheet.createFreezePane(0, 1);
		sheet.protectSheet("dataimport");
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
		if (field.requiredAlways)
			labelText = labelText + " *";
		return labelText;
	}
	
	private XSSFSheet getOrCreateListSheet(XSSFWorkbook workbook) {
		XSSFSheet listSheet = workbook.getSheet(LIST_SHEET);
		if (listSheet == null) {
			listSheet = workbook.createSheet(LIST_SHEET);
			workbook.setSheetHidden(workbook.getSheetIndex(listSheet), true);
		}
		return listSheet;
	}
	
	private String makeSafeExcelName(String baseName) {
		if (baseName == null || baseName.trim().isEmpty())
			baseName = "List";
		String safeName = baseName.replaceAll("[^A-Za-z0-9_]", "_");
		if (!safeName.isEmpty() && !Character.isLetter(safeName.charAt(0)) && safeName.charAt(0) != '_') {
			safeName = "_" + safeName;
		}
		if (safeName.length() > 240)
			safeName = safeName.substring(0, 240);
		return safeName;
	}
	
	private void handleCodedField(XSSFWorkbook workbook, XSSFSheet sheet, FieldModel field, int columnIndex) {
		log.debug("Creating dropdown for CODED field: '{}' with {} answers", field.label, field.answers.size());
		
		if (field.answers.isEmpty()) {
			log.warn("CODED field '{}' has no answers - creating text column without dropdown", field.label);
			return;
		}
		
		createSimpleDropdown(workbook, sheet, field, columnIndex);
		
		log.debug("Dropdown created for CODED field: '{}' with {} options", field.label, field.answers.size());
	}
	
	private void createSimpleDropdown(XSSFWorkbook workbook, XSSFSheet sheet, FieldModel field, int columnIndex) {
		if (field.answers == null || field.answers.isEmpty()) {
			return;
		}
		
		String fieldName = makeSafeExcelName(field.label != null ? field.label : field.id);
		
		// Use the hidden __lists sheet to avoid external link warnings
		XSSFSheet listSheet = getOrCreateListSheet(workbook);
		
		// Find the next available column in the list sheet
		int listColumnIndex = 0;
		while (listSheet.getRow(1) != null && listSheet.getRow(1).getCell(listColumnIndex) != null) {
			listColumnIndex++;
		}
		
		// Add the options to the list sheet
		for (int i = 0; i < field.answers.size(); i++) {
			Row row = listSheet.getRow(i + 1);
			if (row == null) {
				row = listSheet.createRow(i + 1);
			}
			Cell cell = row.getCell(listColumnIndex);
			if (cell == null) {
				cell = row.createCell(listColumnIndex);
			}
			cell.setCellValue(field.answers.get(i).label);
		}
		
		// Create named range pointing to the list sheet
		Name namedRange = workbook.createName();
		namedRange.setNameName(fieldName);
		String columnLetter = CellReference.convertNumToColString(listColumnIndex);
		String reference = "'" + LIST_SHEET + "'!$" + columnLetter + "$2:$" + columnLetter + "$"
		        + (field.answers.size() + 1);
		namedRange.setRefersToFormula(reference);
		
		// Create data validation using the named range
		DataValidationHelper helper = sheet.getDataValidationHelper();
		DataValidationConstraint constraint = helper.createFormulaListConstraint(fieldName);
		CellRangeAddressList region = new CellRangeAddressList(1, 1000, columnIndex, columnIndex);
		DataValidation validation = helper.createValidation(constraint, region);
		
		sheet.addValidationData(validation);
		
		log.debug("Simple dropdown created for '{}' with {} options using proven approach", fieldName, field.answers.size());
	}
	
	private void handleNumericField(XSSFWorkbook workbook, XSSFSheet sheet, FieldModel field, int columnIndex) {
		log.debug("Creating numeric validation for field: '{}'", field.label);
		
		DataValidationHelper helper = sheet.getDataValidationHelper();
		
		// Create a constraint that only allows numeric values (including decimals)
		DataValidationConstraint constraint = helper.createNumericConstraint(DataValidationConstraint.ValidationType.DECIMAL,
		    DataValidationConstraint.OperatorType.BETWEEN, "-999999999999999999999", "999999999999999999999");
		
		CellRangeAddressList region = new CellRangeAddressList(1, 1000, columnIndex, columnIndex);
		DataValidation validation = helper.createValidation(constraint, region);
		validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
		validation.createErrorBox("Invalid Input", "Please enter a valid number");
		validation.createPromptBox("Numeric Field", "Enter a numeric value");
		validation.setShowPromptBox(true);
		
		sheet.addValidationData(validation);
		
		log.debug("Numeric validation created for field: '{}'", field.label);
	}
	
	private void handleDateField(XSSFWorkbook workbook, XSSFSheet sheet, FieldModel field, int columnIndex) {
		log.debug("Creating date validation for field: '{}'", field.label);
		
		DataValidationHelper helper = sheet.getDataValidationHelper();
		
		// Create a constraint that only allows valid dates
		// Using DATE validation type with a very wide range to allow most valid dates
		DataValidationConstraint constraint = helper.createDateConstraint(DataValidationConstraint.OperatorType.BETWEEN,
		    "01/01/1900", "31/12/2099", "dd/mm/yyyy");
		
		CellRangeAddressList region = new CellRangeAddressList(1, 1000, columnIndex, columnIndex);
		DataValidation validation = helper.createValidation(constraint, region);
		validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
		validation.createErrorBox("Invalid Date", "Please enter a valid date in dd/mm/yyyy format");
		validation.createPromptBox("Date Field", "Enter a date in dd/mm/yyyy format");
		validation.setShowPromptBox(true);
		
		sheet.addValidationData(validation);
		
		log.debug("Date validation created for field: '{}'", field.label);
	}
	
	private void ensurePatientRefsNamedRange(XSSFWorkbook workbook) {
		// Dynamic range that grows as refs are added (skips the header in I1, Patient Ref is now in column I)
		String formula = "IF(COUNTA('" + REG_SHEET + "'!$I:$I)<=1," + "'" + REG_SHEET + "'!$I$2," + "OFFSET('" + REG_SHEET
		        + "'!$I$2,0,0,COUNTA('" + REG_SHEET + "'!$I:$I)-1,1))";
		Name name = workbook.getName(PATIENT_REFS_RANGE);
		if (name == null) {
			name = workbook.createName();
			name.setNameName(PATIENT_REFS_RANGE);
		}
		name.setRefersToFormula(formula);
		log.debug("Patient Refs named range '{}' -> {}", PATIENT_REFS_RANGE, formula);
	}
	
	/**
	 * Apply validation to force selecting Patient Ref from the named range on a given sheet/column.
	 */
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
}
