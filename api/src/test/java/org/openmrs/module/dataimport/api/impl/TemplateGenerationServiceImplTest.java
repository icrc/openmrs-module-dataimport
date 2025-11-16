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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openmrs.EncounterRole;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.api.EncounterService;
import org.openmrs.api.LocationService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TemplateGenerationServiceImplTest {
	
	@Test
	@DisplayName("Should create composite version for multiple forms")
	void testCompositeVersionMultipleForms() throws Exception {
		TemplateGenerationServiceImpl service = new TemplateGenerationServiceImpl(null, null, null);
		
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
		TemplateGenerationServiceImpl service = new TemplateGenerationServiceImpl(null, null, null);
		
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
		TemplateGenerationServiceImpl service = new TemplateGenerationServiceImpl(null, null, null);
		
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
		TemplateGenerationServiceImpl service = new TemplateGenerationServiceImpl(null, null, null);
		
		Method createCompositeVersion = TemplateGenerationServiceImpl.class.getDeclaredMethod("createCompositeVersion",
		    List.class);
		createCompositeVersion.setAccessible(true);
		
		List<String> formVersions = Arrays.asList("1.0", null, "2.1");
		String result = (String) createCompositeVersion.invoke(service, formVersions);
		
		assertEquals("1.0,null,2.1", result);
	}
	
	private static class TestableTemplateGenerationServiceImpl extends TemplateGenerationServiceImpl {
		
		private EncounterService encounterService;
		
		private ProviderService providerService;
		
		private LocationService locationService;
		
		TestableTemplateGenerationServiceImpl() {
			super(null, null, null);
		}
		
		void setEncounterService(EncounterService encounterService) {
			this.encounterService = encounterService;
		}
		
		void setProviderService(ProviderService providerService) {
			this.providerService = providerService;
		}
		
		void setLocationService(LocationService locationService) {
			this.locationService = locationService;
		}
		
		@Override
		protected EncounterService fetchEncounterService() {
			return encounterService;
		}
		
		@Override
		protected ProviderService fetchProviderService() {
			return providerService;
		}
		
		@Override
		protected LocationService fetchLocationService() {
			return locationService;
		}
	}
	
	@Test
	@DisplayName("Should populate encounter roles for ui-select-extended fields")
	void testPopulateUiSelectExtendedEncounterRoles() throws Exception {
		TestableTemplateGenerationServiceImpl service = new TestableTemplateGenerationServiceImpl();
		FieldModel field = new FieldModel();
		field.datatype = FieldDatatype.CODED;
		field.uiSelectSourceType = "encounterRole";
		
		EncounterRole roleA = new EncounterRole();
		roleA.setUuid("role-a-uuid");
		roleA.setName("Provider");
		
		EncounterRole roleB = new EncounterRole();
		roleB.setUuid("role-b-uuid");
		roleB.setName("Coordinator");
		
		EncounterService encounterService = Mockito.mock(EncounterService.class);
		Mockito.when(encounterService.getAllEncounterRoles(false)).thenReturn(Arrays.asList(roleA, roleB));
		
		Method populate = TemplateGenerationServiceImpl.class.getDeclaredMethod("populateUiSelectExtendedAnswers",
		    FieldModel.class);
		populate.setAccessible(true);
		service.setEncounterService(encounterService);
		populate.invoke(service, field);
		
		assertEquals(2, field.answers.size());
		assertEquals("Coordinator", field.answers.get(0).label); // Sorted alphabetically
		assertEquals("role-b-uuid", field.answers.get(0).conceptUuid);
		assertEquals("role-b-uuid", field.answers.get(0).value);
		assertEquals("Provider", field.answers.get(1).label);
	}
	
	//	@Test
	@DisplayName("Should populate encounter providers for ui-select-extended fields")
	void testPopulateUiSelectExtendedEncounterProviders() throws Exception {
		TestableTemplateGenerationServiceImpl service = new TestableTemplateGenerationServiceImpl();
		FieldModel field = new FieldModel();
		field.datatype = FieldDatatype.CODED;
		field.uiSelectSourceType = "encounterProvider";
		
		Provider providerA = new Provider();
		providerA.setUuid("provider-a");
		Person providerAPerson = new Person();
		providerAPerson.addName(new PersonName("Zebra", "", "Doctor"));
		providerA.setPerson(providerAPerson);
		
		Provider providerB = new Provider();
		providerB.setUuid("provider-b");
		Person providerBPerson = new Person();
		providerBPerson.addName(new PersonName("Alpha", "", "Doctor"));
		providerB.setPerson(providerBPerson);
		
		ProviderService providerService = Mockito.mock(ProviderService.class);
		Mockito.when(providerService.getAllProviders(false)).thenReturn(Arrays.asList(providerA, providerB));
		
		Method populate = TemplateGenerationServiceImpl.class.getDeclaredMethod("populateUiSelectExtendedAnswers",
		    FieldModel.class);
		populate.setAccessible(true);
		service.setProviderService(providerService);
		populate.invoke(service, field);
		
		assertEquals(2, field.answers.size());
		assertEquals("Alpha Doctor", field.answers.get(0).label);
		assertEquals("provider-b", field.answers.get(0).conceptUuid);
		assertEquals("provider-b", field.answers.get(0).value);
		assertEquals("Dr. Zebra", field.answers.get(1).label);
	}
	
	@Test
	@DisplayName("Should populate encounter locations for ui-select-extended fields")
	void testPopulateUiSelectExtendedEncounterLocations() throws Exception {
		TestableTemplateGenerationServiceImpl service = new TestableTemplateGenerationServiceImpl();
		FieldModel field = new FieldModel();
		field.datatype = FieldDatatype.CODED;
		field.uiSelectSourceType = "encounterLocation";
		
		Location locationA = new Location();
		locationA.setUuid("loc-a");
		locationA.setName("OPD");
		
		Location locationB = new Location();
		locationB.setUuid("loc-b");
		locationB.setName("Emergency");
		
		LocationService locationService = Mockito.mock(LocationService.class);
		Mockito.when(locationService.getAllLocations(false)).thenReturn(Arrays.asList(locationA, locationB));
		
		Method populate = TemplateGenerationServiceImpl.class.getDeclaredMethod("populateUiSelectExtendedAnswers",
		    FieldModel.class);
		populate.setAccessible(true);
		service.setLocationService(locationService);
		populate.invoke(service, field);
		
		assertEquals(2, field.answers.size());
		assertEquals("Emergency", field.answers.get(0).label);
		assertEquals("loc-b", field.answers.get(0).conceptUuid);
		assertEquals("loc-b", field.answers.get(0).value);
		assertEquals("OPD", field.answers.get(1).label);
	}
	
	@Test
	@DisplayName("Should honour location datasource tag filter for ui-select-extended fields")
	void testPopulateUiSelectExtendedLocationDatasourceWithTag() throws Exception {
		TestableTemplateGenerationServiceImpl service = new TestableTemplateGenerationServiceImpl();
		FieldModel field = new FieldModel();
		field.datatype = FieldDatatype.CODED;
		field.uiSelectDatasourceName = "location_datasource";
		field.uiSelectDatasourceConfig.put("tag", "Admission Location");
		
		LocationTag admissionTag = new LocationTag();
		admissionTag.setName("Admission Location");
		
		LocationTag screeningTag = new LocationTag();
		screeningTag.setName("Screening");
		
		Location locationAdmission = new Location();
		locationAdmission.setUuid("loc-admission");
		locationAdmission.setName("Ward A");
		locationAdmission.addTag(admissionTag);
		
		Location locationScreening = new Location();
		locationScreening.setUuid("loc-screening");
		locationScreening.setName("Screening");
		locationScreening.addTag(screeningTag);
		
		Location locationAdmission2 = new Location();
		locationAdmission2.setUuid("loc-admission-b");
		locationAdmission2.setName("Ward B");
		locationAdmission2.addTag(admissionTag);
		
		LocationService locationService = Mockito.mock(LocationService.class);
		Mockito.when(locationService.getAllLocations(false))
		        .thenReturn(Arrays.asList(locationAdmission, locationScreening, locationAdmission2));
		
		Method populate = TemplateGenerationServiceImpl.class.getDeclaredMethod("populateUiSelectExtendedAnswers",
		    FieldModel.class);
		populate.setAccessible(true);
		service.setLocationService(locationService);
		populate.invoke(service, field);
		
		assertEquals(2, field.answers.size());
		assertEquals("Ward A", field.answers.get(0).label);
		assertEquals("Ward B", field.answers.get(1).label);
	}
}
