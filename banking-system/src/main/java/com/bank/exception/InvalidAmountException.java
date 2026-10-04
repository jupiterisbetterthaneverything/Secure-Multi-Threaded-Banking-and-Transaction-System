package com.bank.exception;

import java.math.BigDecimal;

public class InvalidAmountException extends BankingException {

    public InvalidAmountException(BigDecimal amount) {
        super("Amount must be greater than zero, got " + amount);
    }
}
