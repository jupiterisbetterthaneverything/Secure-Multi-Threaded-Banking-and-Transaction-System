package com.bank.dao;

import com.bank.model.Account;
import com.bank.model.AccountType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AccountDAO {

    public long insert(Connection conn, long userId, AccountType type, BigDecimal openingBalance)
            throws SQLException {
        String sql = "INSERT INTO accounts (user_id, account_type, balance) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, type.name());
            ps.setBigDecimal(3, openingBalance);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Insert of account returned no generated key");
            }
        }
    }

    /** Plain read. No lock is taken, so the value may be stale the moment it returns. */
    public Optional<Account> findById(Connection conn, long accountId) throws SQLException {
        String sql = "SELECT id, user_id, account_type, balance FROM accounts WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            return mapSingle(ps);
        }
    }

    /**
     * Reads the account and holds an exclusive row lock until the surrounding
     * transaction commits or rolls back.
     *
     * This is the core of the concurrency design. Any other transaction that
     * runs the same statement on the same row will block here, which makes the
     * read-modify-write cycle atomic even across separate JVMs.
     *
     * The caller MUST have already set autoCommit(false), otherwise the
     * transaction ends immediately and the lock is released before the update.
     */
    public Optional<Account> findByIdForUpdate(Connection conn, long accountId) throws SQLException {
        if (conn.getAutoCommit()) {
            throw new SQLException(
                    "findByIdForUpdate requires an open transaction (setAutoCommit(false))");
        }
        String sql = "SELECT id, user_id, account_type, balance FROM accounts WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            return mapSingle(ps);
        }
    }

    public void updateBalance(Connection conn, long accountId, BigDecimal newBalance)
            throws SQLException {
        String sql = "UPDATE accounts SET balance = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newBalance);
            ps.setLong(2, accountId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Balance update affected no rows for account " + accountId);
            }
        }
    }

    public List<Account> findByUserId(Connection conn, long userId) throws SQLException {
        String sql = "SELECT id, user_id, account_type, balance FROM accounts WHERE user_id = ? ORDER BY id";
        List<Account> accounts = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    accounts.add(map(rs));
                }
            }
        }
        return accounts;
    }

    private Optional<Account> mapSingle(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        }
    }

    private Account map(ResultSet rs) throws SQLException {
        return new Account(
                rs.getLong("id"),
                rs.getLong("user_id"),
                AccountType.valueOf(rs.getString("account_type")),
                rs.getBigDecimal("balance"));
    }
}
