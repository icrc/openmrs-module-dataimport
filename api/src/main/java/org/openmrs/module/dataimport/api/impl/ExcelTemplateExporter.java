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
import org.apache.poi.ss.usermodel.DataValidationConstraint;
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
import java.util.Locale;

public class ExcelTemplateExporter {
	
	private static final Logger log = LoggerFactory.getLogger(ExcelTemplateExporter.class);
	
	private static final String LIST_SHEET = "__lists";
	
	private static final int LIST_START_ROW = 1;
	
	public byte[] export(FormModel form, boolean includePatient, boolean includeVisit, Locale locale) {
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			DataFormat dataFormat = workbook.createDataFormat();
			CellStyle dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd"));
			CellStyle dateTimeStyle = workbook.createCellStyle();
			dateTimeStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd hh:mm"));
			
			if (includePatient)
				createPatientSheet(workbook);
			if (includeVisit)
				createVisitSheet(workbook);
			
			getOrCreateListSheet(workbook);
			
			for (SheetModel sheetModel : form.sheets) {
				XSSFSheet sheet = workbook.createSheet(sheetModel.sheetName);
				Row headerRow = sheet.createRow(0);
				int columnIndex = 0;
				for (FieldModel field : sheetModel.fields) {
					Cell headerCell = headerRow.createCell(columnIndex);
					headerCell.setCellValue(labelWithMarkers(field));
					
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
		Sheet sheet = workbook.createSheet("Patient Registration");
		String[] columnHeaders = { "Given Name", "Family Name", "Gender", "Birthdate (yyyy-mm-dd)", "Address Line 1",
		        "City/Village", "Country", "Phone" };
		Row headerRow = sheet.createRow(0);
		for (int columnIndex = 0; columnIndex < columnHeaders.length; columnIndex++) {
			headerRow.createCell(columnIndex).setCellValue(columnHeaders[columnIndex]);
			sheet.autoSizeColumn(columnIndex);
		}
	}
	
	private static void createVisitSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet("Visit");
		String[] columnHeaders = { "Visit Type UUID", "Start Datetime (yyyy-mm-dd hh:mm)", "Location UUID" };
		Row headerRow = sheet.createRow(0);
		for (int columnIndex = 0; columnIndex < columnHeaders.length; columnIndex++) {
			headerRow.createCell(columnIndex).setCellValue(columnHeaders[columnIndex]);
			sheet.autoSizeColumn(columnIndex);
		}
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
}
