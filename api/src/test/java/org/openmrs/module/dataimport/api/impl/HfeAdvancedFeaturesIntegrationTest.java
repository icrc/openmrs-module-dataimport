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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Form;
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.SheetModel;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Integration tests for advanced HFE features including repeat groups, obsgroups, conditional
 * logic, and internationalization support. Tests use realistic form structures stored as test data
 * to verify parsing behavior and feature support without coupling to specific organization's forms.
 */
public class HfeAdvancedFeaturesIntegrationTest extends BaseHfeIntegrationTest {
	
	private HfeFormParser hfeFormParser;
	
	private ConceptAnswersService conceptAnswersService;
	
	@BeforeEach
	public void setUp() {
		super.setUpBase();
		// Mock ConceptAnswersService for testing
		conceptAnswersService = mock(ConceptAnswersService.class);
		lenient().when(conceptAnswersService.resolveConceptUuid(anyString()))
		        .thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(conceptAnswersService.getConceptName(anyString())).thenReturn("Test Concept Name");
		
		hfeFormParser = new HfeFormParser(conceptAnswersService);
	}
	
	@Test
	public void parse_shouldTrackRepeatGroupFields() throws Exception {
		// Given: A form with repeat groups
		String hfeXml = loadTestResource("testdata/hfe-forms/complex/form-with-repeats-and-obsgroups.xml");
		Form form = createMockForm("Test Repeat Form", "1.0");
		
		// When: Parsing the form
		FormModel formModel = hfeFormParser.parse(hfeXml, form, null);
		
		// Then: Fields from repeat groups should have isRepeat=true
		assertNotNull(formModel);
		assertFalse(formModel.sheets.isEmpty());
		
		SheetModel sheet = formModel.sheets.get(0);
		List<FieldModel> fields = sheet.fields;
		
		// Find fields from the repeat expansion (should have labels like "Left Hip Pain Level")
		List<FieldModel> repeatFields = fields.stream().filter(f -> f.label != null && f.label.contains("Pain Level"))
		        .collect(Collectors.toList());
		
		// Should have 3 repeat fields (Left Hip, Right Hip, Left Knee)
		assertEquals(3, repeatFields.size(), "Should have 3 repeat-expanded fields");
		
		// All repeat fields should have isRepeat=true
		for (FieldModel field : repeatFields) {
			assertTrue(field.isRepeat, "Field '" + field.label + "' should have isRepeat=true");
		}
		
		// Non-repeat fields should have isRepeat=false
		FieldModel weightField = fields.stream().filter(f -> f.conceptUuid != null && f.conceptUuid.contains("5089"))
		        .findFirst().orElse(null);
		assertNotNull(weightField, "Weight field should exist");
		assertFalse(weightField.isRepeat, "Weight field should have isRepeat=false");
	}
	
	@Test
	public void parse_shouldFlattenObsgroupsIntoIndividualFields() throws Exception {
		// Given: A form with obsgroups
		String hfeXml = loadTestResource("testdata/hfe-forms/complex/form-with-repeats-and-obsgroups.xml");
		Form form = createMockForm("Test Obsgroup Form", "1.0");
		
		// When: Parsing the form
		FormModel formModel = hfeFormParser.parse(hfeXml, form, null);
		
		// Then: Obsgroup children should be flattened into individual columns
		assertNotNull(formModel);
		SheetModel sheet = formModel.sheets.get(0);
		
		// Should have separate fields for systolic and diastolic BP
		FieldModel systolicField = sheet.fields.stream().filter(f -> f.label != null && f.label.contains("Systolic"))
		        .findFirst().orElse(null);
		assertNotNull(systolicField, "Systolic BP field should exist as individual field");
		
		FieldModel diastolicField = sheet.fields.stream().filter(f -> f.label != null && f.label.contains("Diastolic"))
		        .findFirst().orElse(null);
		assertNotNull(diastolicField, "Diastolic BP field should exist as individual field");
	}
	
	@Test
	public void parse_shouldDetectConditionalLogic() throws Exception {
		// Given: A form with conditional fields
		String hfeXml = loadTestResource("testdata/hfe-forms/complex/form-with-repeats-and-obsgroups.xml");
		Form form = createMockForm("Test Conditional Form", "1.0");
		
		// When: Parsing the form
		FormModel formModel = hfeFormParser.parse(hfeXml, form, null);
		
		// Then: Conditional fields should have hideExpr set
		assertNotNull(formModel);
		SheetModel sheet = formModel.sheets.get(0);
		
		FieldModel conditionalField = sheet.fields.stream()
		        .filter(f -> f.label != null && f.label.contains("Complication Details")).findFirst().orElse(null);
		
		assertNotNull(conditionalField, "Conditional field should exist");
		assertNotNull(conditionalField.hideExpr, "Conditional field should have hideExpr set");
		assertTrue(conditionalField.hideExpr.contains("advisory"), "hideExpr should indicate it's advisory only");
	}
	
