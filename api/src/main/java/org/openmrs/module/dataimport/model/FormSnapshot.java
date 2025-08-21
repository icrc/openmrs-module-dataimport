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

/**
 * Snapshot of the form model for storage in _Rules sheet.
 */
public class FormSnapshot {
	
	public String formUuid;
	
	public String name;
	
	public String version;
	
	public String encounterTypeUuid;
	
	public final List<SheetModel> sheets = new ArrayList<>();
	
	public final List<RuleModel> rules = new ArrayList<>();
}
