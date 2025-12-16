/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.web.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.service.TemplateGenerationService;
import org.openmrs.module.dataimport.api.service.WorkbookImportService;
import org.openmrs.module.dataimport.model.TemplateOptions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

public class FormTemplateControllerTest {
	
	private TemplateGenerationService templateGenerationService;
	
	private FormTemplateController controller;
	
	@Before
	public void setUp() throws Exception {
		templateGenerationService = mock(TemplateGenerationService.class);
		when(templateGenerationService.generateFromFormUuids(anyList(), any())).thenReturn(new byte[] { 1, 2, 3 });
		
		controller = new FormTemplateController();
		Field field = FormTemplateController.class.getDeclaredField("templateGenerationService");
		field.setAccessible(true);
		field.set(controller, templateGenerationService);
	}
	
	/**
	 * @see FormTemplateController
	 */
	@Test
	public void generateTemplateFromPost_shouldUseCustomFilenameWhenProvided() {
		FormTemplateController.TemplateRequest request = new FormTemplateController.TemplateRequest();
		request.formUuids = Collections.singletonList("form-uuid");
		TemplateOptions options = new TemplateOptions();
		options.setFilename("custom-template.xlsx");
		request.options = options;
		
		ResponseEntity<byte[]> response = controller.generateTemplateFromPost(request);
		
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION),
		    equalTo("attachment; filename=\"custom-template.xlsx\""));
	}
	
	/**
	 * @see FormTemplateController
	 */
	@Test
	public void generateTemplateFromPost_shouldFallbackToDefaultFilenameWhenBlank() {
		FormTemplateController.TemplateRequest request = new FormTemplateController.TemplateRequest();
		request.formUuids = Collections.singletonList("form-uuid");
		TemplateOptions options = new TemplateOptions();
		options.setFilename("   ");
		request.options = options;
		
		ResponseEntity<byte[]> response = controller.generateTemplateFromPost(request);
		
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION),
		    equalTo("attachment; filename=\"o3_form_template.xlsx\""));
	}
	
	/**
	 * @see FormTemplateController
	 */
	@Test
	public void generateTemplateFromPost_shouldFallbackToDefaultFilenameWhenOptionsNotProvided() {
		FormTemplateController.TemplateRequest request = new FormTemplateController.TemplateRequest();
		request.formUuids = Collections.singletonList("form-uuid");
		request.options = null;
		
		ResponseEntity<byte[]> response = controller.generateTemplateFromPost(request);
		
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION),
		    equalTo("attachment; filename=\"o3_form_template.xlsx\""));
	}
	
	/**
	 * @see FormTemplateController
	 */
	@Test
	public void downloadQuarantineReport_shouldGenerateReportWithExpectedHeaders() throws Exception {
		UUID batchId = UUID.randomUUID();
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		InputStream inputStream = new ByteArrayInputStream(new byte[] { 1, 2, 3 });
		when(file.getInputStream()).thenReturn(inputStream);
		
		byte[] reportBytes = new byte[] { 4, 5, 6 };
		WorkbookImportService workbookImportService = mock(WorkbookImportService.class);
		when(workbookImportService.generateQuarantineReport(any(InputStream.class), eq(batchId))).thenReturn(reportBytes);
		
		try (MockedStatic<Context> mockedContext = Mockito.mockStatic(Context.class)) {
			mockedContext.when(() -> Context.getRegisteredComponent("workbookImportService", WorkbookImportService.class))
			        .thenReturn(workbookImportService);
			
			ResponseEntity<byte[]> response = controller.downloadQuarantineReport(file, batchId.toString());
			
			assertThat(response.getStatusCodeValue(), is(200));
			assertThat(response.getBody(), equalTo(reportBytes));
			assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION),
			    equalTo("attachment; filename=\"quarantine_report_" + batchId.toString() + ".xlsx\""));
			assertThat(response.getHeaders().getContentType(),
			    equalTo(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
			assertThat(response.getHeaders().getContentLength(), is((long) reportBytes.length));
			
			mockedContext.verify(() -> Context.getRegisteredComponent("workbookImportService", WorkbookImportService.class));
		}
	}
	
	/**
	 * @see FormTemplateController
	 */
	@Test
	public void downloadQuarantineReport_shouldThrowWhenFileMissing() {
		assertThrows(APIException.class, () -> controller.downloadQuarantineReport(null, UUID.randomUUID().toString()));
	}
	
	/**
	 * @see FormTemplateController
	 */
	@Test
	public void downloadQuarantineReport_shouldThrowWhenFileEmpty() throws Exception {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(true);
		
		assertThrows(APIException.class, () -> controller.downloadQuarantineReport(file, UUID.randomUUID().toString()));
	}
	
	/**
	 * @see FormTemplateController
	 */
	@Test
	public void downloadQuarantineReport_shouldThrowWhenBatchIdMissing() throws Exception {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		
		assertThrows(APIException.class, () -> controller.downloadQuarantineReport(file, ""));
	}
	
	/**
	 * @see FormTemplateController
	 */
	@Test
	public void downloadQuarantineReport_shouldThrowWhenServiceNotAvailable() throws Exception {
		UUID batchId = UUID.randomUUID();
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 1 }));
		
		try (MockedStatic<Context> mockedContext = Mockito.mockStatic(Context.class)) {
			mockedContext.when(() -> Context.getRegisteredComponent("workbookImportService", WorkbookImportService.class))
			        .thenReturn(null);
			
			assertThrows(APIException.class, () -> controller.downloadQuarantineReport(file, batchId.toString()));
			
			mockedContext.verify(() -> Context.getRegisteredComponent("workbookImportService", WorkbookImportService.class));
		}
	}
	
	/**
	 * @see FormTemplateController
	 */
	@Test
	public void downloadQuarantineReport_shouldThrowWhenBatchIdInvalid() throws Exception {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		
		assertThrows(APIException.class, () -> controller.downloadQuarantineReport(file, "invalid-uuid"));
	}
	
	/**
	 * Tests that multi-location options are properly passed to the service.
	 * 
	 * @see FormTemplateController#generateTemplateFromPost(FormTemplateController.TemplateRequest)
	 */
	@Test
	public void generateTemplateFromPost_shouldPassMultiLocationOptionsToService() {
		FormTemplateController.TemplateRequest request = new FormTemplateController.TemplateRequest();
		request.formUuids = Collections.singletonList("form-uuid");
		TemplateOptions options = new TemplateOptions();
		options.locationUuid = "default-loc";
		options.locationUuids = Arrays.asList("loc-1", "loc-2", "loc-3");
		options.locationTagFilter = "Visit Location";
		options.restrictToAllowedLocations = true;
		request.options = options;
		
		controller.generateTemplateFromPost(request);
		
		verify(templateGenerationService).generateFromFormUuids(eq(Collections.singletonList("form-uuid")),
		    argThat(opts -> "default-loc".equals(opts.locationUuid) && opts.locationUuids.size() == 3
		            && opts.locationUuids.contains("loc-1") && "Visit Location".equals(opts.locationTagFilter)
		            && opts.restrictToAllowedLocations));
	}
	
	/**
	 * Tests that empty locationUuids list is properly handled.
	 * 
	 * @see FormTemplateController#generateTemplateFromPost(FormTemplateController.TemplateRequest)
	 */
	@Test
	public void generateTemplateFromPost_shouldHandleEmptyLocationUuids() {
		FormTemplateController.TemplateRequest request = new FormTemplateController.TemplateRequest();
		request.formUuids = Collections.singletonList("form-uuid");
		TemplateOptions options = new TemplateOptions();
		options.locationUuids = Collections.emptyList();
		options.restrictToAllowedLocations = false;
		request.options = options;
		
		controller.generateTemplateFromPost(request);
		
		verify(templateGenerationService).generateFromFormUuids(eq(Collections.singletonList("form-uuid")),
		    argThat(opts -> opts.locationUuids.isEmpty() && !opts.restrictToAllowedLocations));
	}
}