	@Test
	public void parse_shouldHandleLabelCodeAttributesWithoutMessageSource() throws Exception {
		// Given: A form with labelCode attributes and no MessageSource injected
		String hfeXml = loadTestResource("testdata/hfe-forms/complex/form-with-i18n-labels.xml");
		Form form = createMockForm("Test I18n Form", "1.0");
		
		// When: Parsing without MessageSource (default behavior)
		FormModel formModel = hfeFormParser.parse(hfeXml, form, null);
		
		// Then: Should fallback to using labelCode as literal text
		assertNotNull(formModel);
		SheetModel sheet = formModel.sheets.get(0);
		
		// Should have fields with labelCode values as labels
		FieldModel weightField = sheet.fields.stream().filter(f -> f.conceptUuid != null && f.conceptUuid.contains("5089"))
		        .findFirst().orElse(null);
		
		assertNotNull(weightField, "Weight field should exist");
		assertNotNull(weightField.label, "Weight field should have a label");
		// Label should be the labelCode value since MessageSource is not available
		assertEquals("vitals.weight.label", weightField.label,
		    "Should use labelCode as fallback when MessageSource unavailable");
	}
	
	@Test
	public void parse_shouldResolveLabelCodeWithMessageSource() throws Exception {
		// Given: A form with labelCode attributes and MessageSource injected
		String hfeXml = loadTestResource("testdata/hfe-forms/complex/form-with-i18n-labels.xml");
		Form form = createMockForm("Test I18n Form", "1.0");
		
		// Create and inject a mock MessageSource
		org.openmrs.messagesource.MessageSourceService messageSource = new org.openmrs.messagesource.impl.MessageSourceServiceImpl();
		hfeFormParser.setMessageSourceService(messageSource);
		
		// When: Parsing with MessageSource
		FormModel formModel = hfeFormParser.parse(hfeXml, form, null);
		
		// Then: Should attempt to resolve labelCode (will fallback since messages not loaded)
		assertNotNull(formModel);
		SheetModel sheet = formModel.sheets.get(0);
		
		// Verify fields exist (actual i18n resolution depends on loaded message properties)
		assertFalse(sheet.fields.isEmpty(), "Should have parsed fields");
		assertTrue(sheet.fields.size() >= 4, "Should have at least 4 fields");
	}
	
	@Test
	public void parse_shouldHandleComplexFormWithMultipleFeatures() throws Exception {
		// Given: A complex form with repeats, obsgroups, and conditional logic
		String hfeXml = loadTestResource("testdata/hfe-forms/complex/form-with-repeats-and-obsgroups.xml");
		Form form = createMockForm("Complex Form", "1.0");
		
		// When: Parsing the form
		FormModel formModel = hfeFormParser.parse(hfeXml, form, null);
		
		// Then: Should parse successfully with all fields
		assertNotNull(formModel);
		assertFalse(formModel.sheets.isEmpty());
		
		SheetModel sheet = formModel.sheets.get(0);
		assertFalse(sheet.fields.isEmpty(), "Should have fields");
		
		// Verify we have encounter metadata fields
		assertTrue(sheet.fields.stream().anyMatch(f -> "encounterDate".equals(f.id)), "Should have encounterDate field");
		assertTrue(sheet.fields.stream().anyMatch(f -> "encounterProvider".equals(f.id)),
		    "Should have encounterProvider field");
		
		// Verify we have regular obs fields
		long regularObsCount = sheet.fields.stream().filter(f -> f.conceptUuid != null && !f.isRepeat).count();
		assertTrue(regularObsCount > 0, "Should have regular obs fields");
		
		// Verify we have repeat fields
		long repeatFieldCount = sheet.fields.stream().filter(f -> f.isRepeat).count();
		assertTrue(repeatFieldCount > 0, "Should have repeat fields");
	}
	
	/**
	 * Helper method to create mock Form object for testing
	 */
	private Form createMockForm(String name, String version) {
		Form form = new Form();
		form.setName(name);
		form.setVersion(version);
		form.setUuid("test-form-" + name.replaceAll("\\s+", "-").toLowerCase());
		return form;
	}
}
