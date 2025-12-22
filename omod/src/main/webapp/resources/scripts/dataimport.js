(function() {
    'use strict';

    var selectedForms = [];
    var uploadedFormFileIds = [];
    var currentBatchId = null;
    var currentDataFile = null;
    var contextPath = null;
    var validationErrors = [];
    var currentErrorPage = 1;
    var errorsPerPage = 20;

    function init() {
        contextPath = getContextPath();
        setupEventListeners();
    }

    function getContextPath() {
        var path = window.location.pathname;
        var parts = path.split('/');
        return '/' + parts[1];
    }

    function setupEventListeners() {
        // Forms dropdown trigger
        var formsSelect = document.getElementById('formsSelect');
        if (formsSelect) {
            formsSelect.addEventListener('click', function(e) {
                e.preventDefault();
                window.toggleDropdown('formsDropdown');
            });
        }

        // Locations dropdown trigger
        var locationsSelect = document.getElementById('locationsSelect');
        if (locationsSelect) {
            locationsSelect.addEventListener('click', function(e) {
                e.preventDefault();
                window.toggleDropdown('locationsDropdown');
            });
        }

        // Forms checkboxes
        var formCheckboxes = document.querySelectorAll('.form-checkbox');
        formCheckboxes.forEach(function(checkbox) {
            checkbox.addEventListener('change', function() {
                toggleFormSelection(this.value, this.checked);
                updateGenerateButton();
                window.updateFormsDropdownLabel();
            });
        });

        // Forms select/deselect all
        var selectAllFormsBtn = document.getElementById('selectAllFormsBtn');
        if (selectAllFormsBtn) {
            selectAllFormsBtn.addEventListener('click', function(e) {
                e.preventDefault();
                window.selectAllForms();
            });
        }

        var deselectAllFormsBtn = document.getElementById('deselectAllFormsBtn');
        if (deselectAllFormsBtn) {
            deselectAllFormsBtn.addEventListener('click', function(e) {
                e.preventDefault();
                window.deselectAllForms();
            });
        }

        // Multi-location selection event listeners
        var locationCheckboxes = document.querySelectorAll('.location-checkbox');
        locationCheckboxes.forEach(function(checkbox) {
            checkbox.addEventListener('change', function() {
                updateLocationCharCount();
                window.updateLocationsDropdownLabel();
            });
        });

        // Locations select/deselect all
        var selectAllLocationsBtn = document.getElementById('selectAllLocationsBtn');
        if (selectAllLocationsBtn) {
            selectAllLocationsBtn.addEventListener('click', function(e) {
                e.preventDefault();
                window.selectAllLocations();
            });
        }

        var deselectAllLocationsBtn = document.getElementById('deselectAllLocationsBtn');
        if (deselectAllLocationsBtn) {
            deselectAllLocationsBtn.addEventListener('click', function(e) {
                e.preventDefault();
                window.deselectAllLocations();
            });
        }

        var formFilesInput = document.getElementById('formFilesInput');
        if (formFilesInput) {
            formFilesInput.addEventListener('change', function() {
                var uploadFormsBtn = document.getElementById('uploadFormsBtn');
                uploadFormsBtn.disabled = this.files.length === 0;
            });
        }

        var dataFileInput = document.getElementById('dataFileInput');
        if (dataFileInput) {
            dataFileInput.addEventListener('change', function() {
                currentDataFile = this.files[0];
                var dryRunBtn = document.getElementById('dryRunBtn');
                dryRunBtn.disabled = !currentDataFile;

                var commitBtn = document.getElementById('commitImportBtn');
                commitBtn.disabled = true;

                var quarantineBtn = document.getElementById('downloadQuarantineBtn');
                quarantineBtn.disabled = true;

                hideValidationResults();
            });
        }

        var generateBtn = document.getElementById('generateTemplateBtn');
        if (generateBtn) {
            generateBtn.addEventListener('click', generateTemplate);
        }

        var uploadFormsBtn = document.getElementById('uploadFormsBtn');
        if (uploadFormsBtn) {
            uploadFormsBtn.addEventListener('click', uploadFormFiles);
        }

        var dryRunBtn = document.getElementById('dryRunBtn');
        if (dryRunBtn) {
            dryRunBtn.addEventListener('click', performDryRun);
        }

        var commitBtn = document.getElementById('commitImportBtn');
        if (commitBtn) {
            commitBtn.addEventListener('click', commitImport);
        }

        var quarantineBtn = document.getElementById('downloadQuarantineBtn');
        if (quarantineBtn) {
            quarantineBtn.addEventListener('click', downloadQuarantine);
        }
    }

    function getSelectedLocationUuids() {
        var selected = [];
        var checkboxes = document.querySelectorAll('.location-checkbox:checked');
        checkboxes.forEach(function(cb) {
            selected.push(cb.value);
        });
        return selected;
    }

    function updateLocationCharCount() {
        var totalChars = 0;
        var checkboxes = document.querySelectorAll('.location-checkbox:checked');
        checkboxes.forEach(function(cb) {
            var name = cb.getAttribute('data-name') || '';
            totalChars += name.length + 1; // +1 for separator
        });

        var charCountEl = document.getElementById('locationCharCount');
        if (charCountEl) {
            if (checkboxes.length === 0) {
                charCountEl.textContent = '(all locations will be included)';
                charCountEl.style.color = '#666';
            } else if (totalChars > 30000) {
                charCountEl.textContent = checkboxes.length + ' locations selected (~' + totalChars + ' chars - exceeds Excel limit!)';
                charCountEl.style.color = '#dc3545';
            } else if (totalChars > 25000) {
                charCountEl.textContent = checkboxes.length + ' locations selected (~' + totalChars + ' chars - approaching limit)';
                charCountEl.style.color = '#ffc107';
            } else {
                charCountEl.textContent = checkboxes.length + ' locations selected';
                charCountEl.style.color = '#666';
            }
        }
    }

    function toggleFormSelection(formUuid, selected) {
        if (selected) {
            if (selectedForms.indexOf(formUuid) === -1) {
                selectedForms.push(formUuid);
            }
        } else {
            var index = selectedForms.indexOf(formUuid);
            if (index > -1) {
                selectedForms.splice(index, 1);
            }
        }
    }

    function updateGenerateButton() {
        var generateBtn = document.getElementById('generateTemplateBtn');
        generateBtn.disabled = selectedForms.length === 0 && uploadedFormFileIds.length === 0;
    }

    function generateTemplate() {
        var includePatient = document.getElementById('includePatientRegistration').checked;
        var locationUuid = document.getElementById('locationSelect').value;
        var locationUuids = getSelectedLocationUuids();
        var restrictToAllowedLocations = document.getElementById('restrictToAllowedLocations');
        var restrict = restrictToAllowedLocations ? restrictToAllowedLocations.checked : false;
        var autoCloseVisitsCheckbox = document.getElementById('autoCloseVisits');
        var autoCloseVisits = autoCloseVisitsCheckbox ? autoCloseVisitsCheckbox.checked : false;

        var allFormUuids = selectedForms.concat(uploadedFormFileIds);

        if (allFormUuids.length === 0) {
            showStatus('templateStatus', 'Please select at least one form', 'error');
            return;
        }

        var generateBtn = document.getElementById('generateTemplateBtn');
        generateBtn.disabled = true;
        showStatus('templateStatus', 'Generating template...', 'info');

        var data = {
            formUuids: allFormUuids,
            options: {
                includePatient: includePatient
            }
        };

        if (locationUuid) {
            data.options.locationUuid = locationUuid;
        }

        // Multi-location options
        if (locationUuids.length > 0) {
            data.options.locationUuids = locationUuids;
        }

        data.options.restrictToAllowedLocations = restrict;
        data.options.autoCloseVisits = autoCloseVisits;

        var xhr = new XMLHttpRequest();
        xhr.open('POST', contextPath + '/ws/rest/v1/dataimport/forms/template.xlsx', true);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.responseType = 'blob';

        xhr.onload = function() {
            if (xhr.status === 200) {
                var blob = xhr.response;
                var url = window.URL.createObjectURL(blob);
                var a = document.createElement('a');
                a.href = url;
                a.download = data.options.filename || 'form_template.xlsx';
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);

                showStatus('templateStatus', 'Template downloaded successfully', 'success');
            } else {
                showStatus('templateStatus', 'Failed to generate template', 'error');
            }
            generateBtn.disabled = false;
        };

        xhr.onerror = function() {
            showStatus('templateStatus', 'Failed to generate template', 'error');
            generateBtn.disabled = false;
        };

        xhr.send(JSON.stringify(data));
    }

    function uploadFormFiles() {
        var fileInput = document.getElementById('formFilesInput');
        var files = fileInput.files;

        if (files.length === 0) {
            showStatus('uploadFormsStatus', 'Please select files to upload', 'error');
            return;
        }

        var uploadBtn = document.getElementById('uploadFormsBtn');
        uploadBtn.disabled = true;
        showStatus('uploadFormsStatus', 'Uploading form files...', 'info');

        var formData = new FormData();
        for (var i = 0; i < files.length; i++) {
            formData.append('files', files[i]);
        }

        var xhr = new XMLHttpRequest();
        xhr.open('POST', contextPath + '/ws/rest/v1/dataimport/forms/upload', true);

        xhr.onload = function() {
            if (xhr.status === 200) {
                var result = JSON.parse(xhr.responseText);
                uploadedFormFileIds = uploadedFormFileIds.concat(result.fileIds);
                displayUploadedFiles(result.files);
                showStatus('uploadFormsStatus', result.message, 'success');

                updateGenerateButton();

                fileInput.value = '';
                uploadBtn.disabled = true;
            } else {
                showStatus('uploadFormsStatus', 'Upload failed', 'error');
                uploadBtn.disabled = false;
            }
        };

        xhr.onerror = function() {
            showStatus('uploadFormsStatus', 'Upload failed', 'error');
            uploadBtn.disabled = false;
        };

        xhr.send(formData);
    }

    function displayUploadedFiles(files) {
        var container = document.getElementById('uploadedFormsList');
        if (!container) return;

        container.classList.add('show');

        files.forEach(function(file) {
            var div = document.createElement('div');
            div.className = 'uploaded-file-item';

            var nameSpan = document.createElement('span');
            nameSpan.className = 'uploaded-file-name';
            nameSpan.textContent = file.filename;

            var metaSpan = document.createElement('span');
            metaSpan.className = 'uploaded-file-meta';
            metaSpan.textContent = file.detectedType + ' - ' + formatFileSize(parseInt(file.size));

            div.appendChild(nameSpan);
            div.appendChild(metaSpan);
            container.appendChild(div);
        });
    }

    function formatFileSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }

    function performDryRun() {
        if (!currentDataFile) {
            showStatus('importStatus', 'Please select a file first', 'error');
            return;
        }

        var dryRunBtn = document.getElementById('dryRunBtn');
        dryRunBtn.disabled = true;
        showStatus('importStatus', 'Validating file...', 'info');
        hideValidationResults();

        var formData = new FormData();
        formData.append('file', currentDataFile);

        var xhr = new XMLHttpRequest();
        xhr.open('POST', contextPath + '/ws/rest/v1/dataimport', true);

        xhr.onload = function() {
            if (xhr.status === 200 || xhr.status === 201) {
                var result = JSON.parse(xhr.responseText);
                currentBatchId = result.batchId;

                var stats = result.stats || {};
                var validationResult = {
                    success: result.success,
                    batchId: result.batchId,
                    totalRows: stats.totalRows || 0,
                    validRows: stats.okRows || 0,
                    invalidRows: stats.quarantinedRows || 0,
                    hasQuarantine: (stats.quarantinedRows || 0) > 0,
                    errors: result.quarantinedRows || []
                };

                displayValidationResults(validationResult);

                var commitBtn = document.getElementById('commitImportBtn');
                commitBtn.disabled = !result.success || validationResult.invalidRows > 0;

                var quarantineBtn = document.getElementById('downloadQuarantineBtn');
                quarantineBtn.disabled = !validationResult.hasQuarantine;

                if (validationResult.invalidRows === 0) {
                    showStatus('importStatus', 'Validation successful - ready to import', 'success');
                } else {
                    showStatus('importStatus', 'Validation completed with errors', 'warning');
                }

                dryRunBtn.disabled = false;
            } else {
                showStatus('importStatus', 'Validation failed', 'error');
                dryRunBtn.disabled = false;
            }
        };

        xhr.onerror = function() {
            showStatus('importStatus', 'Validation failed', 'error');
            dryRunBtn.disabled = false;
        };

        xhr.send(formData);
    }

    function displayValidationResults(result) {
        var container = document.getElementById('validationResults');
        if (!container) return;

        validationErrors = result.errors || [];
        currentErrorPage = 1;

        var html = '<table cellpadding="3" cellspacing="3" width="100%">';
        html += '<tr>';
        html += '<td width="33%" style="text-align: center;"><b>' + result.totalRows + '</b><br/>Total Rows</td>';
        html += '<td width="33%" style="text-align: center; color: #28a745;"><b>' + result.validRows + '</b><br/>Valid Rows</td>';
        html += '<td width="33%" style="text-align: center; color: #dc3545;"><b>' + result.invalidRows + '</b><br/>Invalid Rows</td>';
        html += '</tr>';
        html += '</table>';

        if (validationErrors.length > 0) {
            html += '<br/>';
            html += '<div class="boxHeader"><b>Validation Errors (' + validationErrors.length + ' total)</b></div>';
            html += '<div class="box">';
            html += '<div id="errorTableContainer"></div>';
            html += '</div>';
        }

        container.innerHTML = html;
        container.classList.add('show');

        if (validationErrors.length > 0) {
            renderErrorTable();
        }
    }

    function renderErrorTable() {
        var container = document.getElementById('errorTableContainer');
        if (!container) return;

        var totalPages = Math.ceil(validationErrors.length / errorsPerPage);
        var startIndex = (currentErrorPage - 1) * errorsPerPage;
        var endIndex = Math.min(startIndex + errorsPerPage, validationErrors.length);

        var html = '<table cellpadding="2" cellspacing="0" width="100%" style="border: 1px solid #ccc;">';
        html += '<thead>';
        html += '<tr style="background-color: #f0f0f0;">';
        html += '<th align="left" style="padding: 5px; border-bottom: 1px solid #ccc;">#</th>';
        html += '<th align="left" style="padding: 5px; border-bottom: 1px solid #ccc;">Error Message</th>';
        html += '</tr>';
        html += '</thead>';
        html += '<tbody>';

        for (var i = startIndex; i < endIndex; i++) {
            var rowClass = (i % 2 === 0) ? 'evenRow' : 'oddRow';
            html += '<tr class="' + rowClass + '">';
            html += '<td style="padding: 5px;">' + (i + 1) + '</td>';
            html += '<td style="padding: 5px;">' + escapeHtml(validationErrors[i].message) + '</td>';
            html += '</tr>';
        }

        html += '</tbody>';
        html += '</table>';

        if (totalPages > 1) {
            html += '<br/>';
            html += '<div style="text-align: center;">';
            html += 'Page ' + currentErrorPage + ' of ' + totalPages + ' &nbsp;&nbsp;';

            if (currentErrorPage > 1) {
                html += '<input type="button" value="&laquo; Previous" onclick="window.dataimport.previousErrorPage()" /> ';
            }

            if (currentErrorPage < totalPages) {
                html += '<input type="button" value="Next &raquo;" onclick="window.dataimport.nextErrorPage()" />';
            }

            html += '</div>';
        }

        container.innerHTML = html;
    }

    function nextErrorPage() {
        var totalPages = Math.ceil(validationErrors.length / errorsPerPage);
        if (currentErrorPage < totalPages) {
            currentErrorPage++;
            renderErrorTable();
        }
    }

    function previousErrorPage() {
        if (currentErrorPage > 1) {
            currentErrorPage--;
            renderErrorTable();
        }
    }

    function hideValidationResults() {
        var container = document.getElementById('validationResults');
        if (container) {
            container.classList.remove('show');
            container.innerHTML = '';
        }
        currentBatchId = null;
    }

    function commitImport() {
        if (!currentBatchId) {
            showStatus('importStatus', 'Please run validation first', 'error');
            return;
        }

        if (!confirm('Are you sure you want to import this data? This action cannot be undone.')) {
            return;
        }

        var commitBtn = document.getElementById('commitImportBtn');
        commitBtn.disabled = true;
        showStatus('importStatus', 'Importing data...', 'info');

        var xhr = new XMLHttpRequest();
        xhr.open('POST', contextPath + '/ws/rest/v1/dataimport?batchId=' + encodeURIComponent(currentBatchId), true);
        xhr.setRequestHeader('Content-Type', 'application/json');

        xhr.onload = function() {
            if (xhr.status === 200 || xhr.status === 201) {
                var result = JSON.parse(xhr.responseText);
                if (result.success) {
                    var stats = result.stats || {};
                    var message = 'Import successful! ' +
                        'Patients: ' + (stats.patientsToCreate || 0) + ', ' +
                        'Encounters: ' + (stats.encountersToCreate || 0) + ', ' +
                        'Observations: ' + (stats.obsToCreate || 0);
                    showStatus('importStatus', message, 'success');

                    hideValidationResults();
                    currentBatchId = null;

                    var dataFileInput = document.getElementById('dataFileInput');
                    if (dataFileInput) dataFileInput.value = '';
                    currentDataFile = null;

                    var dryRunBtn = document.getElementById('dryRunBtn');
                    dryRunBtn.disabled = true;
                } else {
                    var errorMsg = result.message || 'Import failed';
                    if (result.errors && result.errors.length > 0) {
                        errorMsg += ': ' + result.errors.join(', ');
                    }
                    showStatus('importStatus', errorMsg, 'error');
                    commitBtn.disabled = false;
                }
            } else {
                showStatus('importStatus', 'Import failed', 'error');
                commitBtn.disabled = false;
            }
        };

        xhr.onerror = function() {
            showStatus('importStatus', 'Import failed', 'error');
            commitBtn.disabled = false;
        };

        xhr.send(JSON.stringify({}));
    }

    function downloadQuarantine() {
        if (!currentBatchId || !currentDataFile) {
            showStatus('importStatus', 'Batch ID or file not available', 'error');
            return;
        }

        showStatus('importStatus', 'Generating quarantine report...', 'info');

        var formData = new FormData();
        formData.append('file', currentDataFile);
        formData.append('batchId', currentBatchId);

        var xhr = new XMLHttpRequest();
        xhr.open('POST', contextPath + '/ws/rest/v1/dataimport/quarantine-report.xlsx', true);
        xhr.responseType = 'blob';

        xhr.onload = function() {
            if (xhr.status === 200) {
                var blob = xhr.response;
                var url = window.URL.createObjectURL(blob);
                var a = document.createElement('a');
                a.href = url;
                a.download = 'quarantine_report_' + currentBatchId + '.xlsx';
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);

                showStatus('importStatus', 'Quarantine report downloaded', 'success');
            } else {
                showStatus('importStatus', 'Failed to download quarantine report', 'error');
            }
        };

        xhr.onerror = function() {
            showStatus('importStatus', 'Failed to download quarantine report', 'error');
        };

        xhr.send(formData);
    }

    function showStatus(elementId, message, type) {
        var statusEl = document.getElementById(elementId);
        if (!statusEl) return;

        statusEl.textContent = message;
        statusEl.className = 'status-message ' + type;
    }

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Expose functions for onclick handlers
    window.dataimport = {
        nextErrorPage: nextErrorPage,
        previousErrorPage: previousErrorPage
    };

})();

