# OpenMRS Data Import Module - Architecture

## High-Level Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         OpenMRS Data Import Module                          │
│                                                                             │
│  Converts OpenMRS forms (O3/HFE) to Excel templates for offline data       │
│  collection, then imports completed Excel files back into OpenMRS          │
└─────────────────────────────────────────────────────────────────────────────┘

                                    ┌──────────┐
                                    │  INPUT   │
                                    └────┬─────┘
                                         │
                        ┌────────────────┼────────────────┐
                        │                │                │
                   ┌────▼─────┐    ┌────▼─────┐    ┌────▼─────┐
                   │ O3 Form  │    │ HFE Form │    │  Form    │
                   │ (JSON)   │    │  (XML)   │    │  UUID    │
                   └──────────┘    └──────────┘    └──────────┘
                        │                │                │
                        └────────────────┼────────────────┘
                                         │
                                    ┌────▼─────────┐
                                    │ Form Type    │
                                    │ Detection    │
                                    └────┬─────────┘
                                         │
                        ┌────────────────┼────────────────┐
                        │                                 │
                  ┌─────▼──────┐                   ┌─────▼──────┐
                  │ O3 Parser  │                   │ HFE Parser │
                  └─────┬──────┘                   └─────┬──────┘
                        │                                 │
                        └────────────────┬────────────────┘
                                         │
                                  ┌──────▼──────┐
                                  │ FormModel   │
                                  │ (Unified)   │
                                  └──────┬──────┘
                                         │
                                  ┌──────▼──────────┐
                                  │ Concept         │
                                  │ Enrichment      │
                                  └──────┬──────────┘
                                         │
                                  ┌──────▼──────────┐
                                  │ Excel Template  │
                                  │ Generation      │
                                  └──────┬──────────┘
                                         │
                                    ┌────▼─────┐
                                    │ .xlsx    │
                                    │ Template │
                                    └────┬─────┘
                                         │
                              [User fills data offline]
                                         │
                                    ┌────▼─────┐
                                    │ Filled   │
                                    │ .xlsx    │
                                    └────┬─────┘
                                         │
                                  ┌──────▼──────────┐
                                  │ Excel Reader    │
                                  └──────┬──────────┘
                                         │
                                  ┌──────▼──────────┐
                                  │ Data Validation │
                                  └──────┬──────────┘
                                         │
                                  ┌──────▼──────────┐
                                  │ Encounter       │
                                  │ Creation        │
                                  └──────┬──────────┘
                                         │
                                    ┌────▼─────┐
                                    │ OpenMRS  │
                                    │ Database │
                                    └──────────┘
```

---

## Form Type Detection & Resolution

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          FORM RESOLUTION FLOW                              │
└────────────────────────────────────────────────────────────────────────────┘

                              ┌──────────────┐
                              │ Entry Point  │
                              │ - Form UUID  │
                              │ - Form Path  │
                              │ - Form JSON  │
                              └──────┬───────┘
                                     │
                              ┌──────▼───────────────────────────┐
                              │ FormResolverImpl.java            │
                              │                                  │
                              │ resolveForm(String identifier)   │
                              └──────┬───────────────────────────┘
                                     │
                ┌────────────────────┼────────────────────┐
                │                    │                    │
         ┌──────▼──────┐      ┌─────▼──────┐     ┌──────▼──────┐
         │ Is it JSON? │      │ Is it XML? │     │ Is it UUID? │
         └──────┬──────┘      └─────┬──────┘     └──────┬──────┘
                │                   │                    │
                │ Yes               │ Yes                │ Yes
                │                   │                    │
         ┌──────▼──────┐      ┌─────▼──────┐     ┌──────▼──────────────┐
         │ Parse JSON  │      │ Parse XML  │     │ Lookup in OpenMRS   │
         │ Schema      │      │ Root       │     │ FormService         │
         └──────┬──────┘      └─────┬──────┘     └──────┬──────────────┘
                │                   │                    │
         ┌──────▼────────┐   ┌──────▼────────┐   ┌──────▼──────────────┐
         │ Has "pages"   │   │ Root element  │   │ Form.resources      │
         │ array?        │   │ "htmlform"?   │   │ field (JSON/XML)    │
         └──────┬────────┘   └──────┬────────┘   └──────┬──────────────┘
                │                   │                    │
                │ Yes               │ Yes                │
                │                   │                    │
         ┌──────▼────────┐   ┢──────▼────────┐   ┌──────▼──────────────┐
         │ FormType:     │   │ FormType:     │   │ Parse content &     │
         │ O3_JSON       │   │ HFE           │   │ detect type         │
         └──────┬────────┘   └──────┬────────┘   └──────┬──────────────┘
                │                   │                    │
                └───────────────────┼────────────────────┘
                                    │
                             ┌──────▼───────┐
                             │ Route to     │
                             │ Parser       │
                             └──────────────┘
```

### Form Type Enum

