package com.dvms;

import com.dvms.ui.LoginFrame;
import javax.swing.SwingUtilities;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}