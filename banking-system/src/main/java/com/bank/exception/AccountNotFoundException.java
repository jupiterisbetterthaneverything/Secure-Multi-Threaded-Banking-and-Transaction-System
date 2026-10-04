package com.bank.exception;

public class AccountNotFoundException extends BankingException {

    public AccountNotFoundException(long accountId) {
        super("No account found with id " + accountId);
    }
}
