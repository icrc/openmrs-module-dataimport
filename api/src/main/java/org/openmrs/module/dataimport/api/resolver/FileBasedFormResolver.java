/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.resolver;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.api.APIException;
import org.openmrs.module.dataimport.api.impl.HfeFormParser;
import org.openmrs.module.dataimport.api.impl.O3FormParser;
import org.openmrs.module.dataimport.api.storage.FormFileStorageService;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.FormType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Element;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * FormResolver implementation that resolves forms from uploaded files. This resolver handles
 * identifiers in the format "file://[id]" where [id] is a UUID assigned when the form file was
 * uploaded.
 */
public class FileBasedFormResolver implements FormResolver {
	
	private static final Logger log = LoggerFactory.getLogger(FileBasedFormResolver.class);
	
	private final FormFileStorageService storageService;
	
	private final O3FormParser o3Parser;
	
	private final HfeFormParser hfeParser;
	
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	@Autowired
	public FileBasedFormResolver(FormFileStorageService storageService, O3FormParser o3Parser, HfeFormParser hfeParser) {
		this.storageService = storageService;
		this.o3Parser = o3Parser;
		this.hfeParser = hfeParser;
	}
	
	@Override
	public FormType getFormType() {
		// This resolver handles both types, determined at runtime
		return null;
	}
	
	@Override
	public boolean canResolve(String formIdentifier) {
		return storageService.isFileBasedIdentifier(formIdentifier);
	}
	
	@Override
	public FormModel resolve(String formIdentifier, Locale locale) {
		if (!canResolve(formIdentifier)) {
			throw new APIException("Not a file-based identifier: " + formIdentifier);
		}
		
		Optional<byte[]> contentOpt = storageService.getFileContent(formIdentifier);
		if (!contentOpt.isPresent()) {
			throw new APIException("Form file not found for identifier: " + formIdentifier);
		}
		
		byte[] content = contentOpt.get();
		String contentString = new String(content, StandardCharsets.UTF_8);
		
		FormType detectedType = detectFormType(contentString);
		FormModel model;
		
		if (detectedType == FormType.O3_JSON) {
			log.info("Parsing file-based form as O3 JSON: {}", formIdentifier);
			model = o3Parser.parse(contentString, locale);
			model.formType = FormType.O3_JSON;
		} else if (detectedType == FormType.HFE_XML) {
			log.info("Parsing file-based form as HFE XML: {}", formIdentifier);
			// Create a mock Form object from HFE XML metadata
			Form mockForm = createMockFormFromHfeXml(contentString, formIdentifier);
			model = hfeParser.parse(contentString, mockForm, locale);
			model.formType = FormType.HFE_XML;
		} else {
			throw new APIException("Unable to detect form type for: " + formIdentifier);
		}
		
		if (model.formUuid == null || model.formUuid.trim().isEmpty()) {
			String fileId = storageService.extractFileId(formIdentifier);
			model.formUuid = "file-" + fileId;
		}
		
		if (model.name == null || model.name.trim().isEmpty()) {
			Optional<FormFileStorageService.FormFileMetadata> metadataOpt = storageService.getFileMetadata(formIdentifier);
			metadataOpt.ifPresent(metadata -> {
				String filename = metadata.getOriginalFilename();
				// Strip extension
				int dotIndex = filename.lastIndexOf('.');
				model.name = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
			});
		}
		
		if (model.version == null || model.version.trim().isEmpty()) {
			model.version = "1.0";
		}
		
		log.info("Successfully resolved file-based form: {} (type={}, name={})", formIdentifier, model.formType, model.name);
		
		return model;
	}
	
	@Override
	public String getFormName(String formIdentifier) {
		Optional<FormFileStorageService.FormFileMetadata> metadataOpt = storageService.getFileMetadata(formIdentifier);
		if (metadataOpt.isPresent()) {
			String filename = metadataOpt.get().getOriginalFilename();
			int dotIndex = filename.lastIndexOf('.');
			return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
		}
		return "Unknown Form";
	}
	
	@Override
	public String getFormVersion(String formIdentifier) {
		return "1.0";
	}
	
