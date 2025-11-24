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
import org.openmrs.module.dataimport.api.cache.ConceptAnswerCache;
import org.openmrs.module.dataimport.api.cache.FormSchemaCache;
import org.openmrs.module.dataimport.api.resolver.FormResolver;
import org.openmrs.module.dataimport.api.resolver.FormResolverRegistry;
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.openmrs.module.dataimport.api.service.PerformanceMetricsService;
import org.openmrs.module.dataimport.api.service.TemplateGenerationService;
import org.openmrs.module.dataimport.api.util.QueryCounter;
import org.openmrs.module.dataimport.model.AnswerModel;
import org.openmrs.module.dataimport.model.FieldDatatype;
import org.openmrs.module.dataimport.model.FieldModel;
import org.openmrs.module.dataimport.model.FormModel;
import org.openmrs.module.dataimport.model.PerformanceMetrics;
import org.openmrs.module.dataimport.model.SheetModel;
import org.openmrs.module.dataimport.model.TemplateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TemplateGenerationServiceImpl implements TemplateGenerationService {
	
	private static final Logger log = LoggerFactory.getLogger(TemplateGenerationServiceImpl.class);
	
	private final FormResolverRegistry resolverRegistry;
	
	private final ExcelTemplateExporter exporter;
	
	private final ConceptAnswersService conceptAnswersService;
	
	private final FormSchemaCache formSchemaCache;
	
	private final ConceptAnswerCache conceptAnswerCache;
	
	private final PerformanceMetricsService metricsService;
	
	@Autowired
	public TemplateGenerationServiceImpl(FormResolverRegistry resolverRegistry, ExcelTemplateExporter exporter,
	    ConceptAnswersService conceptAnswersProvider, FormSchemaCache formSchemaCache, ConceptAnswerCache conceptAnswerCache,
	    PerformanceMetricsService metricsService) {
		this.resolverRegistry = resolverRegistry;
		this.exporter = exporter;
		this.conceptAnswersService = conceptAnswersProvider;
		this.formSchemaCache = formSchemaCache;
		this.conceptAnswerCache = conceptAnswerCache;
		this.metricsService = metricsService;
	}
	
	@Override
	public byte[] generateFromFormUuids(List<String> formUuids, TemplateOptions opts) {
		if (formUuids == null || formUuids.isEmpty()) {
			throw new APIException("At least one form UUID is required");
		}
		
		if (opts == null) {
			throw new APIException("Template options cannot be null");
		}
		
		// Start performance tracking
		PerformanceMetrics metrics = metricsService.startMetrics();
		metrics.setFormUuids(new ArrayList<>(formUuids));

		// Start database query counting
		QueryCounter.start();

		try {
			// Preload all concept UUIDs for multi-form optimization
			Set<String> allConceptUuids = ConcurrentHashMap.newKeySet();
			Instant formResolutionStart = Instant.now();

			FormModel merged = new FormModel();
			merged.name = "Template";

			// Pre-size lists based on expected form count
			List<String> formVersions = new ArrayList<>(formUuids.size());
			int totalFields = 0;

			// Use parallel form resolution for multiple forms (if >= 2 forms)
			List<FormModel> resolvedForms = formUuids.size() >= 2 ? resolveFormsInParallel(formUuids, allConceptUuids)
			        : resolveFormsSequentially(formUuids, allConceptUuids);

			// Merge resolved forms
			for (FormModel formModel : resolvedForms) {
				String formVersion = formModel.version != null ? formModel.version : "1.0";
				formVersions.add(formVersion);

				// Count total fields
				for (SheetModel sheet : formModel.sheets) {
					totalFields += sheet.fields.size();
					// Set form metadata on sheets
					sheet.formUuid = formModel.formUuid;
					sheet.formVersion = formVersion;
					sheet.encounterTypeUuid = formModel.encounterTypeUuid;
					sheet.formType = formModel.formType;
				}

				// Merge into combined model
				if (merged.encounterTypeUuid == null) {
					merged.encounterTypeUuid = formModel.encounterTypeUuid;
				}
				merged.sheets.addAll(formModel.sheets);
				merged.rules.addAll(formModel.rules);
				if (merged.formUuid == null) {
					merged.formUuid = formModel.formUuid;
				}
			}
			
			metrics.setTotalFields(totalFields);
			metrics.addFormResolutionTime(Instant.now().toEpochMilli() - formResolutionStart.toEpochMilli());
			
			// Batch preload all concept answers
			Instant conceptLoadingStart = Instant.now();
			if (!allConceptUuids.isEmpty()) {
				log.debug("Preloading {} concept answers", allConceptUuids.size());
				conceptAnswerCache.preloadConcepts(allConceptUuids);
			}
			metrics.addConceptLoadingTime(Instant.now().toEpochMilli() - conceptLoadingStart.toEpochMilli());
			
			// Now enrich forms with cached concept answers
			for (SheetModel sheet : merged.sheets) {
				enrichFormWithConceptAnswers(sheet);
			}
			
			merged.version = createCompositeVersion(formVersions);
			
			// Generate Excel workbook
			Instant excelStart = Instant.now();
			byte[] result = exporter.export(merged, opts.includePatient, opts.locationUuid);
			metrics.setExcelGenerationMillis(Instant.now().toEpochMilli() - excelStart.toEpochMilli());
			
			// Record success metrics
			metrics.setEndTime(Instant.now());
			metrics.setSuccess(true);
			metrics.setCacheHitRate(formSchemaCache.getHitRate());
			metrics.setMemoryUsedMB(getMemoryUsedMB());
			metrics.setDatabaseQueries(QueryCounter.getCount());

			metricsService.log(metrics);

			return result;
		}
		catch (RuntimeException e) {
			// Record failure metrics
			metrics.setEndTime(Instant.now());
			metrics.setSuccess(false);
			metrics.setErrorMessage(e.getMessage());
			metrics.setDatabaseQueries(QueryCounter.getCount());
			metricsService.log(metrics);

			throw new APIException("Failed to generate Excel template: " + e.getMessage(), e);
		}
		finally {
			// Clean up query counter
			QueryCounter.clear();
		}
	}
	
	/**
	 * Resolves forms in parallel using a thread pool for better I/O throughput.
	 * This is particularly beneficial when forms are on different databases or have high latency.
	 *
	 * @param formUuids UUIDs of forms to resolve
	 * @param allConceptUuids concurrent set to collect concept UUIDs across threads
	 * @return list of resolved forms in original order
	 */
	private List<FormModel> resolveFormsInParallel(List<String> formUuids, Set<String> allConceptUuids) {
		// Use fixed thread pool sized to number of forms (max 5 to avoid overwhelming DB)
		int threadPoolSize = Math.min(formUuids.size(), 5);
		ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

		log.debug("Resolving {} forms in parallel using {} threads", formUuids.size(), threadPoolSize);

		try {
			List<Future<FormModel>> futures = new ArrayList<>(formUuids.size());

			// Submit all form resolution tasks
			for (String uuid : formUuids) {
				Future<FormModel> future = executor.submit(() -> {
					try {
						FormModel formModel = formSchemaCache.get(uuid);

						// Collect concept UUIDs from this form (thread-safe with ConcurrentHashMap.newKeySet())
						collectConceptUuids(formModel, allConceptUuids);

						return formModel;
					}
					catch (Exception e) {
						log.error(
						    "Error resolving form {} in parallel: {}. Tip: verify form resources under Administration → Forms or REST /form/{}?v=full",
						    uuid, e.getMessage(), uuid);
						throw new APIException("Error processing form " + uuid + ": " + e.getMessage(), e);
					}
				});
				futures.add(future);
			}

			// Collect results in order
			List<FormModel> results = new ArrayList<>(futures.size());
			for (Future<FormModel> future : futures) {
				results.add(future.get()); // Will throw ExecutionException if task failed
			}

			log.debug("Successfully resolved {} forms in parallel", results.size());
			return results;
		}
		catch (Exception e) {
			throw new APIException("Parallel form resolution failed: " + e.getMessage(), e);
		}
		finally {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			}
			catch (InterruptedException e) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Resolves forms sequentially (used for single form or as fallback).
	 *
	 * @param formUuids UUIDs of forms to resolve
	 * @param allConceptUuids set to collect concept UUIDs
	 * @return list of resolved forms
	 */
	private List<FormModel> resolveFormsSequentially(List<String> formUuids, Set<String> allConceptUuids) {
		List<FormModel> results = new ArrayList<>(formUuids.size());

		for (String uuid : formUuids) {
			try {
				FormModel formModel = formSchemaCache.get(uuid);
				collectConceptUuids(formModel, allConceptUuids);
				results.add(formModel);
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

		return results;
	}

	/**
	 * Collects concept UUIDs from a form model for batch preloading.
	 *
	 * @param formModel form to collect concepts from
	 * @param allConceptUuids set to add concepts to (can be concurrent)
	 */
	private void collectConceptUuids(FormModel formModel, Set<String> allConceptUuids) {
		for (SheetModel sheet : formModel.sheets) {
			for (FieldModel field : sheet.fields) {
				if (field.datatype == FieldDatatype.CODED && field.conceptUuid != null
				        && !field.conceptUuid.trim().isEmpty()) {
					allConceptUuids.add(field.conceptUuid);
				}
				// Also collect concept UUIDs from existing answers that might need label enrichment
				for (AnswerModel answer : field.answers) {
					if (answer.conceptUuid != null && !answer.conceptUuid.trim().isEmpty()) {
						allConceptUuids.add(answer.conceptUuid);
					}
				}
			}
		}
	}

	/**
	 * Enriches a sheet's fields with concept answers using cache. This method is now called per-sheet
	 * after concept preloading.
	 */
	private void enrichFormWithConceptAnswers(SheetModel sheet) {
		for (FieldModel field : sheet.fields) {
			if (field.datatype == FieldDatatype.CODED) {
				if (field.answers.isEmpty() && field.uiSelectSourceType != null
				        && !field.uiSelectSourceType.trim().isEmpty()) {
					populateUiSelectExtendedAnswers(field);
				}
				
				if (field.answers.isEmpty() && field.conceptUuid != null && !field.conceptUuid.trim().isEmpty()
				        && field.uiSelectSourceType == null) {
					// Use cache instead of direct service call
					List<AnswerModel> loadedAnswers = conceptAnswerCache.getOrEmpty(field.conceptUuid);
					if (loadedAnswers != null && !loadedAnswers.isEmpty()) {
						field.answers.addAll(loadedAnswers);
					} else {
						log.debug("No answers found for coded field '{}' with concept {}", field.label, field.conceptUuid);
					}
				}
				
				if (field.answers.isEmpty()) {} else if (field.uiSelectSourceType == null) {
					// Enrich answer labels from cache
					for (AnswerModel answer : field.answers) {
						boolean missingLabel = (answer.label == null || answer.label.trim().isEmpty());
						if (missingLabel && answer.conceptUuid != null && !answer.conceptUuid.trim().isEmpty()) {
							List<AnswerModel> cachedAnswers = conceptAnswerCache.getOrEmpty(answer.conceptUuid);
							if (!cachedAnswers.isEmpty() && cachedAnswers.get(0).label != null) {
								answer.label = cachedAnswers.get(0).label;
							} else {
								answer.label = answer.conceptUuid;
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Gets current memory usage in megabytes.
	 */
	private long getMemoryUsedMB() {
		Runtime runtime = Runtime.getRuntime();
		long usedMemory = runtime.totalMemory() - runtime.freeMemory();
		return usedMemory / (1024 * 1024); // Convert bytes to MB
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

		// Clear and pre-size answer list for better performance
		field.answers.clear();
		if (field.answers instanceof ArrayList) {
			((ArrayList<AnswerModel>) field.answers).ensureCapacity(roles.size());
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

		// Clear and pre-size answer list for better performance
		field.answers.clear();
		if (field.answers instanceof ArrayList) {
			((ArrayList<AnswerModel>) field.answers).ensureCapacity(providers.size());
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

		List<Location> locations;

		// Optimize: filter at query level when tag is specified
		if (requiredTagName != null && !requiredTagName.trim().isEmpty()) {
			String tagFilter = requiredTagName.trim();
			// Try to use tag-based query if available
			try {
				locations = locationService.getLocationsByTag(locationService.getLocationTagByName(tagFilter));
				if (locations == null) {
					locations = new ArrayList<>();
				}
				log.debug("Loaded {} locations with tag '{}' using query-level filtering", locations.size(), tagFilter);
			}
			catch (Exception e) {
				// Fallback to in-memory filtering if tag-based query fails
				log.debug("Tag-based location query failed, falling back to in-memory filtering: {}", e.getMessage());
				locations = locationService.getAllLocations(false);
				if (locations != null) {
					final String finalTagFilter = tagFilter;
					locations = locations.stream().filter(location -> locationHasTag(location, finalTagFilter))
					        .collect(java.util.stream.Collectors.toList());
				} else {
					locations = new ArrayList<>();
				}
			}
		} else {
			// No tag filter - get all locations
			locations = locationService.getAllLocations(false);
			if (locations == null) {
				locations = new ArrayList<>();
			}
		}

		if (locations.isEmpty()) {
			return;
		}

		// Clear and pre-size answer list for better performance
		field.answers.clear();
		if (field.answers instanceof ArrayList) {
			((ArrayList<AnswerModel>) field.answers).ensureCapacity(locations.size());
		}

		// Sort and convert to answer models
		locations.stream().sorted(Comparator.comparing(this::locationDisplayName, String.CASE_INSENSITIVE_ORDER))
		        .forEach(location -> {
			        String label = locationDisplayName(location);
			        field.answers.add(new AnswerModel(label, location.getUuid(), location.getUuid()));
		        });
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
