# OpenMRS Excel Import and Export Module

A focused OpenMRS module that converts both O3 and OpenMRS 2 (HFE) forms to Excel templates using a clean model-based approach.

## What It Does

Converts OpenMRS forms (both O3 JSON schemas and HTML Form Entry XML) into Excel templates for offline data collection, then processes completed Excel files back into OpenMRS using a robust model-based system.

## Key Features

- **Dual Form Support** - Converts both O3 JSON forms and HTML Form Entry (HFE) forms
- **Pluggable Architecture** - Form resolver SPI automatically detects form type
- **Excel Template Generation** - Creates Excel templates with validation, dropdowns, and multi-select support
- **HFE Advanced Parsing** - Supports `<repeat>`, `<obsgroup>`, concept mappings, widget detection
- **Dry-Run Validation** - Validates data before import with detailed error reporting
- **Color-Coded Reports** - Download Excel reports with error rows highlighted
- **Partial Import** - Imports valid rows even if some rows fail validation
- **REST API** - Programmatic template generation via REST endpoints

## Workflow

1. **Generate Template** → Download Excel template from form UUIDs (O3 or HFE forms)
2. **Fill Data** → Enter patient and encounter data offline
3. **Upload & Validate** → Dry-run to check for errors
4. **Download Report** → Get color-coded Excel with highlighted errors (optional)
5. **Fix Errors** → Correct issues in the highlighted rows
6. **Commit** → Import valid data into OpenMRS

## Form Type Support

### O3 Forms (JSON Schema)
The module automatically detects O3 forms by looking for JSON schema resources attached to the form. It parses the schema structure, extracts fields, and generates appropriate Excel columns with validation.

### HTML Form Entry (HFE) Forms
The module automatically detects HFE forms by looking for "htmlform" resources. Supported HFE elements:

**Data Collection Elements:**
- `<obs conceptId="..."/>` - Observation fields (supports UUID, numeric ID, or mappings like "CIEL:5089")
- `<obsgroup groupingConceptId="...">` - Observation groups (flattened into sheet)
- `<encounterDate/>` - Encounter date/time field
- `<encounterLocation/>` - Location picker (loads all non-retired locations)
- `<encounterProvider/>` - Provider picker (loads all non-retired providers)

**HFE Directives:**
- `<repeat with="[...]">` - Template expansion with placeholder substitution (`{0}`, `{1}`, `{2}`)

**Concept Resolution:**
- UUID: `conceptId="5089AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"`
- Numeric ID: `conceptId="5089"`
- Mapping: `conceptId="CIEL:5089"` or `conceptId="PIH:Weight"`

**Datatype Inference (4-tier priority):**
1. **Widget structure** - Detects `<select>`, `<input type="checkbox">`, `<input type="radio">`, etc.
2. **Style attribute** - e.g., `style="date"`, `style="text"`, `style="textarea"`
3. **Answer attributes** - `answerConceptIds` / `answerConceptId` → CODED
4. **Concept datatype** - Fetches from concept dictionary (including drug concepts)

**Answer Resolution:**
- Multiple answers: `answerConceptIds="CIEL:1065,CIEL:1066"` with optional `answerLabels="Yes,No"`
- Single answer: `answerConceptId="CIEL:1065"` with optional `answerLabel="Yes"` (for single checkboxes)
- Auto-fetch from concept: If no answers specified, loads from concept's answer set

**Ignored Elements** (non-data collection):
- `<uiInclude>`, `<includeFragment>` - UI resources (CSS/JS)
- `<script>` - JavaScript code
- `<uimessage>` - i18n message codes
- `<lookup>` - Dynamic value lookups (display-only)
- `<ifMode>` - Conditional rendering directives

### Example: Processing the Care Plan HFE Form

For the provided care plan form with `<repeat>` directives:

