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

public class SheetModel {
	
	public String sheetName;
	
	public String formUuid; // Track which form this sheet belongs to
	
	public String formVersion; // Track individual form version
	
	public String encounterTypeUuid; // Track encounter type for this sheet
	
	public FormType formType; // Track form type (O3_JSON or HFE_XML)
	
	public final List<FieldModel> fields = new ArrayList<>();
	
	SheetModel shallowCopy() {
		SheetModel c = new SheetModel();
		c.sheetName = sheetName;
		c.formUuid = formUuid;
		c.formVersion = formVersion;
		c.encounterTypeUuid = encounterTypeUuid;
		c.formType = formType;
		for (FieldModel f : fields) {
			c.fields.add(f.copyHeaderOnly());
		}
		return c;
	}
}
