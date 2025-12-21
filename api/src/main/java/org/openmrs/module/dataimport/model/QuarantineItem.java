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
public final class QuarantineItem {
	
	public enum Severity {
		WARNING, // Issue flagged but doesn't block import
		ERROR // Issue blocks the row from being imported
	}
	
	private final String sheet;
	
	private final int row;
	
	private final int col;
	
	private final String code;
	
	private final String message;
	
	private final String[] hints;
	
	private final Severity severity;
	
	public QuarantineItem(String sheet, int row, int col, String code, String message, String[] hints, Severity severity) {
		this.sheet = sheet;
		this.row = row;
		this.col = col;
		this.code = code;
		this.message = message;
		this.hints = hints;
		this.severity = severity != null ? severity : Severity.ERROR;
	}
	
	public static QuarantineItem error(String sheet, int row, int col, String code, String message) {
		return new QuarantineItem(sheet, row, col, code, message, null, Severity.ERROR);
	}
	
	public static QuarantineItem error(String sheet, int row, int col, String code, String message, String[] hints) {
		return new QuarantineItem(sheet, row, col, code, message, hints, Severity.ERROR);
	}
	
	public static QuarantineItem warning(String sheet, int row, int col, String code, String message) {
		return new QuarantineItem(sheet, row, col, code, message, null, Severity.WARNING);
	}
	
	public static QuarantineItem warning(String sheet, int row, int col, String code, String message, String[] hints) {
		return new QuarantineItem(sheet, row, col, code, message, hints, Severity.WARNING);
	}
	
	public boolean isError() {
		return severity == Severity.ERROR;
	}
	
	public boolean isWarning() {
		return severity == Severity.WARNING;
	}
}
