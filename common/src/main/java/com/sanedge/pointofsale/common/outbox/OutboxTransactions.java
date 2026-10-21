package com.sanedge.pointofsale.common.outbox;

import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Helper for the transactional outbox contract.
 *
 * <p>The outbox row must be written in the same transaction as the business row,
 * so a failing outbox write has to abort the whole transaction instead of
 * leaving an aggregate without its event. Service methods that answer with an
 * error response (instead of propagating the exception) call
 * {@link #markRollbackOnly()} so the transaction is rolled back anyway.
 */
public final class OutboxTransactions {

    private OutboxTransactions() {
    }

    /**
     * Marks the current transaction as rollback-only.
     *
     * <p>No-op when no transaction is active (e.g. unit tests calling the service
     * directly), so it is always safe to call from an error path.
     */
    public static void markRollbackOnly() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
}
