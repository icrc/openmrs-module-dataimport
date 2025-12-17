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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Location;
import org.openmrs.module.dataimport.model.QuarantineItem;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for location validation during import. Tests the multi-location feature: per-row
 * location validation, allowed location enforcement, and retired location detection.
 */
public class LocationValidationTest {
	
	private Map<String, Location> locationCache;
	
	private Set<String> allowedLocationUuids;
	
	@BeforeEach
	public void setUp() {
		// Build a mock location cache
		locationCache = new HashMap<>();
		
		Location activeLocation1 = createLocation("loc-uuid-1", "Hospital A", false);
		Location activeLocation2 = createLocation("loc-uuid-2", "Clinic B", false);
		Location activeLocation3 = createLocation("loc-uuid-3", "Health Center C", false);
		Location retiredLocation = createLocation("loc-uuid-retired", "Old Facility", true);
		
		locationCache.put("loc-uuid-1", activeLocation1);
		locationCache.put("loc-uuid-2", activeLocation2);
		locationCache.put("loc-uuid-3", activeLocation3);
		locationCache.put("loc-uuid-retired", retiredLocation);
		
		// Default allowed list includes some but not all locations
		allowedLocationUuids = new HashSet<>(Arrays.asList("loc-uuid-1", "loc-uuid-2"));
	}
	
	private Location createLocation(String uuid, String name, boolean retired) {
		Location location = new Location();
		location.setUuid(uuid);
		location.setName(name);
		location.setRetired(retired);
		return location;
	}
	
	// T016: Test valid location UUID passes validation
	@Test
	public void validateLocation_shouldPassForValidLocationUuid() {
		String locationUuid = "loc-uuid-1";
		
		LocationValidationResult result = LocationValidation.validateLocation(locationUuid, locationCache,
		    allowedLocationUuids, false, // restrictToAllowedLocations
		    "default-loc");
		
		assertTrue(result.isValid());
		assertNull(result.getErrorCode());
		assertNull(result.getErrorMessage());
		assertEquals("loc-uuid-1", result.getResolvedLocationUuid());
	}
	
	// T017: Test invalid location UUID is quarantined with INVALID_LOCATION error code
	@Test
	public void validateLocation_shouldReturnInvalidLocationForNonexistentUuid() {
		String locationUuid = "nonexistent-uuid";
		
		LocationValidationResult result = LocationValidation.validateLocation(locationUuid, locationCache,
		    allowedLocationUuids, false, "default-loc");
		
		assertFalse(result.isValid());
		assertEquals("INVALID_LOCATION", result.getErrorCode());
		assertTrue(result.getErrorMessage().contains("nonexistent-uuid"));
		assertTrue(result.getErrorMessage().contains("does not exist"));
	}
	
	// T018: Test retired location is quarantined with clear error message
	@Test
	public void validateLocation_shouldReturnErrorForRetiredLocation() {
		String locationUuid = "loc-uuid-retired";
		
		LocationValidationResult result = LocationValidation.validateLocation(locationUuid, locationCache,
		    allowedLocationUuids, false, "default-loc");
		
		assertFalse(result.isValid());
		assertEquals("INVALID_LOCATION", result.getErrorCode());
		assertTrue(result.getErrorMessage().contains("retired"));
		assertTrue(
		    result.getErrorMessage().contains("Old Facility") || result.getErrorMessage().contains("loc-uuid-retired"));
	}
	
	// T019: Test empty location cell uses default location from template metadata
	@Test
	public void validateLocation_shouldUseDefaultLocationWhenEmpty() {
		String locationUuid = ""; // empty
		
		LocationValidationResult result = LocationValidation.validateLocation(locationUuid, locationCache,
		    allowedLocationUuids, false, "loc-uuid-1" // default location
		);
		
		assertTrue(result.isValid());
		assertEquals("loc-uuid-1", result.getResolvedLocationUuid());
	}
	
	@Test
	public void validateLocation_shouldUseDefaultLocationWhenNull() {
		LocationValidationResult result = LocationValidation.validateLocation(null, locationCache, allowedLocationUuids,
		    false, "loc-uuid-2");
		
		assertTrue(result.isValid());
		assertEquals("loc-uuid-2", result.getResolvedLocationUuid());
	}
	
