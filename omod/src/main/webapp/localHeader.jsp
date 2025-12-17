<%--
  This Source Code Form is subject to the terms of the Mozilla Public License,
  v. 2.0. If a copy of the MPL was not distributed with this file, You can
  obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
  the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
  Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
  graphic logo is a trademark of OpenMRS Inc.
--%>
<ul id="menu">
    <li class="first">
        <a href="${pageContext.request.contextPath}/admin"><openmrs:message code="admin.title.short"/></a>
    </li>
    <li <c:if test="${currentPage == 'template' || (empty currentPage && empty param.page)}">class="active"</c:if>>
        <a href="${pageContext.request.contextPath}/module/dataimport/dataimport.form?page=template">
            <spring:message code="dataimport.tab.template"/>
        </a>
    </li>
    <li <c:if test="${currentPage == 'import'}">class="active"</c:if>>
        <a href="${pageContext.request.contextPath}/module/dataimport/dataimport.form?page=import">
            <spring:message code="dataimport.tab.import"/>
        </a>
    </li>
    <li <c:if test="${currentPage == 'auditlog' || param.page == 'auditlog'}">class="active"</c:if>>
        <a href="${pageContext.request.contextPath}/module/dataimport/auditlog.form">
            <spring:message code="dataimport.tab.history"/>
        </a>
    </li>
</ul>
