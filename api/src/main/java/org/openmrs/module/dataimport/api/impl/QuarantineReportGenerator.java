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
import org.apache.poi.ss.usermodel.CellType;
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
			Map<String, Map<Integer, List<QuarantineItem>>> errorMap = buildErrorMap(importPlan.getIssues());
			Set<String> blockedPatientRefs = importPlan.getBlockedPatientRefs() != null ? importPlan.getBlockedPatientRefs()
			        : Collections.emptySet();
			
			if (!blockedPatientRefs.isEmpty()) {
				Sheet patientSheet = sourceWb.getSheet(DataImportConstants.PATIENT_SHEET_NAME);
				Map<String, Integer> patientRowIndex = indexPatientSheet(patientSheet);
				Map<Integer, List<QuarantineItem>> patientSheetErrors = errorMap
				        .computeIfAbsent(DataImportConstants.PATIENT_SHEET_NAME, k -> new HashMap<>());
				for (String patientRef : blockedPatientRefs) {
					Integer rowIndex = patientRowIndex.get(patientRef);
					if (rowIndex != null && !patientSheetErrors.containsKey(rowIndex)) {
						String message = "Patient '" + patientRef + "' has quarantined rows";
						List<QuarantineItem> rowErrors = new ArrayList<>();
						rowErrors.add(QuarantineItem.error(DataImportConstants.PATIENT_SHEET_NAME, rowIndex, 0,
						    "PATIENT_BLOCKED", message));
						patientSheetErrors.put(rowIndex, rowErrors);
					}
				}
				if (patientSheetErrors.isEmpty()) {
					errorMap.remove(DataImportConstants.PATIENT_SHEET_NAME);
				}
			}
			
			Map<Integer, List<QuarantineItem>> patientSheetErrors = errorMap.get(DataImportConstants.PATIENT_SHEET_NAME);
			if (patientSheetErrors != null && !patientSheetErrors.isEmpty()) {
				Sheet sourcePatientSheet = sourceWb.getSheet(DataImportConstants.PATIENT_SHEET_NAME);
				if (sourcePatientSheet != null) {
					Sheet reportPatientSheet = reportWb.createSheet(DataImportConstants.PATIENT_SHEET_NAME);
					copySheetWithHighlighting(sourcePatientSheet, reportPatientSheet, patientSheetErrors);
				}
			}
			
			for (Map.Entry<String, Map<Integer, List<QuarantineItem>>> entry : errorMap.entrySet()) {
				String sheetName = entry.getKey();
				Map<Integer, List<QuarantineItem>> sheetErrors = entry.getValue();
				
				if (sheetErrors == null || sheetErrors.isEmpty() || sheetName == null || sheetName.startsWith("__")) {
					continue;
				}
				
				if (DataImportConstants.PATIENT_SHEET_NAME.equals(sheetName)) {
					continue;
				}
				
				Sheet sourceSheet = sourceWb.getSheet(sheetName);
				if (sourceSheet == null) {
					continue;
				}
				
				Sheet reportSheet = reportWb.createSheet(sheetName);
				copySheetWithHighlighting(sourceSheet, reportSheet, sheetErrors);
			}
			
			copyMetaSheet(sourceWb, reportWb);
			reorderSheets(reportWb);
			
			Sheet metaSheet = reportWb.getSheet(DataImportConstants.META_SHEET);
			if (metaSheet != null) {
				int metaIndex = reportWb.getSheetIndex(metaSheet);
				if (reportWb.getActiveSheetIndex() == metaIndex && reportWb.getNumberOfSheets() > 1) {
					Sheet patientSheet = reportWb.getSheet(DataImportConstants.PATIENT_SHEET_NAME);
					if (patientSheet != null) {
						reportWb.setActiveSheet(reportWb.getSheetIndex(patientSheet));
					} else {
						for (int i = 0; i < reportWb.getNumberOfSheets(); i++) {
							if (i != metaIndex) {
								reportWb.setActiveSheet(i);
								break;
							}
						}
					}
				}
				reportWb.setSheetVisibility(metaIndex, SheetVisibility.HIDDEN);
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
	
	private Map<String, Map<Integer, List<QuarantineItem>>> buildErrorMap(java.util.List<QuarantineItem> issues) {
		Map<String, Map<Integer, List<QuarantineItem>>> errorMap = new HashMap<>();
		
		for (QuarantineItem issue : issues) {
			errorMap.computeIfAbsent(issue.getSheet(), k -> new HashMap<>())
			        .computeIfAbsent(issue.getRow(), k -> new ArrayList<>()).add(issue);
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
	}
	
	private void reorderSheets(XSSFWorkbook workbook) {
		if (workbook == null) {
			return;
		}
		
		int numSheets = workbook.getNumberOfSheets();
		if (numSheets == 0) {
			return;
		}
		
		Sheet metaSheet = workbook.getSheet(DataImportConstants.META_SHEET);
		if (metaSheet != null) {
			int currentMetaIndex = workbook.getSheetIndex(metaSheet);
			int lastPosition = numSheets - 1;
			if (currentMetaIndex != lastPosition) {
				workbook.setSheetOrder(DataImportConstants.META_SHEET, lastPosition);
			}
		}
		
		Sheet patientSheet = workbook.getSheet(DataImportConstants.PATIENT_SHEET_NAME);
		if (patientSheet != null) {
			int currentPatientIndex = workbook.getSheetIndex(patientSheet);
			if (currentPatientIndex != 0) {
				workbook.setSheetOrder(DataImportConstants.PATIENT_SHEET_NAME, 0);
			}
		}
	}
	
	private void copySheetWithHighlighting(Sheet sourceSheet, Sheet targetSheet, Map<Integer, List<QuarantineItem>> errors) {
		if (errors == null || errors.isEmpty()) {
			return;
		}
		
		Workbook workbook = targetSheet.getWorkbook();
		CellStyle errorStyle = createErrorStyle(workbook);
		CellStyle warningStyle = createWarningStyle(workbook);
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
			
			// Copy row normally first (no highlighting)
			copyRow(sourceRow, targetRow, null);
			
			// Then highlight each column that has an error/warning
			List<QuarantineItem> rowItems = errors.get(rowNum);
			if (rowItems != null && !rowItems.isEmpty()) {
				for (QuarantineItem item : rowItems) {
					int colIndex = item.getCol();
					Cell targetCell = targetRow.getCell(colIndex);
					if (targetCell != null) {
						CellStyle styleToUse = item.isWarning() ? warningStyle : errorStyle;
						targetCell.setCellStyle(styleToUse);
					}
				}
			}
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
		CellType cellType = sourceCell.getCellType();
		if (cellType == CellType.FORMULA) {
			copyFormulaValue(sourceCell, targetCell);
			return;
		}
		
		switch (cellType) {
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
			case BLANK:
				targetCell.setBlank();
				break;
			case ERROR:
				targetCell.setCellErrorValue(sourceCell.getErrorCellValue());
				break;
			case _NONE:
			default:
				break;
		}
	}
	
	private void copyFormulaValue(Cell sourceCell, Cell targetCell) {
		CellType cachedType = sourceCell.getCachedFormulaResultType();
		switch (cachedType) {
			case STRING:
				targetCell.setCellValue(sourceCell.getRichStringCellValue().getString());
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
			case ERROR:
				targetCell.setCellErrorValue(sourceCell.getErrorCellValue());
				break;
			case BLANK:
			case _NONE:
			default:
				targetCell.setBlank();
				break;
		}
	}
	
	private CellStyle createErrorStyle(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();
		style.setFillForegroundColor(IndexedColors.CORAL.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		return style;
	}
	
	private CellStyle createWarningStyle(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();
		style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
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
