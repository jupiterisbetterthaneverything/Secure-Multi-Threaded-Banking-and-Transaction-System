package com.bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {

    private final long id;
    private final long accountId;
    private final Long relatedAccountId;
    private final OperationType operationType;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final LocalDateTime createdAt;

    public Transaction(long id, long accountId, Long relatedAccountId,
                       OperationType operationType, BigDecimal amount,
                       BigDecimal balanceAfter, LocalDateTime createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.relatedAccountId = relatedAccountId;
        this.operationType = operationType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public long getAccountId() {
        return accountId;
    }

    public Long getRelatedAccountId() {
        return relatedAccountId;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return String.format("[%s] %-12s %10s -> balance %s",
                createdAt, operationType, amount, balanceAfter);
    }
}
