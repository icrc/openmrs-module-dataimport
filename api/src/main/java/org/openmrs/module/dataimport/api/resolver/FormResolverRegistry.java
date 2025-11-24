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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Registry for all form resolvers. Auto-detects which resolver to use for a given form. Resolvers
 * are checked in order until one returns true from canResolve(). Typically O3JsonFormResolver
 * should be registered first, then HfeFormResolver.
 */
public class FormResolverRegistry {
	
	private static final Logger log = LoggerFactory.getLogger(FormResolverRegistry.class);
	
	private final List<FormResolver> resolvers;
	
	@Autowired
	public FormResolverRegistry(List<FormResolver> resolvers) {
		this.resolvers = resolvers;
		log.info("FormResolverRegistry initialized with {} resolver(s): {}", resolvers.size(),
		    resolvers.stream().map(r -> r.getFormType().getCode()).toArray());
	}
	
	/**
	 * Find the first resolver that can handle this form.
	 *
	 * @param formUuid The form UUID
	 * @return The compatible FormResolver, or null if none found
	 */
	public FormResolver findResolver(String formUuid) {
		if (formUuid == null || formUuid.trim().isEmpty()) {
			return null;
		}
		
		for (FormResolver resolver : resolvers) {
			try {
				if (resolver.canResolve(formUuid)) {
					log.debug("Form {} matched resolver: {}", formUuid, resolver.getFormType().getCode());
					return resolver;
				}
			}
			catch (Exception e) {
				log.debug("Resolver {} threw exception checking form {}: {}", resolver.getFormType().getCode(), formUuid,
				    e.getMessage());
			}
		}
		
		log.warn("No resolver found for form {}", formUuid);
		return null;
	}
	
	/**
	 * @return All registered resolvers
	 */
	public List<FormResolver> getAllResolvers() {
		return resolvers;
	}
}
