package com.bank.model;

import java.math.BigDecimal;

/**
 * Snapshot of an account row. Deliberately immutable: the authoritative
 * balance lives in the database, not in a long-lived Java object. Holding a
 * mutable balance in memory is exactly how stale-read bugs get introduced.
 */
public class Account {

    private final long id;
    private final long userId;
    private final AccountType type;
    private final BigDecimal balance;

    public Account(long id, long userId, AccountType type, BigDecimal balance) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.balance = balance;
    }

    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public AccountType getType() {
        return type;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    @Override
    public String toString() {
        return "Account{id=" + id + ", type=" + type + ", balance=" + balance + "}";
    }
}
