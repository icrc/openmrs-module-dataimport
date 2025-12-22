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

import org.openmrs.EncounterRole;
import org.openmrs.Location;
import org.openmrs.Provider;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.api.LocationService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.resolver.FormResolver;
import org.openmrs.module.dataimport.api.resolver.FormResolverRegistry;
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.openmrs.module.dataimport.api.service.TemplateGenerationService;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.SheetModel;
import org.openmrs.module.dataimport.model.TemplateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TemplateGenerationServiceImpl implements TemplateGenerationService {
	
	private static final Logger log = LoggerFactory.getLogger(TemplateGenerationServiceImpl.class);
	
	/** Character limit for Excel dropdown validation (approximate) */
	private static final int EXCEL_DROPDOWN_CHAR_LIMIT = 30000;
	
	private final FormResolverRegistry resolverRegistry;
	
	private final ExcelTemplateExporter exporter;
	
	private final ConceptAnswersService conceptAnswersService;
	
	/** Thread-local storage for TemplateOptions during template generation */
	private ThreadLocal<TemplateOptions> currentOptions = new ThreadLocal<>();
	
	@Autowired
	public TemplateGenerationServiceImpl(FormResolverRegistry resolverRegistry, ExcelTemplateExporter exporter,
	    ConceptAnswersService conceptAnswersProvider) {
		this.resolverRegistry = resolverRegistry;
		this.exporter = exporter;
		this.conceptAnswersService = conceptAnswersProvider;
	}
	
	@Override
	public byte[] generateFromFormUuids(List<String> formUuids, TemplateOptions opts) {
		if (formUuids == null || formUuids.isEmpty()) {
			throw new APIException("At least one form UUID is required");
		}
		
		if (opts == null) {
			throw new APIException("Template options cannot be null");
		}
		
		// Store current options in ThreadLocal for use during form enrichment
		currentOptions.set(opts);
		
		try {
			FormModel merged = new FormModel();
			merged.name = "Template";
			
			List<String> formVersions = new ArrayList<>();
			
			for (String uuid : formUuids) {
				try {
					FormResolver resolver = resolverRegistry.findResolver(uuid);
					if (resolver == null) {
						throw new APIException("No compatible form resolver found for form " + uuid
						        + ". Ensure the form has either a JSON schema (O3) or htmlform resource (HFE).");
					}
					
					String resolverType = resolver.getFormType() != null ? resolver.getFormType().getCode() : "MULTI-TYPE";
					log.debug("Using {} resolver for form {}", resolverType, uuid);
					
					FormModel formModel = resolver.resolve(uuid, null);
					enrichFormWithConceptAnswers(formModel);
					
					String formVersion = formModel.version != null ? formModel.version : "1.0";
					formVersions.add(formVersion);
					
					for (SheetModel sheet : formModel.sheets) {
						sheet.formUuid = formModel.formUuid;
						sheet.formVersion = formVersion;
						sheet.encounterTypeUuid = formModel.encounterTypeUuid;
						sheet.formType = formModel.formType;
					}
					
					if (merged.encounterTypeUuid == null) {
						merged.encounterTypeUuid = formModel.encounterTypeUuid;
					}
					merged.sheets.addAll(formModel.sheets);
					merged.rules.addAll(formModel.rules);
					if (merged.formUuid == null) {
						merged.formUuid = formModel.formUuid;
					}
					
				}
				catch (APIException e) {
					log.error(
					    "Error processing form {}: {}. Tip: verify form resources under Administration → Forms or REST /form/{}?v=full",
					    uuid, e.getMessage(), uuid);
					throw e;
				}
				catch (Exception e) {
					throw new APIException("Error processing form " + uuid + ": " + e.getMessage(), e);
				}
			}
			
			merged.version = createCompositeVersion(formVersions);
			
			try {
				return exporter.export(merged, opts);
			}
			catch (RuntimeException e) {
				throw new APIException("Failed to generate Excel template: " + e.getMessage(), e);
			}
		}
		finally {
			// Clean up ThreadLocal
			currentOptions.remove();
		}
	}
	
	private void enrichFormWithConceptAnswers(FormModel form) {
		for (SheetModel sheet : form.sheets) {
			for (FieldModel field : sheet.fields) {
				if (field.datatype == FieldDatatype.CODED) {
					enrichCodedField(field);
				}
			}
		}
	}
	
	private void enrichCodedField(FieldModel field) {
		loadAnswersIfNeeded(field);
		enrichAnswerLabels(field);
	}
	
	private void loadAnswersIfNeeded(FieldModel field) {
		if (!field.answers.isEmpty()) {
			return;
		}
		
		if (hasUiSelectSource(field)) {
			populateUiSelectExtendedAnswers(field);
		} else if (hasConceptUuid(field)) {
			loadAnswersFromConcept(field);
		}
	}
	
	private void enrichAnswerLabels(FieldModel field) {
		if (field.answers.isEmpty() || field.uiSelectSourceType != null) {
			return;
		}
		
		for (AnswerModel answer : field.answers) {
			if (needsLabelEnrichment(answer)) {
				enrichAnswerLabel(answer);
			}
		}
	}
	
	private boolean hasUiSelectSource(FieldModel field) {
		return field.uiSelectSourceType != null && !field.uiSelectSourceType.trim().isEmpty();
	}
	
	private boolean hasConceptUuid(FieldModel field) {
		return field.conceptUuid != null && !field.conceptUuid.trim().isEmpty();
	}
	
	private void loadAnswersFromConcept(FieldModel field) {
		List<AnswerModel> loadedAnswers = conceptAnswersService.loadAnswersForConcept(field.conceptUuid);
		if (loadedAnswers != null && !loadedAnswers.isEmpty()) {
			field.answers.addAll(loadedAnswers);
		} else {
			log.debug("No answers found for coded field '{}' with concept {}", field.label, field.conceptUuid);
		}
	}
	
	private boolean needsLabelEnrichment(AnswerModel answer) {
		boolean missingLabel = (answer.label == null || answer.label.trim().isEmpty());
		boolean hasConceptUuid = (answer.conceptUuid != null && !answer.conceptUuid.trim().isEmpty());
		return missingLabel && hasConceptUuid;
	}
	
	private void enrichAnswerLabel(AnswerModel answer) {
		AnswerModel conceptAnswer = conceptAnswersService.loadSingleConcept(answer.conceptUuid);
		if (conceptAnswer != null && conceptAnswer.label != null) {
			answer.label = conceptAnswer.label;
		} else {
			answer.label = answer.conceptUuid;
		}
	}
	
	private void populateUiSelectExtendedAnswers(FieldModel field) {
		String sourceType = field.uiSelectSourceType != null ? field.uiSelectSourceType.trim() : "";
		String datasourceName = field.uiSelectDatasourceName != null ? field.uiSelectDatasourceName.trim() : "";
		boolean handled = false;
		
		try {
			if (!sourceType.isEmpty()) {
				handled = handleUiSelectSourceType(field, sourceType);
			}
			
			if (!handled && !datasourceName.isEmpty()) {
				handled = handleUiSelectDatasource(field, datasourceName);
			}
			
			if (!handled) {
				log.warn("Unsupported ui-select-extended source '{}' / datasource '{}' for field '{}'", sourceType,
				    datasourceName, field.label);
			}
		}
		catch (Exception e) {
			log.warn("Failed to populate ui-select-extended answers for field '{}' (type='{}', datasource='{}'): {}",
			    field.label, sourceType, datasourceName, e.getMessage(), e);
		}
	}
	
	private boolean handleUiSelectSourceType(FieldModel field, String sourceType) {
		String normalized = sourceType.trim().toLowerCase(Locale.ROOT);
		switch (normalized) {
			case "encounterrole":
				loadEncounterRoleAnswers(field);
				return true;
			case "encounterprovider":
				loadEncounterProviderAnswers(field);
				return true;
			case "encounterlocation":
				loadEncounterLocationAnswers(field, null);
				return true;
			default:
				return false;
		}
	}
	
	private boolean handleUiSelectDatasource(FieldModel field, String datasourceName) {
		String normalized = datasourceName.trim().toLowerCase(Locale.ROOT);
		if (normalized.equals("location_datasource")) {
			String tagName = field.uiSelectDatasourceConfig.get("tag");
			loadEncounterLocationAnswers(field, tagName);
			return true;
		}
		return false;
	}
	
	protected EncounterService fetchEncounterService() {
		try {
			return Context.getEncounterService();
		}
		catch (Exception e) {
			return null;
		}
	}
	
	private void loadEncounterRoleAnswers(FieldModel field) {
		EncounterService encounterService = fetchEncounterService();
		if (encounterService == null) {
			return;
		}
		
		List<EncounterRole> roles = encounterService.getAllEncounterRoles(false);
		if (roles == null || roles.isEmpty()) {
			return;
		}
		
		if (!field.answers.isEmpty()) {
			field.answers.clear();
		}
		
		roles.stream().sorted(Comparator.comparing(role -> {
			String name = role.getName();
			return name != null ? name : role.getUuid();
		})).forEach(role -> {
			String name = role.getName() != null ? role.getName() : role.getUuid();
			field.answers.add(new AnswerModel(name, role.getUuid(), role.getUuid()));
		});
	}
	
	private void loadEncounterProviderAnswers(FieldModel field) {
		ProviderService providerService = fetchProviderService();
		if (providerService == null) {
			return;
		}
		
		List<Provider> providers = providerService.getAllProviders(false);
		if (providers == null || providers.isEmpty()) {
			return;
		}
		
		if (!field.answers.isEmpty()) {
			field.answers.clear();
		}
		
		providers.stream().sorted(Comparator.comparing(this::providerDisplayName, String.CASE_INSENSITIVE_ORDER))
		        .forEach(provider -> {
			        String label = providerDisplayName(provider);
			        field.answers.add(new AnswerModel(label, provider.getUuid(), provider.getUuid()));
		        });
	}
	
	private void loadEncounterLocationAnswers(FieldModel field, String requiredTagName) {
		LocationService locationService = fetchLocationService();
		if (locationService == null) {
			return;
		}
		
		List<Location> locations = locationService.getAllLocations(false);
		if (locations == null || locations.isEmpty()) {
			return;
		}
		
		// Get location filtering options from ThreadLocal
		TemplateOptions opts = currentOptions.get();
		final Set<String> allowedUuids = new HashSet<>();
		final String tagFilter;
		
		if (opts != null) {
			// If locationUuids specified, filter to only those locations
			if (opts.locationUuids != null && !opts.locationUuids.isEmpty()) {
				allowedUuids.addAll(opts.locationUuids);
			}
			// Also include the default locationUuid if set
			if (opts.locationUuid != null && !opts.locationUuid.isEmpty()) {
				allowedUuids.add(opts.locationUuid);
			}
			// Use requiredTagName from HFE form configuration if specified
			tagFilter = requiredTagName != null && !requiredTagName.isEmpty() ? requiredTagName.trim() : "";
		} else {
			tagFilter = requiredTagName != null ? requiredTagName.trim() : "";
		}
		
		if (!field.answers.isEmpty()) {
			field.answers.clear();
		}
		
		// Track character count for Excel dropdown limit warning
		int totalCharCount = 0;
		
		List<Location> filteredLocations = new ArrayList<>();
		for (Location location : locations) {
			// Filter by allowed UUIDs if specified
			if (!allowedUuids.isEmpty() && !allowedUuids.contains(location.getUuid())) {
				continue;
			}
			// Filter by tag if specified
			if (!tagFilter.isEmpty() && !locationHasTag(location, tagFilter)) {
				continue;
			}
			filteredLocations.add(location);
		}
		
		// Sort and add to field answers
		filteredLocations.stream().sorted(Comparator.comparing(this::locationDisplayName, String.CASE_INSENSITIVE_ORDER))
		        .forEach(location -> {
			        String label = locationDisplayName(location);
			        field.answers.add(new AnswerModel(label, location.getUuid(), location.getUuid()));
		        });
		
		// Estimate dropdown character count and warn if approaching limit
		for (AnswerModel answer : field.answers) {
			totalCharCount += answer.label != null ? answer.label.length() : 0;
			totalCharCount += 1; // separator
		}
		
		if (totalCharCount > EXCEL_DROPDOWN_CHAR_LIMIT) {
			log.warn(
			    "Location dropdown may exceed Excel character limit ({} chars). Consider reducing location count. "
			            + "Current count: {} locations, estimated {} chars.",
			    EXCEL_DROPDOWN_CHAR_LIMIT, field.answers.size(), totalCharCount);
		}
	}
	
	private String providerDisplayName(Provider provider) {
		if (provider == null) {
			return "";
		}
		String name = provider.getName();
		if (name != null && !name.trim().isEmpty()) {
			return name.trim();
		}
		if (provider.getPerson() != null && provider.getPerson().getPersonName() != null) {
			String fullName = provider.getPerson().getPersonName().getFullName();
			if (fullName != null && !fullName.trim().isEmpty()) {
				return fullName.trim();
			}
		}
		return provider.getUuid();
	}
	
	protected ProviderService fetchProviderService() {
		try {
			return Context.getProviderService();
		}
		catch (Exception e) {
			return null;
		}
	}
	
	protected LocationService fetchLocationService() {
		try {
			return Context.getLocationService();
		}
		catch (Exception e) {
			return null;
		}
	}
	
	private String locationDisplayName(Location location) {
		if (location == null) {
			return "";
		}
		String name = location.getName();
		return (name != null && !name.trim().isEmpty()) ? name.trim() : location.getUuid();
	}
	
	private boolean locationHasTag(Location location, String tagName) {
		if (location == null || tagName == null || tagName.trim().isEmpty()) {
			return false;
		}
		String desired = tagName.trim().toLowerCase(Locale.ROOT);
		if (location.getTags() == null || location.getTags().isEmpty()) {
			return false;
		}
		return location.getTags().stream().filter(tag -> tag != null && tag.getName() != null)
		        .anyMatch(tag -> tag.getName().trim().toLowerCase(Locale.ROOT).equals(desired));
	}
	
	private String createCompositeVersion(List<String> formVersions) {
		if (formVersions.isEmpty()) {
			return "1.0";
		}
		
		if (formVersions.size() == 1) {
			return formVersions.get(0);
		}
		
		return String.join(",", formVersions);
	}
}
