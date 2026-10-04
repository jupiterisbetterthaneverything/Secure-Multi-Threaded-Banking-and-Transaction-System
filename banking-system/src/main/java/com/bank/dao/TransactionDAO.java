package com.bank.dao;

import com.bank.model.OperationType;
import com.bank.model.Transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {

    /**
     * Appends one row to the audit ledger. Written inside the same database
     * transaction as the balance update, so a transaction row can never exist
     * without its corresponding balance change, or vice versa.
     */
    public void insert(Connection conn, long accountId, Long relatedAccountId,
                       OperationType type, BigDecimal amount, BigDecimal balanceAfter)
            throws SQLException {
        String sql = "INSERT INTO transactions "
                + "(account_id, related_account_id, operation_type, amount, balance_after) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            if (relatedAccountId == null) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, relatedAccountId);
            }
            ps.setString(3, type.name());
            ps.setBigDecimal(4, amount);
            ps.setBigDecimal(5, balanceAfter);
            ps.executeUpdate();
        }
    }

    public List<Transaction> findByAccountId(Connection conn, long accountId, int limit)
            throws SQLException {
        String sql = "SELECT id, account_id, related_account_id, operation_type, amount, "
                + "balance_after, created_at FROM transactions "
                + "WHERE account_id = ? ORDER BY created_at DESC, id DESC LIMIT ?";
        List<Transaction> history = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long relatedValue = rs.getLong("related_account_id");
                    // wasNull() reports on the most recent column read, so it
                    // must be captured immediately, not inline further below.
                    Long relatedAccountId = rs.wasNull() ? null : relatedValue;
                    history.add(new Transaction(
                            rs.getLong("id"),
                            rs.getLong("account_id"),
                            relatedAccountId,
                            OperationType.valueOf(rs.getString("operation_type")),
                            rs.getBigDecimal("amount"),
                            rs.getBigDecimal("balance_after"),
                            rs.getTimestamp("created_at").toLocalDateTime()));
                }
            }
        }
        return history;
    }

    public long countByAccountId(Connection conn, long accountId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM transactions WHERE account_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }
}