	// T020: Test location not in allowed list is quarantined when restrictToAllowedLocations=true
	@Test
	public void validateLocation_shouldQuarantineLocationNotInAllowedListWhenRestricted() {
		String locationUuid = "loc-uuid-3"; // valid location but not in allowed list
		
		LocationValidationResult result = LocationValidation.validateLocation(locationUuid, locationCache,
		    allowedLocationUuids, // only contains loc-uuid-1 and loc-uuid-2
		    true, // restrictToAllowedLocations
		    "default-loc");
		
		assertFalse(result.isValid());
		assertEquals("LOCATION_NOT_ALLOWED", result.getErrorCode());
		assertTrue(result.getErrorMessage().contains("not in the allowed location list")
		        || result.getErrorMessage().contains("Health Center C"));
	}
	
	// T21: Test any valid location accepted when restrictToAllowedLocations=false (backward compatibility)
	@Test
	public void validateLocation_shouldAcceptAnyValidLocationWhenNotRestricted() {
		String locationUuid = "loc-uuid-3"; // valid location not in allowed list
		
		LocationValidationResult result = LocationValidation.validateLocation(locationUuid, locationCache,
		    allowedLocationUuids, // only contains loc-uuid-1 and loc-uuid-2
		    false, // not restricted
		    "default-loc");
		
		assertTrue(result.isValid());
		assertEquals("loc-uuid-3", result.getResolvedLocationUuid());
	}
	
	@Test
	public void validateLocation_shouldAcceptAllowedLocationWhenRestricted() {
		String locationUuid = "loc-uuid-1"; // in allowed list
		
		LocationValidationResult result = LocationValidation.validateLocation(locationUuid, locationCache,
		    allowedLocationUuids, true, // restricted
		    "default-loc");
		
		assertTrue(result.isValid());
		assertEquals("loc-uuid-1", result.getResolvedLocationUuid());
	}
	
	@Test
	public void validateLocation_shouldValidateDefaultLocationWhenRestrictedAndCellEmpty() {
		// Default location is loc-uuid-1 which is in allowed list
		LocationValidationResult result = LocationValidation.validateLocation("", locationCache, allowedLocationUuids, true,
		    "loc-uuid-1");
		
		assertTrue(result.isValid());
		assertEquals("loc-uuid-1", result.getResolvedLocationUuid());
	}
	
	@Test
	public void validateLocation_shouldFailIfDefaultLocationNotInAllowedListWhenRestricted() {
		// Default location is loc-uuid-3 which is NOT in allowed list
		LocationValidationResult result = LocationValidation.validateLocation("", locationCache, allowedLocationUuids, true,
		    "loc-uuid-3");
		
		assertFalse(result.isValid());
		assertEquals("LOCATION_NOT_ALLOWED", result.getErrorCode());
	}
	
	@Test
	public void validateLocation_shouldSkipAllowedListCheckWhenAllowedListEmpty() {
		// Empty allowed list means "allow all locations"
		Set<String> emptyAllowedList = Collections.emptySet();
		
		LocationValidationResult result = LocationValidation.validateLocation("loc-uuid-3", locationCache, emptyAllowedList,
		    true, // restricted but list is empty
		    "default-loc");
		
		assertTrue(result.isValid());
		assertEquals("loc-uuid-3", result.getResolvedLocationUuid());
	}
	
	// Test QuarantineItem creation helper
	@Test
	public void createQuarantineItem_shouldCreateProperlyFormattedItem() {
		LocationValidationResult result = LocationValidation.validateLocation("nonexistent-uuid", locationCache,
		    allowedLocationUuids, false, "default-loc");
		
		QuarantineItem item = LocationValidation.createQuarantineItem(result, "TestSheet", 5, 3);
		
		assertNotNull(item);
		assertEquals("TestSheet", item.getSheet());
		assertEquals(5, item.getRow());
		assertEquals(3, item.getCol());
		assertEquals("INVALID_LOCATION", item.getCode());
		assertTrue(item.getMessage().contains("nonexistent-uuid"));
	}
}
