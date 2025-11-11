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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class QuarantineReportGenerator {
	
	public byte[] generateReport(InputStream originalWorkbook, ImportPlan importPlan) {
		if (originalWorkbook == null || importPlan == null) {
			throw new IllegalArgumentException("Original workbook and import plan are required");
		}
		
		try (XSSFWorkbook sourceWb = new XSSFWorkbook(originalWorkbook); XSSFWorkbook reportWb = new XSSFWorkbook()) {
			Map<String, Set<Integer>> errorRowMap = buildErrorRowMap(importPlan.getIssues());
			Set<String> patientRefsWithIssues = new HashSet<>();
			
			for (Sheet sourceSheet : sourceWb) {
				String sheetName = sourceSheet.getSheetName();
				
				if (sheetName.startsWith("__")) {
					continue;
				}
				
				if (DataImportConstants.PATIENT_SHEET_NAME.equals(sheetName)) {
					continue;
				}
				
				Set<Integer> sheetErrorRows = errorRowMap.get(sheetName);
				if (sheetErrorRows == null || sheetErrorRows.isEmpty()) {
					continue;
				}
				
				patientRefsWithIssues.addAll(resolvePatientRefsForRows(sourceSheet, sheetErrorRows));
				
				Sheet reportSheet = reportWb.createSheet(sheetName);
				copyEntireSheetWithHighlighting(sourceSheet, reportSheet, sheetErrorRows);
			}
			
			Sheet patientSource = sourceWb.getSheet(DataImportConstants.PATIENT_SHEET_NAME);
			if (patientSource != null) {
				Set<Integer> patientSheetErrors = errorRowMap.getOrDefault(DataImportConstants.PATIENT_SHEET_NAME,
				    Collections.emptySet());
				Set<Integer> patientRowsToInclude = new TreeSet<>(patientSheetErrors);
				
				if (!patientRefsWithIssues.isEmpty()) {
					Map<String, Integer> patientRowIndex = indexPatientRows(patientSource);
					for (String ref : patientRefsWithIssues) {
						Integer rowIndex = patientRowIndex.get(ref);
						if (rowIndex != null) {
							patientRowsToInclude.add(rowIndex);
						}
					}
				}
				
				if (!patientRowsToInclude.isEmpty()) {
					patientRowsToInclude.add(0);
					
					Sheet patientReportSheet = reportWb.createSheet(DataImportConstants.PATIENT_SHEET_NAME);
					copySelectedRowsWithHighlighting(patientSource, patientReportSheet, patientRowsToInclude,
					    patientSheetErrors);
				}
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
	
	private Map<String, Set<Integer>> buildErrorRowMap(List<QuarantineItem> issues) {
		Map<String, Set<Integer>> errorMap = new HashMap<>();
		
		for (QuarantineItem issue : issues) {
			errorMap.computeIfAbsent(issue.getSheet(), k -> new LinkedHashSet<>()).add(issue.getRow());
		}
		
		return errorMap;
	}
	
	private Set<String> resolvePatientRefsForRows(Sheet sourceSheet, Set<Integer> rowIndexes) {
		if (rowIndexes == null || rowIndexes.isEmpty()) {
			return Collections.emptySet();
		}
		
		Set<String> refs = new HashSet<>();
		for (Integer rowIndex : rowIndexes) {
			if (rowIndex == null || rowIndex == 0) {
				continue;
			}
			String patientRef = WorkbookUtil.Cells.getString(sourceSheet, rowIndex, 0);
			if (patientRef != null && !patientRef.trim().isEmpty()) {
				refs.add(patientRef.trim());
			}
		}
		return refs;
	}
	
	private Map<String, Integer> indexPatientRows(Sheet patientSheet) {
		Map<String, Integer> index = new HashMap<>();
		Row headerRow = patientSheet.getRow(0);
		if (headerRow == null) {
			return index;
		}
		
		int patientRefColumn = findColumnIndex(headerRow, DataImportConstants.PATIENT_REF_HEADER);
		if (patientRefColumn < 0) {
			return index;
		}
		
		for (int rowNum = 1; rowNum <= patientSheet.getLastRowNum(); rowNum++) {
			String ref = WorkbookUtil.Cells.getString(patientSheet, rowNum, patientRefColumn);
			if (ref != null && !ref.trim().isEmpty()) {
				index.put(ref.trim(), rowNum);
			}
		}
		
		return index;
	}
	
	private int findColumnIndex(Row headerRow, String headerLabel) {
		if (headerRow == null || headerLabel == null) {
			return -1;
		}
		
		int lastCellNum = headerRow.getLastCellNum();
		for (int cellNum = 0; cellNum < lastCellNum; cellNum++) {
			String value = WorkbookUtil.Cells.getString(headerRow.getCell(cellNum));
			if (headerLabel.equalsIgnoreCase(value != null ? value.trim() : null)) {
				return cellNum;
			}
		}
		return -1;
	}
	
	private void copyEntireSheetWithHighlighting(Sheet sourceSheet, Sheet targetSheet, Set<Integer> errorRows) {
		List<Integer> rowsToCopy = new ArrayList<>();
		for (int rowNum = 0; rowNum <= sourceSheet.getLastRowNum(); rowNum++) {
			rowsToCopy.add(rowNum);
		}
		copyRows(sourceSheet, targetSheet, rowsToCopy, errorRows, true);
	}
	
	private void copySelectedRowsWithHighlighting(Sheet sourceSheet, Sheet targetSheet, Set<Integer> sourceRowIndexes,
	        Set<Integer> errorRows) {
		List<Integer> rowsToCopy = new ArrayList<>(sourceRowIndexes);
		Collections.sort(rowsToCopy);
		copyRows(sourceSheet, targetSheet, rowsToCopy, errorRows, false);
	}
	
	private void copyRows(Sheet sourceSheet, Sheet targetSheet, List<Integer> sourceRows, Set<Integer> errorRows,
	        boolean preserveRowIndices) {
		Workbook workbook = targetSheet.getWorkbook();
		CellStyle errorStyle = createErrorStyle(workbook);
		CellStyle headerStyle = createHeaderStyle(workbook);
		
		int targetRowPointer = 0;
		
		for (Integer sourceRowIndex : sourceRows) {
			if (sourceRowIndex == null) {
				continue;
			}
			
			Row sourceRow = sourceSheet.getRow(sourceRowIndex);
			if (sourceRow == null) {
				continue;
			}
			
			int targetRowIndex = preserveRowIndices ? sourceRowIndex : targetRowPointer++;
			Row targetRow = targetSheet.createRow(targetRowIndex);
			
			boolean hasError = errorRows != null && errorRows.contains(sourceRowIndex);
			CellStyle rowStyle = sourceRowIndex == 0 ? headerStyle : (hasError ? errorStyle : null);
			
			int maxCellNum = sourceRow.getLastCellNum();
			for (int cellNum = 0; cellNum < maxCellNum; cellNum++) {
				Cell sourceCell = sourceRow.getCell(cellNum);
				if (sourceCell == null) {
					continue;
				}
				
				Cell targetCell = targetRow.createCell(cellNum);
				copyCellValue(sourceCell, targetCell);
				
				if (rowStyle != null) {
					targetCell.setCellStyle(rowStyle);
				}
			}
		}
		
		Row headerRow = sourceSheet.getRow(0);
		if (headerRow != null) {
			for (int i = 0; i <= headerRow.getLastCellNum(); i++) {
				int width = sourceSheet.getColumnWidth(i);
				if (width > 0) {
					targetSheet.setColumnWidth(i, width);
				}
			}
		}
		
		if (sourceSheet.getPaneInformation() != null) {
			targetSheet.createFreezePane(sourceSheet.getPaneInformation().getVerticalSplitPosition(),
			    sourceSheet.getPaneInformation().getHorizontalSplitPosition());
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
}
