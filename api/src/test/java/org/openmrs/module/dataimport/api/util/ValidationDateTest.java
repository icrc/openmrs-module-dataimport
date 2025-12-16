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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.model.ColumnMeta;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.ObsBlueprint;
import org.openmrs.module.dataimport.model.QuarantineItem;
import org.openmrs.module.dataimport.model.SheetMeta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationDateTest {
	
	/**
	 * @see ValidationDate
	 */
	@Test
	public void validDate_shouldTestValidateDateObs() {
		LocalDate validDate = LocalDate.of(2023, 6, 15);
		ObsBlueprint obs = ObsBlueprint.date("test-concept-uuid", validDate);
		
		ColumnMeta column = new ColumnMeta();
		column.colIndex = 1;
		column.datatype = FieldDatatype.DATE;
		column.conceptUuid = "test-concept-uuid";
		column.min = null;
		column.max = null;
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.columns.add(column);
		
		List<QuarantineItem> quarantineItems = new ArrayList<>();
		
		Validation.checkRow(Collections.singletonList(obs), sheetMeta, quarantineItems, "TestSheet", 1);
		
		assertTrue(quarantineItems.isEmpty());
	}
	
	/**
	 * @see ValidationDate
	 */
	@Test
	public void dateTooEarly_shouldTestValidateDateObs() {
		LocalDate earlyDate = LocalDate.of(2020, 1, 1);
		ObsBlueprint obs = ObsBlueprint.date("test-concept-uuid", earlyDate);
		
		ColumnMeta column = new ColumnMeta();
		column.colIndex = 1;
		column.datatype = FieldDatatype.DATE;
		column.conceptUuid = "test-concept-uuid";
		column.min = (double) LocalDate.of(2022, 1, 1).toEpochDay();
		column.max = null;
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.columns.add(column);
		
		List<QuarantineItem> quarantineItems = new ArrayList<>();
		
		Validation.checkRow(Collections.singletonList(obs), sheetMeta, quarantineItems, "TestSheet", 1);
		
		assertEquals(1, quarantineItems.size());
		QuarantineItem item = quarantineItems.get(0);
		assertEquals("TestSheet", item.getSheet());
		assertEquals(1, item.getRow());
		assertEquals(1, item.getCol());
		assertEquals("DATE_TOO_EARLY", item.getCode());
		assertTrue(item.getMessage().contains("is before minimum date"));
	}
	
	/**
	 * @see ValidationDate
	 */
	@Test
	public void dateTooLate_shouldTestValidateDateObs() {
		LocalDate lateDate = LocalDate.of(2023, 6, 15);
		ObsBlueprint obs = ObsBlueprint.date("test-concept-uuid", lateDate);
		
		ColumnMeta column = new ColumnMeta();
		column.colIndex = 1;
		column.datatype = FieldDatatype.DATE;
		column.conceptUuid = "test-concept-uuid";
		column.min = null;
		column.max = (double) LocalDate.of(2022, 12, 31).toEpochDay();
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.columns.add(column);
		
		List<QuarantineItem> quarantineItems = new ArrayList<>();
		
		Validation.checkRow(Collections.singletonList(obs), sheetMeta, quarantineItems, "TestSheet", 1);
		
		assertEquals(1, quarantineItems.size());
		QuarantineItem item = quarantineItems.get(0);
		assertEquals("TestSheet", item.getSheet());
		assertEquals(1, item.getRow());
		assertEquals(1, item.getCol());
		assertEquals("DATE_TOO_LATE", item.getCode());
		assertTrue(item.getMessage().contains("is after maximum date"));
	}
	
	/**
	 * @see ValidationDate Future dates are allowed by design (for scheduled appointments, expected
	 *      dates, etc.) unless explicitly constrained with a max date.
	 */
	@Test
	public void futureDate_shouldBeAllowedWithoutExplicitMaxConstraint() {
		LocalDate futureDate = LocalDate.now().plusDays(1);
		ObsBlueprint obs = ObsBlueprint.date("test-concept-uuid", futureDate);
		
		ColumnMeta column = new ColumnMeta();
		column.colIndex = 1;
		column.datatype = FieldDatatype.DATE;
		column.conceptUuid = "test-concept-uuid";
		column.min = null;
		column.max = null;
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.columns.add(column);
		
		List<QuarantineItem> quarantineItems = new ArrayList<>();
		
		Validation.checkRow(Collections.singletonList(obs), sheetMeta, quarantineItems, "TestSheet", 1);
		
		// Future dates should be allowed when no explicit max constraint is set
		assertTrue(quarantineItems.isEmpty(), "Future dates should be allowed without explicit max constraint");
	}
	
	/**
	 * @see ValidationDate
	 */
	@Test
	public void dateWithinRange_shouldTestValidateDateObs() {
		LocalDate validDate = LocalDate.of(2023, 6, 15);
		ObsBlueprint obs = ObsBlueprint.date("test-concept-uuid", validDate);
		
		ColumnMeta column = new ColumnMeta();
		column.colIndex = 1;
		column.datatype = FieldDatatype.DATE;
		column.conceptUuid = "test-concept-uuid";
		column.min = (double) LocalDate.of(2022, 1, 1).toEpochDay();
		column.max = (double) LocalDate.of(2024, 12, 31).toEpochDay();
		
		SheetMeta sheetMeta = new SheetMeta();
		sheetMeta.columns.add(column);
		
		List<QuarantineItem> quarantineItems = new ArrayList<>();
		
		Validation.checkRow(Collections.singletonList(obs), sheetMeta, quarantineItems, "TestSheet", 1);
		
		assertTrue(quarantineItems.isEmpty());
	}
}
