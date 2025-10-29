# OpenMRS Excel Import and Export Module

A focused OpenMRS module that converts O3 forms to Excel templates using a clean model-based approach.

## What It Does

Converts OpenMRS 3 forms into Excel templates for offline data collection, then processes completed Excel files back into OpenMRS using a robust model-based system.

## Key Features

- **O3 Form Parsing** - Converts O3 JSON forms to structured model objects
- **Excel Template Generation** - Creates Excel templates with validation, dropdowns, and multi-select support
- **REST API** - Programmatic template generation via REST endpoints

## API Endpoints

- `POST /ws/rest/v1/dataimport/forms/template.xlsx` - Generate Excel template from form UUIDs
- `POST /ws/rest/v1/dataimport` (multipart/form-data) - Validate Excel workbook (dry-run)
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
  "message": "Import completed successfully",
  "batchId": "550e8400-e29b-41d4-a716-446655440000",
  "stats": { ... },
  "createdEncounterUuids": [ ... ]
}
```
