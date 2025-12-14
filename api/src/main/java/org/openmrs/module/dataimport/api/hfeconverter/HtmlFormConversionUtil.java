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

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openmrs.Concept;
import org.openmrs.api.context.Context;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for HFE to O3 JSON conversion. Adapted from hfe-o3-form-schema-converter
 * HtmlFormConversionUtil.
 */
public class HtmlFormConversionUtil {
	
	private static final Logger log = LoggerFactory.getLogger(HtmlFormConversionUtil.class);
	
	public static Concept getConceptByUuidOrId(String conceptIdentifier) {
		if (StringUtils.isBlank(conceptIdentifier)) {
			return null;
		}
		
		boolean sessionOpened = false;
		try {
			if (!Context.isSessionOpen()) {
				Context.openSession();
				sessionOpened = true;
			}
			
			Context.addProxyPrivilege(PrivilegeConstants.MANAGE_CONCEPTS);
			Context.addProxyPrivilege(PrivilegeConstants.GET_CONCEPTS);
			Concept concept = Context.getConceptService().getConceptByUuid(conceptIdentifier);
			if (concept != null) {
				return concept;
			}
			
			concept = Context.getConceptService().getConcept(conceptIdentifier);
			return concept;
		}
		catch (Exception e) {
			log.warn("Failed to fetch concept by UUID or ID '{}': {}", conceptIdentifier, e.getMessage());
			return null;
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_CONCEPTS);
			Context.removeProxyPrivilege(PrivilegeConstants.MANAGE_CONCEPTS);
			if (sessionOpened) {
				Context.closeSession();
			}
		}
	}
	
	public static List<Element> filterRelatedTags(Elements similarCheckboxTags) {
		List<Element> validChildren = new ArrayList<>();
		for (Element tag : similarCheckboxTags) {
			boolean isGrouped = false;
			Elements parents = tag.parents();
			for (Element parent : parents) {
				if (parent.normalName().equals("obsgroup")) {
					isGrouped = true;
				}
			}
			
			if (!isGrouped) {
				validChildren.add(tag);
			}
		}
		return validChildren;
	}
	
	public static boolean obsIsInAGroup(Element obs) {
		boolean isGrouped = false;
		Elements parents = obs.parents();
		for (Element parent : parents) {
			if (parent.normalName().equals("obsgroup")) {
				isGrouped = true;
				break;
			}
		}
		return isGrouped;
	}
	
	public static boolean isRepeat(Element obs) {
		boolean isRepeat = false;
		Elements parents = obs.parents();
		for (Element parent : parents) {
			if (parent.normalName().equals("repeat")) {
				isRepeat = true;
				break;
			}
		}
		return isRepeat;
	}
	
	public static String extractQuestionLabel(Element obsTag) {
		String inlineObsLabel = obsTag.attr("labelText");
		String qLabel = "";
		if (StringUtils.isNotBlank(inlineObsLabel)) {
			qLabel = inlineObsLabel;
		} else {
			Element parentElement = obsTag.parent();
			if (parentElement == null) {
				return null;
			}
			if (parentElement.normalName().equals("td") && StringUtils.isNotBlank(parentElement.ownText())) {
				qLabel = parentElement.ownText();
			} else if (parentElement.normalName().equals("td")) {
				Element previousTd = parentElement.previousElementSibling();
				if (previousTd != null && previousTd.normalName().equals("td")
				        && StringUtils.isNotBlank(previousTd.ownText())) {
					qLabel = previousTd.ownText();
				}
			}
		}
		return qLabel;
	}
}
