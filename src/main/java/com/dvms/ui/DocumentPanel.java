package com.dvms.ui;

import com.dvms.config.DatabaseConnection;
import com.dvms.dao.DocumentVersionDAO;
import com.dvms.model.Document;
import com.dvms.model.DocumentVersion;
import com.dvms.model.User;
import com.dvms.service.DocumentService;
import com.dvms.service.FileStorageService;
import com.dvms.service.StorageException;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

public final class DocumentPanel extends JPanel {
    private final User user;
    private final java.awt.Component parent;
    private final DocumentVersionDAO versionDAO = new DocumentVersionDAO();
    private final DocumentService documentService = new DocumentService();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[] {"Document ID", "Title", "Description", "Owner ID", "Created Date", "Updated Date"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final javax.swing.table.TableRowSorter<DefaultTableModel> rowSorter = new javax.swing.table.TableRowSorter<>(tableModel);
    private final JTextField searchField = new JTextField(15);
    private final Map<Long, Document> documentsById = new HashMap<>();
    private final JLabel status = new JLabel(" ");

    public DocumentPanel(User user, java.awt.Component parent) {
        super(new BorderLayout(10, 10));
        this.user = user;
        this.parent = parent;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSorter(rowSorter);

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void updateFilter() {
                String text = searchField.getText().trim();
                if (text.isEmpty()) {
                    rowSorter.setRowFilter(null);
                } else {
                    rowSorter.setRowFilter(javax.swing.RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
                }
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateFilter();
            }
        });

        JPanel heading = new JPanel(new BorderLayout(10, 10));
        JLabel title = new JLabel("Documents");
        title.setFont(title.getFont().deriveFont(20f));
        heading.add(title, BorderLayout.WEST);

        JPanel searchAndStatus = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JLabel searchLabel = new JLabel("Search:");
        searchAndStatus.add(searchLabel);
        searchAndStatus.add(searchField);
        status.setHorizontalAlignment(SwingConstants.RIGHT);
        searchAndStatus.add(status);
        heading.add(searchAndStatus, BorderLayout.EAST);

        add(heading, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(createActions(), BorderLayout.SOUTH);
        refreshDocuments();
    }

    private JPanel createActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        addButton(actions, "Add Document", event -> showAddDialog());
        addButton(actions, "View / Export Document", event -> viewSelectedDocument());
        addButton(actions, "Update Metadata", event -> showUpdateDialog());
        addButton(actions, "Update Document", event -> showUpdateDocumentDialog());
        addButton(actions, "Delete Document", event -> deleteSelectedDocument());
        addButton(actions, "Version History", event -> showVersionHistoryForSelection());
        addButton(actions, "Refresh", event -> refreshDocuments());
        return actions;
    }

    private static void addButton(JPanel panel, String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        panel.add(button);
    }

