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
import org.openmrs.Concept;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.SheetModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ExcelTemplateExporter {
	
	private static final Logger log = LoggerFactory.getLogger(ExcelTemplateExporter.class);
	
	private static final String LIST_SHEET = "__lists";
	
	private static final String META_SHEET = "__meta";
	
	private static final String CONCEPTS_SHEET = "__concepts";
	
	private static final String PATIENT_SHEET_NAME = "Patient Registration";
	
	private static final String REG_SHEET = PATIENT_SHEET_NAME;
	
	private static final String PATIENT_REF_HEADER = "Patient Ref";
	
	private static final String PATIENT_REFS_RANGE = "PatientRefs";
	
	private static final int FIRST_DATA_ROW = 1;
	
	private static final int LAST_DATA_ROW = 1000;
	
	// Track used names to avoid duplicates across multiple sheets
	private final Map<String, Integer> usedNames = new HashMap<>();
	
	public byte[] export(FormModel form, boolean includePatient) {
		return export(form, includePatient, null);
	}
	
	public byte[] export(FormModel form, boolean includePatient, String locationUuid) {
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			DataFormat dataFormat = workbook.createDataFormat();
			CellStyle dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd"));
			CellStyle dateTimeStyle = workbook.createCellStyle();
			dateTimeStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd hh:mm"));
			
			if (includePatient) {
				createPatientSheet(workbook);
			}
			
			getOrCreateListSheet(workbook);
			createMetaSheet(workbook, form, locationUuid);
			
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
				sheet.createFreezePane(0, 1);
			}
			
			createConceptsSheet(workbook, form);
			ensurePatientRefsNamedRange(workbook);
			for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
				XSSFSheet s = workbook.getSheetAt(i);
				if (!PATIENT_SHEET_NAME.equals(s.getSheetName())) {
					applyPatientRefDropdown(s, 0, FIRST_DATA_ROW, LAST_DATA_ROW);
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
		
		// Excel doesn't allow named ranges to have names that are valid cell references (e.g., "q1", "A1")
		// A valid cell reference is: one or more letters followed by one or more digits (nothing else)
		// Use regex to check if it matches this exact pattern
		if (isExcelCellReference(safeName)) {
			safeName = "field_" + safeName;
		}
		
		if (safeName.length() > 240)
			safeName = safeName.substring(0, 240);
		return safeName;
	}
	
	/**
	 * Checks if a string is a valid Excel cell reference (e.g., "A1", "Q1", "AB123"). Excel cell
	 * references are one or more letters followed by one or more digits.
	 */
	private boolean isExcelCellReference(String str) {
		if (str == null || str.isEmpty()) {
			return false;
		}
		// Pattern: one or more letters, followed by one or more digits, and nothing else
		return str.matches("^[A-Za-z]+\\d+$");
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
		
		// Append counter to make unique, but ensure total length doesn't exceed 240
		String uniqueName = safeName + "_" + count;
		if (uniqueName.length() > 240) {
			int availableSpace = 240 - String.valueOf(count).length() - 1; // -1 for underscore
			uniqueName = safeName.substring(0, Math.max(1, availableSpace)) + "_" + count;
		}
		return uniqueName;
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
		if (field.answers.isEmpty()) {
			return;
		}
		
		String fieldName = makeUniqueExcelName(field.label != null ? field.label : field.id);
		
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
			AnswerModel answer = field.answers.get(i);
			// Use label if available, otherwise fall back to concept UUID or value
			String displayValue = answer.label != null ? answer.label
			        : (answer.conceptUuid != null ? answer.conceptUuid : (answer.value != null ? answer.value : ""));
			cell.setCellValue(displayValue);
		}
		
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
	
	private void ensurePatientRefsNamedRange(XSSFWorkbook workbook) {
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
	
	private void createMetaSheet(XSSFWorkbook workbook, FormModel form, String locationUuid) {
		XSSFSheet metaSheet = workbook.createSheet(META_SHEET);
		workbook.setSheetHidden(workbook.getSheetIndex(metaSheet), true);
		
		CellStyle headerStyle = createHeaderStyle(workbook);
		
		// Template metadata table
		Row headerRow = metaSheet.createRow(0);
		Cell keyHeader = headerRow.createCell(0);
		keyHeader.setCellValue("key");
		keyHeader.setCellStyle(headerStyle);
		Cell valueHeader = headerRow.createCell(1);
		valueHeader.setCellValue("value");
		valueHeader.setCellStyle(headerStyle);
		
		int rowIndex = 1;
		metaSheet.createRow(rowIndex++).createCell(0).setCellValue("templateVersion");
		metaSheet.getRow(rowIndex - 1).createCell(1).setCellValue(1);
		
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
		
		rowIndex += 2; // Skip a row
		
		Row formHeaderRow = metaSheet.createRow(rowIndex++);
		String[] formHeaders = { "formUuid", "formName", "formVersion", "encounterTypeUuid", "sheetName" };
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
			String encounterTypeUuid = sheetModel.encounterTypeUuid != null ? sheetModel.encounterTypeUuid : "";
			formRow.createCell(0).setCellValue(formUuid);
			formRow.createCell(1).setCellValue(formName);
			formRow.createCell(2).setCellValue(formVersion);
			formRow.createCell(3).setCellValue(encounterTypeUuid);
			formRow.createCell(4).setCellValue(sheetModel.sheetName);
		}
		
		rowIndex += 2; // Skip a row
		Row columnHeaderRow = metaSheet.createRow(rowIndex++);
		String[] columnHeaders = { "sheetName", "colIndex", "fieldId", "label", "conceptUuid", "datatype", "isRepeat",
		        "isMulti", "answersJson" };
		for (int i = 0; i < columnHeaders.length; i++) {
			Cell cell = columnHeaderRow.createCell(i);
			cell.setCellValue(columnHeaders[i]);
			cell.setCellStyle(headerStyle);
		}
		
		if (workbook.getSheet(PATIENT_SHEET_NAME) != null) {
			String[] patientHeaders = { "Given Name", "Family Name", "Gender", "Birthdate (yyyy-mm-dd)", "Address Line 1",
			        "City/Village", "Country", "Phone", "Patient Ref" };
			for (int colIndex = 0; colIndex < patientHeaders.length; colIndex++) {
				Row row = metaSheet.createRow(rowIndex++);
				row.createCell(0).setCellValue(PATIENT_SHEET_NAME);
				row.createCell(1).setCellValue(colIndex);
				row.createCell(2).setCellValue(""); // fieldId
				row.createCell(3).setCellValue(patientHeaders[colIndex]);
				row.createCell(4).setCellValue(""); // conceptUuid
				row.createCell(5).setCellValue("TEXT"); // datatype
				row.createCell(6).setCellValue(false); // isRepeat
				row.createCell(7).setCellValue(false); // isMulti
				row.createCell(8).setCellValue("{}"); // answersJson
			}
		}
		
		for (SheetModel sheetModel : form.sheets) {
			int colIndex = 0;
			
			Row row = metaSheet.createRow(rowIndex++);
			row.createCell(0).setCellValue(sheetModel.sheetName);
			row.createCell(1).setCellValue(colIndex++);
			row.createCell(2).setCellValue(""); // fieldId
			row.createCell(3).setCellValue("Patient Ref");
			row.createCell(4).setCellValue(""); // conceptUuid
			row.createCell(5).setCellValue("TEXT"); // datatype
			row.createCell(6).setCellValue(false); // isRepeat
			row.createCell(7).setCellValue(false); // isMulti
			row.createCell(8).setCellValue("{}"); // answersJson
			
			for (FieldModel field : sheetModel.fields) {
				row = metaSheet.createRow(rowIndex++);
				row.createCell(0).setCellValue(sheetModel.sheetName);
				row.createCell(1).setCellValue(colIndex++);
				row.createCell(2).setCellValue(field.id != null ? field.id : "");
				row.createCell(3).setCellValue(field.label != null ? field.label : "");
				row.createCell(4).setCellValue(field.conceptUuid != null ? field.conceptUuid : "");
				row.createCell(5).setCellValue(field.datatype != null ? field.datatype.name() : "TEXT");
				row.createCell(6).setCellValue(false); // isRepeat - TODO: implement repeat groups
				row.createCell(7).setCellValue(field.isMulti); // isMulti
				
				// Build answers JSON for coded fields
				if (field.datatype == FieldDatatype.CODED && !field.answers.isEmpty()) {
					Map<String, String> answersMap = new HashMap<>();
					for (org.openmrs.module.dataimport.model.AnswerModel answer : field.answers) {
						if (answer.label != null && answer.conceptUuid != null) {
							answersMap.put(normalizeLabel(answer.label), answer.conceptUuid);
						}
					}
					row.createCell(8).setCellValue(mapToJson(answersMap));
				} else {
					row.createCell(8).setCellValue("{}");
				}
			}
		}
		
		for (int i = 0; i < columnHeaders.length; i++) {
			metaSheet.autoSizeColumn(i);
		}
		
		log.debug("Created metadata sheet with {} rows", rowIndex);
	}
	
	private void createConceptsSheet(XSSFWorkbook workbook, FormModel form) {
		XSSFSheet conceptsSheet = workbook.createSheet(CONCEPTS_SHEET);
		workbook.setSheetHidden(workbook.getSheetIndex(conceptsSheet), true);
		CellStyle headerStyle = createHeaderStyle(workbook);
		Row header = conceptsSheet.createRow(0);
		String[] headers = { "sheetName", "fieldId", "questionLabel", "questionConceptUuid", "questionDatatype",
		        "questionClass", "isMulti", "answerLabel", "answerConceptUuid", "answerDatatype", "answerClass" };
		for (int i = 0; i < headers.length; i++) {
			Cell cell = header.createCell(i);
			cell.setCellValue(headers[i]);
			cell.setCellStyle(headerStyle);
		}
		int rowIndex = 1;
		ConceptService conceptService = null;
		try {
			conceptService = Context.getConceptService();
		}
		catch (Exception ignore) {
			// Running outside OpenMRS context (tests), concept details will be left blank
		}
		for (SheetModel sheetModel : form.sheets) {
			for (FieldModel field : sheetModel.fields) {
				if (field.datatype == FieldDatatype.CODED && !field.answers.isEmpty()) {
					for (org.openmrs.module.dataimport.model.AnswerModel answer : field.answers) {
						Row r = conceptsSheet.createRow(rowIndex++);
						r.createCell(0).setCellValue(sheetModel.sheetName);
						r.createCell(1).setCellValue(field.id != null ? field.id : "");
						r.createCell(2).setCellValue(field.label != null ? field.label : "");
						r.createCell(3).setCellValue(field.conceptUuid != null ? field.conceptUuid : "");
						// question concept metadata via ConceptService if available
						String qDatatype = "";
						String qClass = "";
						if (conceptService != null && field.conceptUuid != null && !field.conceptUuid.trim().isEmpty()) {
							Concept qc = conceptService.getConceptByUuid(field.conceptUuid);
							if (qc != null) {
								qDatatype = qc.getDatatype() != null ? qc.getDatatype().getName() : "";
								qClass = qc.getConceptClass() != null ? qc.getConceptClass().getName() : "";
							}
						}
						r.createCell(4).setCellValue(qDatatype);
						r.createCell(5).setCellValue(qClass);
						r.createCell(6).setCellValue(field.isMulti);
						r.createCell(7).setCellValue(answer.label != null ? answer.label : "");
						r.createCell(8).setCellValue(answer.conceptUuid != null ? answer.conceptUuid : "");
						// answer concept metadata via ConceptService if available
						String aDatatype = "";
						String aClass = "";
						if (conceptService != null && answer.conceptUuid != null && !answer.conceptUuid.trim().isEmpty()) {
							Concept ac = conceptService.getConceptByUuid(answer.conceptUuid);
							if (ac != null) {
								aDatatype = ac.getDatatype() != null ? ac.getDatatype().getName() : "";
								aClass = ac.getConceptClass() != null ? ac.getConceptClass().getName() : "";
							}
						}
						r.createCell(9).setCellValue(aDatatype);
						r.createCell(10).setCellValue(aClass);
					}
				} else {
					Row r = conceptsSheet.createRow(rowIndex++);
					r.createCell(0).setCellValue(sheetModel.sheetName);
					r.createCell(1).setCellValue(field.id != null ? field.id : "");
					r.createCell(2).setCellValue(field.label != null ? field.label : "");
					r.createCell(3).setCellValue(field.conceptUuid != null ? field.conceptUuid : "");
					String qDatatype = "";
					String qClass = "";
					if (conceptService != null && field.conceptUuid != null && !field.conceptUuid.trim().isEmpty()) {
						Concept qc = conceptService.getConceptByUuid(field.conceptUuid);
						if (qc != null) {
							qDatatype = qc.getDatatype() != null ? qc.getDatatype().getName() : "";
							qClass = qc.getConceptClass() != null ? qc.getConceptClass().getName() : "";
						}
					}
					r.createCell(4).setCellValue(qDatatype);
					r.createCell(5).setCellValue(qClass);
					r.createCell(6).setCellValue(field.isMulti);
					r.createCell(7).setCellValue("");
					r.createCell(8).setCellValue("");
					r.createCell(9).setCellValue("");
					r.createCell(10).setCellValue("");
				}
			}
		}
		for (int i = 0; i < headers.length; i++) {
			conceptsSheet.autoSizeColumn(i);
		}
	}
	
	private String mapToJson(Map<String, String> map) {
		if (map.isEmpty())
			return "{}";
		
		StringBuilder json = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, String> entry : map.entrySet()) {
			if (!first)
				json.append(",");
			json.append("\"").append(entry.getKey().replace("\"", "\\\"")).append("\":\"").append(entry.getValue())
			        .append("\"");
			first = false;
		}
		json.append("}");
		return json.toString();
	}
	
	private String normalizeLabel(String label) {
		if (label == null)
			return null;
		return label.trim().toLowerCase().replaceAll("\\s+", " ");
	}
	
}
