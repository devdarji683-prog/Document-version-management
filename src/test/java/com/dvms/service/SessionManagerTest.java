package com.dvms.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @Test
    void initiallyNotLoggedIn() {
        SessionManager.logout();
        assertFalse(SessionManager.isLoggedIn());
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void loginSetsCurrentUser() {
        com.dvms.model.User user = new com.dvms.model.User();
        user.setId(1);
        user.setUsername("testuser");

        SessionManager.login(user);

        assertTrue(SessionManager.isLoggedIn());
        assertEquals("testuser", SessionManager.getCurrentUser().getUsername());
        SessionManager.logout();
    }

    @Test
    void logoutClearsSession() {
        com.dvms.model.User user = new com.dvms.model.User();
        user.setId(1);
        SessionManager.login(user);
        SessionManager.logout();

        assertFalse(SessionManager.isLoggedIn());
        assertNull(SessionManager.getCurrentUser());
    }

    @Test
    void loginRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> SessionManager.login(null));
    }
}
