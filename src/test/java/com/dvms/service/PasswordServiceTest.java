package com.dvms.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {

    @Test
    void hashAndVerifyCorrectPassword() {
        char[] password = "SecurePass123!".toCharArray();
        String hash = PasswordService.hashPassword(password);

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2"));
        assertTrue(PasswordService.verifyPassword("SecurePass123!".toCharArray(), hash));
    }

    @Test
    void verifyIncorrectPasswordReturnsFalse() {
        char[] password = "CorrectPassword".toCharArray();
        String hash = PasswordService.hashPassword(password);

        assertFalse(PasswordService.verifyPassword("WrongPassword".toCharArray(), hash));
    }

    @Test
    void verifyNullPasswordReturnsFalse() {
        assertFalse(PasswordService.verifyPassword(null, "$2a$12$cPgSLHXNyhmNSJby5Oi.nOnoTroScsxALyXUh3UVvp3l0J24CCbo6"));
    }

    @Test
    void verifyEmptyPasswordReturnsFalse() {
        assertFalse(PasswordService.verifyPassword(new char[0], "$2a$12$cPgSLHXNyhmNSJby5Oi.nOnoTroScsxALyXUh3UVvp3l0J24CCbo6"));
    }

    @Test
    void verifyNullHashReturnsFalse() {
        assertFalse(PasswordService.verifyPassword("password".toCharArray(), null));
    }

    @Test
    void verifyBlankHashReturnsFalse() {
        assertFalse(PasswordService.verifyPassword("password".toCharArray(), "   "));
    }

    @Test
    void verifyMalformedHashReturnsFalseWithoutException() {
        assertFalse(PasswordService.verifyPassword("password".toCharArray(), "not-a-bcrypt-hash"));
        assertFalse(PasswordService.verifyPassword("password".toCharArray(), "$2a$12$short"));
    }

    @Test
    void hashPasswordRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> PasswordService.hashPassword(null));
    }

    @Test
    void hashPasswordRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> PasswordService.hashPassword(new char[0]));
    }

    @Test
    void differentPasswordsProduceDifferentHashes() {
        String hash1 = PasswordService.hashPassword("Password1".toCharArray());
        String hash2 = PasswordService.hashPassword("Password2".toCharArray());

        assertNotEquals(hash1, hash2);
    }

    @Test
    void samePasswordProducesDifferentHashesDueToSalt() {
        String hash1 = PasswordService.hashPassword("SamePassword".toCharArray());
        String hash2 = PasswordService.hashPassword("SamePassword".toCharArray());

        assertNotEquals(hash1, hash2);
        assertTrue(PasswordService.verifyPassword("SamePassword".toCharArray(), hash1));
        assertTrue(PasswordService.verifyPassword("SamePassword".toCharArray(), hash2));
    }

    @Test
    void plaintextPasswordDoesNotVerifyAsBcrypt() {
        assertFalse(PasswordService.verifyPassword("password".toCharArray(), "password"));
    }

    @Test
    void isValidBCryptHashValidatesCorrectly() {
        String validHash = "$2a$12$cPgSLHXNyhmNSJby5Oi.nOnoTroScsxALyXUh3UVvp3l0J24CCbo6";
        assertTrue(PasswordService.isValidBCryptHash(validHash));
        assertTrue(PasswordService.isValidBCryptHash("$2b$12$eImiTXuWVxfM37uY4JANjOL.oUT1uv7IedNNmA5qd8.affDnXOOm6"));

        assertFalse(PasswordService.isValidBCryptHash(null));
        assertFalse(PasswordService.isValidBCryptHash(""));
        assertFalse(PasswordService.isValidBCryptHash("plainPassword123"));
        assertFalse(PasswordService.isValidBCryptHash("$2a$12$short"));
        assertFalse(PasswordService.isValidBCryptHash("$2c$12$cPgSLHXNyhmNSJby5Oi.nOnoTroScsxALyXUh3UVvp3l0J24CCbo6"));
    }
}
