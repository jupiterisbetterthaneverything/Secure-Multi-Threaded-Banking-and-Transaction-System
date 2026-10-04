package com.bank.service;

import com.bank.config.DatabaseConfig;
import com.bank.dao.AccountDAO;
import com.bank.dao.TransactionDAO;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.BankingException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidAmountException;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.OperationType;
import com.bank.model.Transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Single-account operations: deposit, withdraw, balance, history.
 *
 * The locking strategy is two-layered:
 *   1. An in-JVM ReentrantLock per account, which cuts contention early and
 *      keeps this process orderly.
 *   2. A database row lock (SELECT ... FOR UPDATE) inside an explicit
 *      transaction, which is what actually guarantees correctness - including
 *      when a second instance of the app is running.
 *
 * Layer 1 alone would be a bug. Layer 2 alone would be correct but noisier.
 */
public class AccountService {

    private final AccountDAO accountDAO = new AccountDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();

    public long openAccount(long userId, AccountType type, BigDecimal openingBalance)
            throws BankingException {
        if (openingBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAmountException(openingBalance);
        }
        try (Connection conn = DatabaseConfig.getConnection()) {
            return accountDAO.insert(conn, userId, type, openingBalance);
        } catch (SQLException e) {
            throw new BankingException("Could not open account for user " + userId, e);
        }
    }

    public BigDecimal deposit(long accountId, BigDecimal amount) throws BankingException {
        requirePositive(amount);
        return mutateBalance(accountId, amount, OperationType.DEPOSIT);
    }

    public BigDecimal withdraw(long accountId, BigDecimal amount) throws BankingException {
        requirePositive(amount);
        return mutateBalance(accountId, amount.negate(), OperationType.WITHDRAW);
    }

    /**
     * Applies a signed delta to one account atomically.
     *
     * The whole read-check-write sequence sits between setAutoCommit(false)
     * and commit(), so no other transaction can observe or modify the row in
     * between. This is the fix for the classic lost-update race, where two
     * threads both read 1000, both compute 1100, and one write silently
     * overwrites the other.
     */
    private BigDecimal mutateBalance(long accountId, BigDecimal delta, OperationType type)
            throws BankingException {

        ReentrantLock lock = com.bank.util.AccountLockManager.lockFor(accountId);
        lock.lock();
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            Account account = accountDAO.findByIdForUpdate(conn, accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));

            BigDecimal newBalance = account.getBalance().add(delta);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientFundsException(
                        accountId, account.getBalance(), delta.abs());
            }

            accountDAO.updateBalance(conn, accountId, newBalance);
            transactionDAO.insert(conn, accountId, null, type, delta.abs(), newBalance);

            conn.commit();
            return newBalance;

        } catch (BankingException e) {
            rollbackQuietly(conn);
            throw e;
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new BankingException(type + " failed on account " + accountId, e);
        } finally {
            closeQuietly(conn);
            lock.unlock();
        }
    }

    public BigDecimal getBalance(long accountId) throws BankingException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return accountDAO.findById(conn, accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId))
                    .getBalance();
        } catch (SQLException e) {
            throw new BankingException("Could not read balance for account " + accountId, e);
        }
    }

    public List<Account> listAccounts(long userId) throws BankingException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return accountDAO.findByUserId(conn, userId);
        } catch (SQLException e) {
            throw new BankingException("Could not list accounts for user " + userId, e);
        }
    }

    public List<Transaction> getHistory(long accountId, int limit) throws BankingException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return transactionDAO.findByAccountId(conn, accountId, limit);
        } catch (SQLException e) {
            throw new BankingException("Could not read history for account " + accountId, e);
        }
    }

    private void requirePositive(BigDecimal amount) throws InvalidAmountException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException(amount);
        }
    }

    static void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // Nothing useful to do; the original failure is what matters.
            }
        }
    }

    static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException ignored) {
                // Connection is returned to the pool regardless.
            }
        }
    }
}
