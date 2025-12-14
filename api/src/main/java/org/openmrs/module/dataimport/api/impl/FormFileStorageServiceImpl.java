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

import javax.annotation.PreDestroy;

import org.openmrs.module.dataimport.api.storage.FormFileStorageService;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of FormFileStorageService that stores files in OpenMRS app data directory. Files
 * are stored under {openmrs.appDataDirectory}/dataimport/uploaded-forms/ and tracked in-memory
 * until server restart.
 */
@Service("dataimport.FormFileStorageService")
public class FormFileStorageServiceImpl implements FormFileStorageService {
	
	private static final Logger log = LoggerFactory.getLogger(FormFileStorageServiceImpl.class);
	
	private static final String STORAGE_SUBDIR = "dataimport/uploaded-forms";
	
	private static final String FILE_ID_PREFIX = "file://";
	
	private final Map<String, FormFileMetadata> fileRegistry = new ConcurrentHashMap<>();
	
	private Path storageDirectory;
	
	private synchronized void initializeStorageDirectory() throws IOException {
		if (storageDirectory == null) {
			String appDataDirPath = OpenmrsUtil.getApplicationDataDirectory();
			storageDirectory = Paths.get(appDataDirPath, STORAGE_SUBDIR);
			
			if (!Files.exists(storageDirectory)) {
				Files.createDirectories(storageDirectory);
				log.info("Created form file storage directory: {}", storageDirectory);
			}
		}
	}
	
	@Override
	public String storeFile(String filename, byte[] content, String formType) throws IOException {
		initializeStorageDirectory();
		
		String fileId = UUID.randomUUID().toString();
		String safeFilename = sanitizeFilename(filename);
		String storedFilename = fileId + "_" + safeFilename;
		Path filePath = storageDirectory.resolve(storedFilename);
		
		Files.write(filePath, content);
		
		FormFileMetadata metadata = new FormFileMetadata(fileId, filename, formType, System.currentTimeMillis(), filePath);
		fileRegistry.put(fileId, metadata);
		
		log.info("Stored form file: {} (type={}, size={} bytes) with ID: {}", filename, formType, content.length, fileId);
		
		return FILE_ID_PREFIX + fileId;
	}
	
	@Override
	public List<String> storeFiles(List<FormFileData> files) throws IOException {
		List<String> fileIds = new ArrayList<>();
		for (FormFileData file : files) {
			String fileId = storeFile(file.getFilename(), file.getContent(), file.getFormType());
			fileIds.add(fileId);
		}
		return fileIds;
	}
	
	@Override
	public Optional<byte[]> getFileContent(String fileId) {
		String id = extractFileId(fileId);
		FormFileMetadata metadata = fileRegistry.get(id);
		
		if (metadata == null) {
			log.warn("File not found in registry: {}", fileId);
			return Optional.empty();
		}
		
		try {
			byte[] content = Files.readAllBytes(metadata.getFilePath());
			return Optional.of(content);
		}
		catch (IOException e) {
			log.error("Failed to read file: {}", metadata.getFilePath(), e);
			return Optional.empty();
		}
	}
	
	@Override
	public Optional<Path> getFilePath(String fileId) {
		String id = extractFileId(fileId);
		FormFileMetadata metadata = fileRegistry.get(id);
		return Optional.ofNullable(metadata).map(FormFileMetadata::getFilePath);
	}
	
	@Override
	public Optional<FormFileMetadata> getFileMetadata(String fileId) {
		String id = extractFileId(fileId);
		return Optional.ofNullable(fileRegistry.get(id));
	}
	
	@Override
	public boolean isFileBasedIdentifier(String identifier) {
		if (identifier == null) {
			return false;
		}
		// Check if it starts with "file://" or if it's a UUID that exists in registry
		if (identifier.startsWith(FILE_ID_PREFIX)) {
			String id = identifier.substring(FILE_ID_PREFIX.length());
			return fileRegistry.containsKey(id);
		}
		// Also check if it's a raw UUID in the registry
		return fileRegistry.containsKey(identifier);
	}
	
	@Override
	public String extractFileId(String identifier) {
		if (identifier == null) {
			return null;
		}
		return identifier.startsWith(FILE_ID_PREFIX) ? identifier.substring(FILE_ID_PREFIX.length()) : identifier;
	}
	
	@Override
	@PreDestroy
	public void cleanup() {
		log.info("Cleaning up {} uploaded form files", fileRegistry.size());
		
		for (FormFileMetadata metadata : fileRegistry.values()) {
			try {
				Files.deleteIfExists(metadata.getFilePath());
				log.debug("Deleted file: {}", metadata.getFilePath());
			}
			catch (IOException e) {
				log.warn("Failed to delete file: {}", metadata.getFilePath(), e);
			}
		}
		
		fileRegistry.clear();
	}
	
	private String sanitizeFilename(String filename) {
		if (filename == null) {
			return "unknown";
		}
		String name = Paths.get(filename).getFileName().toString();
		return name.replaceAll("[^a-zA-Z0-9._-]", "_");
	}
}