	/**
	 * Creates a mock Form object from HFE XML metadata. Extracts formUuid, formName, formVersion, and
	 * formEncounterType from the root element attributes. Falls back to file metadata and defaults when
	 * attributes are not present.
	 */
	private Form createMockFormFromHfeXml(String hfeXml, String formIdentifier) {
		Form mockForm = new Form();
		
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// Security: disable external entities
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			
			DocumentBuilder builder = factory.newDocumentBuilder();
			org.w3c.dom.Document doc = builder.parse(new ByteArrayInputStream(hfeXml.getBytes(StandardCharsets.UTF_8)));
			Element rootElement = doc.getDocumentElement();
			
			// Extract metadata from attributes
			String formUuid = rootElement.getAttribute("formUuid");
			String formName = rootElement.getAttribute("formName");
			String formVersion = rootElement.getAttribute("formVersion");
			String encounterTypeUuid = rootElement.getAttribute("formEncounterType");
			
			// Set UUID
			if (formUuid != null && !formUuid.trim().isEmpty()) {
				mockForm.setUuid(formUuid.trim());
			} else {
				// Generate UUID from file identifier
				String fileId = storageService.extractFileId(formIdentifier);
				mockForm.setUuid("file-" + fileId);
			}
			
			// Set name
			if (formName != null && !formName.trim().isEmpty()) {
				mockForm.setName(formName.trim());
			} else {
				// Fall back to filename
				Optional<FormFileStorageService.FormFileMetadata> metadataOpt = storageService
				        .getFileMetadata(formIdentifier);
				if (metadataOpt.isPresent()) {
					String filename = metadataOpt.get().getOriginalFilename();
					int dotIndex = filename.lastIndexOf('.');
					mockForm.setName(dotIndex > 0 ? filename.substring(0, dotIndex) : filename);
				} else {
					mockForm.setName("Uploaded Form");
				}
			}
			
			// Set version
			if (formVersion != null && !formVersion.trim().isEmpty()) {
				mockForm.setVersion(formVersion.trim());
			} else {
				mockForm.setVersion("1.0");
			}
			
			// Set encounter type if present
			if (encounterTypeUuid != null && !encounterTypeUuid.trim().isEmpty()) {
				EncounterType encounterType = new EncounterType();
				encounterType.setUuid(encounterTypeUuid.trim());
				mockForm.setEncounterType(encounterType);
			}
			
			log.debug("Created mock Form from HFE XML: uuid={}, name={}, version={}, encounterType={}", mockForm.getUuid(),
			    mockForm.getName(), mockForm.getVersion(),
			    mockForm.getEncounterType() != null ? mockForm.getEncounterType().getUuid() : "null");
			
		}
		catch (Exception e) {
			log.warn("Failed to parse HFE XML metadata, using defaults: {}", e.getMessage());
			// Set minimal defaults
			String fileId = storageService.extractFileId(formIdentifier);
			mockForm.setUuid("file-" + fileId);
			mockForm.setName("Uploaded Form");
			mockForm.setVersion("1.0");
		}
		
		return mockForm;
	}
	
	/**
	 * Auto-detects the form type from file content. Tries JSON parsing first, then XML parsing.
	 */
	private FormType detectFormType(String content) {
		String trimmed = content.trim();
		
		// Try JSON detection
		if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
			try {
				JsonNode root = objectMapper.readTree(content);
				// Check for O3 form structure (has "pages" array)
				if (root.has("pages") && root.get("pages").isArray()) {
					return FormType.O3_JSON;
				}
				// Could be other JSON format
				log.warn("JSON detected but doesn't match O3 schema structure");
			}
			catch (Exception e) {
				log.debug("Failed to parse as JSON: {}", e.getMessage());
			}
		}
		
		// Try XML detection
		if (trimmed.startsWith("<")) {
			try {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				// Security: disable external entities
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
				factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
				factory.setXIncludeAware(false);
				factory.setExpandEntityReferences(false);
				
				DocumentBuilder builder = factory.newDocumentBuilder();
				org.w3c.dom.Document doc = builder.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
				
				// Check for HFE root element
				String rootTag = doc.getDocumentElement().getTagName();
				if ("htmlform".equalsIgnoreCase(rootTag)) {
					return FormType.HFE_XML;
				}
				log.warn("XML detected but root tag is '{}', expected 'htmlform'", rootTag);
			}
			catch (Exception e) {
				log.debug("Failed to parse as XML: {}", e.getMessage());
			}
		}
		
		return null;
	}
}
