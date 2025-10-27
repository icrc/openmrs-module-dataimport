/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api;

import lombok.Getter;

import java.time.Instant;

/**
 * Blueprint for creating an encounter during import.
 */
@Getter
public final class EncounterBlueprint {
	
	private final String encounterTypeUuid;
	
	private final Instant encounterDatetime;
	
	private final String locationUuid;
	
	private final String providerUuid;
	
	private final String formUuid;
	
	public EncounterBlueprint(String encounterTypeUuid, Instant encounterDatetime, String locationUuid, String providerUuid,
	    String formUuid) {
		this.encounterTypeUuid = encounterTypeUuid;
		this.encounterDatetime = encounterDatetime;
		this.locationUuid = locationUuid;
		this.providerUuid = providerUuid;
		this.formUuid = formUuid;
	}
	
	public static Builder builder() {
		return new Builder();
	}
	
	public static class Builder {
		
		private String encounterTypeUuid;
		
		private Instant encounterDatetime;
		
		private String locationUuid;
		
		private String providerUuid;
		
		private String formUuid;
		
		public Builder encounterTypeUuid(String encounterTypeUuid) {
			this.encounterTypeUuid = encounterTypeUuid;
			return this;
		}
		
		public Builder encounterDatetime(Instant encounterDatetime) {
			this.encounterDatetime = encounterDatetime;
			return this;
		}
		
		public Builder locationUuid(String locationUuid) {
			this.locationUuid = locationUuid;
			return this;
		}
		
		public Builder providerUuid(String providerUuid) {
			this.providerUuid = providerUuid;
			return this;
		}
		
		public Builder formUuid(String formUuid) {
			this.formUuid = formUuid;
			return this;
		}
		
		public EncounterBlueprint build() {
			return new EncounterBlueprint(encounterTypeUuid, encounterDatetime, locationUuid, providerUuid, formUuid);
		}
	}
}
