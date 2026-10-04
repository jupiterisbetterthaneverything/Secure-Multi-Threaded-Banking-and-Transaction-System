package com.bank.util;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtil {

    /**
     * bcrypt cost factor. Each increment doubles the hashing time.
     * 12 is a reasonable 2020s default; raise it as hardware gets faster.
     */
    private static final int COST = 12;

    private PasswordUtil() {
    }

    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(COST));
    }

    public static boolean matches(String plainPassword, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return BCrypt.checkpw(plainPassword, storedHash);
    }
}