// Global dropdown functions (called from JSP inline handlers)
window.toggleDropdown = function(dropdownId) {
    var dropdown = document.getElementById(dropdownId);
    if (dropdown) {
        var isVisible = dropdown.style.display === 'block';

        // Close all dropdowns first
        var allDropdowns = document.querySelectorAll('.multiselect-dropdown');
        allDropdowns.forEach(function(dd) {
            dd.style.display = 'none';
        });

        // Toggle the clicked one
        dropdown.style.display = isVisible ? 'none' : 'block';
    }
};

// Close dropdowns when clicking outside
document.addEventListener('DOMContentLoaded', function() {
    document.addEventListener('click', function(event) {
        if (!event.target.closest('.custom-multiselect')) {
            var allDropdowns = document.querySelectorAll('.multiselect-dropdown');
            allDropdowns.forEach(function(dd) {
                dd.style.display = 'none';
            });
        }
    });
});

window.updateFormsDropdownLabel = function() {
    var checkboxes = document.querySelectorAll('.form-checkbox');
    var selectedCount = 0;
    var selectedNames = [];

    checkboxes.forEach(function(cb) {
        if (cb.checked) {
            selectedCount++;
            var label = cb.parentElement.textContent.trim();
            selectedNames.push(label);
        }
    });

    var labelElement = document.getElementById('formsDropdownLabel');
    if (selectedCount === 0) {
        labelElement.textContent = 'Select forms...';
        labelElement.style.color = '#999';
    } else if (selectedCount === 1) {
        labelElement.textContent = selectedNames[0].substring(0, 50) + (selectedNames[0].length > 50 ? '...' : '');
        labelElement.style.color = '#000';
    } else {
        labelElement.textContent = selectedCount + ' forms selected';
        labelElement.style.color = '#000';
    }
};

