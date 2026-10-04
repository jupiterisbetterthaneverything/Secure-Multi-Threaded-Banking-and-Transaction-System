package com.bank.service;

import com.bank.config.DatabaseConfig;
import com.bank.dao.AccountDAO;
import com.bank.dao.TransactionDAO;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.BankingException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidAmountException;
import com.bank.model.Account;
import com.bank.model.OperationType;
import com.bank.util.AccountLockManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Money movement between two accounts.
 *
 * Deadlock prevention: both the in-JVM locks and the database row locks are
 * acquired in ascending account-id order. Without that discipline, a transfer
 * of 1 -> 2 running at the same time as 2 -> 1 can have each thread holding
 * the lock the other one needs, and neither ever proceeds.
 */
public class TransferService {

    private final AccountDAO accountDAO = new AccountDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();

    public void transfer(long fromAccountId, long toAccountId, BigDecimal amount)
            throws BankingException {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException(amount);
        }
        if (fromAccountId == toAccountId) {
            throw new BankingException("Cannot transfer an account to itself");
        }

        ReentrantLock[] ordered = AccountLockManager.lockPairOrdered(fromAccountId, toAccountId);
        ordered[0].lock();
        ordered[1].lock();

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            // Row locks taken in the same ascending order as the JVM locks.
            long lowId = Math.min(fromAccountId, toAccountId);
            long highId = Math.max(fromAccountId, toAccountId);
            Account low = accountDAO.findByIdForUpdate(conn, lowId)
                    .orElseThrow(() -> new AccountNotFoundException(lowId));
            Account high = accountDAO.findByIdForUpdate(conn, highId)
                    .orElseThrow(() -> new AccountNotFoundException(highId));

            Account source = (low.getId() == fromAccountId) ? low : high;
            Account target = (low.getId() == fromAccountId) ? high : low;

            if (source.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                        fromAccountId, source.getBalance(), amount);
            }

            BigDecimal sourceAfter = source.getBalance().subtract(amount);
            BigDecimal targetAfter = target.getBalance().add(amount);

            accountDAO.updateBalance(conn, fromAccountId, sourceAfter);
            accountDAO.updateBalance(conn, toAccountId, targetAfter);

            // Two ledger rows: one debit, one credit. Both or neither.
            transactionDAO.insert(conn, fromAccountId, toAccountId,
                    OperationType.TRANSFER_OUT, amount, sourceAfter);
            transactionDAO.insert(conn, toAccountId, fromAccountId,
                    OperationType.TRANSFER_IN, amount, targetAfter);

            conn.commit();

        } catch (BankingException e) {
            AccountService.rollbackQuietly(conn);
            throw e;
        } catch (SQLException e) {
            AccountService.rollbackQuietly(conn);
            throw new BankingException(
                    "Transfer of " + amount + " from " + fromAccountId
                            + " to " + toAccountId + " failed", e);
        } finally {
            AccountService.closeQuietly(conn);
            ordered[1].unlock();
            ordered[0].unlock();
        }
    }
}
