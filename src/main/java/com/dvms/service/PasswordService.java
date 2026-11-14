package com.dvms.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.util.regex.Pattern;

public final class PasswordService {
    public static final int WORK_FACTOR = 12;
    private static final Pattern BCRYPT_PATTERN =
            Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private PasswordService() {
    }

    public static String hashPassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty.");
        }
        return BCrypt.withDefaults().hashToString(WORK_FACTOR, password);
    }

    public static boolean verifyPassword(char[] password, String storedHash) {
        if (password == null || password.length == 0 || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            BCrypt.Result result = BCrypt.verifyer().verify(password, storedHash);
            return result.verified;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidBCryptHash(String hash) {
        return hash != null && BCRYPT_PATTERN.matcher(hash).matches();
    }
}