window.selectAllForms = function() {
    var checkboxes = document.querySelectorAll('.form-checkbox');
    checkboxes.forEach(function(cb) {
        cb.checked = true;
    });
    window.updateFormsDropdownLabel();
};

window.deselectAllForms = function() {
    var checkboxes = document.querySelectorAll('.form-checkbox');
    checkboxes.forEach(function(cb) {
        cb.checked = false;
    });
    window.updateFormsDropdownLabel();
};

window.updateLocationsDropdownLabel = function() {
    var checkboxes = document.querySelectorAll('.location-checkbox');
    var selectedCount = 0;
    var totalCharCount = 0;

    checkboxes.forEach(function(cb) {
        if (cb.checked) {
            selectedCount++;
            var name = cb.getAttribute('data-name') || '';
            totalCharCount += name.length + 1;
        }
    });

    var labelElement = document.getElementById('locationsDropdownLabel');
    var charCountElement = document.getElementById('locationCharCount');

    if (selectedCount === 0) {
        labelElement.textContent = 'All locations';
        labelElement.style.color = '#000';
        charCountElement.textContent = '';
    } else {
        labelElement.textContent = selectedCount + ' location' + (selectedCount === 1 ? '' : 's') + ' selected';
        labelElement.style.color = '#000';

        // Show character count warning
        if (totalCharCount > 30000) {
            charCountElement.textContent = '(~' + totalCharCount + ' chars - exceeds Excel limit!)';
            charCountElement.style.color = '#da1e28';
        } else if (totalCharCount > 25000) {
            charCountElement.textContent = '(~' + totalCharCount + ' chars - approaching limit)';
            charCountElement.style.color = '#f1c21b';
        } else {
            charCountElement.textContent = '(~' + totalCharCount + ' chars)';
            charCountElement.style.color = '#666';
        }
    }
};

window.selectAllLocations = function() {
    var checkboxes = document.querySelectorAll('.location-checkbox');
    checkboxes.forEach(function(cb) {
        cb.checked = true;
    });
    window.updateLocationsDropdownLabel();
};

window.deselectAllLocations = function() {
    var checkboxes = document.querySelectorAll('.location-checkbox');
    checkboxes.forEach(function(cb) {
        cb.checked = false;
    });
    window.updateLocationsDropdownLabel();
};
