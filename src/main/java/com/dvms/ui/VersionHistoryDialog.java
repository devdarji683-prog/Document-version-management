package com.dvms.ui;

import com.dvms.model.Document;
import com.dvms.model.DocumentVersion;
import com.dvms.model.User;
import com.dvms.service.DocumentService;
import com.dvms.service.FileStorageService;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/**
 * Dialog to display and manage document version history.
 */
public final class VersionHistoryDialog extends JDialog {
    private final User currentUser;
    private final Document document;
    private final Runnable onVersionRestored;
    private final DocumentService documentService = new DocumentService();

    private final DefaultTableModel historyModel = new DefaultTableModel(
            new Object[] {"Version", "Change Summary", "Author (User ID)", "Creation Date", "Integrity Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable historyTable = new JTable(historyModel);
    private final List<DocumentVersion> loadedVersions = new ArrayList<>();

    public VersionHistoryDialog(Document document, User currentUser, java.awt.Component parent) {
        this(document, currentUser, parent, null);
    }

    public VersionHistoryDialog(Document document, User currentUser, java.awt.Component parent, Runnable onVersionRestored) {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent), "Version History - " + document.getTitle(),
                JDialog.ModalityType.APPLICATION_MODAL);
        this.currentUser = currentUser;
        this.document = document;
        this.onVersionRestored = onVersionRestored;

        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(780, 480));

