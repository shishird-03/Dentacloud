package com.dentacloud.shared;

import jakarta.persistence.EntityManager;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Every transaction starts with set_config('app.clinic_id', ..., true), the function form of
 * SET LOCAL, so row-level security sees the tenant for exactly this transaction (PRD 8).
 * No tenant bound means an empty value, and the policies then match no rows.
 */
class TenantTransactionManager extends JpaTransactionManager {
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        EntityManager em = holder.getEntityManager();
        UUID tenant = TenantContext.current();
        em.createNativeQuery("select set_config('app.clinic_id', ?1, true)")
                .setParameter(1, tenant == null ? "" : tenant.toString())
                .getSingleResult();
    }
}
