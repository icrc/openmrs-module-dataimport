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
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for Excel workbook validation in tests. Provides helper methods for asserting Excel
 * structure, metadata, and data validation.
 */
public class ExcelTestHelper {
	
	private static final DataFormatter formatter = new DataFormatter();
	
	/**
	 * Checks if a workbook contains a sheet with the given name.
	 *
	 * @param workbook the workbook to check
	 * @param sheetName the sheet name to look for
	 * @return true if the sheet exists, false otherwise
	 */
	public static boolean hasSheet(Workbook workbook, String sheetName) {
		return workbook.getSheet(sheetName) != null;
	}
	
	/**
	 * Checks if a sheet contains a column with the given header name.
	 *
	 * @param sheet the sheet to check
	 * @param columnName the column header name to look for
	 * @return true if the column exists, false otherwise
	 */
	public static boolean hasColumn(Sheet sheet, String columnName) {
		return getColumnIndex(sheet, columnName) != -1;
	}
	
	/**
	 * Gets the index of a column by its header name.
	 *
	 * @param sheet the sheet to search
	 * @param columnName the column header name
	 * @return the 0-based column index, or -1 if not found
	 */
	public static int getColumnIndex(Sheet sheet, String columnName) {
		Row headerRow = sheet.getRow(0);
		if (headerRow == null) {
			return -1;
		}
		
		for (int i = 0; i < headerRow.getLastCellNum(); i++) {
			Cell cell = headerRow.getCell(i);
			if (cell != null && columnName.equals(cell.getStringCellValue())) {
				return i;
			}
		}
		return -1;
	}
	
	/**
	 * Gets all column names from the header row.
	 *
	 * @param sheet the sheet to read
	 * @return list of column header names
	 */
	public static List<String> getColumnNames(Sheet sheet) {
		List<String> columns = new ArrayList<>();
		Row headerRow = sheet.getRow(0);
		if (headerRow == null) {
			return columns;
		}
		
		for (int i = 0; i < headerRow.getLastCellNum(); i++) {
			Cell cell = headerRow.getCell(i);
			if (cell != null) {
				columns.add(cell.getStringCellValue());
			}
		}
		return columns;
	}
	