```java
public enum FormType {
    O3_JSON("o3-json"),      // O3 forms with JSON schema
    HFE("hfe");              // HTML Form Entry forms (XML)

    private final String code;
}
```

---

## Detailed Parser Architecture

```
┌────────────────────────────────────────────────────────────────────────────┐
│                           PARSER LAYER                                     │
└────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────┐       ┌──────────────────────────────────┐
│      O3FormParser.java          │       │      HfeFormParser.java          │
│                                 │       │                                  │
│  Input: JSON Schema             │       │  Input: HFE XML                  │
│  Injected: ConceptAnswersService│       │  Injected: ConceptAnswersService │
│  ┌──────────────────────────┐  │       │  ┌───────────────────────────┐  │
│  │ Parse Schema Structure   │  │       │  │ Parse DOM Tree            │  │
│  │ - pages[]                │  │       │  │ - <htmlform>              │  │
│  │ - sections[]             │  │       │  │ - <obs>, <obsgroup>       │  │
│  │ - questions[]            │  │       │  │ - attributes              │  │
│  └──────────┬───────────────┘  │       │  └──────────┬────────────────┘  │
│             │                   │       │             │                    │
│  ┌──────────▼───────────────┐  │       │  ┌──────────▼────────────────┐  │
│  │ Extract Question Options │  │       │  │ Extract Obs Elements      │  │
│  │ - concept (direct UUID)  │  │       │  │ - conceptId (UUID/ID/map) │  │
│  │ - rendering              │  │       │  │ - style                   │  │
│  │ - answers[]              │  │       │  │ - answerConceptIds        │  │
│  └──────────┬───────────────┘  │       │  └──────────┬────────────────┘  │
│             │                   │       │             │                    │
│  ┌──────────▼───────────────┐  │       │  ┌──────────▼────────────────┐  │
│  │ Datatype Inference       │  │       │  │ Datatype Inference        │  │
│  │ - From rendering type    │  │       │  │ Priority:                 │  │
│  │ - Direct mapping         │  │       │  │ 1. Widget structure       │  │
│  │                          │  │       │  │ 2. Style attribute        │  │
│  │ DatatypeInferenceConfig  │  │       │  │ 3. Answer attributes      │  │
│  │ .fromO3Rendering()       │  │       │  │ 4. Concept datatype       │  │
│  │                          │  │       │  │                           │  │
│  │                          │  │       │  │ DatatypeInferenceConfig   │  │
│  │                          │  │       │  │ .fromStyleAttribute()     │  │
│  │                          │  │       │  │ .fromWidgetElement()      │  │
│  │                          │  │       │  │ .fromAnswerAttributes()   │  │
│  └──────────┬───────────────┘  │       │  └──────────┬────────────────┘  │
│             │                   │       │             │                    │
│  ┌──────────▼───────────────┐  │       │  ┌──────────▼────────────────┐  │
│  │ Concept Resolution       │  │       │  │ Concept Resolution        │  │
│  │ - Direct UUIDs           │  │       │  │ conceptAnswersService     │  │
│  │ - No mapping needed      │  │       │  │ .resolveConceptUuid()     │  │
│  │                          │  │       │  │                           │  │
│  │                          │  │       │  │ Strategies:               │  │
│  │                          │  │       │  │ 1. UUID lookup            │  │
│  │                          │  │       │  │ 2. Numeric ID lookup      │  │
│  │                          │  │       │  │ 3. Mapping (CIEL:5089)    │  │
│  └──────────┬───────────────┘  │       │  └──────────┬────────────────┘  │
│             │                   │       │             │                    │
└─────────────┼───────────────────┘       └─────────────┼────────────────────┘
              │                                         │
              └─────────────┬───────────────────────────┘
                            │
                     ┌──────▼──────┐
                     │ FormModel   │
                     │             │
                     │ - SheetModel│
                     │ - FieldModel│
                     └─────────────┘
```

### O3 Form Example

```json
{
  "name": "Vitals Form",
  "pages": [{
    "label": "Vitals",
    "sections": [{
      "questions": [{
        "id": "weight",
        "label": "Weight (kg)",
        "type": "obs",
        "questionOptions": {
          "concept": "5089a0a1-...",    ← Direct UUID
          "rendering": "number"          ← Maps to NUMERIC
        }
      }]
    }]
  }]
}
```

### HFE Form Example

```xml
<htmlform>
  <obs conceptId="CIEL:5089"           ← Mapping code
       labelText="Weight (kg)"
       style="number"/>                ← Maps to NUMERIC
</htmlform>
```

---

## Concept Management Layer

