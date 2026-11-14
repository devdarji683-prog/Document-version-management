package com.dvms.service;

import com.dvms.model.User;

public final class SessionManager {
    private static volatile User currentUser;

    private SessionManager() {
    }

    public static void login(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Cannot start a session without an authenticated user.");
        }
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static void logout() {
        currentUser = null;
    }
}
