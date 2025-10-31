/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for working with Excel workbooks during import.
 */
public class WorkbookUtil {
	
	/**
	 * Gets all sheets except the Patient Registration sheet and hidden sheets.
	 */
	public static List<Sheet> nonPatientSheets(Workbook workbook, String patientSheetName) {
		List<Sheet> sheets = new ArrayList<>();
		for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
			Sheet sheet = workbook.getSheetAt(i);
			if (!patientSheetName.equals(sheet.getSheetName()) && !workbook.isSheetHidden(i)) {
				sheets.add(sheet);
			}
		}
		return sheets;
	}
	
	/**
	 * Finds the last row with data in a sheet.
	 */
	public static int lastDataRow(Sheet sheet) {
		int lastRow = sheet.getLastRowNum();
		// Check if the last row is actually empty
		while (lastRow >= 0) {
			Row row = sheet.getRow(lastRow);
			if (row != null && !isEmptyRow(sheet, lastRow)) {
				break;
			}
			lastRow--;
		}
		return lastRow;
	}
	
	/**
	 * Checks if a row is empty (all cells are blank).
	 */
	public static boolean isEmptyRow(Sheet sheet, int rowIndex) {
		Row row = sheet.getRow(rowIndex);
		if (row == null)
			return true;
		
		for (int i = 0; i < row.getLastCellNum(); i++) {
			Cell cell = row.getCell(i);
			if (!Cells.isBlank(cell)) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Utility methods for reading cell values.
	 */
	public static class Cells {
		
		public static boolean isBlank(Cell cell) {
			if (cell == null)
				return true;
			
			switch (cell.getCellType()) {
				case BLANK:
					return true;
				case STRING:
					return cell.getStringCellValue().trim().isEmpty();
				case NUMERIC:
					return false; // Numeric cells are never blank
				case BOOLEAN:
					return false; // Boolean cells are never blank
				case FORMULA:
					try {
						return cell.getStringCellValue().trim().isEmpty();
					}
					catch (Exception e) {
						return false;
					}
				default:
					return true;
			}
		}
		
		public static String getString(Cell cell) {
			if (cell == null)
				return null;
			
			switch (cell.getCellType()) {
				case STRING:
					return cell.getStringCellValue().trim();
				case NUMERIC:
					// Convert numeric to string
					double numValue = cell.getNumericCellValue();
					if (numValue == (long) numValue) {
						return String.valueOf((long) numValue);
					} else {
						return String.valueOf(numValue);
					}
				case BOOLEAN:
					return String.valueOf(cell.getBooleanCellValue());
				case FORMULA:
					try {
						return cell.getStringCellValue().trim();
					}
					catch (Exception e) {
						try {
							return String.valueOf(cell.getNumericCellValue());
						}
						catch (Exception e2) {
							return String.valueOf(cell.getBooleanCellValue());
						}
					}
				default:
					return null;
			}
		}
		
		public static String getString(Sheet sheet, int rowIndex, int colIndex) {
			Row row = sheet.getRow(rowIndex);
			if (row == null)
				return null;
			return getString(row.getCell(colIndex));
		}
		
		public static Double getDouble(Cell cell) {
			if (cell == null)
				return null;
			
			switch (cell.getCellType()) {
				case NUMERIC:
					return cell.getNumericCellValue();
				case STRING:
					try {
						return Double.parseDouble(cell.getStringCellValue().trim());
					}
					catch (NumberFormatException e) {
						return null;
					}
				case FORMULA:
					try {
						return cell.getNumericCellValue();
					}
					catch (Exception e) {
						return null;
					}
				default:
					return null;
			}
		}
		
		public static LocalDate getLocalDate(Cell cell) {
			if (cell == null)
				return null;
			
			switch (cell.getCellType()) {
				case NUMERIC:
					// Excel date serial number
					return LocalDate.of(1900, 1, 1).plusDays((long) cell.getNumericCellValue() - 2);
				case STRING:
					try {
						return LocalDate.parse(cell.getStringCellValue().trim());
					}
					catch (Exception e) {
						return null;
					}
				case FORMULA:
					try {
						return LocalDate.of(1900, 1, 1).plusDays((long) cell.getNumericCellValue() - 2);
					}
					catch (Exception e) {
						return null;
					}
				default:
					return null;
			}
		}
		
		public static Instant getInstant(Cell cell) {
			if (cell == null)
				return null;
			
			switch (cell.getCellType()) {
				case NUMERIC:
					// Excel datetime serial number
					return Instant.ofEpochMilli((long) ((cell.getNumericCellValue() - 25569) * 86400000));
				case STRING:
					try {
						return Instant.parse(cell.getStringCellValue().trim());
					}
					catch (Exception e) {
						return null;
					}
				case FORMULA:
					try {
						return Instant.ofEpochMilli((long) ((cell.getNumericCellValue() - 25569) * 86400000));
					}
					catch (Exception e) {
						return null;
					}
				default:
					return null;
			}
		}
		
		public static Boolean getBoolean(Cell cell) {
			if (cell == null)
				return null;
			
			switch (cell.getCellType()) {
				case BOOLEAN:
					return cell.getBooleanCellValue();
				case STRING:
					String str = cell.getStringCellValue().trim().toLowerCase();
					if ("true".equals(str) || "yes".equals(str) || "1".equals(str)) {
						return true;
					} else if ("false".equals(str) || "no".equals(str) || "0".equals(str)) {
						return false;
					}
					return null;
				case NUMERIC:
					return cell.getNumericCellValue() != 0;
				case FORMULA:
					try {
						return cell.getBooleanCellValue();
					}
					catch (Exception e) {
						return null;
					}
				default:
					return null;
			}
		}
	}
}