```xml
<repeat with="['icrcforms.wttc.carePlan.hipSurgery','ICRC:HIP_SURGERY', 'ICRC:HIP_SURGERY_DISPOSITION_COMMENT'],
              ['icrcforms.wttc.carePlan.kneeSurgery','ICRC:KNEE_SURGERY','ICRC:KNEE_SURGERY_DISPOSITION_COMMENT']">
    <obs conceptId="{1}" />
    <obs conceptId="{2}" style="text" />
</repeat>
```

**The parser will:**
1. Detect the `<repeat>` tag and extract 2 tuples
2. Expand the template twice:
   - First iteration: Create fields for `ICRC:HIP_SURGERY` (coded) and `ICRC:HIP_SURGERY_DISPOSITION_COMMENT` (text)
   - Second iteration: Create fields for `ICRC:KNEE_SURGERY` (coded) and `ICRC:KNEE_SURGERY_DISPOSITION_COMMENT` (text)
3. Resolve concept UUIDs via concept mapping service
4. Fetch answer sets for coded fields from the concept dictionary
5. Generate Excel columns with dropdowns for coded fields and text fields for comments

**Final Excel template will contain columns:**
- encounterDate
- encounterLocation (dropdown with locations)
- encounterProvider (dropdown with providers)
- ICRC:HIP_SURGERY (dropdown with answers from concept)
- ICRC:HIP_SURGERY_DISPOSITION_COMMENT (text)
- ICRC:KNEE_SURGERY (dropdown with answers)
- ICRC:KNEE_SURGERY_DISPOSITION_COMMENT (text)
- ... (all other repeated sections expanded similarly)
- ICRC:1990797 (textarea for comments)
- __meta sheet (tracks formType="hfe-xml" for import compatibility)

### Form Type Detection

The module uses a **FormResolver SPI** that auto-detects form types:
1. Try `O3JsonFormResolver` - looks for JSON schema resource
2. Try `HfeFormResolver` - looks for "htmlform" resource
3. If no resolver matches, throws error with helpful message

You can mix O3 and HFE forms in a single template generation request - the module will process each appropriately.

## Architecture

### Excel Template Model (ETM)
Both O3 and HFE forms are parsed into a unified internal model:
- `FormModel` - Represents the entire form with metadata
- `SheetModel` - Represents an Excel sheet (one per form section)
- `FieldModel` - Represents a single data field/column
- `AnswerModel` - Represents coded answer options

### Processing Pipeline
```
Form UUID → FormResolver → FormParser → FormModel (ETM) → ExcelTemplateExporter → .xlsx file
     ↓            ↓             ↓
 Auto-detect   O3JsonFormResolver    Unified model works
 form type     HfeFormResolver      for both form types
```

**Key Classes:**
- `FormResolverRegistry` - Auto-detects and selects appropriate resolver
- `O3JsonFormResolver` - Parses O3 JSON schemas
- `HfeFormResolver` - Parses HFE XML forms
- `O3FormParser` - Converts O3 schema to FormModel
- `HfeFormParser` - Converts HFE XML to FormModel (HfeFormParser.java:261-472 for `<repeat>` expansion)
- `ExcelTemplateExporter` - Generates Excel from FormModel
- `TemplateGenerationServiceImpl` - Orchestrates the workflow (TemplateGenerationServiceImpl.java:58-123)

## API Endpoints

- `POST /ws/rest/v1/dataimport/forms/template.xlsx` - Generate Excel template from form UUIDs
- `POST /ws/rest/v1/dataimport` (multipart/form-data) - Validate Excel workbook (dry-run)
- `POST /ws/rest/v1/dataimport/quarantine-report.xlsx` (multipart/form-data) - Download color-coded report with error rows highlighted
- `POST /ws/rest/v1/dataimport?batchId={uuid}` - Commit validated import plan

## Installation
The module supports OpenMRS Platform 2.7.0 and above hence supporting java 8, 11, 17, 21 for now.

1. Build: `mvn clean package`
2. Install the generated `.omod` file (`omod/target/dataimport-1.0.0-SNAPSHOT.omod`) through OpenMRS Admin
3. Ensure you have the required privileges: `Data Import Module Privilege`