```
┌────────────────────────────────────────────────────────────────────────────┐
│                      CONCEPT MANAGEMENT LAYER                              │
│                     (Unified for O3 & HFE)                                 │
└────────────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────────────────┐
                    │   ConceptAnswersService         │
                    │   (Single Entry Point)          │
                    │                                 │
                    │  - Spring-managed service       │
                    │  - Dependency injection         │
                    │  - Used by all parsers/services │
                    └────────────┬────────────────────┘
                                 │
                                 │ delegates to
                                 ▼
                         ┌──────────────────────┐
                         │   ConceptManager     │
                         │   (Internal Hub)     │
                         └──────────┬───────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
┌───────▼──────────┐    ┌───────────▼────────┐    ┌───────────▼──────────┐
│ Concept          │    │ Answer              │    │ Concept              │
│ Resolution       │    │ Loading             │    │ Validation           │
│                  │    │                     │    │                      │
│ resolveUuid()    │    │ loadAnswers()       │    │ isValidConcept()     │
│                  │    │ loadSingleConcept() │    │ isConceptOfType()    │
│ Strategies:      │    │                     │    │ isValidAnswer()      │
│ 1. UUID          │    │ Sources:            │    │                      │
│ 2. Numeric ID    │    │ - Concept answers   │    │ ConceptValidation    │
│ 3. Mapping code  │    │ - Set members       │    │ Service (detailed)   │
│                  │    │                     │    │                      │
│ Cache: ✓         │    │ Cache: ✓            │    │                      │
└──────────────────┘    └─────────────────────┘    └──────────────────────┘

        │                           │                           │
        └───────────────────────────┼───────────────────────────┘
                                    │
                         ┌──────────▼───────────┐
                         │  OpenMRS             │
                         │  ConceptService      │
                         │                      │
                         │  - getConceptByUuid  │
                         │  - getConcept (ID)   │
                         │  - getConceptByMap   │
                         └──────────────────────┘

┌────────────────────────────────────────────────────────────────────────────┐
│                         CACHE ARCHITECTURE                                 │
└────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│ ConceptManager - Three-Level Cache (ConcurrentHashMap)                  │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │ Level 1: UUID Cache                                            │    │
│  │ Map<String, String>                                            │    │
│  │                                                                 │    │
│  │ "CIEL:5089"      → "c607c80f-1ea9-4da3-bb88-6276ce8868dd"     │    │
│  │ "5089"           → "c607c80f-1ea9-4da3-bb88-6276ce8868dd"     │    │
│  │ "c607c80f-..."   → "c607c80f-1ea9-4da3-bb88-6276ce8868dd"     │    │
│  └────────────────────────────────────────────────────────────────┘    │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │ Level 2: Concept Object Cache                                  │    │
│  │ Map<String, Concept>                                            │    │
│  │                                                                 │    │
│  │ "c607c80f-..." → Concept{name: "Weight", datatype: NM, ...}   │    │
│  └────────────────────────────────────────────────────────────────┘    │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │ Level 3: Answers Cache                                          │    │
│  │ Map<String, List<AnswerModel>>                                  │    │
│  │                                                                 │    │
│  │ "uuid-gender" → [AnswerModel{Male}, AnswerModel{Female}, ...]  │    │
│  └────────────────────────────────────────────────────────────────┘    │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Template Generation Flow

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    TEMPLATE GENERATION PIPELINE                            │
└────────────────────────────────────────────────────────────────────────────┘

┌──────────────┐
│  FormModel   │
│  (Unified)   │
└──────┬───────┘
       │
       │ Contains:
       │ - formUuid, formName, formVersion
       │ - formType (O3_JSON or HFE)
       │ - List<SheetModel> sheets
       │
┌──────▼─────────────────────────────────────────────────────────┐
│ TemplateGenerationServiceImpl.java                             │
│                                                                 │
│ generateTemplate(formUuid, includePatient, locationUuid)       │
└──────┬──────────────────────────────────────────────────────────┘
       │
       │ Step 1: Resolve Form
       ▼
┌──────────────────────┐
│ FormResolverImpl     │
│ - Detect type        │
│ - Route to parser    │
└──────┬───────────────┘
       │
       │ Step 2: Enrich with Concept Data
       ▼
┌──────────────────────────────────────────────────────────────┐
│ FOR EACH CODED FIELD:                                        │
│                                                              │
│ if (field.datatype == CODED && field.answers.isEmpty()) {   │
│     if (field.uiSelectSourceType != null) {                 │
│         // Load from special sources                        │
│         populateUiSelectExtendedAnswers()                   │
│     } else if (field.conceptUuid != null) {                 │
│         // Load from concept                                │
│         field.answers = ConceptManager                      │
│             .loadAnswersForConcept(field.conceptUuid)       │
│     }                                                        │
│ }                                                            │
│                                                              │
│ if (field.answers has missing labels) {                     │
│     for (answer : field.answers) {                          │
│         if (answer.label == null) {                         │
│             AnswerModel loaded = ConceptManager             │
│                 .loadSingleConceptAsAnswer(answer.uuid)     │
│             answer.label = loaded.label                     │
│         }                                                    │
│     }                                                        │
│ }                                                            │
└──────┬───────────────────────────────────────────────────────┘
       │
       │ Step 3: Export to Excel
       ▼
┌──────────────────────────────────────────────────────────────┐
│ ExcelTemplateExporter.java                                   │
│                                                              │
│ export(FormModel, includePatient, locationUuid)             │
└──────┬───────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│ Excel Workbook Structure:                                    │
│                                                              │
│ ┌────────────────────────────────────────────┐              │
│ │ 1. Patient Sheet (if includePatient=true)  │              │
│ │    - Given Name, Family Name, Gender, DOB  │              │
│ │    - Patient Ref (formula-generated)       │              │
│ │    - Protected sheet                       │              │
│ └────────────────────────────────────────────┘              │
│                                                              │
│ ┌────────────────────────────────────────────┐              │
│ │ 2. Data Sheets (one per SheetModel)        │              │
│ │    - Patient Ref column (dropdown)         │              │
│ │    - Field columns with appropriate types: │              │
│ │      • DATE: date format                   │              │
│ │      • DATETIME: datetime format           │              │
│ │      • CODED: dropdown from __lists        │              │
│ │      • NUMERIC: number format              │              │
│ │      • TEXT: text format                   │              │
│ │      • BOOLEAN: Yes/No dropdown            │              │
│ │    - Frozen header row                     │              │
│ └────────────────────────────────────────────┘              │
│                                                              │
│ ┌────────────────────────────────────────────┐              │
│ │ 3. __lists Sheet (hidden)                  │              │
│ │    - Named ranges for dropdowns            │              │
│ │    - Each CODED field gets a column        │              │
│ │    - Gender options (Male/Female/Unknown)  │              │
│ └────────────────────────────────────────────┘              │
│                                                              │
│ ┌────────────────────────────────────────────┐              │
│ │ 4. __meta Sheet (hidden)                   │              │
│ │    - Template metadata                     │              │
│ │    - Form mappings                         │              │
│ │    - Column metadata for each field        │              │
│ └────────────────────────────────────────────┘              │
│                                                              │
│ ┌────────────────────────────────────────────┐              │
│ │ 5. __concepts Sheet (hidden)               │              │
│ │    - Concept details for validation        │              │
│ │    - Question & answer concept metadata    │              │
│ │    - Datatype and class information        │              │
│ └────────────────────────────────────────────┘              │
│                                                              │
│ ┌────────────────────────────────────────────┐              │
│ │ 6. __longStrings Sheet (if needed)         │              │
│ │    - For JSON strings > 32k chars          │              │
│ │    - Referenced by placeholder in __meta   │              │
│ └────────────────────────────────────────────┘              │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────┐
│  .xlsx File  │
│  Download    │
└──────────────┘
```

