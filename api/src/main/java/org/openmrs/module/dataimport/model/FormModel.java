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

import java.util.ArrayList;
import java.util.List;

public class FormModel {
	
	public String formUuid;
	
	public String name;
	
	public String version;
	
	public String encounterTypeUuid;
	
	public final List<SheetModel> sheets = new ArrayList<>();
	
	public final List<RuleModel> rules = new ArrayList<>();
	
	public FormSnapshot snapshot() {
		FormSnapshot snap = new FormSnapshot();
		snap.formUuid = formUuid;
		snap.name = name;
		snap.version = version;
		snap.encounterTypeUuid = encounterTypeUuid;
		snap.rules.addAll(rules);
		for (SheetModel s : sheets) {
			snap.sheets.add(s.shallowCopy());
		}
		return snap;
	}
}
