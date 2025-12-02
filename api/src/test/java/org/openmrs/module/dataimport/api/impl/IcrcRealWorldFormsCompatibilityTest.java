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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.openmrs.Form;
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.openmrs.module.dataimport.model.FormModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Comprehensive compatibility test for real-world ICRC HTML forms. Tests the HFE parser against
 * actual production forms to identify parsing issues, unsupported features, and edge cases. This
 * test is disabled by default and requires the ICRC distribution to be present at the expected
 * location. Enable by setting system property: -DtestRealIcrcForms=true
 */
@EnabledIf("isIcrcDistroAvailable")
public class IcrcRealWorldFormsCompatibilityTest {
	
	private static final Logger log = LoggerFactory.getLogger(IcrcRealWorldFormsCompatibilityTest.class);
	
	private static final String ICRC_FORMS_PATH = "/Users/mac/Documents/work/madiro.icrc/local distro/openmrs-distro-icrc/web/openmrs_config/htmlforms";
	
	private HfeFormParser parser;
	
	private ConceptAnswersService conceptAnswersService;
	
	// Test results tracking
	private Map<String, TestResult> testResults = new HashMap<>();
	
	@BeforeEach
	public void setUp() {
		conceptAnswersService = mock(ConceptAnswersService.class);
		when(conceptAnswersService.resolveConceptUuid(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		parser = new HfeFormParser(conceptAnswersService);
	}
	
	/**
	 * Condition method for EnabledIf annotation
	 */
	static boolean isIcrcDistroAvailable() {
		boolean enabled = Boolean.getBoolean("testRealIcrcForms");
		boolean pathExists = Files.exists(Paths.get(ICRC_FORMS_PATH));
		return enabled && pathExists;
	}
	
	@Test
	public void testAllIcrcForms_shouldParseWithoutCriticalErrors() throws IOException {
		log.info("Starting comprehensive ICRC forms compatibility test...");
		log.info("Forms location: {}", ICRC_FORMS_PATH);
		
		List<Path> allForms = findAllHtmlForms();
		log.info("Found {} HTML forms to test", allForms.size());
		
		int successCount = 0;
		int warningCount = 0;
		int errorCount = 0;
		
		for (Path formPath : allForms) {
			TestResult result = testSingleForm(formPath);
			testResults.put(formPath.toString(), result);
			
			if (result.status == TestStatus.SUCCESS) {
				successCount++;
			} else if (result.status == TestStatus.WARNING) {
				warningCount++;
			} else {
				errorCount++;
			}
		}
		
		// Generate detailed report
		generateTestReport(successCount, warningCount, errorCount);
		
		// Test should pass if no critical errors
		assertTrue(errorCount == 0, String.format("Found %d forms with critical errors. See logs for details.", errorCount));
	}
	
	@Test
	public void testSampleFormsFromEachProgram_shouldParseSuccessfully() throws IOException {
		// Test representative forms from each program
		Map<String, String> sampleForms = new HashMap<>();
		sampleForms.put("WTTC", "wttc/vitals.xml");
		sampleForms.put("MHPSS", "mhpss/mhpssBaselineForm.xml");
		sampleForms.put("PRP", "prp/clinicalNotesform.xml");
		sampleForms.put("HCD", "hcd/hcdBaseline.xml");
		
		for (Map.Entry<String, String> entry : sampleForms.entrySet()) {
			String program = entry.getKey();
			String formPath = entry.getValue();
			Path fullPath = Paths.get(ICRC_FORMS_PATH, formPath);
			
			if (!Files.exists(fullPath)) {
				log.warn("Sample form not found: {}", fullPath);
				continue;
			}
			
			log.info("Testing {} sample form: {}", program, formPath);
			TestResult result = testSingleForm(fullPath);
			
			assertNotNull(result.formModel, String.format("%s form should parse successfully", program));
			int fieldCount = result.formModel.sheets.stream().mapToInt(sheet -> sheet.fields.size()).sum();
			assertFalse(fieldCount == 0, String.format("%s form should have fields", program));
			
			log.info("{} form: {} fields extracted", program, fieldCount);
		}
	}
	
	@Test
	public void testComplexForms_shouldHandleObsgroupsAndRepeats() throws IOException {
		// Test known complex forms
		List<String> complexForms = new ArrayList<>();
		complexForms.add("prp/completeWheelchairandPosturalServiceAssessmentForm.xml");
		complexForms.add("prp/diabetesFootAssessmentForm.xml");
		complexForms.add("mhpss/mhpssBaselineForm.xml");
		
		for (String formPath : complexForms) {
			Path fullPath = Paths.get(ICRC_FORMS_PATH, formPath);
			
			if (!Files.exists(fullPath)) {
				log.warn("Complex form not found: {}", fullPath);
				continue;
			}
			
			log.info("Testing complex form: {}", formPath);
			TestResult result = testSingleForm(fullPath);
			
			assertNotNull(result.formModel, "Complex form should parse");
			int fieldCount = result.formModel.sheets.stream().mapToInt(sheet -> sheet.fields.size()).sum();
			log.info("Complex form {}: {} fields, {} warnings", formPath, fieldCount, result.warnings.size());
			
			// Log any warnings for complex forms
			for (String warning : result.warnings) {
				log.warn("  - {}", warning);
			}
		}
	}
	
	@Test
	public void testFormsWithUimessage_shouldDetectI18nUsage() throws IOException {
		log.info("Testing i18n (uimessage) usage across forms...");
		
		List<Path> allForms = findAllHtmlForms();
		int formsWithI18n = 0;
		int totalI18nReferences = 0;
		
		for (Path formPath : allForms) {
			String content = new String(Files.readAllBytes(formPath));
			
			if (content.contains("<uimessage")) {
				formsWithI18n++;
				
				// Count uimessage references
				int count = 0;
				int index = 0;
				while ((index = content.indexOf("<uimessage", index)) != -1) {
					count++;
					index++;
				}
				totalI18nReferences += count;
			}
		}
		
		log.info("i18n Analysis:");
		log.info("  Forms with <uimessage>: {}/{}", formsWithI18n, allForms.size());
		log.info("  Total <uimessage> references: {}", totalI18nReferences);
		log.info("  NOTE: MessageSource i18n lookup is not yet implemented (TODO in HfeFormParser.java:673)");
		
		assertTrue(formsWithI18n > 0, "Should detect forms using i18n");
	}
	
	@Test
	public void testFormsWithObsgroups_shouldFlattenCorrectly() throws IOException {
		log.info("Testing obsgroup flattening...");
		
		List<Path> allForms = findAllHtmlForms();
		int formsWithObsgroups = 0;
		
		for (Path formPath : allForms) {
			String content = new String(Files.readAllBytes(formPath));
			
			if (content.contains("<obsgroup")) {
				formsWithObsgroups++;
				
				TestResult result = testSingleForm(formPath);
				if (result.status != TestStatus.ERROR) {
					assertNotNull(result.formModel, "Form with obsgroup should parse");
					int fieldCount = result.formModel.sheets.stream().mapToInt(sheet -> sheet.fields.size()).sum();
					log.debug("  {} has {} fields (flattened from obsgroups)", formPath.getFileName(), fieldCount);
				}
			}
		}
		
		log.info("Found {} forms with obsgroups", formsWithObsgroups);
		assertTrue(formsWithObsgroups > 0, "Should find forms with obsgroups");
	}
	
	/**
	 * Test a single form and return detailed results
	 */
	private TestResult testSingleForm(Path formPath) {
		TestResult result = new TestResult();
		result.formPath = formPath.toString();
		
		try {
			String xmlContent = new String(Files.readAllBytes(formPath));
			Form mockForm = createMockForm(formPath.getFileName().toString());
			
			// Attempt to parse
			FormModel formModel = parser.parse(xmlContent, mockForm, null);
			result.formModel = formModel;
			result.status = TestStatus.SUCCESS;
			
			// Analyze for warnings
			analyzeFormForIssues(xmlContent, formModel, result);
			
			if (!result.warnings.isEmpty()) {
				result.status = TestStatus.WARNING;
			}
			
		}
		catch (Exception e) {
			result.status = TestStatus.ERROR;
			result.error = e;
			result.errorMessage = e.getMessage();
			log.error("Failed to parse form: {}", formPath, e);
		}
		
		return result;
	}
	
	/**
	 * Analyze form for potential issues and unsupported features
	 */
	private void analyzeFormForIssues(String xmlContent, FormModel formModel, TestResult result) {
		// Check for uimessage (i18n not implemented)
		if (xmlContent.contains("labelCode=")) {
			result.warnings.add("Uses labelCode attribute (i18n not fully implemented)");
		}
		
		// Check for repeat groups
		if (xmlContent.contains("<repeat")) {
			result.warnings.add("Contains repeat groups (metadata tracking TODO)");
		}
		
		// Check for conditional logic
		if (xmlContent.contains("hideWhenExpression") || xmlContent.contains("showId=") || xmlContent.contains("hideId=")) {
			result.warnings.add("Contains conditional logic (client-side evaluation not implemented)");
		}
		
		// Check for custom widgets
		if (xmlContent.contains("appui.widget") || xmlContent.contains("custom:")) {
			result.warnings.add("Contains custom widgets (may not be fully supported)");
		}
		
		// Check for uiInclude (JavaScript/CSS)
		if (xmlContent.contains("<uiInclude")) {
			result.warnings.add("Contains UI includes (JavaScript/CSS ignored in Excel export)");
		}
		
		// Check for includeFragment
		if (xmlContent.contains("<includeFragment")) {
			result.warnings.add("Contains fragments (not supported in Excel export)");
		}
		
		// Check for lookup expressions
		if (xmlContent.contains("<lookup")) {
			result.warnings.add("Contains lookup expressions (may not be evaluated)");
		}
		
		// Check for deeply nested obsgroups
		int obsgroupDepth = countObsgroupDepth(xmlContent);
		if (obsgroupDepth > 2) {
			result.warnings.add(String.format("Deep obsgroup nesting (%d levels) - may have issues", obsgroupDepth));
		}
	}
	
	/**
	 * Count maximum depth of nested obsgroups
	 */
	private int countObsgroupDepth(String xmlContent) {
		int maxDepth = 0;
		int currentDepth = 0;
		
		int index = 0;
		while (index < xmlContent.length()) {
			int openTag = xmlContent.indexOf("<obsgroup", index);
			int closeTag = xmlContent.indexOf("</obsgroup>", index);
			
			if (openTag == -1)
				break;
			
			if (closeTag == -1 || openTag < closeTag) {
				currentDepth++;
				maxDepth = Math.max(maxDepth, currentDepth);
				index = openTag + 1;
			} else {
				currentDepth--;
				index = closeTag + 1;
			}
		}
		
		return maxDepth;
	}
	
	/**
	 * Find all HTML form XML files
	 */
	private List<Path> findAllHtmlForms() throws IOException {
		try (Stream<Path> paths = Files.walk(Paths.get(ICRC_FORMS_PATH))) {
			return paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".xml"))
			        .filter(p -> !p.toString().contains(".DS_Store")).collect(Collectors.toList());
		}
	}
	
