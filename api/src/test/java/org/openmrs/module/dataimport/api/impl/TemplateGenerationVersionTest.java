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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openmrs.module.dataimport.api.impl.TemplateGenerationServiceImpl;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@DisplayName("Template Generation Version Tests")
class TemplateGenerationVersionTest {
	
	@Test
	@DisplayName("Should create composite version for multiple forms")
	void testCompositeVersionMultipleForms() throws Exception {
		TemplateGenerationServiceImpl service = new TemplateGenerationServiceImpl(null, null, null, null);
		
		// Test composite version creation using reflection to access private method
		Method createCompositeVersion = TemplateGenerationServiceImpl.class.getDeclaredMethod("createCompositeVersion",
		    List.class);
		createCompositeVersion.setAccessible(true);
		
		List<String> formVersions = Arrays.asList("1.0", "2.1", "1.5");
		String result = (String) createCompositeVersion.invoke(service, formVersions);
		
		assertEquals("1.0,2.1,1.5", result);
	}
	
	@Test
	@DisplayName("Should return single version for single form")
	void testSingleFormVersion() throws Exception {
		TemplateGenerationServiceImpl service = new TemplateGenerationServiceImpl(null, null, null, null);
		
		Method createCompositeVersion = TemplateGenerationServiceImpl.class.getDeclaredMethod("createCompositeVersion",
		    List.class);
		createCompositeVersion.setAccessible(true);
		
		List<String> formVersions = Collections.singletonList("2.1");
		String result = (String) createCompositeVersion.invoke(service, formVersions);
		
		assertEquals("2.1", result);
	}
	
	@Test
	@DisplayName("Should return default version for empty list")
	void testEmptyFormVersions() throws Exception {
		TemplateGenerationServiceImpl service = new TemplateGenerationServiceImpl(null, null, null, null);
		
		Method createCompositeVersion = TemplateGenerationServiceImpl.class.getDeclaredMethod("createCompositeVersion",
		    List.class);
		createCompositeVersion.setAccessible(true);
		
		List<String> formVersions = Collections.emptyList();
		String result = (String) createCompositeVersion.invoke(service, formVersions);
		
		assertEquals("1.0", result);
	}
	
	@Test
	@DisplayName("Should handle null versions gracefully")
	void testNullVersions() throws Exception {
		TemplateGenerationServiceImpl service = new TemplateGenerationServiceImpl(null, null, null, null);
		
		Method createCompositeVersion = TemplateGenerationServiceImpl.class.getDeclaredMethod("createCompositeVersion",
		    List.class);
		createCompositeVersion.setAccessible(true);
		
		List<String> formVersions = Arrays.asList("1.0", null, "2.1");
		String result = (String) createCompositeVersion.invoke(service, formVersions);
		
		assertEquals("1.0,null,2.1", result);
	}
}