---

## Data Import Flow

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         DATA IMPORT PIPELINE                               │
└────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│ Filled .xlsx     │
│ File Upload      │
└────────┬─────────┘
         │
         ▼
┌────────────────────────────────────────────────────────────┐
│ WorkbookImportServiceImpl.java                             │
│                                                            │
│ importWorkbook(InputStream, User, reportCallback)          │
└────────┬───────────────────────────────────────────────────┘
         │
         │ Step 1: Load Metadata
         ▼
┌────────────────────────────────────────────────────────────┐
│ ExcelReader.java                                           │
│                                                            │
│ readWorkbook(InputStream)                                  │
│                                                            │
│ ┌────────────────────────────────────────────────────┐    │
│ │ Read __meta Sheet                                  │    │
│ │ - Template version, form mappings                  │    │
│ │ - Column metadata → ColumnMeta objects             │    │
│ │   • conceptUuid                                    │    │
│ │   • datatype (NUMERIC, DATE, CODED, etc.)         │    │
│ │   • answers (Map<String, String>)                 │    │
│ │   • validation rules (min, max, required)         │    │
│ └────────────────────────────────────────────────────┘    │
│                                                            │
│ ┌────────────────────────────────────────────────────┐    │
│ │ Read __concepts Sheet (if present)                 │    │
│ │ - Question/answer concept mappings                 │    │
│ │ - Datatype metadata for validation                │    │
│ └────────────────────────────────────────────────────┘    │
│                                                            │
│ ┌────────────────────────────────────────────────────┐    │
│ │ Read Data Sheets                                   │    │
│ │ - Skip header row                                  │    │
│ │ - Process rows 2-1001                              │    │
│ │ - Create RowData objects                           │    │
│ └────────────────────────────────────────────────────┘    │
└────────┬───────────────────────────────────────────────────┘
         │
         │ Returns: WorkbookData
         │ - FormMeta (per sheet)
         │ - List<RowData> (per sheet)
         │
         ▼
