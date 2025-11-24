# Concept Mapping Guide

## Overview

The OpenMRS Data Import Module supports flexible concept resolution using multiple identification strategies. This guide explains how to use concept mappings and other identifiers in your form definitions.

## Supported Concept Identifiers

### 1. UUID (Universally Unique Identifier)

**Format:** `550e8400-e29b-41d4-a716-446655440000` (36-character UUID)

**Example:**
```xml
<obs conceptId="550e8400-e29b-41d4-a716-446655440000" labelText="Weight"/>
```

**Use When:**
- You have direct access to OpenMRS concept UUIDs
- Maximum portability across OpenMRS instances
- Recommended for production use

---

### 2. Numeric ID

**Format:** Integer number (e.g., `5089`)

**Example:**
```xml
<obs conceptId="5089" labelText="Weight"/>
```

**Use When:**
- Working with legacy forms that use numeric IDs
- Developing/testing on a specific OpenMRS instance
- **Warning:** Not portable across instances (IDs may differ)

---

### 3. Concept Mapping Codes

**Format:** `SOURCE:CODE`

**Separator:** `:` (colon)

**Example:**
```xml
<obs conceptId="CIEL:5089" labelText="Weight"/>
```

**Supported Mapping Sources:**

| Source | Description | Example |
|--------|-------------|---------|
| **CIEL** | Columbia International eHealth Laboratory | `CIEL:5089` |
| **LOINC** | Logical Observation Identifiers Names and Codes | `LOINC:29463-7` |
| **SNOMED** | Systematized Nomenclature of Medicine | `SNOMED:27113001` |
| **ICD-10** | International Classification of Diseases (10th revision) | `ICD-10:E11.9` |
| **ICD-11** | International Classification of Diseases (11th revision) | `ICD-11:5A11` |
| **PIH** | Partners In Health | `PIH:3064` |
| **AMPATH** | AMPATH Medical Record System | `AMPATH:5089` |
| **MVP** | Millennium Villages Project | `MVP:5089` |
| **IMO** | Intelligent Medical Objects | `IMO:123456` |
| **RXNORM** | RxNorm (medication codes) | `RXNORM:197361` |

**Use When:**
- Maximum portability across OpenMRS instances
- Using standard medical terminologies
- Sharing forms with other implementations
- **Recommended:** CIEL codes for best interoperability

---

## Concept Resolution Process

The module uses a **3-tier resolution strategy** (in order):

```
1. UUID Lookup
   ↓ (if not found)
2. Numeric ID Lookup
   ↓ (if not found)
3. Mapping Code Lookup (SOURCE:CODE)
   ↓
Result: Concept UUID or null
```

### Resolution Examples

**Input:** `CIEL:5089`
1. Try UUID lookup: Not a valid UUID
2. Try numeric ID: Contains colon, skip
3. Try mapping: Split into `source=CIEL`, `code=5089`
4. Query: `ConceptService.getConceptByMapping("5089", "CIEL")`
5. Result: Returns concept with UUID `c607c80f-1ea9-4da3-bb88-6276ce8868dd`

**Input:** `5089`
1. Try UUID lookup: Not a valid UUID
2. Try numeric ID: Valid integer
3. Query: `ConceptService.getConcept(5089)`
4. Result: Returns concept

**Input:** `c607c80f-1ea9-4da3-bb88-6276ce8868dd`
1. Try UUID lookup: Valid UUID format
2. Query: `ConceptService.getConceptByUuid("c607c80f-...")`
3. Result: Returns concept

---

## Using Concept Mappings in Forms

### HFE (HTML Form Entry) Forms

```xml
<htmlform>
  <!-- Using CIEL mapping -->
  <obs conceptId="CIEL:5089" labelText="Weight (kg)"/>

  <!-- Using LOINC mapping -->
  <obs conceptId="LOINC:29463-7" labelText="Body Weight"/>

  <!-- Using SNOMED mapping -->
  <obs conceptId="SNOMED:27113001" labelText="Body weight"/>

  <!-- Mixed identifiers in same form -->
  <obs conceptId="CIEL:5090" labelText="Height (cm)"/>
  <obs conceptId="5087" labelText="Pulse"/>
  <obs conceptId="a09ab2c5-878e-4905-b25d-5784167d0216" labelText="Temperature"/>

  <!-- Answer concepts with mappings -->
  <obs conceptId="CIEL:1343" labelText="Current HIV status">
    <answerConceptId>CIEL:664</answerConceptId>   <!-- Negative -->
    <answerConceptId>CIEL:703</answerConceptId>   <!-- Positive -->
    <answerConceptId>CIEL:1067</answerConceptId>  <!-- Unknown -->
  </obs>
</htmlform>
```

