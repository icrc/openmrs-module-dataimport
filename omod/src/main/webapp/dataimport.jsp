<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="/WEB-INF/template/include.jsp"%>

<openmrs:require privilege="Manage Forms" otherwise="/login.htm" redirect="/module/dataimport/dataimport.form" />

<%@ include file="/WEB-INF/template/header.jsp"%>

<c:set var="currentPage" value="${param.page}" />
<c:if test="${empty currentPage}">
    <c:set var="currentPage" value="template" />
</c:if>

<%@ include file="localHeader.jsp"%>

<link type="text/css" rel="stylesheet" href="<openmrs:contextPath/>/moduleResources/dataimport/styles/dataimport.css" />
<script type="text/javascript" src="<openmrs:contextPath/>/moduleResources/dataimport/scripts/dataimport.js"></script>

<h2><spring:message code="dataimport.module.name"/></h2>
<p><spring:message code="dataimport.module.description"/></p>

<br/>

<c:if test="${currentPage == 'template'}">
<div class="boxHeader">
    <b><spring:message code="dataimport.template.title"/></b>
</div>
<div class="box">
    <p><spring:message code="dataimport.template.description"/></p>
    <table cellpadding="3" cellspacing="3" width="100%">
        <tr>
            <td valign="top" width="200"><spring:message code="dataimport.template.selectForms"/>:</td>
            <td valign="top">
                <div class="custom-multiselect">
                    <select class="multiselect-trigger" id="formsSelect" readonly style="width: 400px; cursor: pointer;">
                        <option id="formsDropdownLabel"><spring:message code="dataimport.template.selectFormsPlaceholder"/></option>
                    </select>
                    <div id="formsDropdown" class="multiselect-dropdown" style="display: none;">
                        <div style="padding: 8px; border-bottom: 1px solid #ccc; background: #f9f9f9;">
                            <a href="#" id="selectAllFormsBtn" style="font-size: 0.85em; margin-right: 8px;"><spring:message code="dataimport.template.selectAll"/></a>
                            <a href="#" id="deselectAllFormsBtn" style="font-size: 0.85em;"><spring:message code="dataimport.template.deselectAll"/></a>
                        </div>
                        <div style="max-height: 250px; overflow-y: auto;">
                            <c:choose>
                                <c:when test="${empty forms}">
                                    <div style="padding: 12px; color: #666; text-align: center;">
                                        No forms found. Please create forms in the system first.
                                    </div>
                                </c:when>
                                <c:otherwise>
                                    <c:forEach items="${forms}" var="form">
                                        <label class="multiselect-option">
                                            <input type="checkbox" class="form-checkbox" value="${form.uuid}" />
                                            <span><c:out value="${form.name}"/>
                                            <c:if test="${form.published}"> (<spring:message code="Form.published"/>)</c:if>
                                            <c:if test="${not empty form.version}"> - v${form.version}</c:if></span>
                                        </label>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>
                </div>
            </td>
        </tr>
        <tr>
            <td valign="top" width="200"><spring:message code="dataimport.upload.selectFormFiles"/> (Optional):</td>
            <td valign="top">
                <input type="file" id="formFilesInput" accept=".json,.xml" multiple />
                <input type="button" id="uploadFormsBtn" value="<spring:message code="dataimport.upload.uploadForms"/>" disabled style="margin-left: 10px;" />
                <span id="uploadFormsStatus" style="margin-left: 10px;"></span>
                <br/>
                <span style="color: #666; font-size: 0.9em;"><spring:message code="dataimport.upload.formsDescription"/></span>
                <div id="uploadedFormsList" style="margin-top: 5px;"></div>
            </td>
        </tr>
        <tr>
            <td valign="top"></td>
            <td valign="top">
                <input type="checkbox" id="includePatientRegistration" checked />
                <spring:message code="dataimport.template.includePatient"/>
            </td>
        </tr>
        <tr>
            <td valign="top"><spring:message code="dataimport.template.defaultLocation"/>:</td>
            <td valign="top">
                <select id="locationSelect">
                    <option value=""><spring:message code="dataimport.template.selectLocation"/></option>
                    <c:forEach items="${locations}" var="location">
                        <option value="${location.uuid}"><c:out value="${location.name}"/></option>
                    </c:forEach>
                </select>
                <span style="color: #666; font-size: 0.9em;"> (<spring:message code="dataimport.template.defaultLocationHint"/>)</span>
            </td>
        </tr>
        <tr>
            <td valign="top"><spring:message code="dataimport.template.allowedLocations"/>:</td>
            <td valign="top">
                <div class="custom-multiselect">
                    <select class="multiselect-trigger" id="locationsSelect" readonly style="width: 400px; cursor: pointer;">
                        <option id="locationsDropdownLabel"><spring:message code="dataimport.template.allLocations"/></option>
                    </select>
                    <div id="locationsDropdown" class="multiselect-dropdown" style="display: none;">
                        <div style="padding: 8px; border-bottom: 1px solid #ccc; background: #f9f9f9;">
                            <a href="#" id="selectAllLocationsBtn" style="font-size: 0.85em; margin-right: 8px;"><spring:message code="dataimport.template.selectAll"/></a>
                            <a href="#" id="deselectAllLocationsBtn" style="font-size: 0.85em;"><spring:message code="dataimport.template.deselectAll"/></a>
                            <span id="locationCharCount" style="margin-left: 15px; color: #666; font-size: 0.8em;"></span>
                        </div>
                        <div style="max-height: 250px; overflow-y: auto;">
                            <c:choose>
                                <c:when test="${empty locations}">
                                    <div style="padding: 12px; color: #666; text-align: center;">
                                        No locations found. Please create locations in the system first.
                                    </div>
                                </c:when>
                                <c:otherwise>
                                    <c:forEach items="${locations}" var="location">
                                        <label class="multiselect-option">
                                            <input type="checkbox" class="location-checkbox" value="${location.uuid}" data-name="${location.name}" />
                                            <span><c:out value="${location.name}"/></span>
                                        </label>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>
                </div>
                <div style="margin-top: 8px;">
                    <input type="checkbox" id="restrictToAllowedLocations" />
                    <label for="restrictToAllowedLocations"><spring:message code="dataimport.template.restrictLocations"/></label>
                </div>
            </td>
        </tr>
        <tr>
            <td valign="top"><spring:message code="dataimport.template.visitOptions"/>:</td>
            <td valign="top">
                <input type="checkbox" id="autoCloseVisits" />
                <label for="autoCloseVisits"><spring:message code="dataimport.template.autoCloseVisits"/></label>
                <br/>
                <span style="color: #666; font-size: 0.9em;"><spring:message code="dataimport.template.autoCloseVisitsHint"/></span>
            </td>
        </tr>
        <tr>
            <td></td>
            <td>
                <input type="button" id="generateTemplateBtn" value="<spring:message code="dataimport.template.generate"/>" disabled />
                <span id="templateStatus" style="margin-left: 10px;"></span>
            </td>
        </tr>
    </table>
</div>
</c:if>

<c:if test="${currentPage == 'import'}">
<div class="boxHeader">
    <b><spring:message code="dataimport.import.title"/></b>
</div>
<div class="box">
    <p><spring:message code="dataimport.import.description"/></p>

    <table cellpadding="3" cellspacing="3">
        <tr>
            <td valign="top" width="200"><spring:message code="dataimport.import.selectFile"/>:</td>
            <td valign="top">
                <input type="file" id="dataFileInput" accept=".xlsx,.xls" />
            </td>
        </tr>
        <tr>
            <td></td>
            <td>
                <input type="button" id="dryRunBtn" value="<spring:message code="dataimport.import.dryRun"/>" disabled />
                <input type="button" id="commitImportBtn" value="<spring:message code="dataimport.import.commit"/>" disabled />
                <input type="button" id="downloadQuarantineBtn" value="<spring:message code="dataimport.import.downloadQuarantine"/>" disabled />
                <br/>
                <span id="importStatus" style="margin-top: 5px; display: inline-block;"></span>
            </td>
        </tr>
        <tr>
            <td></td>
            <td>
                <div id="validationResults"></div>
            </td>
        </tr>
    </table>
</div>
</c:if>

<%@ include file="/WEB-INF/template/footer.jsp"%>