┌────────────────────────────────────────────────────────────┐
│ Step 2: Process Each Sheet                                 │
│                                                            │
│ FOR EACH SHEET:                                            │
│   FOR EACH ROW:                                            │
│     1. Skip if all cells empty                            │
│     2. Extract patient reference                          │
│     3. Find/Create Patient                                │
│     4. Process row → Create Encounter                     │
└────────┬───────────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────────┐
│ Step 3: Row Processing                                     │
│                                                            │
│ processRow(RowData, FormMeta, Patient, User)              │
│                                                            │
│ ┌────────────────────────────────────────────────────┐    │
│ │ A. Validation Phase                                │    │
│ │                                                    │    │
│ │ FOR EACH CELL:                                     │    │
│ │   - Get ColumnMeta (conceptUuid, datatype, etc)   │    │
│ │   - Validate value against datatype               │    │
│ │   - Check required fields                         │    │
│ │   - Validate numeric ranges                       │    │
│ │   - Validate coded answers                        │    │
│ │                                                    │    │
│ │ Using: ConceptValidationService                   │    │
│ └────────────────────────────────────────────────────┘    │
│                                                            │
│ ┌────────────────────────────────────────────────────┐    │
│ │ B. Observation Blueprint Creation                  │    │
│ │                                                    │    │
│ │ FOR EACH CELL with value:                         │    │
│ │   - Get conceptUuid from ColumnMeta               │    │
│ │   - Convert cell value to appropriate type        │    │
│ │                                                    │    │
│ │   IF datatype == CODED:                           │    │
│ │     answerUuid = AnswerMapper                     │    │
│ │       .getAnswerConceptUuid(column.answers, value)│    │
│ │     blueprint = ObsBlueprint                      │    │
│ │       .coded(conceptUuid, answerUuid)             │    │
│ │                                                    │    │
│ │   ELSE IF datatype == NUMERIC:                    │    │
│ │     numValue = Double.parseDouble(value)          │    │
│ │     blueprint = ObsBlueprint                      │    │
│ │       .numeric(conceptUuid, numValue)             │    │
│ │                                                    │    │
│ │   ELSE IF datatype == DATE:                       │    │
│ │     date = parseDate(value)                       │    │
│ │     blueprint = ObsBlueprint                      │    │
│ │       .date(conceptUuid, date)                    │    │
│ │                                                    │    │
│ │   ... (similar for DATETIME, BOOLEAN, TEXT)       │    │
│ └────────────────────────────────────────────────────┘    │
└────────┬───────────────────────────────────────────────────┘
         │
         │ List<ObsBlueprint>
         ▼
