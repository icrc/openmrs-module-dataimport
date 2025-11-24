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

import org.openmrs.EncounterType;
import org.openmrs.Form;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder class for creating HFE test forms and data. Provides a fluent API for constructing HFE
 * XML content and OpenMRS Form objects for testing.
 */
public class HfeTestDataBuilder {
	
	/**
	 * Creates a new HFE form builder.
	 *
	 * @return a new HfeFormBuilder instance
	 */
	public static HfeFormBuilder hfeForm() {
		return new HfeFormBuilder();
	}
	
	/**
	 * Builder for creating HFE forms with a fluent API.
	 */
	public static class HfeFormBuilder {
		
		private String formUuid = "test-form-uuid";
		
		private String formName = "Test HFE Form";
		
		private String formVersion = "1.0";
		
		private String encounterTypeUuid = "test-encounter-type-uuid";
		
		private String encounterTypeName = "Test Encounter Type";
		
		private List<ObsElement> observations = new ArrayList<>();
		
		private List<RepeatGroup> repeats = new ArrayList<>();
		
		private List<ObsgroupElement> obsgroups = new ArrayList<>();
		
		private StringBuilder customXml = new StringBuilder();
		
		/**
		 * Sets the form UUID.
		 *
		 * @param uuid the form UUID
		 * @return this builder
		 */
		public HfeFormBuilder withFormUuid(String uuid) {
			this.formUuid = uuid;
			return this;
		}
		
		/**
		 * Sets the form name.
		 *
		 * @param name the form name
		 * @return this builder
		 */
		public HfeFormBuilder withFormName(String name) {
			this.formName = name;
			return this;
		}
		
		/**
		 * Sets the form version.
		 *
		 * @param version the form version
		 * @return this builder
		 */
		public HfeFormBuilder withFormVersion(String version) {
			this.formVersion = version;
			return this;
		}
		
		/**
		 * Sets the encounter type UUID.
		 *
		 * @param uuid the encounter type UUID
		 * @return this builder
		 */
		public HfeFormBuilder withEncounterTypeUuid(String uuid) {
			this.encounterTypeUuid = uuid;
			return this;
		}
		
		/**
		 * Sets the encounter type name.
		 *
		 * @param name the encounter type name
		 * @return this builder
		 */
		public HfeFormBuilder withEncounterTypeName(String name) {
			this.encounterTypeName = name;
			return this;
		}
		
		/**
		 * Adds an obs element to the form.
		 *
		 * @param conceptId the concept ID or mapping (e.g., "CIEL:5089", "uuid-here")
		 * @param labelText the label text for the field
		 * @param style the widget style (e.g., "number", "date", "text", "radio", "dropdown")
		 * @return this builder
		 */
		public HfeFormBuilder withObs(String conceptId, String labelText, String style) {
			observations.add(new ObsElement(conceptId, labelText, style, null));
			return this;
		}
		
		/**
		 * Adds an obs element with a hideWhenExpression for conditional fields.
		 *
		 * @param conceptId the concept ID or mapping
		 * @param labelText the label text for the field
		 * @param style the widget style
		 * @param hideWhenExpression the conditional expression
		 * @return this builder
		 */
		public HfeFormBuilder withConditionalObs(String conceptId, String labelText, String style,
		        String hideWhenExpression) {
			observations.add(new ObsElement(conceptId, labelText, style, hideWhenExpression));
			return this;
		}
		
		/**
		 * Adds a repeat group to the form.
		 *
		 * @param withAttribute the "with" attribute value (e.g., "['label1','CONCEPT:1'],
		 *            ['label2','CONCEPT:2']")
		 * @param elements the obs elements within the repeat
		 * @return this builder
		 */
		public HfeFormBuilder withRepeat(String withAttribute, ObsElement... elements) {
			List<ObsElement> elementList = new ArrayList<>();
			for (ObsElement elem : elements) {
				elementList.add(elem);
			}
			repeats.add(new RepeatGroup(withAttribute, elementList));
			return this;
		}
		
		/**
		 * Adds an obsgroup to the form.
		 *
		 * @param groupingConceptId the concept ID for the obsgroup
		 * @param elements the obs elements within the group
		 * @return this builder
		 */
		public HfeFormBuilder withObsgroup(String groupingConceptId, ObsElement... elements) {
			List<ObsElement> elementList = new ArrayList<>();
			for (ObsElement elem : elements) {
				elementList.add(elem);
			}
			obsgroups.add(new ObsgroupElement(groupingConceptId, elementList));
			return this;
		}
		