## Usage

### Generate Excel Template via Postman

1. **Request Type**: POST
2. **URL**: `http://localhost:8056/openmrs/ws/rest/v1/dataimport/forms/template.xlsx`
3. **Headers**:
   - `Content-Type: application/json`
   - `Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
4. **Body** (raw JSON):
   ```json
   {
     "formUuids": ["c4efe3f7-a556-3377-bc69-cae193418ebd"],
     "options": {
       "includePatient": true
     }
   }
   ```
5. **Download**: Click dropdown next to **Send** → **Send and Download**, or after response, click **Save Response** → **Save to a file**

### Upload and Validate Excel Workbook (Dry-Run)

1. **Request Type**: POST
2. **URL**: `http://localhost:8056/openmrs/ws/rest/v1/dataimport`
3. **Authentication**: Basic Auth (username:password) or session cookie
4. **Content-Type**: `multipart/form-data` (set automatically when using form-data in Postman)
5. **Body** (form-data):
   - Key: `file` (type: File)
   - Value: Select your Excel file (.xlsx or .xls)

**Note**: REST endpoints under `/ws/rest/v1/...` bypass CSRF protection, so no CSRF token is needed!

**Example Response**:
```json
{
  "success": true,
  "batchId": "550e8400-e29b-41d4-a716-446655440000",
  "stats": { ... },
  "okRows": [ ... ],
  "quarantinedRows": [ ... ],
  "message": "Dry run completed successfully"
}
```

### Download Quarantine Report (Color-Coded Error Report)

If the dry-run returned quarantined rows, you can download a color-coded Excel file with error rows highlighted in coral.

1. **Request Type**: POST
2. **URL**: `http://localhost:8056/openmrs/ws/rest/v1/dataimport/quarantine-report.xlsx`
3. **Authentication**: Basic Auth (username:password) or session cookie
4. **Content-Type**: `multipart/form-data` (set automatically when using form-data in Postman)
5. **Body** (form-data):
   - Key: `file` (type: File)
     - Value: Select the **same** Excel file you uploaded for dry-run
   - Key: `batchId` (type: Text)
     - Value: The `batchId` from the dry-run response (e.g., `550e8400-e29b-41d4-a716-446655440000`)
6. **Download**: The response is an Excel file download with error rows highlighted

**Example cURL command**:
```bash
curl -u admin:Admin123 -X POST \
  -F "file=@/path/to/your/file.xlsx" \
  -F "batchId=550e8400-e29b-41d4-a716-446655440000" \
  -H "Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" \
  --output quarantine_report.xlsx \
  "http://localhost:8080/openmrs/ws/rest/v1/dataimport/quarantine-report.xlsx"
```

**Note**: After downloading the report, fix the highlighted rows and re-upload for validation or commit.

### Commit Import Plan

1. **Request Type**: POST
2. **URL**: `http://localhost:8056/openmrs/ws/rest/v1/dataimport?batchId={uuid}`
   - Replace `{uuid}` with the batchId from the dry-run response
3. **Headers**:
   - `Authorization: Basic <base64(username:password)>` (or use session cookie)
4. **Body**: Empty (no body required)

**Note**: REST endpoints under `/ws/rest/v1/...` bypass CSRF protection!

**Example cURL command**:
```bash
curl -u admin:Admin123 -X POST \
  "http://localhost:8080/openmrs/ws/rest/v1/dataimport?batchId=550e8400-e29b-41d4-a716-446655440000"
```

**Example Response**:
```json
{
  "success": true,
  "message": "Import completed: 10 patients, 8 encounters",
  "batchId": "550e8400-e29b-41d4-a716-446655440000",
  "stats": { ... },
  "createdEncounterUuids": [ ... ],
  "errors": [ ]
}
```

**Note**: The `message` field includes a summary of the import with any failure counts. The `errors` array contains detailed error messages for any rows that failed during import (e.g., "Row 5 (sheet Main): Encounter creation failed: ...").
