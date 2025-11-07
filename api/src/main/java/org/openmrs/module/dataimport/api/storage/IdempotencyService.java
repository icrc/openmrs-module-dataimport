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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IdempotencyService {
	
	private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
	
	private static final Map<String, String> storage = new ConcurrentHashMap<>();
	
	public boolean exists(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
			return false;
		}
		
		boolean exists = storage.containsKey(idempotencyKey);
		if (exists) {
			log.debug("Idempotency key already exists: {}", idempotencyKey);
		}
		
		return exists;
	}
	
	public void record(String idempotencyKey, String encounterUuid) {
		if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
			throw new IllegalArgumentException("Idempotency key cannot be null or empty");
		}
		
		if (encounterUuid == null || encounterUuid.trim().isEmpty()) {
			throw new IllegalArgumentException("Encounter UUID cannot be null or empty");
		}
		
		storage.put(idempotencyKey, encounterUuid);
	}
	
	public int size() {
		return storage.size();
	}
}