### O3 (JSON Schema) Forms

```json
{
  "pages": [
    {
      "label": "Vitals",
      "sections": [
        {
          "questions": [
            {
              "id": "weight",
              "label": "Weight (kg)",
              "type": "obs",
              "questionOptions": {
                "concept": "CIEL:5089",
                "rendering": "number"
              }
            },
            {
              "id": "height",
              "label": "Height (cm)",
              "type": "obs",
              "questionOptions": {
                "concept": "CIEL:5090",
                "rendering": "number"
              }
            }
          ]
        }
      ]
    }
  ]
}
```

---

## Performance Considerations

### Caching

The module implements **multi-level caching** to optimize concept lookups:

```
┌─────────────────────────────────────────┐
│ ConceptManager Cache Layers            │
├─────────────────────────────────────────┤
│ 1. UUID Cache (input → resolved UUID)  │
│    - Caches all resolution results      │
│    - Includes mapping lookups           │
│                                         │
│ 2. Object Cache (UUID → Concept)       │
│    - Caches full Concept objects        │
│    - Avoids repeated database queries   │
│                                         │
│ 3. Answers Cache (UUID → AnswerModels) │
│    - Caches concept answer lists        │
│    - Reused across templates            │
└─────────────────────────────────────────┘
```

**Cache Statistics:**
```java
String stats = ConceptManager.getCacheStats();
// Output: "ConceptManager Cache Stats - UUIDs: 150, Objects: 145, Answers: 42"
```

**Clear Caches:**
```java
ConceptManager.clearCaches();
```

Use when concept data may have changed (e.g., after importing new concept dictionaries).

---

## Best Practices

### Recommended

1. **Use CIEL mappings for portability**
   ```xml
   <obs conceptId="CIEL:5089" labelText="Weight"/>
   ```

2. **Use UUIDs when available**
   ```xml
   <obs conceptId="c607c80f-1ea9-4da3-bb88-6276ce8868dd" labelText="Weight"/>
   ```

3. **Document mapping sources in form comments**
   ```xml
   <!-- CIEL concepts from version 20231201 -->
   <obs conceptId="CIEL:5089" labelText="Weight"/>
   ```

4. **Validate mappings exist in your OpenMRS instance**
   ```java
   ValidationResult result = ConceptValidationService.validateConceptExists("CIEL:5089");
   if (!result.isValid()) {
       log.error("Concept mapping not found: {}", result.getErrorSummary());
   }
   ```

### Avoid

1. **Don't rely on numeric IDs for production forms**
   ```xml
   <!-- NOT PORTABLE - ID 5089 may differ across instances -->
   <obs conceptId="5089" labelText="Weight"/>
   ```

2. **Don't use unsupported mapping sources**
   ```xml
   <!-- CUSTOM:123 - may not be recognized -->
   <obs conceptId="CUSTOM:123" labelText="Custom Concept"/>
   ```

3. **Don't mix identifier types inconsistently**
   - Choose one primary strategy per form (e.g., all CIEL, or all UUIDs)
   - Document exceptions clearly

---

## Troubleshooting

### Concept Not Found

**Error:** `Concept not found for UUID: CIEL:5089`

**Possible Causes:**
1. Mapping source not installed in OpenMRS
2. Mapping code doesn't exist
3. Typo in source or code

**Solutions:**
```bash
# 1. Check if CIEL dictionary is installed
# OpenMRS Admin → Manage Concept Sources → Look for "CIEL"

# 2. Verify the mapping exists
# OpenMRS Admin → Manage Concepts → Search for code "5089" with source "CIEL"

# 3. Use ConceptManager for debugging
String resolved = ConceptManager.resolveConceptUuid("CIEL:5089");
if (resolved == null) {
    log.error("Mapping not found - check source and code");
}
```

### Wrong Concept Resolved

**Issue:** Mapping resolves to unexpected concept

