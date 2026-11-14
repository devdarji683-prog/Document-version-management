USE document_version_management;

CREATE TABLE document_versions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    document_id BIGINT UNSIGNED NOT NULL,
    version_number INT UNSIGNED NOT NULL,
    relative_storage_path VARCHAR(1024) NOT NULL,
    sha256_checksum BINARY(32) NOT NULL,
    author_user_id BIGINT UNSIGNED NOT NULL,
    change_summary TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_document_versions_document_number (document_id, version_number),
    UNIQUE KEY uq_document_versions_id_document (id, document_id),
    KEY idx_document_versions_author_created (author_user_id, created_at),
    CONSTRAINT chk_document_versions_positive_number CHECK (version_number > 0),
    CONSTRAINT chk_document_versions_relative_path CHECK (
        relative_storage_path <> ''
        AND LEFT(relative_storage_path, 1) <> '/'
        AND LEFT(relative_storage_path, 1) <> CHAR(92)
        AND LOCATE(CHAR(92), relative_storage_path) = 0
        AND relative_storage_path NOT REGEXP '^[A-Za-z]:'
        AND relative_storage_path NOT REGEXP '(^|/)[.][.](/|$)'
    ),
    CONSTRAINT fk_document_versions_document
        FOREIGN KEY (document_id) REFERENCES documents (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT fk_document_versions_author
        FOREIGN KEY (author_user_id) REFERENCES users (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_current_version
    FOREIGN KEY (current_version_id, id)
    REFERENCES document_versions (id, document_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

DELIMITER $$

CREATE TRIGGER trg_document_versions_before_update
BEFORE UPDATE ON document_versions
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Document versions are immutable; insert a new version instead.';
END$$

CREATE TRIGGER trg_document_versions_before_delete
BEFORE DELETE ON document_versions
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Document versions are immutable and cannot be deleted.';
END$$

DELIMITER ;