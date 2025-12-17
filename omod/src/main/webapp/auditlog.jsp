<%@ include file="/WEB-INF/template/include.jsp"%>

<openmrs:require privilege="Manage Forms" otherwise="/login.htm" redirect="/module/dataimport/auditlog.form" />

<%@ include file="/WEB-INF/template/header.jsp"%>
<c:set var="currentPage" value="auditlog" scope="request"/>
<%@ include file="localHeader.jsp"%>

<link type="text/css" rel="stylesheet" href="<openmrs:contextPath/>/moduleResources/dataimport/styles/dataimport.css" />

<style>
    .audit-table { width: 100%; border-collapse: collapse; }
    .audit-table th, .audit-table td { padding: 8px; border: 1px solid #ddd; text-align: left; }
    .audit-table th { background-color: #f5f5f5; }
    .audit-table tr:nth-child(even) { background-color: #fafafa; }
    .audit-table tr:hover { background-color: #f0f0f0; }
    .status-SUCCESS { color: #28a745; font-weight: bold; }
    .status-PARTIAL_SUCCESS { color: #ffc107; font-weight: bold; }
    .status-FAILED { color: #dc3545; font-weight: bold; }
    .status-VALIDATION_ERRORS { color: #fd7e14; font-weight: bold; }
    .action-DRY_RUN { background-color: #e7f3ff; }
    .action-COMMIT { background-color: #e8f5e9; }
    .filter-bar { margin-bottom: 15px; }
    .filter-bar select, .filter-bar input { margin-right: 10px; padding: 5px; }
    .pagination { margin-top: 15px; text-align: center; }
    .pagination a, .pagination span { padding: 5px 10px; margin: 0 2px; border: 1px solid #ddd; text-decoration: none; }
    .pagination a:hover { background-color: #f0f0f0; }
    .pagination .current { background-color: #007bff; color: white; border-color: #007bff; }
    .error-details { max-height: 100px; overflow-y: auto; font-size: 0.85em; color: #666; }
    .batch-id { font-family: monospace; font-size: 0.85em; }
    .detail-row { display: none; background-color: #f9f9f9; }
    .detail-row td { padding: 15px; }
    .stats-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
    .stat-item { background: #fff; padding: 10px; border: 1px solid #eee; border-radius: 4px; }
    .stat-label { color: #666; font-size: 0.85em; }
    .stat-value { font-size: 1.2em; font-weight: bold; }
</style>

<h2><spring:message code="dataimport.auditlog.title"/></h2>

<p><spring:message code="dataimport.auditlog.description"/></p>

<div class="boxHeader">
    <b><spring:message code="dataimport.auditlog.title"/></b>
</div>
<div class="box">
    <div class="filter-bar">
        <select id="statusFilter">
            <option value=""><spring:message code="dataimport.auditlog.allStatuses"/></option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="PARTIAL_SUCCESS">PARTIAL_SUCCESS</option>
            <option value="FAILED">FAILED</option>
            <option value="VALIDATION_ERRORS">VALIDATION_ERRORS</option>
        </select>
        <select id="actionFilter">
            <option value=""><spring:message code="dataimport.auditlog.allActions"/></option>
            <option value="DRY_RUN">DRY_RUN</option>
            <option value="COMMIT">COMMIT</option>
        </select>
        <input type="button" id="refreshBtn" value="<spring:message code="dataimport.auditlog.refresh"/>" />
        <input type="button" id="cleanupBtn" value="<spring:message code="dataimport.auditlog.cleanup"/>" style="margin-left: 20px;" />
    </div>

    <div id="auditLogContainer">
        <p><spring:message code="dataimport.auditlog.loading"/></p>
    </div>

    <div id="paginationContainer" class="pagination"></div>
</div>

<script type="text/javascript">
(function() {
    var contextPath = '<openmrs:contextPath/>';
    var currentPage = 0;
    var pageSize = 20;

    function formatDate(dateStr) {
        if (!dateStr) return '-';
        var date = new Date(dateStr);
        return date.toLocaleString();
    }

    function formatDuration(ms) {
        if (!ms) return '-';
        if (ms < 1000) return ms + 'ms';
        if (ms < 60000) return (ms / 1000).toFixed(1) + 's';
        return (ms / 60000).toFixed(1) + 'min';
    }

    function loadAuditLogs(page) {
        var status = document.getElementById('statusFilter').value;
        var action = document.getElementById('actionFilter').value;

        var url = contextPath + '/rest/v1/dataimport/audit?startIndex=' + (page * pageSize) + '&maxResults=' + pageSize;
        if (status) url += '&status=' + status;
        if (action) url += '&action=' + action;

        var xhr = new XMLHttpRequest();
        xhr.open('GET', url, true);
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    var response = JSON.parse(xhr.responseText);
                    renderAuditLogs(response.results || []);
                    renderPagination(response.totalCount || 0, page);
                } else {
                    document.getElementById('auditLogContainer').innerHTML =
                        '<p style="color: red;">Error loading audit logs: ' + xhr.statusText + '</p>';
                }
            }
        };
        xhr.send();
    }

    function renderAuditLogs(logs) {
        var container = document.getElementById('auditLogContainer');

        if (!logs || logs.length === 0) {
            container.innerHTML = '<p><spring:message code="dataimport.auditlog.noLogs"/></p>';
            return;
        }

        var html = '<table class="audit-table">';
        html += '<thead><tr>';
        html += '<th><spring:message code="dataimport.auditlog.date"/></th>';
        html += '<th><spring:message code="dataimport.auditlog.action"/></th>';
        html += '<th><spring:message code="dataimport.auditlog.status"/></th>';
        html += '<th><spring:message code="dataimport.auditlog.filename"/></th>';
        html += '<th><spring:message code="dataimport.auditlog.totalRows"/></th>';
        html += '<th><spring:message code="dataimport.auditlog.duration"/></th>';
        html += '<th><spring:message code="dataimport.auditlog.user"/></th>';
        html += '<th></th>';
        html += '</tr></thead><tbody>';

        for (var i = 0; i < logs.length; i++) {
            var log = logs[i];
            var rowId = 'row-' + log.uuid;
            var detailId = 'detail-' + log.uuid;

            html += '<tr id="' + rowId + '" class="action-' + log.action + '">';
            html += '<td>' + formatDate(log.dateCreated) + '</td>';
            html += '<td>' + (log.action || '-') + '</td>';
            html += '<td class="status-' + log.status + '">' + (log.status || '-') + '</td>';
            html += '<td>' + (log.filename || '-') + '</td>';
            html += '<td>' + (log.totalRows != null ? log.totalRows : '-') + '</td>';
            html += '<td>' + formatDuration(log.durationMs) + '</td>';
            html += '<td>' + (log.creatorName || '-') + '</td>';
            html += '<td><a href="#" onclick="toggleDetail(\'' + log.uuid + '\'); return false;"><spring:message code="dataimport.auditlog.viewDetails"/></a></td>';
            html += '</tr>';

            // Detail row
            html += '<tr id="' + detailId + '" class="detail-row"><td colspan="8">';
            html += '<div class="stats-grid">';
            html += '<div class="stat-item"><div class="stat-label"><spring:message code="dataimport.auditlog.batchId"/></div><div class="stat-value batch-id">' + (log.batchId || '-') + '</div></div>';
            html += '<div class="stat-item"><div class="stat-label"><spring:message code="dataimport.auditlog.successfulRows"/></div><div class="stat-value">' + (log.successfulRows != null ? log.successfulRows : '-') + '</div></div>';
            html += '<div class="stat-item"><div class="stat-label"><spring:message code="dataimport.auditlog.quarantinedRows"/></div><div class="stat-value">' + (log.quarantinedRows != null ? log.quarantinedRows : '-') + '</div></div>';
            html += '<div class="stat-item"><div class="stat-label"><spring:message code="dataimport.auditlog.patientsCreated"/></div><div class="stat-value">' + (log.patientsCreated != null ? log.patientsCreated : '-') + '</div></div>';
            html += '<div class="stat-item"><div class="stat-label"><spring:message code="dataimport.auditlog.encountersCreated"/></div><div class="stat-value">' + (log.encountersCreated != null ? log.encountersCreated : '-') + '</div></div>';
            html += '<div class="stat-item"><div class="stat-label"><spring:message code="dataimport.auditlog.obsCreated"/></div><div class="stat-value">' + (log.obsCreated != null ? log.obsCreated : '-') + '</div></div>';
            html += '</div>';
            if (log.errorSummary) {
                html += '<div style="margin-top: 10px;"><strong><spring:message code="dataimport.auditlog.errorSummary"/>:</strong><div class="error-details">' + log.errorSummary + '</div></div>';
            }
            html += '</td></tr>';
        }

        html += '</tbody></table>';
        container.innerHTML = html;
    }

    function renderPagination(totalCount, currentPage) {
        var container = document.getElementById('paginationContainer');
        var totalPages = Math.ceil(totalCount / pageSize);

        if (totalPages <= 1) {
            container.innerHTML = '';
            return;
        }

        var html = '';
        if (currentPage > 0) {
            html += '<a href="#" onclick="goToPage(' + (currentPage - 1) + '); return false;">&laquo; Prev</a>';
        }

        var startPage = Math.max(0, currentPage - 2);
        var endPage = Math.min(totalPages - 1, currentPage + 2);

        for (var i = startPage; i <= endPage; i++) {
            if (i === currentPage) {
                html += '<span class="current">' + (i + 1) + '</span>';
            } else {
                html += '<a href="#" onclick="goToPage(' + i + '); return false;">' + (i + 1) + '</a>';
            }
        }

        if (currentPage < totalPages - 1) {
            html += '<a href="#" onclick="goToPage(' + (currentPage + 1) + '); return false;">Next &raquo;</a>';
        }

        container.innerHTML = html;
    }

    window.toggleDetail = function(uuid) {
        var detailRow = document.getElementById('detail-' + uuid);
        if (detailRow) {
            detailRow.style.display = detailRow.style.display === 'table-row' ? 'none' : 'table-row';
        }
    };

    window.goToPage = function(page) {
        currentPage = page;
        loadAuditLogs(page);
    };

    function runCleanup() {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', contextPath + '/rest/v1/dataimport/audit/config', true);
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4 && xhr.status === 200) {
                var config = JSON.parse(xhr.responseText);
                var confirmMsg = '<spring:message code="dataimport.auditlog.cleanup.confirm"/>'.replace('{0}', config.retentionDays);
                if (confirm(confirmMsg)) {
                    var cleanupXhr = new XMLHttpRequest();
                    cleanupXhr.open('POST', contextPath + '/rest/v1/dataimport/audit/cleanup', true);
                    cleanupXhr.setRequestHeader('Content-Type', 'application/json');
                    cleanupXhr.onreadystatechange = function() {
                        if (cleanupXhr.readyState === 4) {
                            if (cleanupXhr.status === 200) {
                                var result = JSON.parse(cleanupXhr.responseText);
                                var successMsg = '<spring:message code="dataimport.auditlog.cleanup.success"/>'.replace('{0}', result.deletedCount);
                                alert(successMsg);
                                loadAuditLogs(0);
                            } else {
                                alert('Cleanup failed: ' + cleanupXhr.statusText);
                            }
                        }
                    };
                    cleanupXhr.send('{}');
                }
            }
        };
        xhr.send();
    }

    // Event listeners
    document.getElementById('refreshBtn').addEventListener('click', function() {
        loadAuditLogs(currentPage);
    });

    document.getElementById('cleanupBtn').addEventListener('click', runCleanup);

    document.getElementById('statusFilter').addEventListener('change', function() {
        currentPage = 0;
        loadAuditLogs(0);
    });

    document.getElementById('actionFilter').addEventListener('change', function() {
        currentPage = 0;
        loadAuditLogs(0);
    });

    // Initial load
    loadAuditLogs(0);
})();
</script>

<%@ include file="/WEB-INF/template/footer.jsp"%>
