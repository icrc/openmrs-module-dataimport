# Concept Management Overview

## Introduction

This document provides a comprehensive overview of the concept management system in the OpenMRS Data Import Module. The system has been refactored to centralize concept operations, standardize answer mapping, and provide robust validation.

## Architecture

### Core Components

```
┌─────────────────────────────────────────────────────────────────┐
│                    Concept Management Layer                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │            ConceptAnswersService (Entry Point)             │ │
│  │                                                            │ │
│  │  - Single interface for all concept operations            │ │
│  │  - Injected via Spring (parsers, services)                │ │
│  │  - Retrieved via Context (static utilities)               │ │
│  └─────────────────────────┬──────────────────────────────────┘ │
│                            │                                     │
│                            ▼                                     │
│  ┌────────────────┐  ┌───────────────────┐  ┌────────────────┐ │
│  │ ConceptManager │  │ AnswerMapper      │  │ Validation     │ │
│  │  (Internal)    │  │                   │  │                │ │
│  │ - Resolution   │  │ - List ↔ Map      │  │ - Pre-flight   │ │
│  │ - Caching      │  │ - Normalization   │  │ - Datatype     │ │
│  │ - Name Loading │  │ - Enrichment      │  │ - Answers      │ │
│  │ - Answers      │  │ - Merging         │  │                │ │
│  └────────────────┘  └───────────────────┘  └────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │           DatatypeInferenceConfig                          │ │
│  │                                                            │ │
│  │  - Priority Rules (Widget > Style > Answers > Concept)    │ │
│  │  - HL7 Mapping (NM, DT, TS, BIT, CWE, ST, etc.)          │ │
│  │  - O3 Rendering (date, number, select, etc.)             │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Components

### 1. ConceptAnswersService (Primary Interface)

**Location:** `org.openmrs.module.dataimport.api.service.ConceptAnswersService`
**Implementation:** `org.openmrs.module.dataimport.api.impl.CoreConceptAnswersServiceImpl`

**Purpose:** Single entry point for all concept operations across the module

**Key Features:**
- Centralized concept resolution (UUID, numeric ID, mapping codes)
- Module-wide caching (delegated to ConceptManager)
- Locale-aware concept name loading
- Answer loading (from concept answers or set members)
- Datatype conversion and field type inference
- Spring-managed service with dependency injection

**Usage Patterns:**

**In Services and Parsers (Constructor Injection):**
```java
public class HfeFormParser {
    private final ConceptAnswersService conceptAnswersService;

    @Autowired
    public HfeFormParser(ConceptAnswersService conceptAnswersService) {
        this.conceptAnswersService = conceptAnswersService;
    }

    private void parseField(String conceptId) {
        // Resolve concept identifier to UUID
        String uuid = conceptAnswersService.resolveConceptUuid("CIEL:5089");

        // Get concept name
        String name = conceptAnswersService.getConceptName(uuid);

        // Load answers
        List<AnswerModel> answers = conceptAnswersService.loadAnswersForConcept(uuid);

        // Get concept datatype
        ConceptDatatype datatype = conceptAnswersService.getConceptDatatype(uuid);

        // Convert to field datatype
        FieldDatatype fieldType = conceptAnswersService.convertToFieldDatatype(datatype);
    }
}
```

**In Static Utility Classes (Context Retrieval):**
```java
public class Validation {
    public static void validateField(String conceptUuid) {
        ConceptAnswersService service = Context.getRegisteredComponent(
            "coreConceptAnswersProvider",
            ConceptAnswersService.class
        );

        if (service == null) {
            // Service not available - skip validation or use fallback
            return;
        }

        ConceptDatatype datatype = service.getConceptDatatype(conceptUuid);
        // ... validation logic
    }
}
```

---

### 2. ConceptManager (Internal Implementation)

**Location:** `org.openmrs.module.dataimport.api.util.ConceptManager`

**Purpose:** Internal utility providing the actual concept resolution, caching, and management logic

**⚠️ Usage Note:** This class is used internally by `ConceptAnswersService`. Production code should use `ConceptAnswersService` instead.

**Key Features:**
- Multi-strategy concept resolution (UUID, numeric ID, mapping codes)
- Three-level caching (UUID cache, object cache, answers cache)
- Locale-aware concept name loading
- Answer loading (from concept answers or set members)
- Datatype conversion
- Static utility methods for internal use

**Common Operations (Internal Use):**
```java
// These are called by ConceptAnswersService, not directly by application code
String uuid = ConceptManager.resolveConceptUuid("CIEL:5089");
String name = ConceptManager.getConceptName(uuid);
List<AnswerModel> answers = ConceptManager.loadAnswersForConcept(uuid);
boolean valid = ConceptManager.isValidConcept(uuid);
boolean isNumeric = ConceptManager.isConceptOfType(uuid, "NM");