	/**
	 * Checks if a column has dropdown validation.
	 *
	 * @param sheet the sheet to check
	 * @param columnIndex the 0-based column index
	 * @return true if the column has dropdown validation, false otherwise
	 */
	public static boolean hasDropdownValidation(Sheet sheet, int columnIndex) {
		List<? extends DataValidation> validations = sheet.getDataValidations();
		if (validations == null) {
			return false;
		}
		
		for (DataValidation validation : validations) {
			if (validation.getRegions() != null) {
				for (int i = 0; i < validation.getRegions().countRanges(); i++) {
					if (validation.getRegions().getCellRangeAddress(i).getFirstColumn() == columnIndex) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * Gets the dropdown options for a column by checking named ranges and list formulas.
	 *
	 * @param workbook the workbook
	 * @param sheet the sheet
	 * @param columnIndex the 0-based column index
	 * @return list of dropdown option values, or empty list if no dropdown
	 */
	public static List<String> getDropdownOptions(Workbook workbook, Sheet sheet, int columnIndex) {
		List<String> options = new ArrayList<>();
		
		// Try to find dropdown validation for this column
		List<? extends DataValidation> validations = sheet.getDataValidations();
		if (validations == null) {
			return options;
		}
		
		for (DataValidation validation : validations) {
			if (validation.getRegions() != null) {
				for (int i = 0; i < validation.getRegions().countRanges(); i++) {
					if (validation.getRegions().getCellRangeAddress(i).getFirstColumn() == columnIndex) {
						DataValidationConstraint constraint = validation.getValidationConstraint();
						if (constraint != null) {
							String formula = constraint.getFormula1();
							if (formula != null) {
								// Parse named range or explicit list
								options.addAll(parseDropdownFormula(workbook, formula));
							}
						}
					}
				}
			}
		}
		
		return options;
	}
	
	/**
	 * Parses a dropdown formula to extract option values.
	 *
	 * @param workbook the workbook
	 * @param formula the formula string
	 * @return list of option values
	 */
	private static List<String> parseDropdownFormula(Workbook workbook, String formula) {
		List<String> options = new ArrayList<>();
		
		// Check if it's a named range
		Name namedRange = workbook.getName(formula);
		if (namedRange != null) {
			String refersTo = namedRange.getRefersToFormula();
			// Parse the range reference and read cell values
			// Format: '__lists'!$A$1:$A$5
			if (refersTo != null && refersTo.contains("!")) {
				String[] parts = refersTo.split("!");
				if (parts.length == 2) {
					String sheetName = parts[0].replace("'", "");
					Sheet listSheet = workbook.getSheet(sheetName);
					if (listSheet != null) {
						String range = parts[1];
						// Parse range (simplified - assumes single column)
						options.addAll(readRangeValues(listSheet, range));
					}
				}
			}
		} else if (formula.contains("\"")) {
			// Explicit list: "Option1,Option2,Option3"
			String listStr = formula.replace("\"", "");
			for (String opt : listStr.split(",")) {
				options.add(opt.trim());
			}
		}
		
		return options;
	}
	
	/**
	 * Reads cell values from a range reference.
	 *
	 * @param sheet the sheet
	 * @param range the range reference (e.g., "$A$1:$A$5")
	 * @return list of cell values
	 */
	private static List<String> readRangeValues(Sheet sheet, String range) {
		List<String> values = new ArrayList<>();
		// Simplified range parsing
		range = range.replace("$", "");
		String[] parts = range.split(":");
		if (parts.length == 2) {
			CellReference start = new CellReference(parts[0]);
			CellReference end = new CellReference(parts[1]);
			
			for (int rowIdx = start.getRow(); rowIdx <= end.getRow(); rowIdx++) {
				Row row = sheet.getRow(rowIdx);
				if (row != null) {
					Cell cell = row.getCell(start.getCol());
					if (cell != null) {
						values.add(formatter.formatCellValue(cell));
					}
				}
			}
		}
		return values;
	}
	
	/**
	 * Checks if a cell has a specific number format.
	 *
	 * @param sheet the sheet
	 * @param rowIndex the 0-based row index
	 * @param columnIndex the 0-based column index
	 * @param expectedFormat the expected format string (e.g., "0.0", "yyyy-mm-dd")
	 * @return true if the cell format matches, false otherwise
	 */
	public static boolean hasCellFormat(Sheet sheet, int rowIndex, int columnIndex, String expectedFormat) {
		Row row = sheet.getRow(rowIndex);
		if (row == null) {
			return false;
		}
		
		Cell cell = row.getCell(columnIndex);
		if (cell == null) {
			return false;
		}
		
		CellStyle style = cell.getCellStyle();
		if (style == null) {
			return false;
		}
		
		String format = style.getDataFormatString();
		return format != null && format.contains(expectedFormat);
	}
	
	/**
	 * Reads metadata from the __meta sheet.
	 *
	 * @param workbook the workbook
	 * @return map of metadata key-value pairs
	 */
	public static Map<String, Object> readMetadata(Workbook workbook) {
		Map<String, Object> metadata = new HashMap<>();
		Sheet metaSheet = workbook.getSheet("__meta");
		if (metaSheet == null) {
			return metadata;
		}
		
		for (int i = 0; i <= metaSheet.getLastRowNum(); i++) {
			Row row = metaSheet.getRow(i);
			if (row != null && row.getCell(0) != null && row.getCell(1) != null) {
				String key = formatter.formatCellValue(row.getCell(0));
				String value = formatter.formatCellValue(row.getCell(1));
				metadata.put(key, value);
			}
		}
		
		return metadata;
	}
	
	/**
	 * Reads column metadata from the __concepts or __meta sheet.
	 *
	 * @param workbook the workbook
	 * @param sheetName the data sheet name
	 * @return list of column metadata maps
	 */
	public static List<Map<String, String>> readColumnMetadata(Workbook workbook, String sheetName) {
		List<Map<String, String>> columns = new ArrayList<>();
		Sheet conceptsSheet = workbook.getSheet("__concepts");
		if (conceptsSheet == null) {
			return columns;
		}
		
		// Read header row to get column names
		Row headerRow = conceptsSheet.getRow(0);
		if (headerRow == null) {
			return columns;
		}
		
		List<String> headers = new ArrayList<>();
		for (int i = 0; i < headerRow.getLastCellNum(); i++) {
			Cell cell = headerRow.getCell(i);
			headers.add(cell != null ? cell.getStringCellValue() : "");
		}
		
		// Read data rows
		for (int i = 1; i <= conceptsSheet.getLastRowNum(); i++) {
			Row row = conceptsSheet.getRow(i);
			if (row != null) {
				Map<String, String> columnMeta = new HashMap<>();
				for (int j = 0; j < headers.size(); j++) {
					Cell cell = row.getCell(j);
					columnMeta.put(headers.get(j), cell != null ? formatter.formatCellValue(cell) : "");
				}
				// Filter by sheet name if column has a sheet reference
				if (columnMeta.get("sheet") == null || columnMeta.get("sheet").equals(sheetName)) {
					columns.add(columnMeta);
				}
			}
		}
		
		return columns;
	}
	
	/**
	 * Checks if concept metadata exists for a given concept UUID.
	 *
	 * @param workbook the workbook
	 * @param conceptUuid the concept UUID to look for
	 * @return true if metadata exists, false otherwise
	 */
	public static boolean hasConceptMetadata(Workbook workbook, String conceptUuid) {
		Sheet conceptsSheet = workbook.getSheet("__concepts");
		if (conceptsSheet == null) {
			return false;
		}
		
		// Find the conceptUuid column
		Row headerRow = conceptsSheet.getRow(0);
		if (headerRow == null) {
			return false;
		}
		
		int uuidColumn = -1;
		for (int i = 0; i < headerRow.getLastCellNum(); i++) {
			Cell cell = headerRow.getCell(i);
			if (cell != null && "conceptUuid".equals(cell.getStringCellValue())) {
				uuidColumn = i;
				break;
			}
		}
		
		if (uuidColumn == -1) {
			return false;
		}
		
		// Search for the UUID
		for (int i = 1; i <= conceptsSheet.getLastRowNum(); i++) {
			Row row = conceptsSheet.getRow(i);
			if (row != null) {
				Cell cell = row.getCell(uuidColumn);
				if (cell != null && conceptUuid.equals(formatter.formatCellValue(cell))) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Gets the value of a cell.
	 *
	 * @param sheet the sheet
	 * @param rowIndex the 0-based row index
	 * @param columnIndex the 0-based column index
	 * @return the cell value as an Object, or null if empty
	 */
	public static Object getCellValue(Sheet sheet, int rowIndex, int columnIndex) {
		Row row = sheet.getRow(rowIndex);
		if (row == null) {
			return null;
		}
		
		Cell cell = row.getCell(columnIndex);
		if (cell == null) {
			return null;
		}
		
		switch (cell.getCellType()) {
			case STRING:
				return cell.getStringCellValue();
			case NUMERIC:
				return cell.getNumericCellValue();
			case BOOLEAN:
				return cell.getBooleanCellValue();
			case FORMULA:
				return cell.getCellFormula();
			default:
				return null;
		}
	}
	
	/**
	 * Checks if a cell is empty.
	 *
	 * @param sheet the sheet
	 * @param rowIndex the 0-based row index
	 * @param columnIndex the 0-based column index
	 * @return true if the cell is empty or null, false otherwise
	 */
	public static boolean cellIsEmpty(Sheet sheet, int rowIndex, int columnIndex) {
		Row row = sheet.getRow(rowIndex);
		if (row == null) {
			return true;
		}
		
		Cell cell = row.getCell(columnIndex);
		return cell == null || cell.getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK;
	}
	
	/**
	 * Sets a cell value (for filling templates in tests).
	 *
	 * @param sheet the sheet
	 * @param rowIndex the 0-based row index
	 * @param columnIndex the 0-based column index
	 * @param value the value to set
	 */
	public static void setCellValue(Sheet sheet, int rowIndex, int columnIndex, Object value) {
		Row row = sheet.getRow(rowIndex);
		if (row == null) {
			row = sheet.createRow(rowIndex);
		}
		
		Cell cell = row.getCell(columnIndex);
		if (cell == null) {
			cell = row.createCell(columnIndex);
		}
		
		if (value == null) {
			cell.setBlank();
		} else if (value instanceof String) {
			cell.setCellValue((String) value);
		} else if (value instanceof Number) {
			cell.setCellValue(((Number) value).doubleValue());
		} else if (value instanceof Boolean) {
			cell.setCellValue((Boolean) value);
		} else {
			cell.setCellValue(value.toString());
		}
	}
	
	/**
	 * Writes a row of data to a sheet.
	 *
	 * @param sheet the sheet
	 * @param rowIndex the 0-based row index
	 * @param values the values to write
	 */
	public static void writeRow(Sheet sheet, int rowIndex, Object... values) {
		Row row = sheet.getRow(rowIndex);
		if (row == null) {
			row = sheet.createRow(rowIndex);
		}
		
		for (int i = 0; i < values.length; i++) {
			setCellValue(sheet, rowIndex, i, values[i]);
		}
	}
}