        setupUI();
        loadVersionHistory();
    }

    private void setupUI() {
        setLayout(new BorderLayout(12, 12));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        JLabel title = new JLabel("Version History — " + document.getTitle(), SwingConstants.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        header.add(title, BorderLayout.WEST);

        // Table
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.setAutoCreateRowSorter(true);
        JScrollPane tableScrollPane = new JScrollPane(historyTable);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton verifyButton = new JButton("Verify Integrity");
        JButton restoreButton = new JButton("Restore Selected Version");
        JButton refreshButton = new JButton("Refresh");
        JButton closeButton = new JButton("Close");

        verifyButton.addActionListener(event -> verifySelectedVersionIntegrity());
        restoreButton.addActionListener(event -> restoreSelectedVersion());
        refreshButton.addActionListener(event -> loadVersionHistory());
        closeButton.addActionListener(event -> dispose());

        buttonPanel.add(verifyButton);
        buttonPanel.add(restoreButton);
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);

        // Legend / status footer
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        legendPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));
        JLabel legendLabel = new JLabel("* Version records are immutable. Restoring a version creates a new version with its content.");
        legendLabel.setFont(legendLabel.getFont().deriveFont(Font.ITALIC, 11f));
        legendPanel.add(legendLabel);

        // Combine buttonPanel and legendPanel into a single SOUTH container
        JPanel southContainer = new JPanel();
        southContainer.setLayout(new BoxLayout(southContainer, BoxLayout.Y_AXIS));
        southContainer.add(buttonPanel);
        southContainer.add(legendPanel);

        add(header, BorderLayout.NORTH);
        add(tableScrollPane, BorderLayout.CENTER);
        add(southContainer, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private void loadVersionHistory() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<DocumentVersion>, Void>() {
            @Override
            protected List<DocumentVersion> doInBackground() throws Exception {
                return documentService.getVersionHistory(document.getId(), currentUser);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    List<DocumentVersion> versions = get();
                    loadedVersions.clear();
                    historyModel.setRowCount(0);

                    if (versions == null || versions.isEmpty()) {
                        JOptionPane.showMessageDialog(VersionHistoryDialog.this,
                                "No version history exists for this document.",
                                "Version History", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        loadedVersions.addAll(versions);
                        for (DocumentVersion version : versions) {
                            historyModel.addRow(new Object[] {
                                    version.getVersionNumber(),
                                    version.getChangeSummary(),
                                    version.getAuthorUserId(),
                                    format(version.getCreatedAt()),
                                    "Not checked"
                            });
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("Version history loading was interrupted.", e);
                } catch (ExecutionException e) {
                    showError("Could not load version history.", e.getCause());
                }
            }
        }.execute();
    }

    private void verifySelectedVersionIntegrity() {
        DocumentVersion version = getSelectedVersion();
        if (version == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select a version to verify.",
                    "Select Version", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int selectedViewRow = historyTable.getSelectedRow();
        int modelRow = historyTable.convertRowIndexToModel(selectedViewRow);

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    Path filePath = FileStorageService.resolveStoragePath(version.getRelativeStoragePath());
                    if (!Files.exists(filePath)) {
                        return "Missing file";
                    }
                    boolean ok = FileStorageService.verifyChecksum(filePath, version.getSha256Checksum());
                    return ok ? "Verified" : "Checksum mismatch";
                } catch (Exception e) {
                    return "Error: " + e.getMessage();
                }
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    String status = get();
                    historyModel.setValueAt(status, modelRow, 4);
                    if ("Verified".equals(status)) {
                        JOptionPane.showMessageDialog(
                                VersionHistoryDialog.this,
                                "Integrity verified: Version " + version.getVersionNumber()
                                        + " matches its recorded SHA-256 checksum.",
                                "Integrity Verified",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(
                                VersionHistoryDialog.this,
                                "Integrity check failed: " + status + "\n"
                                        + "The file may be corrupted, missing, or altered.",
                                "Integrity Check Failed",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("Integrity verification was interrupted.", e);
                } catch (ExecutionException e) {
                    showError("Could not verify integrity.", e.getCause());
                }
            }
        }.execute();
    }

    private void restoreSelectedVersion() {
        DocumentVersion version = getSelectedVersion();
        if (version == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select a version to restore.",
                    "Select Version", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Restore version " + version.getVersionNumber() + "?\n\n"
                        + "This will create a new version with the content from version " + version.getVersionNumber() + ".\n"
                        + "All existing historical versions will remain unchanged.",
                "Confirm Restoration",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<DocumentService.RestoreVersionResult, Void>() {
            @Override
            protected DocumentService.RestoreVersionResult doInBackground() throws Exception {
                String summary = "Restored from version " + version.getVersionNumber();
                return documentService.restoreVersion(
                        document.getId(),
                        version.getVersionNumber(),
                        currentUser,
                        summary);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    DocumentService.RestoreVersionResult result = get();
                    if (result.isSuccess()) {
                        JOptionPane.showMessageDialog(
                                VersionHistoryDialog.this,
                                "Version " + version.getVersionNumber() + " restored successfully.\n"
                                        + "A new version (" + result.getVersionNumber() + ") has been created.",
                                "Restore Successful",
                                JOptionPane.INFORMATION_MESSAGE);
                        loadVersionHistory();
                        if (onVersionRestored != null) {
                            onVersionRestored.run();
                        }
                    } else {
                        JOptionPane.showMessageDialog(
                                VersionHistoryDialog.this,
                                result.getErrorMessage(),
                                "Restore Failed",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError("Restore operation was interrupted.", e);
                } catch (ExecutionException e) {
                    showError("Could not restore version.", e.getCause());
                }
            }
        }.execute();
    }

    private DocumentVersion getSelectedVersion() {
        int selectedRow = historyTable.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        int modelRow = historyTable.convertRowIndexToModel(selectedRow);
        Object value = historyModel.getValueAt(modelRow, 0);
        if (!(value instanceof Number)) {
            return null;
        }
        int versionNumber = ((Number) value).intValue();
        for (DocumentVersion v : loadedVersions) {
            if (v.getVersionNumber() == versionNumber) {
                return v;
            }
        }
        return null;
    }

    private static String format(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.toString().replace('T', ' ');
    }

    private void showError(String message, Throwable cause) {
        String detail = cause == null || cause.getMessage() == null ? message : message + "\n" + cause.getMessage();
        JOptionPane.showMessageDialog(this, detail, "Operation Failed", JOptionPane.ERROR_MESSAGE);
    }
}
