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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemplateMeta {
	
	public int templateVersion = 1;
	
	public Instant generatedAt;
	
	public String locale = "en";
	
	public String templateName;
	
	public String defaultLocationUuid;
	
	public int formCount;
	
	public final List<FormInfo> forms = new ArrayList<>();
	
	public final Map<String, SheetMeta> sheets = new HashMap<>();
	
	public SheetMeta getSheetMeta(String sheetName) {
		return sheets.get(sheetName);
	}
	
	public void addSheetMeta(String sheetName, SheetMeta sheetMeta) {
		sheets.put(sheetName, sheetMeta);
	}
	
	public void addForm(FormInfo formInfo) {
		forms.add(formInfo);
		formCount = forms.size();
	}
	
	public static class FormInfo {
		
		public String formUuid;
		
		public String formName;
		
		public String formVersion;
		
		public String encounterTypeUuid;
		
		public String sheetName;
		
		public FormInfo() {
		}
		
		public FormInfo(String formUuid, String formName, String formVersion, String encounterTypeUuid, String sheetName) {
			this.formUuid = formUuid;
			this.formName = formName;
			this.formVersion = formVersion;
			this.encounterTypeUuid = encounterTypeUuid;
			this.sheetName = sheetName;
		}
	}
}
