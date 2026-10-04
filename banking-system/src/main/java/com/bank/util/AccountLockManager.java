package com.bank.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-account locks, so that two threads touching different accounts never
 * block each other.
 *
 * IMPORTANT: this is an in-JVM optimisation only. It reduces contention and
 * keeps this process well behaved, but it protects nothing if a second copy
 * of the application runs against the same database. The real correctness
 * guarantee lives in the database layer (SELECT ... FOR UPDATE inside a
 * transaction) - see AccountDAO#findByIdForUpdate.
 */
public final class AccountLockManager {

    private static final Map<Long, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private AccountLockManager() {
    }

    public static ReentrantLock lockFor(long accountId) {
        // Fair locks: threads acquire in arrival order, which prevents a
        // thread from being starved indefinitely under heavy contention.
        return LOCKS.computeIfAbsent(accountId, id -> new ReentrantLock(true));
    }

    /**
     * Acquires two locks in ascending account-id order.
     *
     * This ordering is the deadlock prevention strategy. If thread A transfers
     * 1 -> 2 while thread B transfers 2 -> 1, both threads still grab lock(1)
     * before lock(2), so neither can hold one while waiting on the other.
     */
    public static ReentrantLock[] lockPairOrdered(long firstId, long secondId) {
        long low = Math.min(firstId, secondId);
        long high = Math.max(firstId, secondId);
        return new ReentrantLock[]{lockFor(low), lockFor(high)};
    }
}
