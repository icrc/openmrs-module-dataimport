/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.storage;

import lombok.Getter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing temporary storage of uploaded form files. Files are stored in the OpenMRS
 * app data directory and cleaned up on server restart.
 */
public interface FormFileStorageService {
	
	/**
	 * Stores an uploaded form file and returns a unique identifier.
	 *
	 * @param filename the original filename
	 * @param content the file content
	 * @param formType the detected form type (O3_JSON or HFE_XML)
	 * @return a unique identifier for the stored file (e.g., "file://abc123")
	 * @throws IOException if storage fails
	 */
	String storeFile(String filename, byte[] content, String formType) throws IOException;
	
	/**
	 * Stores multiple uploaded form files.
	 *
	 * @param files list of file data to store
	 * @return list of unique identifiers for the stored files
	 * @throws IOException if storage fails
	 */
	List<String> storeFiles(List<FormFileData> files) throws IOException;
	
	/**
	 * Retrieves the content of a stored file by its identifier.
	 *
	 * @param fileId the file identifier (e.g., "file://abc123" or "abc123")
	 * @return the file content if found
	 */
	Optional<byte[]> getFileContent(String fileId);
	
	/**
	 * Retrieves the file path for a stored file.
	 *
	 * @param fileId the file identifier
	 * @return the file path if found
	 */
	Optional<Path> getFilePath(String fileId);
	
	/**
	 * Retrieves metadata about a stored file.
	 *
	 * @param fileId the file identifier
	 * @return the file metadata if found
	 */
	Optional<FormFileMetadata> getFileMetadata(String fileId);
	
	/**
	 * Checks if a file identifier points to a stored file.
	 *
	 * @param identifier the identifier to check (can be "file://abc123" or "abc123")
	 * @return true if this is a valid file-based identifier
	 */
	boolean isFileBasedIdentifier(String identifier);
	
	/**
	 * Extracts the file ID from an identifier.
	 *
	 * @param identifier the identifier (e.g., "file://abc123")
	 * @return the file ID (e.g., "abc123")
	 */
	String extractFileId(String identifier);
	
	/**
	 * Cleans up all stored files. Called on shutdown.
	 */
	void cleanup();
	
	/**
	 * Data class for file upload.
	 */
	@Getter
	class FormFileData {
		
		private final String filename;
		
		private final byte[] content;
		
		private final String formType;
		
		public FormFileData(String filename, byte[] content, String formType) {
			this.filename = filename;
			this.content = content;
			this.formType = formType;
		}
		
	}
	
	/**
	 * Metadata about a stored file.
	 */
	@Getter
	class FormFileMetadata {
		
		private final String fileId;
		
		private final String originalFilename;
		
		private final String formType;
		
		private final long timestamp;
		
		private final Path filePath;
		
		public FormFileMetadata(String fileId, String originalFilename, String formType, long timestamp, Path filePath) {
			this.fileId = fileId;
			this.originalFilename = originalFilename;
			this.formType = formType;
			this.timestamp = timestamp;
			this.filePath = filePath;
		}
		
	}
}
