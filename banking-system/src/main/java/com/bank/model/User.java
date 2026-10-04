package com.bank.model;

import java.time.LocalDateTime;

public class User {

    private final long id;
    private final String username;
    private final String fullName;
    private final LocalDateTime createdAt;

    public User(long id, String username, String fullName, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "'}";
    }
}
