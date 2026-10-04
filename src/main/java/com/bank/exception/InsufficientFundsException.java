package com.bank.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends BankingException {

    public InsufficientFundsException(long accountId, BigDecimal balance, BigDecimal requested) {
        super("Account " + accountId + " has balance " + balance
                + " which is less than the requested " + requested);
    }
}
