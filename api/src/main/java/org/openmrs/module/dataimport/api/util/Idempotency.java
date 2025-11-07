/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.util;

import org.apache.poi.ss.usermodel.Workbook;
import org.openmrs.module.dataimport.api.EncounterBlueprint;
import org.openmrs.module.dataimport.api.ObsBlueprint;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class Idempotency {
	
	public static String key(Workbook workbook, String sheetName, int rowIndex, EncounterBlueprint encounter,
	        List<ObsBlueprint> obs) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			
			String workbookHash = String.valueOf(workbook.hashCode());
			digest.update(workbookHash.getBytes());
			
			digest.update(sheetName.getBytes());
			digest.update(String.valueOf(rowIndex).getBytes());
			
			if (encounter.getEncounterTypeUuid() != null) {
				digest.update(encounter.getEncounterTypeUuid().getBytes());
			}
			if (encounter.getEncounterDatetime() != null) {
				digest.update(encounter.getEncounterDatetime().toString().getBytes());
			}
			if (encounter.getLocationUuid() != null) {
				digest.update(encounter.getLocationUuid().getBytes());
			}
			if (encounter.getProviderUuid() != null) {
				digest.update(encounter.getProviderUuid().getBytes());
			}
			if (encounter.getFormUuid() != null) {
				digest.update(encounter.getFormUuid().getBytes());
			}
			
			for (ObsBlueprint obsBlueprint : obs) {
				digest.update(obsBlueprint.getConceptUuid().getBytes());
				
				if (obsBlueprint.getValue() != null) {
					String valueStr = obsValueToString(obsBlueprint.getValue());
					if (valueStr != null) {
						digest.update(valueStr.getBytes());
					}
				}
			}
			
			byte[] hash = digest.digest();
			return bytesToHex(hash);
			
		}
		catch (NoSuchAlgorithmException e) {
			String workbookHash = String.valueOf(workbook.hashCode());
			return String.valueOf((workbookHash + sheetName + rowIndex + encounter + obs).hashCode());
		}
	}
	
	private static String obsValueToString(ObsBlueprint.ObsValue value) {
		if (value instanceof ObsBlueprint.ObsValue.TextValue) {
			return ((ObsBlueprint.ObsValue.TextValue) value).getValue();
		} else if (value instanceof ObsBlueprint.ObsValue.NumericValue) {
			return String.valueOf(((ObsBlueprint.ObsValue.NumericValue) value).getValue());
		} else if (value instanceof ObsBlueprint.ObsValue.CodedValue) {
			return ((ObsBlueprint.ObsValue.CodedValue) value).getAnswerConceptUuid();
		} else if (value instanceof ObsBlueprint.ObsValue.DateValue) {
			return ((ObsBlueprint.ObsValue.DateValue) value).getValue().toString();
		} else if (value instanceof ObsBlueprint.ObsValue.DateTimeValue) {
			return ((ObsBlueprint.ObsValue.DateTimeValue) value).getValue().toString();
		} else if (value instanceof ObsBlueprint.ObsValue.BooleanValue) {
			return String.valueOf(((ObsBlueprint.ObsValue.BooleanValue) value).getValue());
		}
		return null;
	}
	
	private static String bytesToHex(byte[] bytes) {
		StringBuilder result = new StringBuilder();
		for (byte b : bytes) {
			result.append(String.format("%02x", b));
		}
		return result.toString();
	}
}
