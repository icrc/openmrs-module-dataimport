<%@ include file="/WEB-INF/template/include.jsp"%>

<openmrs:require privilege="Manage Forms" otherwise="/login.htm" redirect="/module/dataimport/dataimport.form" />

<%@ include file="/WEB-INF/template/header.jsp"%>

<c:set var="currentPage" value="${param.page}" />
<c:if test="${empty currentPage}">
    <c:set var="currentPage" value="template" />
</c:if>

<%@ include file="localHeader.jsp"%>

<link type="text/css" rel="stylesheet" href="<openmrs:contextPath/>/moduleResources/dataimport/styles/dataimport.css" />

<h2><spring:message code="dataimport.title"/></h2>

<br/>

<c:if test="${currentPage == 'template'}">
<!-- Section 1: Template Generation -->
<div class="boxHeader">
    <b><spring:message code="dataimport.template.title"/></b>
</div>
<div class="box">
    <p><spring:message code="dataimport.template.description"/></p>

    <table cellpadding="3" cellspacing="3" width="100%">
        <tr>
            <td valign="top" width="200"><spring:message code="dataimport.template.selectForms"/>:</td>
            <td valign="top">
                <div style="max-height: 200px; overflow-y: auto; border: 1px solid #ccc; padding: 5px;">
                    <table cellpadding="2" cellspacing="0" width="100%">
                        <c:forEach items="${forms}" var="form" varStatus="status">
                            <tr class="${status.index % 2 == 0 ? 'evenRow' : 'oddRow'}">
                                <td>
                                    <input type="checkbox" id="form-${form.uuid}" class="form-checkbox" value="${form.uuid}"/>
                                    <label for="form-${form.uuid}">
                                        <c:out value="${form.name}"/>
                                        <c:if test="${form.published}"> (<spring:message code="Form.published"/>)</c:if>
                                        <c:if test="${not empty form.version}"> - v${form.version}</c:if>
                                    </label>
                                    <c:if test="${not empty form.description}">
                                        <br/><span style="color: #666; font-size: 0.9em; margin-left: 20px;"><c:out value="${form.description}"/></span>
                                    </c:if>
                                </td>
                            </tr>
                        </c:forEach>
                    </table>
                </div>
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
                <div style="margin-bottom: 5px;">
                    <a href="#" id="selectAllLocations"><spring:message code="dataimport.template.selectAll"/></a> |
                    <a href="#" id="deselectAllLocations"><spring:message code="dataimport.template.deselectAll"/></a>
                    <span id="locationCharCount" style="margin-left: 15px; color: #666; font-size: 0.9em;"></span>
                </div>
                <div style="max-height: 150px; overflow-y: auto; border: 1px solid #ccc; padding: 5px;">
                    <table cellpadding="2" cellspacing="0" width="100%">
                        <c:forEach items="${locations}" var="location" varStatus="status">
                            <tr class="${status.index % 2 == 0 ? 'evenRow' : 'oddRow'}">
                                <td>
                                    <input type="checkbox" id="loc-${location.uuid}" class="location-checkbox" value="${location.uuid}" data-name="${location.name}"/>
                                    <label for="loc-${location.uuid}"><c:out value="${location.name}"/></label>
                                </td>
                            </tr>
                        </c:forEach>
                    </table>
                </div>
                <div style="margin-top: 5px;">
                    <input type="checkbox" id="restrictToAllowedLocations" />
                    <label for="restrictToAllowedLocations"><spring:message code="dataimport.template.restrictLocations"/></label>
                </div>
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

<br/>

<!-- Section 2: File Upload for Forms -->
<div class="boxHeader">
    <b><spring:message code="dataimport.upload.formsTitle"/></b>
</div>
<div class="box">
    <p><spring:message code="dataimport.upload.formsDescription"/></p>

    <table cellpadding="3" cellspacing="3">
        <tr>
            <td valign="top" width="200"><spring:message code="dataimport.upload.selectFormFiles"/>:</td>
            <td valign="top">
                <input type="file" id="formFilesInput" accept=".json,.xml" multiple />
            </td>
        </tr>
        <tr>
            <td></td>
            <td>
                <input type="button" id="uploadFormsBtn" value="<spring:message code="dataimport.upload.uploadForms"/>" disabled />
                <span id="uploadFormsStatus" style="margin-left: 10px;"></span>
            </td>
        </tr>
        <tr>
            <td></td>
            <td>
                <div id="uploadedFormsList"></div>
            </td>
        </tr>
    </table>
</div>
</c:if>

<c:if test="${currentPage == 'import'}">
<!-- Section 3: Data Import -->
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

<script type="text/javascript" src="<openmrs:contextPath/>/moduleResources/dataimport/scripts/dataimport.js"></script>

<%@ include file="/WEB-INF/template/footer.jsp"%>
