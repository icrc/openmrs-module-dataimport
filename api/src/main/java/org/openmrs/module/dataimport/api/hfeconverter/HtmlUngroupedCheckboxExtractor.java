/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.hfeconverter;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extractor for html <obs conceptId=xxx conceptAnswer=xxx style=checkbox></obs> tag Extractor for
 * html <obs conceptId=xxx conceptAnswer=yyy style=checkbox></obs> tag Extractor for html
 * <obs conceptId=xxx conceptAnswer=zzz style=checkbox></obs> tag
 */
public class HtmlUngroupedCheckboxExtractor {
	
	public static Map<String, List<Element>> getRelatedCheckboxes(String conceptId, String actualConceptUuid,
	        Element parent) {
		Map<String, List<Element>> entry = new HashMap<String, List<Element>>();
		Elements similarCheckboxTags = parent.select("obs[conceptId=" + conceptId + "]");
		List<Element> relatedCheckboxTags = HtmlFormConversionUtil.filterRelatedTags(similarCheckboxTags);
		entry.put(actualConceptUuid, relatedCheckboxTags);
		return entry;
		
	}
}