**Debug:**
```java
// Enable debug logging
log.setLevel("org.openmrs.module.dataimport.api.util", "DEBUG");

// Check what was resolved
String uuid = ConceptManager.resolveConceptUuid("CIEL:5089");
Concept concept = ConceptManager.getConceptByUuid(uuid);
String name = ConceptManager.getConceptName(concept);
log.info("Resolved: {} -> {} ({})", "CIEL:5089", uuid, name);
```

### Performance Issues

**Issue:** Slow form parsing due to concept lookups

**Solutions:**
1. **Pre-warm cache before bulk processing**
   ```java
   // Load commonly used concepts into cache
   List<String> commonConcepts = Arrays.asList("CIEL:5089", "CIEL:5090", "CIEL:5087");
   for (String conceptId : commonConcepts) {
       ConceptManager.resolveConceptUuid(conceptId);
   }
   ```

2. **Batch validate concepts**
   ```java
   ValidationResult result = ConceptValidationService.validateBatch(conceptUuids);
   if (!result.isValid()) {
       log.error("Batch validation failed: {}", result.getErrorSummary());
   }
   ```

---

## API Reference

### ConceptManager

**Resolve Concept ID to UUID:**
```java
String uuid = ConceptManager.resolveConceptUuid("CIEL:5089");
```

**Get Concept Object:**
```java
Concept concept = ConceptManager.getConceptByUuid(uuid);
```

**Get Concept Name:**
```java
String name = ConceptManager.getConceptName(uuid);
```

**Load Answers:**
```java
List<AnswerModel> answers = ConceptManager.loadAnswersForConcept(uuid);
```

**Validate Concept:**
```java
boolean valid = ConceptManager.isValidConcept(uuid);
```

**Check Datatype:**
```java
boolean isNumeric = ConceptManager.isConceptOfType(uuid, "NM");
```

**Validate Answer:**
```java
boolean validAnswer = ConceptManager.isValidAnswer(questionUuid, answerUuid);
```

### ConceptValidationService

**Validate Existence:**
```java
ValidationResult result = ConceptValidationService.validateConceptExists(uuid);
```

**Validate Datatype:**
```java
ValidationResult result = ConceptValidationService.validateConceptDatatype(
    uuid,
    FieldDatatype.NUMERIC
);
```

**Validate Coded Answer:**
```java
ValidationResult result = ConceptValidationService.validateCodedAnswer(
    questionUuid,
    answerUuid
);
```

**Describe Result:**
```java
String description = ConceptValidationService.describeValidationResult(result);
log.info(description);
```

---

## Migration Guide

### From Numeric IDs to CIEL Mappings

**Step 1: Export Current Concepts**
```sql
SELECT
    concept_id,
    uuid,
    CONCAT('CIEL:', concept_reference_map.code) AS ciel_code
FROM concept
LEFT JOIN concept_reference_map ON concept.concept_id = concept_reference_map.concept_id
LEFT JOIN concept_reference_source ON concept_reference_map.source = concept_reference_source.concept_source_id
WHERE concept_reference_source.name = 'CIEL';
```

**Step 2: Create Mapping File**
```csv
numeric_id,uuid,ciel_code
5089,c607c80f-1ea9-4da3-bb88-6276ce8868dd,CIEL:5089
5090,c607c80f-1ea9-4da3-bb88-6276ce8868de,CIEL:5090
```

**Step 3: Update Forms**
```bash
# Use sed or similar tool to replace
sed -i 's/conceptId="5089"/conceptId="CIEL:5089"/g' form.xml
```

**Step 4: Validate**
```java
// Test all mappings resolve correctly
List<String> newIds = Arrays.asList("CIEL:5089", "CIEL:5090");
ValidationResult result = ConceptValidationService.validateBatch(newIds);
assert result.isValid();
```

---

## Additional Resources

- **CIEL Dictionary:** https://app.openconceptlab.org/orgs/CIEL/sources/CIEL/
- **OpenMRS Concept Dictionary:** https://wiki.openmrs.org/display/docs/Concept+Dictionary
- **Mapping Sources:** https://wiki.openmrs.org/display/docs/Concept+Mapping
- **Module Documentation:** See README.md

---

## Changelog

### Version 1.0
- Initial concept mapping support
- UUID, numeric ID, and mapping code resolution
- CIEL, LOINC, SNOMED, ICD-10, ICD-11, PIH, AMPATH, MVP, IMO, RXNORM
- Multi-level caching for performance
- Comprehensive validation utilities