// Cache management
ConceptManager.clearCaches();
String stats = ConceptManager.getCacheStats();
```

---

### 3. AnswerMapper

**Location:** `org.openmrs.module.dataimport.api.util.AnswerMapper`

**Purpose:** Standardized answer mapping and conversion

**Key Features:**
- Conversion between `List<AnswerModel>` and `Map<String, String>`
- Normalized label matching
- Answer validation
- Label enrichment from concepts
- Answer list merging
- JSON serialization

**Common Operations:**
```java
// Convert between representations
Map<String, String> map = AnswerMapper.toMap(answersList);
List<AnswerModel> list = AnswerMapper.toList(answersMap);

// Look up answer concept UUID
String answerUuid = AnswerMapper.getAnswerConceptUuid(answers, "Positive");

// Validate answer
boolean valid = AnswerMapper.isValidAnswer(answers, "Positive");

// Enrich labels from concepts
List<AnswerModel> enriched = AnswerMapper.enrichAnswerLabels(answers);

// Merge answer lists
List<AnswerModel> merged = AnswerMapper.mergeAnswers(primary, secondary);

// Convert to JSON
String json = AnswerMapper.toJson(answers);
```

---

### 4. ConceptValidationService

**Location:** `org.openmrs.module.dataimport.api.util.ConceptValidationService`

**Purpose:** Comprehensive concept validation with detailed error messages

**Key Features:**
- Pre-flight concept validation
- Datatype compatibility checking
- Coded answer validation
- Numeric value range validation
- Batch validation
- Detailed error and warning messages

**Common Operations:**
```java
// Validate concept exists
ValidationResult result = ConceptValidationService.validateConceptExists(uuid);
if (!result.isValid()) {
    log.error("Errors: {}", result.getErrorSummary());
}
if (result.hasWarnings()) {
    log.warn("Warnings: {}", result.getWarningSummary());
}

// Validate datatype
ValidationResult result = ConceptValidationService.validateConceptDatatype(
    uuid,
    FieldDatatype.NUMERIC
);

// Validate coded answer
ValidationResult result = ConceptValidationService.validateCodedAnswer(
    questionUuid,
    answerUuid
);

// Validate numeric value
ValidationResult result = ConceptValidationService.validateNumericValue(
    uuid,
    75.5
);

// Batch validation
ValidationResult result = ConceptValidationService.validateBatch(uuidList);

// Validate all answers for a field
ValidationResult result = ConceptValidationService.validateAnswers(
    questionUuid,
    answersList
);

// Get formatted description
String description = ConceptValidationService.describeValidationResult(result);
log.info(description);
```

---

### 5. DatatypeInferenceConfig

**Location:** `org.openmrs.module.dataimport.api.util.DatatypeInferenceConfig`

**Purpose:** Configurable datatype inference rules and mappings

**Key Features:**
- Documented inference priority system
- HL7 abbreviation mappings
- HFE style keyword mappings
- O3 rendering type mappings
- Widget element mappings
- Comprehensive documentation

**Inference Priority:**
```
1. Widget Structure (Highest)
   <date>, <datetime>, <select>, <checkbox>, etc.

2. Style Attribute
   style="date", style="number", style="radio", etc.

3. Answer Attributes
   answerConceptId, answerConceptIds → CODED

4. Concept Datatype (Fallback)
   NM → NUMERIC, DT → DATE, TS → DATETIME, etc.
```

**Common Operations:**
```java
// Convert HL7 code
FieldDatatype datatype = DatatypeInferenceConfig.fromHl7Code("NM");
// Returns: FieldDatatype.NUMERIC