┌────────────────────────────────────────────────────────────┐
│ Step 4: Encounter Creation                                 │
│                                                            │
│ ObservationServiceImpl.java                               │
│                                                            │
│ ┌────────────────────────────────────────────────────┐    │
│ │ 1. Create/Find Encounter                           │    │
│ │    - Patient                                       │    │
│ │    - EncounterType (from FormMeta)                │    │
│ │    - EncounterDatetime (from row)                 │    │
│ │    - Location (from FormMeta or session)          │    │
│ │    - Provider (from row or user)                  │    │
│ └────────────────────────────────────────────────────┘    │
│                                                            │
│ ┌────────────────────────────────────────────────────┐    │
│ │ 2. Create Observations                             │    │
│ │                                                    │    │
│ │ FOR EACH ObsBlueprint:                            │    │
│ │   Obs obs = new Obs()                             │    │
│ │   obs.setPerson(patient)                          │    │
│ │   obs.setConcept(concept)                         │    │
│ │   obs.setEncounter(encounter)                     │    │
│ │                                                    │    │
│ │   IF blueprint.type == CODED:                     │    │
│ │     obs.setValueCoded(answerConcept)              │    │
│ │   ELSE IF blueprint.type == NUMERIC:              │    │
│ │     obs.setValueNumeric(value)                    │    │
│ │   ELSE IF blueprint.type == DATE:                 │    │
│ │     obs.setValueDate(date)                        │    │
│ │   ... etc                                         │    │
│ │                                                    │    │
│ │   encounter.addObs(obs)                           │    │
│ └────────────────────────────────────────────────────┘    │
│                                                            │
│ ┌────────────────────────────────────────────────────┐    │
│ │ 3. Save Encounter                                  │    │
│ │    - EncounterService.saveEncounter(encounter)    │    │
│ │    - All observations saved atomically            │    │
│ └────────────────────────────────────────────────────┘    │
└────────┬───────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────┐
│ OpenMRS Database │
│ - Encounter      │
│ - Observations   │
└──────────────────┘
```

---

## Form Type Comparison

```
┌────────────────────────────────────────────────────────────────────────────┐
│                     O3 vs HFE FORM COMPARISON                              │
└────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────┬─────────────────────────────────────────┐
│         O3 FORM (JSON)          │         HFE FORM (XML)                  │
├─────────────────────────────────┼─────────────────────────────────────────┤
│ Format: JSON Schema             │ Format: XML                             │
│ Parser: O3FormParser.java       │ Parser: HfeFormParser.java              │
│ Structure: Hierarchical         │ Structure: DOM-based                    │
├─────────────────────────────────┼─────────────────────────────────────────┤
│ CONCEPT IDENTIFICATION          │ CONCEPT IDENTIFICATION                  │
│ - Direct UUIDs only             │ - UUIDs, Numeric IDs, Mappings          │
│ - concept: "uuid-string"        │ - conceptId="CIEL:5089"                 │
│                                 │ - conceptId="5089"                      │
│                                 │ - conceptId="uuid-string"               │
├─────────────────────────────────┼─────────────────────────────────────────┤
│ DATATYPE INFERENCE              │ DATATYPE INFERENCE                      │
│ - rendering: "number"           │ - style="number"                        │
│ - rendering: "date"             │ - style="date"                          │
│ - rendering: "select"           │ - style="dropdown"                      │
│ - Direct mapping                │ - Multi-level priority                  │
│                                 │   1. Widget structure                   │
│                                 │   2. Style attribute                    │
│                                 │   3. Answer attributes                  │
│                                 │   4. Concept datatype                   │
├─────────────────────────────────┼─────────────────────────────────────────┤
│ ANSWERS                         │ ANSWERS                                 │
│ - answers: [                    │ - answerConceptIds="uuid1,uuid2"        │
│     {concept: "uuid1",          │ - answerConceptIds="CIEL:1,CIEL:2"      │
│      label: "Option 1"},        │ - answerLabels="Opt1,Opt2"              │
│     {concept: "uuid2"}          │ - Can mix formats                       │
│   ]                             │                                         │
│ - Or just concept UUIDs         │                                         │
├─────────────────────────────────┼─────────────────────────────────────────┤
│ GROUPING                        │ GROUPING                                │
│ - Sections within pages         │ - <obsgroup>                            │
│ - No explicit obs groups        │ - groupingConceptId                     │
│ - Flattened in export           │ - Nested structure                      │
├─────────────────────────────────┼─────────────────────────────────────────┤
│ CONDITIONAL DISPLAY             │ CONDITIONAL DISPLAY                     │
│ - hide: {                       │ - hideWhenExpression                    │
│     hideWhenExpression: "expr"  │ - showDate, hideDate                    │
│   }                             │ - showId, hideId                        │
│ - JavaScript-based              │ - when attribute                        │
│                                 │ - JavaScript-based                      │
├─────────────────────────────────┼─────────────────────────────────────────┤
│ SPECIAL FEATURES                │ SPECIAL FEATURES                        │
│ - UI Select Extended            │ - Repeat structures                     │
│ - validators                    │ - Complex widgets                       │
│ - calculations                  │ - Lookup expressions                    │
│                                 │ - Drug selection                        │
│                                 │ - Drawing editor                        │
├─────────────────────────────────┼─────────────────────────────────────────┤
│ STORAGE                         │ STORAGE                                 │
│ - Form.resources (JSON)         │ - Form.resources (XML string)           │
│ - FormService.getFormByUuid()   │ - FormService.getFormByUuid()           │
├─────────────────────────────────┼─────────────────────────────────────────┤
│ EXAMPLE                         │ EXAMPLE                                 │
│ {                               │ <htmlform>                              │
│   "pages": [{                   │   <obs conceptId="CIEL:5089"            │
│     "sections": [{              │        labelText="Weight"               │
│       "questions": [{           │        style="number"/>                 │
│         "id": "weight",         │   <obs conceptId="ICRC:Gender"          │
│         "type": "obs",          │        answerConceptIds=                │
│         "questionOptions": {    │          "CIEL:1,CIEL:2"                │
│           "concept": "5089a..", │        style="radio"/>                  │
│           "rendering": "number" │ </htmlform>                             │
│         }                       │                                         │
│       }]                        │                                         │
│     }]                          │                                         │
│   }]                            │                                         │
│ }                               │                                         │
└─────────────────────────────────┴─────────────────────────────────────────┘
```

---

## Component Responsibilities

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         COMPONENT DIAGRAM                                  │
└────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                          SERVICE LAYER                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ TemplateGenerationService                                    │      │
│  │ - Main entry point for template generation                  │      │
│  │ - Orchestrates form resolution, parsing, enrichment, export │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ WorkbookImportService                                        │      │
│  │ - Main entry point for data import                          │      │
│  │ - Orchestrates reading, validation, encounter creation      │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ ConceptAnswersService                                        │      │
│  │ - Legacy interface (now delegates to ConceptManager)        │      │
│  │ - Loads concept answers and labels                          │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ ObservationService                                           │      │
│  │ - Creates observations from blueprints                      │      │
│  │ - Validates concept datatypes                               │      │
│  └──────────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                         RESOLVER LAYER                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ FormResolver                                                 │      │
│  │ - Detects form type (O3 vs HFE)                             │      │
│  │ - Routes to appropriate parser                              │      │
│  │ - Handles UUID lookup, file path, direct content           │      │
│  └──────────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                          PARSER LAYER                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ O3FormParser                                                 │      │
│  │ - Parses JSON schema structure                              │      │
│  │ - Extracts pages, sections, questions                      │      │
│  │ - Uses direct UUIDs                                         │      │
│  │ - Simple datatype inference from rendering                 │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ HfeFormParser                                                │      │
│  │ - Parses XML DOM structure                                  │      │
│  │ - Extracts <obs>, <obsgroup> elements                       │      │
│  │ - Multi-strategy concept resolution                        │      │
│  │ - Complex datatype inference (4-level priority)            │      │
│  │ - Handles repeat structures, complex widgets              │      │
│  └──────────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                       EXPORT/IMPORT LAYER                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ ExcelTemplateExporter                                        │      │
│  │ - Creates Excel workbook from FormModel                     │      │
│  │ - Generates data sheets with proper formatting             │      │
│  │ - Creates metadata sheets (__meta, __concepts)             │      │
│  │ - Applies dropdowns, date formats, validation              │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ ExcelReader                                                  │      │
│  │ - Reads Excel workbook                                      │      │
│  │ - Parses metadata sheets                                    │      │
│  │ - Creates ColumnMeta, FormMeta, RowData objects            │      │
│  │ - Validates structure                                       │      │
│  └──────────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                      UTILITY LAYER (NEW)                                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ ConceptManager                                               │      │
│  │ - Central concept operations hub                            │      │
│  │ - Multi-strategy resolution (UUID, ID, mapping)            │      │
│  │ - Three-level caching                                       │      │
│  │ - Concept name loading                                      │      │
│  │ - Answer loading                                            │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ AnswerMapper                                                 │      │
│  │ - Standardized answer operations                           │      │
│  │ - List ↔ Map conversions                                   │      │
│  │ - Normalized label matching                                │      │
│  │ - Label enrichment                                          │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ ConceptValidationService                                     │      │
│  │ - Pre-flight concept validation                            │      │
│  │ - Datatype compatibility checking                          │      │
│  │ - Coded answer validation                                  │      │
│  │ - Batch validation                                         │      │
│  │ - Detailed error messages                                  │      │
│  └──────────────────────────────────────────────────────────────┘      │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ DatatypeInferenceConfig                                      │      │
│  │ - Documented inference rules                               │      │
│  │ - HL7 code mappings                                        │      │
│  │ - Style keyword mappings                                   │      │
│  │ - O3 rendering mappings                                    │      │
│  │ - Widget element mappings                                  │      │
│  └──────────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                         MODEL LAYER                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────┐      │
│  │ FormModel         - Represents entire form                  │      │
│  │ SheetModel        - Represents one Excel sheet              │      │
│  │ FieldModel        - Represents one form field               │      │
│  │ AnswerModel       - Represents coded answer option          │      │
│  │ ObsBlueprint      - Template for observation creation       │      │
│  │ ColumnMeta        - Metadata for Excel column               │      │
│  │ FormMeta          - Metadata for form/sheet                 │      │
│  │ RowData           - One row of data from Excel              │      │
│  │ WorkbookData      - Entire workbook data structure          │      │
│  └──────────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Key Decision Points

```
┌────────────────────────────────────────────────────────────────────────────┐
│                      DECISION FLOW DIAGRAMS                                │
└────────────────────────────────────────────────────────────────────────────┘

