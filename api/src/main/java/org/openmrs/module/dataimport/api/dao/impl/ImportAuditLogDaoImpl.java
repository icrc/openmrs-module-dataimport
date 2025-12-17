/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.dataimport.api.dao.impl;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.openmrs.module.dataimport.api.dao.ImportAuditLogDao;
import org.openmrs.module.dataimport.api.model.ImportAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * Hibernate implementation of ImportAuditLogDao.
 */
public class ImportAuditLogDaoImpl implements ImportAuditLogDao {
	
	private static final Logger log = LoggerFactory.getLogger(ImportAuditLogDaoImpl.class);
	
	private SessionFactory sessionFactory;
	
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	private Session getCurrentSession() {
		return sessionFactory.getCurrentSession();
	}
	
	@Override
	public ImportAuditLog saveOrUpdate(ImportAuditLog auditLog) {
		getCurrentSession().saveOrUpdate(auditLog);
		return auditLog;
	}
	
	@Override
	public ImportAuditLog getById(Integer id) {
		return (ImportAuditLog) getCurrentSession().get(ImportAuditLog.class, id);
	}
	
	@Override
	public ImportAuditLog getByUuid(String uuid) {
		Criteria criteria = getCurrentSession().createCriteria(ImportAuditLog.class);
		criteria.add(Restrictions.eq("uuid", uuid));
		return (ImportAuditLog) criteria.uniqueResult();
	}
	
	@Override
	public ImportAuditLog getByBatchId(String batchId) {
		Criteria criteria = getCurrentSession().createCriteria(ImportAuditLog.class);
		criteria.add(Restrictions.eq("batchId", batchId));
		criteria.addOrder(Order.desc("dateCreated"));
		criteria.setMaxResults(1);
		return (ImportAuditLog) criteria.uniqueResult();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<ImportAuditLog> getAllByBatchId(String batchId) {
		Criteria criteria = getCurrentSession().createCriteria(ImportAuditLog.class);
		criteria.add(Restrictions.eq("batchId", batchId));
		criteria.addOrder(Order.asc("dateCreated"));
		return criteria.list();
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<ImportAuditLog> getAuditLogs(ImportAuditLog.Status status, ImportAuditLog.Action action, Integer creatorId,
	        Date startDate, Date endDate, Integer startIndex, Integer maxResults) {
		Criteria criteria = buildFilterCriteria(status, action, creatorId, startDate, endDate);
		criteria.addOrder(Order.desc("dateCreated"));
		
		if (startIndex != null) {
			criteria.setFirstResult(startIndex);
		}
		if (maxResults != null) {
			criteria.setMaxResults(maxResults);
		}
		
		return criteria.list();
	}
	
	@Override
	public Long countAuditLogs(ImportAuditLog.Status status, ImportAuditLog.Action action, Integer creatorId, Date startDate,
	        Date endDate) {
		Criteria criteria = buildFilterCriteria(status, action, creatorId, startDate, endDate);
		criteria.setProjection(Projections.rowCount());
		return (Long) criteria.uniqueResult();
	}
	
	@Override
	public int deleteOlderThan(Date olderThan) {
		String hql = "DELETE FROM ImportAuditLog WHERE dateCreated < :olderThan";
		int deleted = getCurrentSession().createQuery(hql).setParameter("olderThan", olderThan).executeUpdate();
		log.info("Deleted {} audit log entries older than {}", deleted, olderThan);
		return deleted;
	}
	
	@Override
	public void delete(Integer id) {
		ImportAuditLog auditLog = getById(id);
		if (auditLog != null) {
			getCurrentSession().delete(auditLog);
		}
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public List<ImportAuditLog> getRecentAuditLogs(int maxResults) {
		Criteria criteria = getCurrentSession().createCriteria(ImportAuditLog.class);
		criteria.addOrder(Order.desc("dateCreated"));
		criteria.setMaxResults(maxResults);
		return criteria.list();
	}
	
	private Criteria buildFilterCriteria(ImportAuditLog.Status status, ImportAuditLog.Action action, Integer creatorId,
	        Date startDate, Date endDate) {
		Criteria criteria = getCurrentSession().createCriteria(ImportAuditLog.class);
		
		if (status != null) {
			criteria.add(Restrictions.eq("status", status));
		}
		if (action != null) {
			criteria.add(Restrictions.eq("action", action));
		}
		if (creatorId != null) {
			criteria.add(Restrictions.eq("creator.userId", creatorId));
		}
		if (startDate != null) {
			criteria.add(Restrictions.ge("dateCreated", startDate));
		}
		if (endDate != null) {
			criteria.add(Restrictions.le("dateCreated", endDate));
		}
		
		return criteria;
	}
}
