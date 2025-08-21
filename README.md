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
       "includePatient": true,
       "includeVisit": false,
       "lang": "en",
       "addDropdowns": true
     }
   }
   ```
5. **Download**: Click dropdown next to **Send** → **Send and Download**, or after response, click **Save Response** → **Save to a file**
