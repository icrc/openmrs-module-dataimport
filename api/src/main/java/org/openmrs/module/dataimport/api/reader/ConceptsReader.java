/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.reader;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.openmrs.module.dataimport.api.util.WorkbookUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional reader for the hidden __concepts sheet. Used for enhanced validation/debugging.
 */
public class ConceptsReader {
	
	private static final Logger log = LoggerFactory.getLogger(ConceptsReader.class);
	
	private static final String CONCEPTS_SHEET = "__concepts";
	
	public static List<RowData> readIfPresent(Workbook workbook) {
		Sheet sheet = workbook.getSheet(CONCEPTS_SHEET);
		if (sheet == null) {
			return new ArrayList<>();
		}
		
		List<RowData> rows = new ArrayList<>();
		for (int i = 1; i <= sheet.getLastRowNum(); i++) {
			Row row = sheet.getRow(i);
			if (row == null)
				continue;
			
			RowData rd = parse(row);
			if (rd != null) {
				rows.add(rd);
			}
		}
		log.debug("Read {} rows from __concepts", rows.size());
		return rows;
	}
	
	private static RowData parse(Row row) {
		// columns: sheetName, fieldId, questionLabel, questionConceptUuid, questionDatatype, questionClass,
		// isMulti, answerLabel, answerConceptUuid, answerDatatype, answerClass
		RowData rd = new RowData();
		rd.sheetName = get(row, 0);
		rd.fieldId = get(row, 1);
		rd.questionLabel = get(row, 2);
		rd.questionConceptUuid = get(row, 3);
		rd.questionDatatype = get(row, 4);
		rd.questionClass = get(row, 5);
		rd.isMulti = "true".equalsIgnoreCase(get(row, 6));
		rd.answerLabel = get(row, 7);
		rd.answerConceptUuid = get(row, 8);
		rd.answerDatatype = get(row, 9);
		rd.answerClass = get(row, 10);
		
		if (rd.sheetName == null) {
			return null;
		}
		return rd;
	}
	
	private static String get(Row row, int idx) {
		Cell c = row.getCell(idx);
		return WorkbookUtil.Cells.getString(c);
	}
	
	public static class RowData {
		
		public String sheetName;
		
		public String fieldId;
		
		public String questionLabel;
		
		public String questionConceptUuid;
		
		public String questionDatatype;
		
		public String questionClass;
		
		public boolean isMulti;
		
		public String answerLabel;
		
		public String answerConceptUuid;
		
		public String answerDatatype;
		
		public String answerClass;
	}
}
