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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

public final class LongStringSupport {
	
	private static final Map<Workbook, Map<String, String>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
	
	private LongStringSupport() {
	}
	
	public static String resolve(Workbook workbook, String value) {
		if (value == null || workbook == null) {
			return value;
		}
		if (!value.startsWith(DataImportConstants.LONG_STRING_PREFIX)) {
			return value;
		}
		
		String id = value.substring(DataImportConstants.LONG_STRING_PREFIX.length());
		Map<String, String> resolved;
		synchronized (CACHE) {
			resolved = CACHE.get(workbook);
			if (resolved == null) {
				resolved = loadStrings(workbook);
				CACHE.put(workbook, resolved);
			}
		}
		return resolved.getOrDefault(id, value);
	}
	
	public static void clearCache(Workbook workbook) {
		if (workbook != null) {
			CACHE.remove(workbook);
		}
	}
	
	private static Map<String, String> loadStrings(Workbook workbook) {
		Sheet sheet = workbook.getSheet(DataImportConstants.LONG_STRING_SHEET);
		if (sheet == null) {
			return Collections.emptyMap();
		}
		
		Map<String, Map<Integer, String>> temp = new HashMap<>();
		Iterator<Row> rowIterator = sheet.iterator();
		if (rowIterator.hasNext()) {
			rowIterator.next(); // skip header
		}
		
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			if (row == null) {
				continue;
			}
			String id = rawString(row.getCell(0));
			if (id == null || id.isEmpty()) {
				continue;
			}
			int part = (int) row.getCell(1).getNumericCellValue();
			String chunk = rawString(row.getCell(2));
			Map<Integer, String> parts = temp.computeIfAbsent(id, k -> new TreeMap<>());
			parts.put(part, chunk != null ? chunk : "");
		}
		
		Map<String, String> resolved = new HashMap<>();
		for (Map.Entry<String, Map<Integer, String>> entry : temp.entrySet()) {
			StringBuilder builder = new StringBuilder();
			for (String chunk : entry.getValue().values()) {
				builder.append(chunk);
			}
			resolved.put(entry.getKey(), builder.toString());
		}
		return resolved;
	}
	
	private static String rawString(Cell cell) {
		if (cell == null) {
			return null;
		}
		switch (cell.getCellType()) {
			case STRING:
				return cell.getStringCellValue();
			case FORMULA:
				try {
					return cell.getStringCellValue();
				}
				catch (Exception e) {
					return null;
				}
			default:
				return null;
		}
	}
}