FORM TYPE DETECTION
═══════════════════

Input: Form identifier (UUID/path/content)
    │
    ├─ Is it valid JSON?
    │   └─ Yes → Has "pages" array?
    │       └─ Yes → O3_JSON
    │       └─ No  → Check XML
    │
    ├─ Is it valid XML?
    │   └─ Yes → Root element "htmlform"?
    │       └─ Yes → HFE
    │       └─ No  → ERROR
    │
    └─ Is it a UUID?
        └─ Yes → Load from FormService
            └─ Parse form.resources
                └─ Recurse to type detection


CONCEPT RESOLUTION (HFE)
════════════════════════

Input: conceptId attribute value
    │
    ├─ Try: UUID lookup
    │   └─ getConceptByUuid(value)
    │       └─ Found? → RETURN uuid
    │
    ├─ Try: Numeric ID
    │   └─ Integer.parseInt(value)
    │       └─ getConcept(id)
    │           └─ Found? → RETURN concept.uuid
    │
    └─ Try: Mapping code (contains ":")
        └─ Split by ":"
            └─ getConceptByMapping(code, source)
                └─ Found? → RETURN concept.uuid
                └─ Not found? → RETURN null


DATATYPE INFERENCE (HFE)
════════════════════════

Input: <obs> element
    │
    ├─ Priority 1: Widget structure
    │   └─ Element name in (date, datetime, checkbox, select)?
    │       └─ Yes → RETURN mapped datatype
    │
    ├─ Priority 2: Style attribute
    │   └─ style="date|number|radio|..."?
    │       └─ Yes → RETURN mapped datatype
    │
    ├─ Priority 3: Answer attributes
    │   └─ Has answerConceptIds or answerLabels?
    │       └─ Yes → RETURN CODED
    │
    └─ Priority 4: Concept datatype
        └─ Lookup concept, get HL7 code
            └─ Map HL7 → FieldDatatype
                └─ RETURN datatype (default: TEXT)