		/**
		 * Adds custom raw XML content to the form.
		 *
		 * @param xml the custom XML content
		 * @return this builder
		 */
		public HfeFormBuilder withCustomXml(String xml) {
			customXml.append(xml);
			return this;
		}
		
		/**
		 * Builds the HFE XML string.
		 *
		 * @return the complete HFE XML
		 */
		public String buildXml() {
			StringBuilder xml = new StringBuilder();
			xml.append("<htmlform>\n");
			
			// Add encounter date
			xml.append("  <encounterDate/>\n");
			
			// Add observations
			for (ObsElement obs : observations) {
				xml.append("  <obs");
				xml.append(" conceptId=\"").append(obs.conceptId).append("\"");
				xml.append(" labelText=\"").append(obs.labelText).append("\"");
				if (obs.style != null) {
					xml.append(" style=\"").append(obs.style).append("\"");
				}
				if (obs.hideWhenExpression != null) {
					xml.append(" hideWhenExpression=\"").append(obs.hideWhenExpression).append("\"");
				}
				xml.append("/>\n");
			}
			
			// Add obsgroups
			for (ObsgroupElement obsgroup : obsgroups) {
				xml.append("  <obsgroup groupingConceptId=\"").append(obsgroup.groupingConceptId).append("\">\n");
				for (ObsElement obs : obsgroup.elements) {
					xml.append("    <obs");
					xml.append(" conceptId=\"").append(obs.conceptId).append("\"");
					xml.append(" labelText=\"").append(obs.labelText).append("\"");
					if (obs.style != null) {
						xml.append(" style=\"").append(obs.style).append("\"");
					}
					xml.append("/>\n");
				}
				xml.append("  </obsgroup>\n");
			}
			
			// Add repeats
			for (RepeatGroup repeat : repeats) {
				xml.append("  <repeat with=\"").append(repeat.withAttribute).append("\">\n");
				xml.append("    <template>\n");
				for (ObsElement obs : repeat.elements) {
					xml.append("      <obs");
					xml.append(" conceptId=\"").append(obs.conceptId).append("\"");
					xml.append(" labelText=\"").append(obs.labelText).append("\"");
					if (obs.style != null) {
						xml.append(" style=\"").append(obs.style).append("\"");
					}
					xml.append("/>\n");
				}
				xml.append("    </template>\n");
				xml.append("  </repeat>\n");
			}
			
			// Add custom XML
			if (customXml.length() > 0) {
				xml.append(customXml);
			}
			
			xml.append("</htmlform>");
			return xml.toString();
		}
		
		/**
		 * Builds an OpenMRS Form object for testing.
		 *
		 * @return a mock OpenMRS Form
		 */
		public Form buildOpenMRSForm() {
			Form form = new Form();
			form.setUuid(formUuid);
			form.setName(formName);
			form.setVersion(formVersion);
			
			// Set encounter type
			EncounterType encounterType = new EncounterType();
			encounterType.setUuid(encounterTypeUuid);
			encounterType.setName(encounterTypeName);
			form.setEncounterType(encounterType);
			
			return form;
		}
	}
	
	/**
	 * Represents an obs element in HFE.
	 */
	public static class ObsElement {
		
		final String conceptId;
		
		final String labelText;
		
		final String style;
		
		final String hideWhenExpression;
		
		public ObsElement(String conceptId, String labelText, String style, String hideWhenExpression) {
			this.conceptId = conceptId;
			this.labelText = labelText;
			this.style = style;
			this.hideWhenExpression = hideWhenExpression;
		}
	}
	
	/**
	 * Represents a repeat group in HFE.
	 */
	public static class RepeatGroup {
		
		final String withAttribute;
		
		final List<ObsElement> elements;
		
		public RepeatGroup(String withAttribute, List<ObsElement> elements) {
			this.withAttribute = withAttribute;
			this.elements = elements;
		}
	}
	
	/**
	 * Represents an obsgroup element in HFE.
	 */
	public static class ObsgroupElement {
		
		final String groupingConceptId;
		
		final List<ObsElement> elements;
		
		public ObsgroupElement(String groupingConceptId, List<ObsElement> elements) {
			this.groupingConceptId = groupingConceptId;
			this.elements = elements;
		}
	}
}
