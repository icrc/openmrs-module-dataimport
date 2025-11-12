/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.model;

import lombok.Getter;

@Getter
public final class SheetStats {
	
	private final String sheetName;
	
	private final int okRows;
	
	private final int quarantinedRows;
	
	private SheetStats(String sheetName, int okRows, int quarantinedRows) {
		this.sheetName = sheetName;
		this.okRows = okRows;
		this.quarantinedRows = quarantinedRows;
	}
	
	static class Builder {
		
		private final String sheetName;
		
		private int okRows;
		
		private int quarantinedRows;
		
		Builder(String sheetName) {
			this.sheetName = sheetName;
		}
		
		void incrementOk() {
			okRows++;
		}
		
		void incrementQuarantined() {
			if (quarantinedRows < Integer.MAX_VALUE) {
				quarantinedRows++;
			}
		}
		
		SheetStats build() {
			return new SheetStats(sheetName, okRows, quarantinedRows);
		}
	}
}