ANSWER RESOLUTION
═════════════════

Input: CODED field
    │
    ├─ Has explicit answers in form?
    │   └─ Yes → Use those answers
    │
    ├─ Has uiSelectSourceType?
    │   └─ Yes → Load from dynamic source
    │       (encounterRole, location, provider, etc.)
    │
    └─ Has conceptUuid only?
        └─ Yes → Load from concept
            ├─ Has concept.answers?
            │   └─ Yes → Use concept answers
            │
            └─ Has concept.setMembers?
                └─ Yes → Use set members
```

---

## Performance Considerations

```
┌────────────────────────────────────────────────────────────────────────────┐
│                      PERFORMANCE OPTIMIZATIONS                             │
└────────────────────────────────────────────────────────────────────────────┘

CACHING STRATEGY
════════════════

┌─────────────────────────────────────────────────────────────┐
│ ConceptManager - Module-Wide Caches                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ UUID Cache (ConcurrentHashMap)                             │
│ - Persists across form processing sessions                 │
│ - Prevents repeated resolution                             │
│ - Key: "CIEL:5089" → Value: "uuid-xxx"                    │
│                                                             │
│ Object Cache (ConcurrentHashMap)                           │
│ - Prevents repeated database queries                       │
│ - Key: "uuid-xxx" → Value: Concept object                 │
│                                                             │
│ Answers Cache (ConcurrentHashMap)                          │
│ - Prevents repeated answer loading                         │
│ - Key: "uuid-xxx" → Value: List<AnswerModel>              │
└─────────────────────────────────────────────────────────────┘

BATCH OPERATIONS
════════════════

Template Generation:
- Single database query per concept (cached)
- Batch concept lookups when possible
- Reuse FormModel for multiple exports

Import:
- Read entire workbook once
- Process rows in batches (commit every N encounters)
- Validation errors collected, not thrown immediately

LAZY LOADING
════════════

- Concept answers loaded only for CODED fields
- Metadata sheets read only when present
- Long strings sheet created only when needed


PARALLEL PROCESSING
═══════════════════

Current: Sequential processing
Future: Parallel row processing (thread-safe with cache)
```

---

## Error Handling

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         ERROR HANDLING STRATEGY                            │
└────────────────────────────────────────────────────────────────────────────┘

FORM PARSING ERRORS
═══════════════════

├─ Invalid JSON/XML format
│   └─ Throw: IllegalArgumentException
│       Message: "Invalid form format"
│
├─ Missing required attributes
│   └─ Log warning, use defaults
│       Continue parsing
│
└─ Concept not found
    └─ Log warning, set concept to null
        Continue parsing (validation later)


TEMPLATE GENERATION ERRORS
═══════════════════════════

├─ Concept resolution failure
│   └─ Log warning with concept ID
│       Skip field or use placeholder
│
├─ Answer loading failure
│   └─ Log warning
│       Create empty answer list
│
└─ Excel generation failure
    └─ Throw: IllegalStateException
        Rollback, return error to user


DATA IMPORT ERRORS
══════════════════

├─ Validation errors (per row)
│   └─ Collect in quarantine report
│       Continue processing other rows
│       Generate Excel with errors highlighted
│
├─ Concept mismatch
│   └─ Add to quarantine
│       Error: "Concept not found: {conceptUuid}"
│
├─ Datatype mismatch
│   └─ Add to quarantine
│       Error: "Expected NUMERIC, got '{value}'"
│
└─ Required field missing
    └─ Add to quarantine
        Error: "Required field '{label}' is empty"


QUARANTINE REPORT
═════════════════

Sheet: "__quarantine"
Columns:
- Row Number
- Error Type
- Field Name
- Value
- Error Message
- Suggested Fix

User can:
1. Review errors
2. Fix in Excel
3. Re-upload
```

---

## Summary

This architecture provides:

1. **Unified Processing** - Both O3 and HFE forms → common FormModel
2. **Flexible Concept Resolution** - UUIDs, IDs, mapping codes (CIEL, LOINC, etc.)
3. **Robust Caching** - Module-wide concept caching for performance
4. **Standardized Operations** - Centralized concept, answer, validation utilities
5. **Excel Round-Trip** - OpenMRS forms → Excel templates → OpenMRS observations
6. **Error Recovery** - Quarantine reports for validation errors
7. **Extensibility** - Easy to add new form types, concept sources, datatypes

**Key Files:**
- `FormResolverImpl.java` - Form type detection & routing
- `O3FormParser.java` - O3 JSON parsing
- `HfeFormParser.java` - HFE XML parsing
- `ConceptManager.java` - Central concept operations
- `ExcelTemplateExporter.java` - Excel generation
- `ExcelReader.java` - Excel parsing
- `WorkbookImportServiceImpl.java` - Import orchestration
