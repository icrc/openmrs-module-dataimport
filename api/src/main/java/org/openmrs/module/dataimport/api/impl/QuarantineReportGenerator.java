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
import org.openmrs.module.dataimport.model.ImportPlan;
import org.openmrs.module.dataimport.model.QuarantineItem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class QuarantineReportGenerator {
	
	public byte[] generateReport(InputStream originalWorkbook, ImportPlan importPlan) {
		if (originalWorkbook == null || importPlan == null) {
			throw new IllegalArgumentException("Original workbook and import plan are required");
		}
		
		try (XSSFWorkbook sourceWb = new XSSFWorkbook(originalWorkbook); XSSFWorkbook reportWb = new XSSFWorkbook()) {
			Map<String, Map<Integer, QuarantineItem>> errorMap = buildErrorMap(importPlan.getIssues());
			for (Sheet sourceSheet : sourceWb) {
				String sheetName = sourceSheet.getSheetName();
				
				if (sheetName.startsWith("__")) {
					continue;
				}
				
				Sheet reportSheet = reportWb.createSheet(sheetName);
				Map<Integer, QuarantineItem> sheetErrors = errorMap.get(sheetName);
				
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
	
	private void copySheetWithHighlighting(Sheet sourceSheet, Sheet targetSheet, Map<Integer, QuarantineItem> errors) {
		Workbook workbook = targetSheet.getWorkbook();
		CellStyle errorStyle = createErrorStyle(workbook);
		CellStyle headerStyle = createHeaderStyle(workbook);
		
		int maxRowNum = sourceSheet.getLastRowNum();
		for (int rowNum = 0; rowNum <= maxRowNum; rowNum++) {
			Row sourceRow = sourceSheet.getRow(rowNum);
			if (sourceRow == null) {
				continue;
			}
			
			Row targetRow = targetSheet.createRow(rowNum);
			
			boolean hasError = errors != null && errors.containsKey(rowNum);
			CellStyle rowStyle = rowNum == 0 ? headerStyle : (hasError ? errorStyle : null);
			
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
		
		for (int i = 0; i <= sourceSheet.getRow(0).getLastCellNum(); i++) {
			int width = sourceSheet.getColumnWidth(i);
			if (width > 0) {
				targetSheet.setColumnWidth(i, width);
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
