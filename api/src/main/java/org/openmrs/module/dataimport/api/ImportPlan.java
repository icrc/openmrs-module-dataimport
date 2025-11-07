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
import org.openmrs.module.dataimport.api.reader.ExcelReader;

import java.util.List;
import java.util.UUID;

@Getter
public final class ImportPlan {
	
	private final UUID batchId;
	
	private final List<RowPlan> rowsOk;
	
	private final List<QuarantineItem> issues;
	
	private final ImportStats stats;
	
	private final ExcelReader.PatientIndex patientIndex;
	
	public ImportPlan(UUID batchId, List<RowPlan> rowsOk, List<QuarantineItem> issues, ImportStats stats,
	    ExcelReader.PatientIndex patientIndex) {
		this.batchId = batchId;
		this.rowsOk = rowsOk;
		this.issues = issues;
		this.stats = stats;
		this.patientIndex = patientIndex;
	}
	
	public static Builder builder() {
		return new Builder();
	}
	
	public static class Builder {
		
		private UUID batchId;
		
		private List<RowPlan> rowsOk;
		
		private List<QuarantineItem> issues;
		
		private ImportStats stats;
		
		private ExcelReader.PatientIndex patientIndex;
		
		public Builder batchId(UUID batchId) {
			this.batchId = batchId;
			return this;
		}
		
		public Builder rowsOk(List<RowPlan> rowsOk) {
			this.rowsOk = rowsOk;
			return this;
		}
		
		public Builder issues(List<QuarantineItem> issues) {
			this.issues = issues;
			return this;
		}
		
		public Builder stats(ImportStats stats) {
			this.stats = stats;
			return this;
		}
		
		public Builder patientIndex(ExcelReader.PatientIndex patientIndex) {
			this.patientIndex = patientIndex;
			return this;
		}
		
		public ImportPlan build() {
			return new ImportPlan(batchId, rowsOk, issues, stats, patientIndex);
		}
	}
}
