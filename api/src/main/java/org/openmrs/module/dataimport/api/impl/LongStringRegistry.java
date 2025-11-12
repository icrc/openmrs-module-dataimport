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

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openmrs.module.dataimport.api.util.DataImportConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class LongStringRegistry {
	
	private static final int CHUNK_SIZE = 32000;
	
	private final Map<String, List<String>> entries = new LinkedHashMap<>();
	
	private int counter = 1;
	
	String register(String value) {
		if (value == null) {
			return null;
		}
		if (value.length() <= DataImportConstants.MAX_CELL_TEXT_LENGTH) {
			return value;
		}
		
		String id = String.format("STR%05d", counter++);
		List<String> chunks = splitIntoChunks(value);
		entries.put(id, chunks);
		return DataImportConstants.LONG_STRING_PREFIX + id;
	}
	
	boolean hasEntries() {
		return !entries.isEmpty();
	}
	
	void writeSheet(XSSFWorkbook workbook) {
		if (!hasEntries()) {
			return;
		}
		
		XSSFSheet sheet = workbook.createSheet(DataImportConstants.LONG_STRING_SHEET);
		Row header = sheet.createRow(0);
		header.createCell(0).setCellValue("id");
		header.createCell(1).setCellValue("part");
		header.createCell(2).setCellValue("chunk");
		
		int rowIndex = 1;
		for (Map.Entry<String, List<String>> entry : entries.entrySet()) {
			String id = entry.getKey();
			List<String> chunks = entry.getValue();
			for (int part = 0; part < chunks.size(); part++) {
				Row row = sheet.createRow(rowIndex++);
				row.createCell(0).setCellValue(id);
				row.createCell(1).setCellValue(part);
				row.createCell(2).setCellValue(chunks.get(part));
			}
		}
		
		workbook.setSheetHidden(workbook.getSheetIndex(sheet), true);
	}
	
	private List<String> splitIntoChunks(String value) {
		List<String> chunks = new ArrayList<>();
		int length = value.length();
		for (int start = 0; start < length; start += CHUNK_SIZE) {
			int end = Math.min(start + CHUNK_SIZE, length);
			chunks.add(value.substring(start, end));
		}
		return chunks;
	}
}
