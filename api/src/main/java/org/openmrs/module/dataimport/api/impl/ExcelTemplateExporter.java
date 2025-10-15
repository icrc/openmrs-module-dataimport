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
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openmrs.module.dataimport.model.AnswerModel;
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
	
	private static final int LIST_START_ROW = 1;
	
	private static final int FIRST_DATA_ROW = 1; // row after header
	
	private static final int LAST_DATA_ROW = 1000;
	
	public byte[] export(FormModel form, boolean includePatient) {
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			DataFormat dataFormat = workbook.createDataFormat();
			CellStyle dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd"));
			CellStyle dateTimeStyle = workbook.createCellStyle();
			dateTimeStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd hh:mm"));
			
			if (includePatient)
				createPatientSheet(workbook);
			
			getOrCreateListSheet(workbook);
			
			for (SheetModel sheetModel : form.sheets) {
				XSSFSheet sheet = workbook.createSheet(sheetModel.sheetName);
				Row headerRow = sheet.createRow(0);
				CellStyle headerStyle = createHeaderStyle(workbook);
				int columnIndex = 0;
				
				// Add Patient Ref column as the first column for each form sheet
				Cell patientRefHeaderCell = headerRow.createCell(columnIndex);
				patientRefHeaderCell.setCellValue(PATIENT_REF_HEADER);
				patientRefHeaderCell.setCellStyle(headerStyle);
				addHeaderComment(sheet, columnIndex, "Select an existing Patient Ref from the registration sheet");
				sheet.autoSizeColumn(columnIndex);
				columnIndex++;
				
				// Inline hint in row 2 to guide users
				Row hintRow = sheet.getRow(1);
				if (hintRow == null)
					hintRow = sheet.createRow(1);
				Cell hintCell = hintRow.getCell(0);
				if (hintCell == null)
					hintCell = hintRow.createCell(0);
				hintCell.setCellValue("Select a Patient Ref (auto-generated in '" + REG_SHEET + "')");
				
				for (FieldModel field : sheetModel.fields) {
					Cell headerCell = headerRow.createCell(columnIndex);
					headerCell.setCellValue(labelWithMarkers(field));
					headerCell.setCellStyle(headerStyle);
					
					if (field.datatype == FieldDatatype.DATE) {
						for (int rowIndex = 1; rowIndex <= 1000; rowIndex++) {
							Cell cell = getOrCreate(sheet, rowIndex, columnIndex);
							cell.setCellStyle(dateStyle);
						}
					} else if (field.datatype == FieldDatatype.DATETIME) {
						for (int rowIndex = 1; rowIndex <= 1000; rowIndex++) {
							Cell cell = getOrCreate(sheet, rowIndex, columnIndex);
							cell.setCellStyle(dateTimeStyle);
						}
					} else if (field.datatype == FieldDatatype.CODED) {
						handleCodedField(workbook, sheet, field, columnIndex);
					}
					sheet.autoSizeColumn(columnIndex);
					columnIndex++;
				}
				// Freeze header row
				sheet.createFreezePane(0, 1);
			}
			
			// Ensure dynamic named range for Patient Ref values exists and apply validation on all non-patient sheets
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
		String[] columnHeaders = { PATIENT_REF_HEADER, "Given Name", "Family Name", "Gender", "Birthdate (yyyy-mm-dd)",
		        "Address Line 1", "City/Village", "Country", "Phone" };
		Row headerRow = sheet.createRow(0);
		for (int columnIndex = 0; columnIndex < columnHeaders.length; columnIndex++) {
			Cell headerCell = headerRow.createCell(columnIndex);
			headerCell.setCellValue(columnHeaders[columnIndex]);
			headerCell.setCellStyle(headerStyle);
			if (columnIndex == 0) {
				addHeaderComment(sheet, columnIndex,
				    "IDs auto-fill when you enter patient data. Use these refs on other sheets.");
			}
			sheet.autoSizeColumn(columnIndex);
		}
		for (int r = FIRST_DATA_ROW; r <= LAST_DATA_ROW; r++) {
			Row dataRow = sheet.getRow(r);
			if (dataRow == null)
				dataRow = sheet.createRow(r);
			Cell a = dataRow.getCell(0);
			if (a == null)
				a = dataRow.createCell(0);
			String dataRange = "B" + (r + 1) + ":I" + (r + 1);
			String formula = "IF(COUNTA(" + dataRange + ")=0,\"\",\"P\"&TEXT(ROW()-1,\"000\"))";
			a.setCellFormula(formula);
		}
		Row hint = sheet.getRow(1);
		if (hint == null)
			hint = sheet.createRow(1);
		Cell hintCell = hint.getCell(0);
		if (hintCell == null)
			hintCell = hint.createCell(0);
		hintCell.setCellValue("Hint: Leave this column; IDs auto-fill. Use these refs on other sheets.");
		sheet.autoSizeColumn(0);
		// Freeze header row
		sheet.createFreezePane(0, 1);
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
	
	/**
	 * Centralized method to handle CODED field dropdown creation. Assumes concept answer enrichment has
	 * already been done by TemplateGenerationServiceImpl.
	 */
	private void handleCodedField(XSSFWorkbook workbook, XSSFSheet sheet, FieldModel field, int columnIndex) {
		log.debug("Creating dropdown for CODED field: '{}' with {} answers", field.label, field.answers.size());
		
		if (field.answers.isEmpty()) {
			log.warn("CODED field '{}' has no answers - creating text column without dropdown", field.label);
			return;
		}
		
		addDropdownForCodedField(workbook, sheet, columnIndex, 1, 1000, field.label != null ? field.label : field.id,
		    field.answers);
		
		log.debug("Dropdown created for CODED field: '{}' with {} options", field.label, field.answers.size());
	}
	
	private void addDropdownForCodedField(XSSFWorkbook workbook, XSSFSheet visibleSheet, int targetColumnIndex,
	        int firstDataRow, int lastDataRow, String headerLabel, java.util.List<AnswerModel> answers) {
		
		if (answers == null || answers.isEmpty()) {
			log.warn("addDropdownForCodedField called with empty answers for '{}'", headerLabel);
			return;
		}
		
		log.debug("Creating dropdown for '{}' with {} answers in column {}", headerLabel, answers.size(), targetColumnIndex);
		
		XSSFSheet listSheet = getOrCreateListSheet(workbook);
		
		int listColumnIndex = 0;
		while (listSheet.getRow(LIST_START_ROW) != null
		        && listSheet.getRow(LIST_START_ROW).getCell(listColumnIndex) != null) {
			listColumnIndex++;
		}
		
		int startRowIndex = LIST_START_ROW;
		int currentRowIndex = startRowIndex;
		for (AnswerModel answer : answers) {
			XSSFRow listRow = listSheet.getRow(currentRowIndex);
			if (listRow == null)
				listRow = listSheet.createRow(currentRowIndex);
			listRow.createCell(listColumnIndex).setCellValue(answer.label);
			currentRowIndex++;
		}
		int endRowIndex = currentRowIndex - 1;
		
		String baseNamedRangeName = makeSafeExcelName(headerLabel);
		String namedRangeName = baseNamedRangeName;
		int nameSuffix = 1;
		while (workbook.getName(namedRangeName) != null) {
			namedRangeName = baseNamedRangeName + "_" + (nameSuffix++);
		}
		
		String columnLetter = CellReference.convertNumToColString(listColumnIndex);
		String listReference = "'" + LIST_SHEET + "'!$" + columnLetter + "$" + (startRowIndex + 1) + ":$" + columnLetter
		        + "$" + (endRowIndex + 1);
		
		Name namedRange = workbook.createName();
		namedRange.setNameName(namedRangeName);
		namedRange.setRefersToFormula(listReference);
		
		if (workbook.getName(namedRangeName) == null) {
			throw new IllegalStateException("Named range not created: " + namedRangeName);
		}
		
		XSSFDataValidationHelper validationHelper = new XSSFDataValidationHelper(visibleSheet);
		DataValidationConstraint validationConstraint = validationHelper.createFormulaListConstraint(namedRangeName);
		CellRangeAddressList validationRegion = new CellRangeAddressList(firstDataRow, lastDataRow, targetColumnIndex,
		        targetColumnIndex);
		XSSFDataValidation dataValidation = (XSSFDataValidation) validationHelper.createValidation(validationConstraint,
		    validationRegion);
		
		dataValidation.setSuppressDropDownArrow(false);
		dataValidation.setShowErrorBox(true);
		dataValidation.setShowPromptBox(true);
		dataValidation.setEmptyCellAllowed(true);
		dataValidation.createErrorBox("Invalid choice", "Please select a value from the dropdown list.");
		dataValidation.createPromptBox("Select Option", "Choose an option from the dropdown list");
		
		visibleSheet.addValidationData(dataValidation);
		log.debug("Dropdown created: '{}' with {} answers (named range: '{}', applied to column {})", headerLabel,
		    answers.size(), namedRangeName, targetColumnIndex);
	}
	
	/** Adds a small tooltip comment on the header cell. */
	private static void addHeaderComment(XSSFSheet sheet, int columnIndex, String text) {
		Drawing<?> drawing = sheet.createDrawingPatriarch();
		ClientAnchor anchor = sheet.getWorkbook().getCreationHelper().createClientAnchor();
		anchor.setCol1(columnIndex);
		anchor.setCol2(columnIndex + 2);
		anchor.setRow1(0);
		anchor.setRow2(4);
		Comment comment = drawing.createCellComment(anchor);
		comment.setString(sheet.getWorkbook().getCreationHelper().createRichTextString(text));
		sheet.getRow(0).getCell(columnIndex).setCellComment(comment);
	}
	
	/**
	 * Create/update a named range that covers the Patient Ref values on the Patient Registration sheet.
	 */
	private void ensurePatientRefsNamedRange(XSSFWorkbook workbook) {
		// Dynamic range that grows as refs are added (skips the header in A1)
		String formula = "IF(COUNTA('" + REG_SHEET + "'!$A:$A)<=1," + "'" + REG_SHEET + "'!$A$2," + "OFFSET('" + REG_SHEET
		        + "'!$A$2,0,0,COUNTA('" + REG_SHEET + "'!$A:$A)-1,1))";
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
		validation.setSuppressDropDownArrow(false);
		validation.setShowErrorBox(true);
		validation.setEmptyCellAllowed(true);
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