    private void showUpdateDocumentDialog() {
        Document document = getSelectedDocument();
        if (document == null) {
            return;
        }

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                "Update Document - " + document.getTitle(),
                JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));
        JTextArea changeSummaryField = new JTextArea(3, 30);
        changeSummaryField.setLineWrap(true);
        changeSummaryField.setWrapStyleWord(true);
        JLabel pathLabel = new JLabel("No file selected");
        JButton browseButton = new JButton("Browse...");
        final Path[] selectedFile = new Path[1];
        browseButton.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                selectedFile[0] = chooser.getSelectedFile().toPath();
                pathLabel.setText(selectedFile[0].getFileName().toString());
            }
        });
        form.add(new JLabel("Change summary"));
        form.add(new JScrollPane(changeSummaryField));
        form.add(new JLabel("Selected file"));
        form.add(pathLabel);
        form.add(browseButton);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save New Version");
        JButton cancelButton = new JButton("Cancel");
        saveButton.addActionListener(event -> {
            if (selectedFile[0] == null || !Files.isRegularFile(selectedFile[0])) {
                JOptionPane.showMessageDialog(dialog, "Select an existing document file.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }

            saveButton.setEnabled(false);
            new SwingWorker<DocumentService.AddDocumentResult, Void>() {
                @Override
                protected DocumentService.AddDocumentResult doInBackground() {
                    try {
                        String summary = changeSummaryField.getText().trim();
                        if (summary.isEmpty()) {
                            summary = "New version";
                        }
                        return documentService.createNewVersion(
                                document.getId(), selectedFile[0], user, summary);
                    } catch (Exception e) {
                        return new DocumentService.AddDocumentResult(false, null,
                                "An unexpected error occurred: " + e.getMessage());
                    }
                }

                @Override
                protected void done() {
                    try {
                        DocumentService.AddDocumentResult result = get();
                        saveButton.setEnabled(true);
                        if (result.isSuccess()) {
                            dialog.dispose();
                            JOptionPane.showMessageDialog(parent,
                                     "New version of \"" + document.getTitle() + "\" saved successfully.",
                                    "Success", JOptionPane.INFORMATION_MESSAGE);
                            refreshDocuments();
                        } else {
                            JOptionPane.showMessageDialog(dialog, result.getErrorMessage(),
                                    "Failed to save new version", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (InterruptedException e) {
                        saveButton.setEnabled(true);
                        Thread.currentThread().interrupt();
                        showError("Operation was interrupted.", e);
                    } catch (ExecutionException e) {
                        saveButton.setEnabled(true);
                        showError("Could not save new version.", e.getCause());
                    }
                }
            }.execute();
        });
        cancelButton.addActionListener(event -> dialog.dispose());
        buttons.add(saveButton);
        buttons.add(cancelButton);

        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public void refreshDocuments() {
        status.setText("Loading documents...");
        new SwingWorker<List<Document>, Void>() {
            @Override
            protected List<Document> doInBackground() throws SQLException {
                return documentService.getDocumentsForUser(user);
            }

            @Override
            protected void done() {
                try {
                    List<Document> documents = get();
                    tableModel.setRowCount(0);
                    documentsById.clear();
                    for (Document document : documents) {
                        documentsById.put(document.getId(), document);
                        tableModel.addRow(new Object[] {
                                document.getId(),
                                document.getTitle(),
                                document.getDescription(),
                                document.getOwnerUserId(),
                                format(document.getCreatedAt()),
                                format(document.getUpdatedAt())
                        });
                    }
                    status.setText(documents.isEmpty() ? "No documents found." : documents.size() + " document(s)");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Document loading was interrupted.", exception);
                } catch (ExecutionException exception) {
                    status.setText("Unable to load documents.");
                    showError("Could not load documents from the database.", exception.getCause());
                }
            }
        }.execute();
    }

    private void showAddDialog() {
        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this), "Add Document", JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));
        JTextField titleField = new JTextField(30);
        JTextArea descriptionField = new JTextArea(4, 30);
        descriptionField.setLineWrap(true);
        descriptionField.setWrapStyleWord(true);
        JTextArea changeSummaryField = new JTextArea(2, 30);
        changeSummaryField.setLineWrap(true);
        changeSummaryField.setWrapStyleWord(true);
        JLabel pathLabel = new JLabel("No file selected");
        JButton browseButton = new JButton("Browse...");
        final Path[] selectedFile = new Path[1];
        browseButton.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                selectedFile[0] = chooser.getSelectedFile().toPath();
                pathLabel.setText(selectedFile[0].getFileName().toString());
            }
        });
        form.add(new JLabel("Document title"));
        form.add(titleField);
        form.add(new JLabel("Description (optional)"));
        form.add(new JScrollPane(descriptionField));
        form.add(new JLabel("Change summary (optional)"));
        form.add(new JScrollPane(changeSummaryField));
        form.add(new JLabel("Selected file"));
        form.add(pathLabel);
        form.add(browseButton);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        saveButton.addActionListener(event -> {
            if (titleField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Document title is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (selectedFile[0] == null || !Files.isRegularFile(selectedFile[0])) {
                JOptionPane.showMessageDialog(dialog, "Select an existing document file.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }

            saveButton.setEnabled(false);

            new SwingWorker<DocumentService.AddDocumentResult, Void>() {
                @Override
                protected DocumentService.AddDocumentResult doInBackground() {
                    try {
                        String title = titleField.getText().trim();
                        String description = descriptionField.getText().trim();
                        if (description.isEmpty()) {
                            description = null;
                        }
                        String summary = changeSummaryField.getText().trim();
                        if (summary.isEmpty()) {
                            summary = null;
                        }
                        return documentService.addDocument(title, description, selectedFile[0], user, summary);
                    } catch (Exception e) {
                        return new DocumentService.AddDocumentResult(false, null,
                                "An unexpected error occurred: " + e.getMessage());
                    }
                }

                @Override
                protected void done() {
                    try {
                        DocumentService.AddDocumentResult result = get();
                        saveButton.setEnabled(true);
                        if (result.isSuccess()) {
                            dialog.dispose();
                            JOptionPane.showMessageDialog(parent,
                                    "Document \"" + result.getDocument().getTitle() + "\" saved successfully.",
                                    "Success", JOptionPane.INFORMATION_MESSAGE);
                            refreshDocuments();
                        } else {
                            JOptionPane.showMessageDialog(dialog, result.getErrorMessage(),
                                    "Failed to save document", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (InterruptedException e) {
                        saveButton.setEnabled(true);
                        Thread.currentThread().interrupt();
                        showError("Operation was interrupted.", e);
                    } catch (ExecutionException e) {
                        saveButton.setEnabled(true);
                        showError("Could not save document.", e.getCause());
                    }
                }
            }.execute();
        });
        cancelButton.addActionListener(event -> dialog.dispose());
        buttons.add(saveButton);
        buttons.add(cancelButton);

        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showUpdateDialog() {
        Document document = getSelectedDocument();
        if (document == null) {
            return;
        }

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                "Update Metadata",
                JDialog.ModalityType.APPLICATION_MODAL);
        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 10, 14));
        JTextField titleField = new JTextField(document.getTitle(), 32);
        JTextArea descriptionField = new JTextArea(document.getDescription() == null ? "" : document.getDescription(), 5, 32);
        descriptionField.setLineWrap(true);
        descriptionField.setWrapStyleWord(true);
        form.add(new JLabel("Document title"));
        form.add(titleField);
        form.add(new JLabel("Description"));
        form.add(new JScrollPane(descriptionField));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        saveButton.addActionListener(event -> {
            String title = titleField.getText().trim();
            if (title.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Document title is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String description = descriptionField.getText().trim();
            saveButton.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws SQLException {
                    documentService.updateDocumentMetadata(document.getId(), title, description, user);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        dialog.dispose();
                        JOptionPane.showMessageDialog(parent, "Document metadata updated successfully.", "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                        refreshDocuments();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        saveButton.setEnabled(true);
                        showError("Metadata update was interrupted.", exception);
                    } catch (ExecutionException exception) {
                        saveButton.setEnabled(true);
                        showError("Could not update document metadata.", exception.getCause());
                    }
                }
            }.execute();
        });
        cancelButton.addActionListener(event -> dialog.dispose());
        buttons.add(saveButton);
        buttons.add(cancelButton);
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void viewSelectedDocument() {
        Document document = getSelectedDocument();
        if (document == null) {
            return;
        }
        if (document.getCurrentVersionId() == null) {
            JOptionPane.showMessageDialog(parent, "This document has no stored version to open.", "Document unavailable",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                DocumentVersion version;
                try (Connection connection = DatabaseConnection.getConnection()) {
                    version = versionDAO.findById(connection, document.getCurrentVersionId())
                            .orElseThrow(() -> new SQLException("Current document version could not be found in database."));
                }

                Path resolvedPath = FileStorageService.resolveStoragePath(version.getRelativeStoragePath());
                if (!Files.exists(resolvedPath)) {
                    throw new StorageException("Stored file is missing from storage location: " + version.getRelativeStoragePath());
                }

                if (!FileStorageService.verifyChecksum(resolvedPath, version.getSha256Checksum())) {
                    throw new StorageException("File integrity check failed: file does not match recorded SHA-256 checksum.");
                }

                return resolvedPath;
            }

            @Override
            protected void done() {
                try {
                    Path storedFile = get();
                    String[] options = {"Open with Default App", "Export Copy to...", "Cancel"};
                    int choice = JOptionPane.showOptionDialog(
                            parent,
                            "File integrity verified successfully.\nFile: " + storedFile.getFileName(),
                            "Document Version " + document.getCurrentVersionId(),
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.INFORMATION_MESSAGE,
                            null,
                            options,
                            options[0]);

                    if (choice == 0) { // Open
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                            Desktop.getDesktop().open(storedFile.toFile());
                        } else {
                            JOptionPane.showMessageDialog(parent,
                                    "Opening files is not supported by the system desktop.\nPlease use 'Export Copy to...' instead.",
                                    "Desktop Action Unsupported", JOptionPane.WARNING_MESSAGE);
                        }
                    } else if (choice == 1) { // Export
                        JFileChooser fileChooser = new JFileChooser();
                        fileChooser.setSelectedFile(new File(storedFile.getFileName().toString()));
                        if (fileChooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
                            Path destination = fileChooser.getSelectedFile().toPath();
                            Files.copy(storedFile, destination, StandardCopyOption.REPLACE_EXISTING);
                            JOptionPane.showMessageDialog(parent,
                                    "File successfully exported to:\n" + destination.toAbsolutePath(),
                                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                        }
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Document retrieval was interrupted.", exception);
                } catch (ExecutionException exception) {
                    showError("Could not access document file.", exception.getCause());
                } catch (Exception exception) {
                    showError("An error occurred while opening or exporting the file.", exception);
                }
            }
        }.execute();
    }

    public void showVersionHistoryForSelection() {
        Document document = getSelectedDocument();
        if (document == null) {
            return;
        }

        new VersionHistoryDialog(document, user, this, this::refreshDocuments).setVisible(true);
    }

    private void deleteSelectedDocument() {
        Document document = getSelectedDocument();
        if (document == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(
                parent,
                "Delete \"" + document.getTitle() + "\"?\n\n"
                        + "Note: Documents with version history cannot be deleted\n"
                        + "to ensure immutable audit trail compliance.",
                "Confirm deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws SQLException {
                documentService.deleteDocument(document.getId(), user);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(parent, "Document deleted successfully.", "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    refreshDocuments();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Document deletion was interrupted.", exception);
                } catch (ExecutionException exception) {
                    showError("Could not delete document.", exception.getCause());
                }
            }
        }.execute();
    }

    private Document getSelectedDocument() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(parent, "Select a document first.", "No document selected",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        int modelRow = table.convertRowIndexToModel(selectedRow);
        long documentId = ((Number) tableModel.getValueAt(modelRow, 0)).longValue();
        Document document = documentsById.get(documentId);
        if (document == null) {
            JOptionPane.showMessageDialog(parent, "Refresh the document list and try again.", "Document unavailable",
                    JOptionPane.WARNING_MESSAGE);
        }
        return document;
    }

    private static String format(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.toString().replace('T', ' ');
    }

    private void showError(String message, Throwable cause) {
        String detail = cause == null || cause.getMessage() == null ? message : message + "\n" + cause.getMessage();
        JOptionPane.showMessageDialog(parent, detail, "Operation failed", JOptionPane.ERROR_MESSAGE);
    }
}
