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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.api.resolver.FileBasedFormResolver;
import org.openmrs.module.dataimport.api.resolver.FormResolverRegistry;
import org.openmrs.module.dataimport.api.storage.FormFileStorageService;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.FormType;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Integration test for file-based form upload and resolution.
 */
public class FileBasedFormIntegrationTest {
	
	private FormFileStorageServiceImpl storageService;
	
	private FileBasedFormResolver fileResolver;
	
	private FormResolverRegistry registry;
	
	@BeforeEach
	public void setUp() {
		storageService = new FormFileStorageServiceImpl();
		
		CoreConceptAnswersServiceImpl conceptAnswersService = new CoreConceptAnswersServiceImpl();
		
		O3FormParser o3Parser = new O3FormParser(conceptAnswersService);
		HfeFormParser hfeParser = new HfeFormParser(conceptAnswersService);
		
		fileResolver = new FileBasedFormResolver(storageService, o3Parser, hfeParser);
	}
	
	@Test
	public void testO3JsonFormUploadAndResolve() throws Exception {
		// Sample O3 JSON form
		String o3Json = "{\n" + "  \"name\": \"Test O3 Form\",\n" + "  \"uuid\": \"test-o3-form-uuid\",\n"
		        + "  \"version\": \"1.0\",\n" + "  \"encounterType\": \"encounter-type-uuid\",\n" + "  \"pages\": [\n"
		        + "    {\n" + "      \"label\": \"Page 1\",\n" + "      \"sections\": [\n" + "        {\n"
		        + "          \"label\": \"Section 1\",\n" + "          \"questions\": [\n" + "            {\n"
		        + "              \"id\": \"q1\",\n" + "              \"label\": \"Patient Name\",\n"
		        + "              \"type\": \"obs\",\n"
		        + "              \"questionOptions\": { \"concept\": \"concept-uuid-1\", \"rendering\": \"text\" }\n"
		        + "            }\n" + "          ]\n" + "        }\n" + "      ]\n" + "    }\n" + "  ]\n" + "}";
		
		byte[] content = o3Json.getBytes(StandardCharsets.UTF_8);
		
		// Store file
		String fileId = storageService.storeFile("test-o3-form.json", content, "O3_JSON");
		
		assertNotNull(fileId);
		assertTrue(fileId.startsWith("file://"));
		
		// Verify storage service recognizes it
		assertTrue(storageService.isFileBasedIdentifier(fileId));
		
		// Verify resolver can handle it
		assertTrue(fileResolver.canResolve(fileId));
		
		// Resolve the form
		FormModel formModel = fileResolver.resolve(fileId, Locale.ENGLISH);
		
		assertNotNull(formModel);
		assertEquals(FormType.O3_JSON, formModel.formType);
		assertEquals("Test O3 Form", formModel.name);
		assertEquals("1.0", formModel.version);
		assertNotNull(formModel.sheets);
		assertTrue(formModel.sheets.size() > 0);
		
		// Cleanup
		storageService.cleanup();
	}
	
	@Test
	public void testHfeXmlFormUploadAndResolve() throws Exception {
		// Sample HFE XML form with metadata attributes
		String hfeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
		        + "<htmlform formUuid=\"test-hfe-uuid\" formName=\"Test HFE Form\" formVersion=\"2.0\" formEncounterType=\"encounter-type-uuid\">\n"
		        + "  <obs conceptId=\"5089\" labelText=\"Weight (kg)\" />\n"
		        + "  <obs conceptId=\"5090\" labelText=\"Height (cm)\" />\n" + "</htmlform>";
		
		byte[] content = hfeXml.getBytes(StandardCharsets.UTF_8);
		
		// Store file
		String fileId = storageService.storeFile("test-hfe-form.xml", content, "HFE_XML");
		
		assertNotNull(fileId);
		assertTrue(fileId.startsWith("file://"));
		
		// Verify storage service recognizes it
		assertTrue(storageService.isFileBasedIdentifier(fileId));
		
		// Verify resolver can handle it
		assertTrue(fileResolver.canResolve(fileId));
		
		// Test full resolution - now works with mock Form object
		FormModel formModel = fileResolver.resolve(fileId, Locale.ENGLISH);
		
		assertNotNull(formModel);
		assertEquals(FormType.HFE_XML, formModel.formType);
		assertEquals("test-hfe-uuid", formModel.formUuid);
		assertEquals("Test HFE Form", formModel.name);
		assertEquals("2.0", formModel.version);
		assertEquals("encounter-type-uuid", formModel.encounterTypeUuid);
		
		// Cleanup
		storageService.cleanup();
	}
	
	@Test
	public void testHfeXmlFormWithoutMetadata() throws Exception {
		// HFE form without metadata attributes - should use fallbacks
		String hfeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<htmlform>\n"
		        + "  <obs conceptId=\"5089\" labelText=\"Weight (kg)\" />\n" + "</htmlform>";
		
		byte[] content = hfeXml.getBytes(StandardCharsets.UTF_8);
		
		// Store file
		String fileId = storageService.storeFile("vitals-form.xml", content, "HFE_XML");
		
		assertNotNull(fileId);
		
		// Resolve the form
		FormModel formModel = fileResolver.resolve(fileId, Locale.ENGLISH);
		
		assertNotNull(formModel);
		assertEquals(FormType.HFE_XML, formModel.formType);
		
		// Should use file ID as UUID fallback
		assertTrue(formModel.formUuid.startsWith("file-"));
		
		// Should use filename as name fallback
		assertEquals("vitals-form", formModel.name);
		
		// Should use default version
		assertEquals("1.0", formModel.version);
		
		// Cleanup
		storageService.cleanup();
	}
	
	@Test
	public void testBatchFileUpload() throws Exception {
		String o3Json = "{\n" + "  \"name\": \"Form 1\",\n" + "  \"pages\": []\n" + "}";
		
		String hfeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<htmlform>\n"
		        + "  <obs conceptId=\"5089\" labelText=\"Weight\" />\n" + "</htmlform>";
		
		java.util.List<FormFileStorageService.FormFileData> files = java.util.Arrays.asList(
		    new FormFileStorageService.FormFileData("form1.json", o3Json.getBytes(StandardCharsets.UTF_8), "O3_JSON"),
		    new FormFileStorageService.FormFileData("form2.xml", hfeXml.getBytes(StandardCharsets.UTF_8), "HFE_XML"));
		
		java.util.List<String> fileIds = storageService.storeFiles(files);
		
		assertEquals(2, fileIds.size());
		assertTrue(fileIds.get(0).startsWith("file://"));
		assertTrue(fileIds.get(1).startsWith("file://"));
		
		// Verify both can be resolved
		assertTrue(fileResolver.canResolve(fileIds.get(0)));
		assertTrue(fileResolver.canResolve(fileIds.get(1)));
		
		// Cleanup
		storageService.cleanup();
	}
	
	@Test
	public void testFileIdExtraction() {
		String fileId = "file://abc-123-def";
		String extracted = storageService.extractFileId(fileId);
		assertEquals("abc-123-def", extracted);
		
		String rawId = "xyz-456";
		String extractedRaw = storageService.extractFileId(rawId);
		assertEquals("xyz-456", extractedRaw);
	}
}
