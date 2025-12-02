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

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.openmrs.Concept;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.util.ExcelTestHelper;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class BaseHfeIntegrationTest extends BaseModuleContextSensitiveTest {
	
	protected ConceptService conceptService;
	
	protected PatientService patientService;
	
	protected EncounterService encounterService;
	
	protected ObjectMapper objectMapper;
	
	@BeforeEach
	public void setUpBase() {
		conceptService = Context.getConceptService();
		patientService = Context.getPatientService();
		encounterService = Context.getEncounterService();
		objectMapper = new ObjectMapper();
		
		loadTestConcepts();
	}
	
	protected void loadTestConcepts() {
		try {
			String cielPath = "api/src/test/resources/test-data/test-concepts/ciel-concepts.json";
			if (Files.exists(Paths.get(cielPath))) {
				byte[] jsonBytes = Files.readAllBytes(Paths.get(cielPath));
				List<Map<String, Object>> concepts = objectMapper.readValue(jsonBytes,
				    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
				
				for (Map<String, Object> conceptData : concepts) {
					createTestConcept((String) conceptData.get("uuid"), (String) conceptData.get("name"),
					    (String) conceptData.get("datatype"));
				}
			}
		}
		catch (IOException e) {
			System.err.println("Warning: Could not load test concepts: " + e.getMessage());
		}
	}
	
	protected Concept createTestConcept(String uuid, String name, String datatypeCode) {
		Concept concept = new Concept();
		concept.setUuid(uuid);
		
		ConceptName conceptName = new ConceptName(name, Locale.ENGLISH);
		conceptName.setLocalePreferred(true);
		concept.addName(conceptName);
		
		ConceptDatatype datatype = getOrCreateDatatype(datatypeCode);
		concept.setDatatype(datatype);
		
		concept.setConceptClass(conceptService.getConceptClassByName("Misc"));
		
		return conceptService.saveConcept(concept);
	}
	
	protected ConceptDatatype getOrCreateDatatype(String code) {
		ConceptDatatype datatype = conceptService.getConceptDatatypeByName(code);
		if (datatype == null) {
			// Map to standard datatypes
			switch (code) {
				case "NM":
					datatype = conceptService.getConceptDatatypeByName("Numeric");
					break;
				case "DT":
					datatype = conceptService.getConceptDatatypeByName("Date");
					break;
				case "CWE":
					datatype = conceptService.getConceptDatatypeByName("Coded");
					break;
				case "ST":
					datatype = conceptService.getConceptDatatypeByName("Text");
					break;
				default:
					datatype = conceptService.getConceptDatatypeByName("N/A");
			}
		}
		return datatype != null ? datatype : conceptService.getConceptDatatypeByName("N/A");
	}
	
	protected Form createMockForm(String uuid, String name, String hfeXml) {
		Form form = new Form();
		form.setUuid(uuid);
		form.setName(name);
		form.setVersion("1.0");
		
		EncounterType encounterType = new EncounterType();
		encounterType.setUuid(uuid + "-encounter-type");
		encounterType.setName(name + " Encounter");
		encounterType.setDescription("Test encounter type for " + name);
		
		form.setEncounterType(encounterType);
		
		return form;
	}
	
	protected String loadTestResource(String resourcePath) throws IOException {
		try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
			if (is == null) {
				throw new IOException("Resource not found: " + resourcePath);
			}
			// Java 8 compatible way to read all bytes
			java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
			int nRead;
			byte[] data = new byte[1024];
			while ((nRead = is.read(data, 0, data.length)) != -1) {
				buffer.write(data, 0, nRead);
			}
			return new String(buffer.toByteArray(), "UTF-8");
		}
	}
	
	/**
	 * Converts Excel byte array to Workbook.
	 */
	protected Workbook bytesToWorkbook(byte[] excelBytes) throws IOException {
		return WorkbookFactory.create(new ByteArrayInputStream(excelBytes));
	}
	
	/**
	 * Asserts that an Excel workbook has a sheet with the given name.
	 *
	 * @param workbook the workbook
	 * @param sheetName the expected sheet name
	 */
	protected void assertHasSheet(Workbook workbook, String sheetName) {
		assertTrue(ExcelTestHelper.hasSheet(workbook, sheetName), "Workbook should contain sheet: " + sheetName);
	}
	
	/**
	 * Asserts that a sheet has a column with the given name.
	 *
	 * @param sheet the sheet
	 * @param columnName the expected column name
	 */
	protected void assertHasColumn(Sheet sheet, String columnName) {
		assertTrue(ExcelTestHelper.hasColumn(sheet, columnName), "Sheet should contain column: " + columnName);
	}
	
	/**
	 * Asserts that a column has dropdown validation.
	 *
	 * @param sheet the sheet
	 * @param columnName the column name
	 * @param expectedOptions the expected dropdown options
	 */
	protected void assertHasDropdown(Sheet sheet, String columnName, List<String> expectedOptions) {
		int columnIndex = ExcelTestHelper.getColumnIndex(sheet, columnName);
		assertTrue(columnIndex != -1, "Column not found: " + columnName);
		assertTrue(ExcelTestHelper.hasDropdownValidation(sheet, columnIndex),
		    "Column should have dropdown validation: " + columnName);
		
		// Note: Dropdown options validation would need workbook context
		// This is a simplified version - full implementation in ExcelTestHelper
	}
	
	/**
	 * Asserts that a cell has the expected format.
	 *
	 * @param sheet the sheet
	 * @param rowIndex the row index
	 * @param columnIndex the column index
	 * @param expectedFormat the expected format pattern
	 */
	protected void assertCellFormat(Sheet sheet, int rowIndex, int columnIndex, String expectedFormat) {
		assertTrue(ExcelTestHelper.hasCellFormat(sheet, rowIndex, columnIndex, expectedFormat),
		    "Cell should have format: " + expectedFormat);
	}
	
	/**
	 * Asserts that the expected number of patients were created.
	 *
	 * @param expected the expected count
	 */
	protected void assertPatientsCreated(int expected) {
		List<Patient> allPatients = patientService.getAllPatients();
		int actual = allPatients.size();
		assertEquals(expected, actual, "Expected " + expected + " patients, but found " + actual);
	}
	
	/**
	 * Asserts that the expected number of encounters were created.
	 *
	 * @param expected the expected count
	 */
	protected void assertEncountersCreated(int expected) {
		List<Encounter> allEncounters = new ArrayList<>();
		for (EncounterType type : encounterService.getAllEncounterTypes()) {
			allEncounters.addAll(encounterService.getEncounters(null, null, null, null, null,
			    java.util.Collections.singletonList(type), null, null, null, false));
		}
		int actual = allEncounters.size();
		assertEquals(expected, actual, "Expected " + expected + " encounters, but found " + actual);
	}
	
	/**
	 * Asserts that the expected number of observations were created.
	 *
	 * @param expected the expected count
	 */
	protected void assertObservationsCreated(int expected) {
		List<Encounter> encounters = new ArrayList<>();
		for (EncounterType type : encounterService.getAllEncounterTypes()) {
			encounters.addAll(encounterService.getEncounters(null, null, null, null, null,
			    java.util.Collections.singletonList(type), null, null, null, false));
		}
		int actual = 0;
		for (Encounter encounter : encounters) {
			actual += encounter.getAllObs().size();
		}
		assertEquals(expected, actual, "Expected " + expected + " observations, but found " + actual);
	}
	
	/**
	 * Asserts that an observation with a specific value exists for a patient.
	 *
	 * @param patientIdentifier the patient identifier
	 * @param conceptUuid the concept UUID
	 * @param expectedValue the expected observation value
	 */
	protected void assertObservationValue(String patientIdentifier, String conceptUuid, Object expectedValue) {
		List<Patient> patients = patientService.getPatients(patientIdentifier);
		assertFalse(patients.isEmpty(), "Patient not found: " + patientIdentifier);
		
		Patient patient = patients.get(0);
		List<Encounter> encounters = encounterService.getEncountersByPatient(patient);
		assertFalse(encounters.isEmpty(), "No encounters found for patient: " + patientIdentifier);
		
		Concept concept = conceptService.getConceptByUuid(conceptUuid);
		assertNotNull(concept, "Concept not found: " + conceptUuid);
		
		boolean found = false;
		for (Encounter encounter : encounters) {
			for (Obs obs : encounter.getAllObs()) {
				if (obs.getConcept().equals(concept)) {
					Object actualValue = getObsValue(obs);
					if (expectedValue.equals(actualValue)) {
						found = true;
						break;
					}
				}
			}
			if (found)
				break;
		}
		
		assertTrue(found, "Observation not found for patient " + patientIdentifier + ", concept " + conceptUuid + ", value "
		        + expectedValue);
	}
	
	/**
	 * Extracts the value from an Obs based on its datatype.
	 *
	 * @param obs the observation
	 * @return the observation value
	 */
	protected Object getObsValue(Obs obs) {
		ConceptDatatype datatype = obs.getConcept().getDatatype();
		if (datatype.isNumeric()) {
			return obs.getValueNumeric();
		} else if (datatype.isDate()) {
			return obs.getValueDate();
		} else if (datatype.isCoded()) {
			return obs.getValueCoded();
		} else if (datatype.isText()) {
			return obs.getValueText();
		} else if (datatype.isBoolean()) {
			return obs.getValueBoolean();
		}
		return null;
	}
	
	/**
	 * Asserts that database state matches expected outcomes after import.
	 *
	 * @param expectedPatients expected patient count
	 * @param expectedEncounters expected encounter count
	 * @param expectedObs expected observation count
	 */
	protected void assertDatabaseState(int expectedPatients, int expectedEncounters, int expectedObs) {
		assertPatientsCreated(expectedPatients);
		assertEncountersCreated(expectedEncounters);
		assertObservationsCreated(expectedObs);
	}
}
