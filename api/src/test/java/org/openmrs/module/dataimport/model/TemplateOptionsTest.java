/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for TemplateOptions model class. Tests the multi-location template feature:
 * locationUuids list and location restriction settings.
 */
public class TemplateOptionsTest {
	
	// T006: Test getEffectiveDefaultLocation() returns locationUuid when set
	@Test
	public void getEffectiveDefaultLocation_shouldReturnLocationUuidWhenSet() {
		TemplateOptions options = new TemplateOptions();
		options.setLocationUuid("default-location-uuid");
		options.setLocationUuids(Arrays.asList("loc-1", "loc-2", "loc-3"));
		
		String result = options.getEffectiveDefaultLocation();
		
		assertEquals("default-location-uuid", result);
	}
	
	// T007: Test getEffectiveDefaultLocation() returns first locationUuids item when locationUuid empty
	@Test
	public void getEffectiveDefaultLocation_shouldReturnFirstLocationUuidWhenDefaultEmpty() {
		TemplateOptions options = new TemplateOptions();
		options.setLocationUuid(""); // empty
		options.setLocationUuids(Arrays.asList("loc-1", "loc-2", "loc-3"));
		
		String result = options.getEffectiveDefaultLocation();
		
		assertEquals("loc-1", result);
	}
	
	@Test
	public void getEffectiveDefaultLocation_shouldReturnFirstLocationUuidWhenDefaultNull() {
		TemplateOptions options = new TemplateOptions();
		options.setLocationUuid(null);
		options.setLocationUuids(Arrays.asList("loc-1", "loc-2", "loc-3"));
		
		String result = options.getEffectiveDefaultLocation();
		
		assertEquals("loc-1", result);
	}
	
	@Test
	public void getEffectiveDefaultLocation_shouldReturnNullWhenNoLocationsSet() {
		TemplateOptions options = new TemplateOptions();
		options.setLocationUuid("");
		options.setLocationUuids(Collections.emptyList());
		
		String result = options.getEffectiveDefaultLocation();
		
		assertNull(result);
	}
	
	@Test
	public void getEffectiveDefaultLocation_shouldReturnNullWhenBothNull() {
		TemplateOptions options = new TemplateOptions();
		options.setLocationUuid(null);
		options.setLocationUuids(null);
		
		String result = options.getEffectiveDefaultLocation();
		
		assertNull(result);
	}
	
	// T008: Test getAllAllowedLocations() combines locationUuid and locationUuids without duplicates
	@Test
	public void getAllAllowedLocations_shouldCombineWithoutDuplicates() {
		TemplateOptions options = new TemplateOptions();
		options.setLocationUuid("loc-1"); // also in locationUuids
		options.setLocationUuids(Arrays.asList("loc-1", "loc-2", "loc-3"));
		
		List<String> result = options.getAllAllowedLocations();
		
		assertEquals(3, result.size());
		assertEquals("loc-1", result.get(0)); // locationUuid comes first
		assertEquals("loc-2", result.get(1));
		assertEquals("loc-3", result.get(2));
	}
	
	@Test
	public void getAllAllowedLocations_shouldIncludeLocationUuidNotInList() {
		TemplateOptions options = new TemplateOptions();
		options.setLocationUuid("default-loc");
		options.setLocationUuids(Arrays.asList("loc-1", "loc-2"));
		
		List<String> result = options.getAllAllowedLocations();
		
		assertEquals(3, result.size());
		assertEquals("default-loc", result.get(0)); // locationUuid comes first
		assertTrue(result.contains("loc-1"));
		assertTrue(result.contains("loc-2"));
	}
	
	@Test
	public void getAllAllowedLocations_shouldWorkWithOnlyLocationUuid() {
		TemplateOptions options = new TemplateOptions();
		options.setLocationUuid("single-location");
		options.setLocationUuids(Collections.emptyList());
		
		List<String> result = options.getAllAllowedLocations();
		
		assertEquals(1, result.size());
		assertEquals("single-location", result.get(0));
	}
	
	@Test
	public void getAllAllowedLocations_shouldWorkWithOnlyLocationUuids() {
		TemplateOptions options = new TemplateOptions();
		options.setLocationUuid("");
		options.setLocationUuids(Arrays.asList("loc-1", "loc-2"));
		
		List<String> result = options.getAllAllowedLocations();
		
		assertEquals(2, result.size());
		assertEquals("loc-1", result.get(0));
		assertEquals("loc-2", result.get(1));
	}
	
	// T009: Test getAllAllowedLocations() returns empty list when no locations set
	@Test
	public void getAllAllowedLocations_shouldReturnEmptyListWhenNoLocations() {
		TemplateOptions options = new TemplateOptions();
		options.setLocationUuid("");
		options.setLocationUuids(Collections.emptyList());
		
		List<String> result = options.getAllAllowedLocations();
		
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}
	
	@Test
	public void getAllAllowedLocations_shouldReturnEmptyListWhenBothNull() {
		TemplateOptions options = new TemplateOptions();
		options.setLocationUuid(null);
		options.setLocationUuids(null);
		
		List<String> result = options.getAllAllowedLocations();
		
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}
	
	// Additional tests for new fields
	@Test
	public void restrictToAllowedLocations_shouldDefaultToFalse() {
		TemplateOptions options = new TemplateOptions();
		
		assertFalse(options.isRestrictToAllowedLocations());
	}
	
	@Test
	public void locationUuids_shouldDefaultToEmptyList() {
		TemplateOptions options = new TemplateOptions();
		
		assertNotNull(options.getLocationUuids());
		assertTrue(options.getLocationUuids().isEmpty());
	}
}
