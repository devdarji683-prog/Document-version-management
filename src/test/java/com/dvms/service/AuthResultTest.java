package com.dvms.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthResultTest {

    @Test
    void successResultContainsUser() {
        com.dvms.model.User user = new com.dvms.model.User();
        user.setId(1);
        user.setUsername("testuser");

        AuthResult result = AuthResult.success(user);

        assertTrue(result.isSuccess());
        assertNotNull(result.getUser());
        assertEquals("testuser", result.getUser().getUsername());
        assertNull(result.getMessage());
    }

    @Test
    void failureResultContainsMessage() {
        AuthResult result = AuthResult.failure("Invalid credentials");

        assertFalse(result.isSuccess());
        assertNull(result.getUser());
        assertEquals("Invalid credentials", result.getMessage());
    }
}
