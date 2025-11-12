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
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openmrs.module.dataimport.api.util.DataImportConstants;
import org.openmrs.module.dataimport.api.util.WorkbookUtil;
import org.openmrs.module.dataimport.model.ImportPlan;
import org.openmrs.module.dataimport.model.QuarantineItem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuarantineReportGenerator {
	
	public byte[] generateReport(InputStream originalWorkbook, ImportPlan importPlan) {
		if (originalWorkbook == null || importPlan == null) {
			throw new IllegalArgumentException("Original workbook and import plan are required");
		}
		
		try (XSSFWorkbook sourceWb = new XSSFWorkbook(originalWorkbook); XSSFWorkbook reportWb = new XSSFWorkbook()) {
			Map<String, Map<Integer, QuarantineItem>> errorMap = buildErrorMap(importPlan.getIssues());
			Set<String> blockedPatientRefs = importPlan.getBlockedPatientRefs() != null ? importPlan.getBlockedPatientRefs()
			        : Collections.emptySet();
			
			copyMetaSheet(sourceWb, reportWb);
			
			if (!blockedPatientRefs.isEmpty()) {
				Sheet patientSheet = sourceWb.getSheet(DataImportConstants.PATIENT_SHEET_NAME);
				Map<String, Integer> patientRowIndex = indexPatientSheet(patientSheet);
				Map<Integer, QuarantineItem> patientSheetErrors = errorMap
				        .computeIfAbsent(DataImportConstants.PATIENT_SHEET_NAME, k -> new HashMap<>());
				for (String patientRef : blockedPatientRefs) {
					Integer rowIndex = patientRowIndex.get(patientRef);
					if (rowIndex != null && !patientSheetErrors.containsKey(rowIndex)) {
						String message = "Patient '" + patientRef + "' has quarantined rows";
						patientSheetErrors.put(rowIndex, QuarantineItem.error(DataImportConstants.PATIENT_SHEET_NAME,
						    rowIndex, 0, "PATIENT_BLOCKED", message));
					}
				}
				if (patientSheetErrors.isEmpty()) {
					errorMap.remove(DataImportConstants.PATIENT_SHEET_NAME);
				}
			}
			
			for (Map.Entry<String, Map<Integer, QuarantineItem>> entry : errorMap.entrySet()) {
				String sheetName = entry.getKey();
				Map<Integer, QuarantineItem> sheetErrors = entry.getValue();
				
				if (sheetErrors == null || sheetErrors.isEmpty() || sheetName == null || sheetName.startsWith("__")) {
					continue;
				}
				
				Sheet sourceSheet = sourceWb.getSheet(sheetName);
				if (sourceSheet == null) {
					continue;
				}
				
				Sheet reportSheet = reportWb.createSheet(sheetName);
				copySheetWithHighlighting(sourceSheet, reportSheet, sheetErrors);
			}
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			reportWb.write(baos);
			reportWb.close();
			return baos.toByteArray();
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to generate quarantine report: " + e.getMessage(), e);
		}
	}
	
	private Map<String, Map<Integer, QuarantineItem>> buildErrorMap(java.util.List<QuarantineItem> issues) {
		Map<String, Map<Integer, QuarantineItem>> errorMap = new HashMap<>();
		
		for (QuarantineItem issue : issues) {
			errorMap.computeIfAbsent(issue.getSheet(), k -> new HashMap<>()).put(issue.getRow(), issue);
		}
		
		return errorMap;
	}
	
	private void copyMetaSheet(XSSFWorkbook sourceWb, XSSFWorkbook reportWb) {
		if (sourceWb == null || reportWb == null) {
			return;
		}
		Sheet metaSheet = sourceWb.getSheet(DataImportConstants.META_SHEET);
		if (metaSheet == null) {
			return;
		}
		
		Sheet metaCopy = reportWb.createSheet(DataImportConstants.META_SHEET);
		copyEntireSheet(metaSheet, metaCopy);
		int sourceIndex = sourceWb.getSheetIndex(metaSheet);
		int targetIndex = reportWb.getSheetIndex(metaCopy);
		SheetVisibility visibility = sourceWb.getSheetVisibility(sourceIndex);
		reportWb.setSheetVisibility(targetIndex, visibility);
	}
	
	private void copySheetWithHighlighting(Sheet sourceSheet, Sheet targetSheet, Map<Integer, QuarantineItem> errors) {
		if (errors == null || errors.isEmpty()) {
			return;
		}
		
		Workbook workbook = targetSheet.getWorkbook();
		CellStyle errorStyle = createErrorStyle(workbook);
		CellStyle headerStyle = createHeaderStyle(workbook);
		
		int targetRowIndex = 0;
		Row headerRow = sourceSheet.getRow(0);
		if (headerRow != null) {
			Row targetHeader = targetSheet.createRow(targetRowIndex++);
			copyRow(headerRow, targetHeader, headerStyle);
		}
		
		List<Integer> sortedRows = new ArrayList<>(errors.keySet());
		Collections.sort(sortedRows);
		
		for (Integer rowNum : sortedRows) {
			Row sourceRow = sourceSheet.getRow(rowNum);
			if (sourceRow == null) {
				continue;
			}
			Row targetRow = targetSheet.createRow(targetRowIndex++);
			copyRow(sourceRow, targetRow, errorStyle);
		}
		
		copyColumnWidths(sourceSheet, targetSheet);
	}
	
	private void copyEntireSheet(Sheet sourceSheet, Sheet targetSheet) {
		if (sourceSheet == null || targetSheet == null) {
			return;
		}
		int lastRow = sourceSheet.getLastRowNum();
		for (int rowNum = 0; rowNum <= lastRow; rowNum++) {
			Row sourceRow = sourceSheet.getRow(rowNum);
			if (sourceRow == null) {
				continue;
			}
			Row targetRow = targetSheet.createRow(rowNum);
			copyRow(sourceRow, targetRow, null);
		}
		copyColumnWidths(sourceSheet, targetSheet);
	}
	
	private void copyRow(Row sourceRow, Row targetRow, CellStyle style) {
		if (sourceRow == null || targetRow == null) {
			return;
		}
		int maxCellNum = sourceRow.getLastCellNum();
		for (int cellNum = 0; cellNum < maxCellNum; cellNum++) {
			Cell sourceCell = sourceRow.getCell(cellNum);
			if (sourceCell == null) {
				continue;
			}
			Cell targetCell = targetRow.createCell(cellNum);
			copyCellValue(sourceCell, targetCell);
			if (style != null) {
				targetCell.setCellStyle(style);
			}
		}
	}
	
	private void copyColumnWidths(Sheet sourceSheet, Sheet targetSheet) {
		Row headerRow = sourceSheet.getRow(0);
		if (headerRow == null) {
			return;
		}
		int lastCell = headerRow.getLastCellNum();
		for (int i = 0; i < lastCell; i++) {
			int width = sourceSheet.getColumnWidth(i);
			if (width > 0) {
				targetSheet.setColumnWidth(i, width);
			}
		}
	}
	
	private void copyCellValue(Cell sourceCell, Cell targetCell) {
		switch (sourceCell.getCellType()) {
			case STRING:
				targetCell.setCellValue(sourceCell.getStringCellValue());
				break;
			case NUMERIC:
				if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(sourceCell)) {
					targetCell.setCellValue(sourceCell.getDateCellValue());
				} else {
					targetCell.setCellValue(sourceCell.getNumericCellValue());
				}
				break;
			case BOOLEAN:
				targetCell.setCellValue(sourceCell.getBooleanCellValue());
				break;
			case FORMULA:
				targetCell.setCellFormula(sourceCell.getCellFormula());
				break;
			case BLANK:
				targetCell.setBlank();
				break;
			case ERROR:
				targetCell.setCellErrorValue(sourceCell.getErrorCellValue());
				break;
			default:
				break;
		}
	}
	
	private CellStyle createErrorStyle(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();
		style.setFillForegroundColor(IndexedColors.CORAL.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		return style;
	}
	
	private CellStyle createHeaderStyle(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();
		Font font = workbook.createFont();
		font.setBold(true);
		style.setFont(font);
		style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		return style;
	}
	
	private Map<String, Integer> indexPatientSheet(Sheet patientSheet) {
		Map<String, Integer> index = new HashMap<>();
		if (patientSheet == null) {
			return index;
		}
		
		int refColumn = 8;
		Row header = patientSheet.getRow(0);
		if (header != null) {
			int lastCell = header.getLastCellNum();
			for (int i = 0; i < lastCell; i++) {
				Cell cell = header.getCell(i);
				String headerValue = WorkbookUtil.Cells.getString(cell);
				if (headerValue != null && headerValue.trim().equalsIgnoreCase(DataImportConstants.PATIENT_REF_HEADER)) {
					refColumn = i;
					break;
				}
			}
		}
		
		int lastRow = patientSheet.getLastRowNum();
		for (int rowNum = 1; rowNum <= lastRow; rowNum++) {
			Row row = patientSheet.getRow(rowNum);
			if (row == null) {
				continue;
			}
			Cell refCell = row.getCell(refColumn);
			String patientRef = WorkbookUtil.Cells.getString(refCell);
			if (patientRef != null && !patientRef.trim().isEmpty()) {
				index.put(patientRef, rowNum);
			}
		}
		
		return index;
	}
}