	/**
	 * Generate detailed test report
	 */
	private void generateTestReport(int successCount, int warningCount, int errorCount) {
		log.info("========================================");
		log.info("ICRC Forms Compatibility Test Report");
		log.info("========================================");
		log.info("Total forms tested: {}", testResults.size());
		log.info("  Success: {} ({} with warnings)", successCount, warningCount);
		log.info("  Errors: {}", errorCount);
		log.info("");
		
		if (errorCount > 0) {
			log.error("Forms with ERRORS:");
			testResults.entrySet().stream().filter(e -> e.getValue().status == TestStatus.ERROR).forEach(e -> {
				TestResult result = e.getValue();
				log.error("  - {}", Paths.get(e.getKey()).getFileName());
				log.error("    Error: {}", result.errorMessage);
			});
			log.info("");
		}
		
		if (warningCount > 0) {
			log.warn("Forms with WARNINGS (parsed but with issues):");
			testResults.entrySet().stream().filter(e -> e.getValue().status == TestStatus.WARNING).limit(10) // Show first 10
			        .forEach(e -> {
				        TestResult result = e.getValue();
				        log.warn("  - {}", Paths.get(e.getKey()).getFileName());
				        result.warnings.forEach(w -> log.warn("    * {}", w));
			        });
			
			if (warningCount > 10) {
				log.warn("  ... and {} more forms with warnings", warningCount - 10);
			}
			log.info("");
		}
		
		// Summary of common issues
		Map<String, Integer> issueCount = new HashMap<>();
		testResults.values().stream().flatMap(r -> r.warnings.stream()).forEach(warning -> {
			String key = warning.split("\\(")[0].trim(); // Group by issue type
			issueCount.put(key, issueCount.getOrDefault(key, 0) + 1);
		});
		
		if (!issueCount.isEmpty()) {
			log.info("Common Issues Summary:");
			issueCount.entrySet().stream().sorted((a, b) -> b.getValue().compareTo(a.getValue()))
			        .forEach(e -> log.info("  {} forms: {}", e.getValue(), e.getKey()));
		}
		
		log.info("========================================");
	}
	
	/**
	 * Create mock Form object for testing
	 */
	private Form createMockForm(String formName) {
		Form form = new Form();
		form.setName(formName);
		form.setVersion("1.0");
		return form;
	}
	
	// Inner classes for test result tracking
	
	private enum TestStatus {
		SUCCESS,
		WARNING,
		ERROR
	}
	
	private static class TestResult {
		
		String formPath;
		
		TestStatus status;
		
		FormModel formModel;
		
		List<String> warnings = new ArrayList<>();
		
		Exception error;
		
		String errorMessage;
	}
}