// Infer from style
FieldDatatype datatype = DatatypeInferenceConfig.fromStyleAttribute("date");
// Returns: FieldDatatype.DATE

// Infer from O3 rendering
FieldDatatype datatype = DatatypeInferenceConfig.fromO3Rendering("number");
// Returns: FieldDatatype.NUMERIC

// Infer from widget
FieldDatatype datatype = DatatypeInferenceConfig.fromWidgetElement("datetime");
// Returns: FieldDatatype.DATETIME

// Infer from answer attributes
FieldDatatype datatype = DatatypeInferenceConfig.fromAnswerAttributes(
    true,  // hasAnswerConceptId
    false, // hasAnswerConceptIds
    false, // hasAnswerLabel
    false  // hasAnswerLabels
);
// Returns: FieldDatatype.CODED

// Get documentation
String docs = DatatypeInferenceConfig.getInferenceDocumentation();
System.out.println(docs);

// Log configuration
DatatypeInferenceConfig.logConfiguration();
```

---

## Integration Guide

### Using in Form Parsers

**Before (Manual Implementation):**
```java
// Manual concept resolution - scattered across codebase
private String resolveConceptUuid(String conceptId) {
    // 50+ lines of resolution logic
    // Local cache only
    // No standardized validation
    // Direct ConceptService calls
}
```

**After (Using ConceptAnswersService):**
```java
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.springframework.beans.factory.annotation.Autowired;

public class HfeFormParser {
    private final ConceptAnswersService conceptAnswersService;

    @Autowired
    public HfeFormParser(ConceptAnswersService conceptAnswersService) {
        this.conceptAnswersService = conceptAnswersService;
    }

    private String resolveConceptUuid(String conceptId) {
        // Single service call with caching and validation
        String uuid = conceptAnswersService.resolveConceptUuid(conceptId);
        return uuid;
    }

    private List<AnswerModel> loadAnswers(String conceptUuid) {
        // Centralized answer loading with caching
        return conceptAnswersService.loadAnswersForConcept(conceptUuid);
    }
}
```

**Spring Configuration:**
```xml
<!-- Parsers with service injection -->
<bean id="hfeFormParser" class="org.openmrs.module.dataimport.api.impl.HfeFormParser">
    <constructor-arg ref="coreConceptAnswersProvider" />
</bean>

<bean id="o3FormParser" class="org.openmrs.module.dataimport.api.impl.O3FormParser">
    <constructor-arg ref="coreConceptAnswersProvider" />
</bean>

<!-- Service implementation -->
<bean id="coreConceptAnswersProvider"
      class="org.openmrs.module.dataimport.api.impl.CoreConceptAnswersServiceImpl" />
```

---

### Using in Template Generation Services

**Before (Direct ConceptService calls):**
```java
public List<AnswerModel> loadAnswersForConcept(String conceptUuid) {
    // Direct ConceptService calls
    // No caching across service instances
    // Manual name resolution
    ConceptService cs = Context.getConceptService();
    Concept concept = cs.getConceptByUuid(conceptUuid);
    // ... manual processing
}
```

**After (Using ConceptAnswersService):**
```java
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;
import org.springframework.beans.factory.annotation.Autowired;

public class TemplateGenerationServiceImpl {
    private final ConceptAnswersService conceptAnswersService;

    @Autowired
    public TemplateGenerationServiceImpl(ConceptAnswersService conceptAnswersService) {
        this.conceptAnswersService = conceptAnswersService;
    }

    public List<AnswerModel> loadAnswersForConcept(String conceptUuid) {
        // Cached, centralized answer loading
        return conceptAnswersService.loadAnswersForConcept(conceptUuid);
    }

    private void enrichField(FieldModel field) {
        // Get concept name
        String name = conceptAnswersService.getConceptName(field.conceptUuid);

        // Get datatype
        ConceptDatatype datatype = conceptAnswersService.getConceptDatatype(field.conceptUuid);
        FieldDatatype fieldType = conceptAnswersService.convertToFieldDatatype(datatype);
    }
}
```

---

### Using in Data Import

**Before (ColumnMeta):**
```java
public String getAnswerConceptUuid(String label) {
    // Manual normalization
    String target = DataImportConstants.normalizeLabel(label);
    for (Map.Entry<String, String> e : answers.entrySet()) {
        if (DataImportConstants.normalizeLabel(e.getKey()).equals(target)) {
            return e.getValue();
        }
    }
    return null;
}
```

**After (Using AnswerMapper):**
```java
import org.openmrs.module.dataimport.api.util.AnswerMapper;

