package com.bank.exception;

public class AuthenticationException extends BankingException {

    public AuthenticationException(String message) {
        super(message);
    }
}
