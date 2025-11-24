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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Form;
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.openmrs.module.dataimport.api.util.HfeTestDataBuilder;
import org.openmrs.module.dataimport.model.FormModel;

import java.util.List;

public class HfeTemplateGenerationTest extends BaseHfeIntegrationTest {
	
	private HfeFormParser hfeFormParser;
	
	private ExcelTemplateExporter templateExporter;
	
	private ConceptAnswersService conceptAnswersService;
	
	@BeforeEach
	public void setUp() {
		super.setUpBase();
		conceptAnswersService = mock(ConceptAnswersService.class);
		when(conceptAnswersService.resolveConceptUuid(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		
		hfeFormParser = new HfeFormParser(conceptAnswersService);
		templateExporter = new ExcelTemplateExporter();
	}
	
	@Test
	public void parse_shouldGenerateCorrectColumnsForBasicHfeForm() throws Exception {
		String hfeXml = loadTestResource("testdata/hfe-forms/vitals-basic.xml");
		Form form = createMockForm("vitals-form-uuid", "Vitals Form", hfeXml);
		
		FormModel formModel = hfeFormParser.parse(hfeXml, form, null);
		
		assertNotNull(formModel, "FormModel should not be null");
		assertNotNull(formModel.sheets, "FormModel should have sheets");
		assertTrue(formModel.sheets.size() > 0, "FormModel should have at least one sheet");
		
		byte[] excelBytes = templateExporter.export(formModel, true);
		assertNotNull(excelBytes, "Excel template should be generated");
		assertTrue(excelBytes.length > 0, "Excel template should not be empty");
		
		Workbook workbook = bytesToWorkbook(excelBytes);
		
		assertHasSheet(workbook, "Vitals Form");
		assertHasSheet(workbook, "__meta");
		assertHasSheet(workbook, "__concepts");
		
		Sheet dataSheet = workbook.getSheet("Vitals Form");
		assertNotNull(dataSheet, "Data sheet should exist");
		
		assertHasColumn(dataSheet, "Patient Ref");
		assertHasColumn(dataSheet, "Encounter Date *");
		assertHasColumn(dataSheet, "Weight (kg)");
		assertHasColumn(dataSheet, "Height (cm)");
		assertHasColumn(dataSheet, "Temperature (C)");
		assertHasColumn(dataSheet, "Systolic BP (mmHg)");
		assertHasColumn(dataSheet, "Diastolic BP (mmHg)");
		assertHasColumn(dataSheet, "Heart Rate (bpm)");
		assertHasColumn(dataSheet, "Respiratory Rate (/min)");
		assertHasColumn(dataSheet, "Oxygen Saturation (%)");
		
		List<String> columns = org.openmrs.module.dataimport.api.util.ExcelTestHelper.getColumnNames(dataSheet);
		assertEquals(10, columns.size(), "Should have 10 columns (patient ref + encounter date + 8 vitals)");
	}
	
	@Test
	public void parse_shouldExpandColumnsCorrectlyForHfeFormWithRepeats() throws Exception {
		String hfeXml = HfeTestDataBuilder.hfeForm().withFormName("Surgery Form")
		        .withRepeat("['Hip Surgery','CIEL:1001','Hip'], ['Knee Surgery','CIEL:1002','Knee']",
		            new HfeTestDataBuilder.ObsElement("{1}", "{2}", "text", null),
		            new HfeTestDataBuilder.ObsElement("{3} Outcome", "CIEL:1003", "text", null))
		        .buildXml();
		
		Form form = createMockForm("surgery-form-uuid", "Surgery Form", hfeXml);
		
		FormModel formModel = hfeFormParser.parse(hfeXml, form, null);
		byte[] excelBytes = templateExporter.export(formModel, true);
		
		Workbook workbook = bytesToWorkbook(excelBytes);
		Sheet dataSheet = workbook.getSheet("Surgery Form");
		assertNotNull(dataSheet, "Data sheet should exist");
		
		List<String> columns = org.openmrs.module.dataimport.api.util.ExcelTestHelper.getColumnNames(dataSheet);
		System.out.println("DEBUG repeat test - Actual columns: " + columns);
		
		assertTrue(columns.size() >= 4, "Should have at least 4 expanded columns from repeat");
		
		assertNotNull(dataSheet.getRow(0), "Should have header row");
	}
	
	@Test
	public void parse_shouldFlattenObsgroupsIntoIndividualColumns() throws Exception {
		String hfeXml = HfeTestDataBuilder.hfeForm().withFormName("Visit Form")
		        .withObsgroup("CIEL:1004", new HfeTestDataBuilder.ObsElement("CIEL:1005", "Admission Date", "date", null),
		            new HfeTestDataBuilder.ObsElement("CIEL:1006", "Discharge Date", "date", null),
		            new HfeTestDataBuilder.ObsElement("CIEL:1007", "Length of Stay", "number", null))
		        .buildXml();
		
		Form form = createMockForm("visit-form-uuid", "Visit Form", hfeXml);
		
		FormModel formModel = hfeFormParser.parse(hfeXml, form, null);
		byte[] excelBytes = templateExporter.export(formModel, true);
		
		Workbook workbook = bytesToWorkbook(excelBytes);
		Sheet dataSheet = workbook.getSheet("Visit Form");
		assertNotNull(dataSheet, "Data sheet should exist");
		
		assertHasColumn(dataSheet, "Admission Date");
		assertHasColumn(dataSheet, "Discharge Date");
		assertHasColumn(dataSheet, "Length of Stay");
		
		List<String> columns = org.openmrs.module.dataimport.api.util.ExcelTestHelper.getColumnNames(dataSheet);
		assertTrue(columns.stream().noneMatch(c -> c.toLowerCase().contains("obsgroup")),
		    "Obsgroup should be flattened, not shown as separate column");
	}
	
	@Test
	public void parse_shouldIncludeConditionalFieldsInTemplate() throws Exception {
		String hfeXml = HfeTestDataBuilder.hfeForm().withFormName("Conditional Form")
		        .withObs("CIEL:1008", "Pregnant", "radio")
		        .withConditionalObs("CIEL:1009", "Gestational Age", "number", "pregnant == 'No'").buildXml();
		
		Form form = createMockForm("conditional-form-uuid", "Conditional Form", hfeXml);
		
		FormModel formModel = hfeFormParser.parse(hfeXml, form, null);
		byte[] excelBytes = templateExporter.export(formModel, true);
		
		Workbook workbook = bytesToWorkbook(excelBytes);
		Sheet dataSheet = workbook.getSheet("Conditional Form");
		assertNotNull(dataSheet, "Data sheet should exist");
		
		assertHasColumn(dataSheet, "Gestational Age");
		
		assertTrue(org.openmrs.module.dataimport.api.util.ExcelTestHelper.hasColumn(dataSheet, "Gestational Age"),
		    "Conditional field should be included in template");
	}
	
	@Test
	public void parse_shouldResolveConceptMappingsAndPopulateMetadata() throws Exception {
		when(conceptAnswersService.resolveConceptUuid("CIEL:5089")).thenReturn("c607c80f-1ea9-4da3-bb88-6276ce8868dd");
		when(conceptAnswersService.resolveConceptUuid("CIEL:5088")).thenReturn("5088aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
		
		String hfeXml = HfeTestDataBuilder.hfeForm().withFormName("Mapping Test Form")
		        .withObs("CIEL:5089", "Weight", "number").withObs("CIEL:5088", "Temperature", "number").buildXml();
		
		Form form = createMockForm("mapping-form-uuid", "Mapping Test Form", hfeXml);
		
		FormModel formModel = hfeFormParser.parse(hfeXml, form, null);
		assertNotNull(formModel, "FormModel should not be null");
		
		assertTrue(formModel.sheets.size() > 0, "Should have at least one sheet");
		assertTrue(formModel.sheets.get(0).fields.size() >= 2, "Should have at least 2 fields");
		
		byte[] excelBytes = templateExporter.export(formModel, true);
		Workbook workbook = bytesToWorkbook(excelBytes);
		
		assertHasSheet(workbook, "__concepts");
		Sheet conceptsSheet = workbook.getSheet("__concepts");
		assertNotNull(conceptsSheet, "Concepts metadata sheet should exist");
		assertTrue(conceptsSheet.getLastRowNum() > 0, "Concepts sheet should have data");
	}
}