public String getAnswerConceptUuid(String label) {
    return AnswerMapper.getAnswerConceptUuid(answers, label);
}
```

---

### Using in Static Utility Classes

**Before (Direct ConceptService calls):**
```java
public class Validation {
    public static void validateDatatype(String conceptUuid) {
        // Direct OpenMRS API calls
        ConceptService cs = Context.getConceptService();
        Concept concept = cs.getConceptByUuid(conceptUuid);
        ConceptDatatype dt = concept.getDatatype();
        // ... validation logic
    }
}
```

**After (Using ConceptAnswersService via Context):**
```java
import org.openmrs.api.context.Context;
import org.openmrs.module.dataimport.api.service.ConceptAnswersService;

public class Validation {
    public static void validateDatatype(String conceptUuid, String expectedType) {
        // Retrieve service via Context for static utilities
        ConceptAnswersService service = Context.getRegisteredComponent(
            "coreConceptAnswersProvider",
            ConceptAnswersService.class
        );

        if (service == null) {
            // Service not available - skip validation or use fallback
            log.warn("ConceptAnswersService not available, skipping validation");
            return;
        }

        // Use service for centralized, cached operations
        ConceptDatatype dt = service.getConceptDatatype(conceptUuid);
        if (dt == null) {
            log.error("Concept not found: {}", conceptUuid);
            return;
        }

        // Validation logic with consistent error handling
        boolean valid = matchesDatatype(dt, expectedType);
        if (!valid) {
            log.error("Datatype mismatch for concept {}", conceptUuid);
        }
    }
}
```

**Similarly in AnswerMapper:**
```java
public class AnswerMapper {
    public static List<AnswerModel> enrichAnswerLabels(List<AnswerModel> answers) {
        ConceptAnswersService service = Context.getRegisteredComponent(
            "coreConceptAnswersProvider",
            ConceptAnswersService.class
        );

        if (service == null) {
            return answers; // Return unchanged if service unavailable
        }

        // Use service to load concept names
        List<AnswerModel> enriched = new ArrayList<>();
        for (AnswerModel answer : answers) {
            if (answer.label == null && answer.conceptUuid != null) {
                AnswerModel loaded = service.loadSingleConcept(answer.conceptUuid);
                enriched.add(loaded != null ? loaded : answer);
            } else {
                enriched.add(answer);
            }
        }
        return enriched;
    }
}
```

---

## Performance Optimizations

### Caching Strategy

**Three-Level Cache:**

1. **UUID Cache** (`Map<String, String>`)
   - Caches: Input identifier → Resolved UUID
   - Includes: Mapping code resolutions
   - Benefit: Avoids repeated resolution logic

2. **Object Cache** (`Map<String, Concept>`)
   - Caches: UUID → Full Concept object
   - Benefit: Avoids repeated database queries

3. **Answers Cache** (`Map<String, List<AnswerModel>>`)
   - Caches: Concept UUID → Answer list
   - Benefit: Reuses answer loading across templates

**Cache Management:**
```java
// Check cache stats
String stats = ConceptManager.getCacheStats();
log.info(stats);
// Output: "ConceptManager Cache Stats - UUIDs: 150, Objects: 145, Answers: 42"

// Clear caches when concepts change
ConceptManager.clearCaches();
```

---

### Batch Operations

**Pre-warm Cache:**
```java
// Before processing many forms
List<String> commonConcepts = Arrays.asList(
    "CIEL:5089",  // Weight
    "CIEL:5090",  // Height
    "CIEL:5087"   // Pulse
);

for (String conceptId : commonConcepts) {
    ConceptManager.resolveConceptUuid(conceptId);
}
```

**Batch Validation:**
```java
// Validate all concepts upfront
List<String> allConcepts = extractConceptsFromForm(form);
ValidationResult result = ConceptValidationService.validateBatch(allConcepts);

