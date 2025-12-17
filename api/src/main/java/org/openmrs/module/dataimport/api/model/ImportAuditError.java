/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Entity representing an individual error detail within an import audit log. Stores structured
 * error information for detailed reporting.
 */
@Entity
@Table(name = "dataimport_audit_error")
public class ImportAuditError {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;
	
	@ManyToOne
	@JoinColumn(name = "audit_log_id", nullable = false)
	private ImportAuditLog auditLog;
	
	@Column(name = "sheet_name", length = 255)
	private String sheetName;
	
	@Column(name = "row_index")
	private Integer rowIndex;
	
	@Column(name = "column_index")
	private Integer columnIndex;
	
	@Column(name = "error_code", nullable = false, length = 100)
	private String errorCode;
	
	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;
	
	public ImportAuditError() {
		// Default constructor
	}
	
	public ImportAuditError(String sheetName, Integer rowIndex, Integer columnIndex, String errorCode, String errorMessage) {
		this.sheetName = sheetName;
		this.rowIndex = rowIndex;
		this.columnIndex = columnIndex;
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
	}
	
	public Integer getId() {
		return id;
	}
	
	public void setId(Integer id) {
		this.id = id;
	}
	
	public ImportAuditLog getAuditLog() {
		return auditLog;
	}
	
	public void setAuditLog(ImportAuditLog auditLog) {
		this.auditLog = auditLog;
	}
	
	public String getSheetName() {
		return sheetName;
	}
	
	public void setSheetName(String sheetName) {
		this.sheetName = sheetName;
	}
	
	public Integer getRowIndex() {
		return rowIndex;
	}
	
	public void setRowIndex(Integer rowIndex) {
		this.rowIndex = rowIndex;
	}
	
	public Integer getColumnIndex() {
		return columnIndex;
	}
	
	public void setColumnIndex(Integer columnIndex) {
		this.columnIndex = columnIndex;
	}
	
	public String getErrorCode() {
		return errorCode;
	}
	
	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}
	
	public String getErrorMessage() {
		return errorMessage;
	}
	
	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}
