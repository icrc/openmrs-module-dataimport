/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport;

import org.openmrs.VisitType;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.dataimport.api.util.DataImportConstants;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataimportActivator extends BaseModuleActivator {
	
	private static final Logger log = LoggerFactory.getLogger(DataimportActivator.class);
	
	public void started() {
		log.info("Started Dataimport");
		ensureDataImportVisitType();
	}
	
	public void shutdown() {
		log.info("Shutdown Dataimport");
	}
	
	private void ensureDataImportVisitType() {
		boolean sessionOpened = false;
		try {
			if (!Context.isSessionOpen()) {
				Context.openSession();
				sessionOpened = true;
			}
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_VISIT_TYPES);
			Context.addProxyPrivilege(PrivilegeConstants.GET_VISIT_TYPES);
			VisitService visitService = Context.getVisitService();
			VisitType visitType = visitService.getVisitTypeByUuid(DataImportConstants.DATA_IMPORT_VISIT_TYPE_UUID);
			if (visitType == null) {
				visitType = new VisitType(DataImportConstants.DATA_IMPORT_VISIT_TYPE_NAME,
				        DataImportConstants.DATA_IMPORT_VISIT_TYPE_DESCRIPTION);
				visitType.setUuid(DataImportConstants.DATA_IMPORT_VISIT_TYPE_UUID);
				visitService.saveVisitType(visitType);
			}
		}
		catch (Exception e) {
			log.warn("Failed to ensure Data Import visit type exists: {}", e.getMessage());
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_VISIT_TYPES);
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_VISIT_TYPES);
			if (sessionOpened) {
				Context.closeSession();
			}
		}
	}
}