if (!result.isValid()) {
    log.error("Form validation failed: {}", result.getErrorSummary());
    throw new FormValidationException(result);
}
```

---

## Migration Path

### Phase 1: Gradual Adoption

1. **Update imports** in new code
2. **Keep existing code** working
3. **No breaking changes**

**Example:**
```java
// Old code still works
ConceptService cs = Context.getConceptService();
Concept concept = cs.getConceptByUuid(uuid);

// New code uses ConceptManager
Concept concept = ConceptManager.getConceptByUuid(uuid);
```

### Phase 2: Refactor Existing Code

1. **Update HfeFormParser** to use ConceptManager
2. **Update CoreConceptAnswersServiceImpl** to delegate to ConceptManager
3. **Update ColumnMeta** to use AnswerMapper (already done)
4. **Update Validation.java** to use ConceptValidationService

### Phase 3: Deprecation (Optional)

1. **Mark old methods** as deprecated
2. **Add migration warnings**
3. **Remove in future version**

---

## Testing Guide

### Unit Tests

**Test Concept Resolution:**
```java
@Test
public void testConceptResolution() {
    // Test UUID resolution
    String uuid1 = ConceptManager.resolveConceptUuid("c607c80f-1ea9-4da3-bb88-6276ce8868dd");
    assertNotNull(uuid1);

    // Test mapping resolution
    String uuid2 = ConceptManager.resolveConceptUuid("CIEL:5089");
    assertNotNull(uuid2);

    // Should resolve to same concept
    assertEquals(uuid1, uuid2);
}
```

**Test Answer Mapping:**
```java
@Test
public void testAnswerMapping() {
    List<AnswerModel> answers = Arrays.asList(
        new AnswerModel("Positive", "uuid1", "uuid1"),
        new AnswerModel("Negative", "uuid2", "uuid2")
    );

    Map<String, String> map = AnswerMapper.toMap(answers);
    assertEquals(2, map.size());

    String uuid = AnswerMapper.getAnswerConceptUuid(answers, "Positive");
    assertEquals("uuid1", uuid);
}
```

**Test Validation:**
```java
@Test
public void testValidation() {
    ValidationResult result = ConceptValidationService.validateConceptExists("CIEL:5089");
    assertTrue(result.isValid());
    assertFalse(result.hasErrors());
}
```

---

## Additional Documentation

- **Concept Mapping Guide:** See `CONCEPT_MAPPING_GUIDE.md`
- **Datatype Inference:** See `DatatypeInferenceConfig` JavaDoc
- **API Reference:** See individual class JavaDocs
- **Module README:** See `README.md`

---

## Summary of Benefits

### ✅ Single Entry Point
- **ConceptAnswersService** is the single interface for all concept operations
- Eliminates scattered concept lookups across codebase
- Enforces consistent usage patterns
- Makes testing and mocking easier

### ✅ Proper Dependency Injection
- Services and parsers receive **ConceptAnswersService** via constructor injection
- Spring-managed lifecycle
- Clear dependencies and easier refactoring
- Supports proper unit testing

### ✅ Centralization
- All concept operations routed through one service
- **ConceptManager** provides internal implementation
- Consistent behavior across module
- Easier to maintain and debug

### ✅ Performance
- Module-wide caching (UUID cache, object cache, answers cache)
- Reduced database queries
- Batch operations support
- Cache shared across all service consumers

### ✅ Standardization
- Consistent answer mapping via **AnswerMapper**
- Unified label normalization
- Standard error messages
- Predictable behavior

### ✅ Validation
- Pre-flight concept checks
- Datatype validation
- Answer compatibility checking
- Clear error reporting

### ✅ Documentation
- Comprehensive guides
- Clear usage patterns
- Inline JavaDoc
- Examples and best practices

### ✅ Extensibility
- Easy to add new mapping sources
- Configurable inference rules
- Pluggable validation logic
- Service-oriented architecture supports future enhancements

---

## Support

For questions or issues:
1. Check this documentation
2. Review inline JavaDoc comments
3. See code examples in test files
4. Consult OpenMRS concept documentation
5. File issue in module repository
