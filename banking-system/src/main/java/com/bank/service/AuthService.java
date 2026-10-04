package com.bank.service;

import com.bank.config.DatabaseConfig;
import com.bank.dao.UserDAO;
import com.bank.exception.AuthenticationException;
import com.bank.exception.BankingException;
import com.bank.model.User;
import com.bank.util.PasswordUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class AuthService {

    private final UserDAO userDAO = new UserDAO();

    public long register(String username, String plainPassword, String fullName)
            throws BankingException {
        validateUsername(username);
        validatePassword(plainPassword);

        String hash = PasswordUtil.hash(plainPassword);
        try (Connection conn = DatabaseConfig.getConnection()) {
            return userDAO.insert(conn, username, hash, fullName);
        } catch (SQLException e) {
            // MySQL error 1062 = duplicate entry on a unique index.
            if (e.getErrorCode() == 1062) {
                throw new BankingException("Username '" + username + "' is already taken");
            }
            throw new BankingException("Registration failed for '" + username + "'", e);
        }
    }

    public User login(String username, String plainPassword) throws BankingException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            Optional<String> storedHash = userDAO.findPasswordHash(conn, username);

            // Same generic message whether the user is missing or the password
            // is wrong, so the response cannot be used to enumerate usernames.
            if (storedHash.isEmpty() || !PasswordUtil.matches(plainPassword, storedHash.get())) {
                throw new AuthenticationException("Invalid username or password");
            }

            return userDAO.findByUsername(conn, username)
                    .orElseThrow(() -> new AuthenticationException("Invalid username or password"));

        } catch (SQLException e) {
            throw new BankingException("Login failed for '" + username + "'", e);
        }
    }

    private void validateUsername(String username) throws BankingException {
        if (username == null || !username.matches("^[A-Za-z0-9_]{3,50}$")) {
            throw new BankingException(
                    "Username must be 3-50 characters: letters, digits or underscore");
        }
    }

    private void validatePassword(String password) throws BankingException {
        if (password == null || password.length() < 8) {
            throw new BankingException("Password must be at least 8 characters");
        }
    }
}
