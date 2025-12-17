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

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.openmrs.BaseOpenmrsObject;
import org.openmrs.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Entity representing an import audit log entry. Tracks all import operations (dry-run and commit)
 * for historical reporting and auditing.
 */
@Entity
@Table(name = "dataimport_audit_log")
public class ImportAuditLog extends BaseOpenmrsObject {
	
	/**
	 * Import action types
	 */
	public enum Action {
		DRY_RUN,
		COMMIT
	}
	
	/**
	 * Import status values
	 */
	public enum Status {
		SUCCESS,
		PARTIAL_SUCCESS,
		FAILED,
		VALIDATION_ERRORS
	}
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;
	
	@Column(name = "batch_id", nullable = false, length = 38)
	private String batchId;
	
	@Column(name = "filename", length = 255)
	private String filename;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 50)
	private Status status;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 50)
	private Action action;
	
	@Column(name = "total_rows")
	private Integer totalRows;
	
	@Column(name = "successful_rows")
	private Integer successfulRows;
	
	@Column(name = "quarantined_rows")
	private Integer quarantinedRows;
	
	@Column(name = "patients_created")
	private Integer patientsCreated;
	
	@Column(name = "encounters_created")
	private Integer encountersCreated;
	
	@Column(name = "obs_created")
	private Integer obsCreated;
	
	@Column(name = "location_uuid", length = 38)
	private String locationUuid;
	
	@Column(name = "form_uuids", columnDefinition = "TEXT")
	private String formUuids;
	
	@Column(name = "error_summary", columnDefinition = "TEXT")
	private String errorSummary;
	
	@ManyToOne
	@JoinColumn(name = "creator", nullable = false)
	private User creator;
	
	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "date_created", nullable = false)
	private Date dateCreated;
	
	@Column(name = "duration_ms")
	private Long durationMs;
	
	@OneToMany(mappedBy = "auditLog", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ImportAuditError> errors = new ArrayList<>();
	
	public ImportAuditLog() {
		// Default constructor
	}
	
	@Override
	public Integer getId() {
		return id;
	}
	
	@Override
	public void setId(Integer id) {
		this.id = id;
	}
	
	public String getBatchId() {
		return batchId;
	}
	
	public void setBatchId(String batchId) {
		this.batchId = batchId;
	}
	
	public String getFilename() {
		return filename;
	}
	
	public void setFilename(String filename) {
		this.filename = filename;
	}
	
	public Status getStatus() {
		return status;
	}
	
	public void setStatus(Status status) {
		this.status = status;
	}
	
	public Action getAction() {
		return action;
	}
	
	public void setAction(Action action) {
		this.action = action;
	}
	
	public Integer getTotalRows() {
		return totalRows;
	}
	
	public void setTotalRows(Integer totalRows) {
		this.totalRows = totalRows;
	}
	
	public Integer getSuccessfulRows() {
		return successfulRows;
	}
	
	public void setSuccessfulRows(Integer successfulRows) {
		this.successfulRows = successfulRows;
	}
	
	public Integer getQuarantinedRows() {
		return quarantinedRows;
	}
	
	public void setQuarantinedRows(Integer quarantinedRows) {
		this.quarantinedRows = quarantinedRows;
	}
	
	public Integer getPatientsCreated() {
		return patientsCreated;
	}
	
	public void setPatientsCreated(Integer patientsCreated) {
		this.patientsCreated = patientsCreated;
	}
	
	public Integer getEncountersCreated() {
		return encountersCreated;
	}
	
	public void setEncountersCreated(Integer encountersCreated) {
		this.encountersCreated = encountersCreated;
	}
	
	public Integer getObsCreated() {
		return obsCreated;
	}
	
	public void setObsCreated(Integer obsCreated) {
		this.obsCreated = obsCreated;
	}
	
	public String getLocationUuid() {
		return locationUuid;
	}
	
	public void setLocationUuid(String locationUuid) {
		this.locationUuid = locationUuid;
	}
	
	public String getFormUuids() {
		return formUuids;
	}
	
	public void setFormUuids(String formUuids) {
		this.formUuids = formUuids;
	}
	
	public String getErrorSummary() {
		return errorSummary;
	}
	
	public void setErrorSummary(String errorSummary) {
		this.errorSummary = errorSummary;
	}
	
	public User getCreator() {
		return creator;
	}
	
	public void setCreator(User creator) {
		this.creator = creator;
	}
	
	public Date getDateCreated() {
		return dateCreated;
	}
	
	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}
	
	public Long getDurationMs() {
		return durationMs;
	}
	
	public void setDurationMs(Long durationMs) {
		this.durationMs = durationMs;
	}
	
	public List<ImportAuditError> getErrors() {
		return errors;
	}
	
	public void setErrors(List<ImportAuditError> errors) {
		this.errors = errors;
	}
	
	public void addError(ImportAuditError error) {
		errors.add(error);
		error.setAuditLog(this);
	}
	
	public void removeError(ImportAuditError error) {
		errors.remove(error);
		error.setAuditLog(null);
	}
}
