-- ============================================================================
-- Document Version Management System — Complete Database Schema
-- ============================================================================
-- This script creates the database, all tables, indexes, constraints, and
-- triggers required by the DVMS Java Swing application.
--
-- Prerequisites : MySQL 8.0.16 or later (enforced CHECK constraints, JSON).
-- Usage         : Open this file in MySQL Workbench or any MySQL client and
--                 execute the entire script against a MySQL server. It is safe
--                 to run on a fresh server; it will NOT drop an existing
--                 database or any of its tables.
--
-- After running this script, configure the database:
--
--   1. Copy db.properties.example to db.properties in the project root
--   2. Replace the placeholder credentials
--   3. Run the database connection test: mvn exec:java -Dexec.mainClass=com.dvms.util.DatabaseConnectionTest
--
-- File Storage (Phase 7):
--   - Default location: document-storage/ directory under project root
--   - Override with DVMS_STORAGE_ROOT env var or -Ddvms.storage.root system property
--   - Files are stored with UUID-based names; paths in database are relative
--   - SHA-256 checksums verify file integrity
--   - Relative paths must NOT contain: absolute paths, backslashes, or .. traversal
--
-- Schema is designed for:
--   - Immutable version records (triggers reject updates/deletes)
--   - Foreign key constraints for data integrity
--   - Efficient indexing for common queries
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Create the database
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS document_version_management
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE document_version_management;

-- ---------------------------------------------------------------------------
-- 2. Users table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    username        VARCHAR(64)      NOT NULL,
    email           VARCHAR(254)     NOT NULL,
    password_hash   VARCHAR(255)     NOT NULL,
    role            VARCHAR(32)      NOT NULL DEFAULT 'USER',
    account_status  VARCHAR(20)      NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE  KEY uq_users_username       (username),
    UNIQUE  KEY uq_users_email          (email),

    CONSTRAINT chk_users_role
        CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_account_status
        CHECK (account_status IN ('ACTIVE', 'DISABLED', 'LOCKED'))
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 3. Documents table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS documents (
    id                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    title               VARCHAR(255)     NOT NULL,
    description         TEXT             NULL,
    owner_user_id       BIGINT UNSIGNED  NOT NULL,
    current_version_id  BIGINT UNSIGNED  NULL,
    created_at          DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                            ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    KEY idx_documents_owner_updated       (owner_user_id, updated_at),
    KEY idx_documents_current_version     (current_version_id, id),

    CONSTRAINT fk_documents_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- NOTE: The current-version foreign key is added after document_versions
--       exists (see section 4 below).

-- ---------------------------------------------------------------------------
-- 4. Document versions table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS document_versions (
    id                      BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    document_id             BIGINT UNSIGNED   NOT NULL,
    version_number          INT UNSIGNED      NOT NULL,
    relative_storage_path   VARCHAR(1024)     NOT NULL,
    sha256_checksum         BINARY(32)        NOT NULL,
    author_user_id          BIGINT UNSIGNED   NOT NULL,
    change_summary          TEXT              NULL,
    created_at              DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE  KEY uq_document_versions_document_number  (document_id, version_number),
    UNIQUE  KEY uq_document_versions_id_document      (id, document_id),
    KEY idx_document_versions_author_created           (author_user_id, created_at),

    CONSTRAINT chk_document_versions_positive_number
        CHECK (version_number > 0),

    CONSTRAINT chk_document_versions_relative_path
        CHECK (
            relative_storage_path <> ''
            AND LEFT(relative_storage_path, 1) <> '/'
            AND LOCATE('\\', relative_storage_path) = 0
            AND relative_storage_path NOT REGEXP '^[A-Za-z]:\\\\'
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

-- Composite FK: a document's current_version_id can only point to one of its
-- own versions (matched on both id and document_id).
ALTER TABLE documents
    ADD CONSTRAINT fk_documents_current_version
    FOREIGN KEY (current_version_id, id)
    REFERENCES document_versions (id, document_id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;

-- Immutability triggers: version records cannot be updated or deleted.
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

-- ---------------------------------------------------------------------------
-- 5. Activity logs table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS activity_logs (
    id            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED  NULL,
    document_id   BIGINT UNSIGNED  NULL,
    action_type   VARCHAR(64)      NOT NULL,
    created_at    DATETIME(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    details       JSON             NULL,

    PRIMARY KEY (id),
    KEY idx_activity_logs_user_created       (user_id, created_at),
    KEY idx_activity_logs_document_created   (document_id, created_at),
    KEY idx_activity_logs_action_created     (action_type, created_at),

    CONSTRAINT fk_activity_logs_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE SET NULL
        ON UPDATE RESTRICT,

    CONSTRAINT fk_activity_logs_document
        FOREIGN KEY (document_id) REFERENCES documents (id)
        ON DELETE SET NULL
        ON UPDATE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 6. Verification queries — uncomment and run to confirm the schema
-- ---------------------------------------------------------------------------
SHOW TABLES;

SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM   information_schema.TABLES
WHERE  TABLE_SCHEMA = 'document_version_management'
ORDER  BY TABLE_NAME;

SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
FROM   information_schema.TABLE_CONSTRAINTS
WHERE  TABLE_SCHEMA = 'document_version_management'
ORDER  BY TABLE_NAME, CONSTRAINT_TYPE, CONSTRAINT_NAME;

SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM   information_schema.STATISTICS
WHERE  TABLE_SCHEMA = 'document_version_management'
ORDER  BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

SHOW CREATE TABLE users;
SHOW CREATE TABLE documents;
SHOW CREATE TABLE document_versions;
SHOW CREATE TABLE activity_logs;
SHOW TRIGGERS LIKE 'document_versions';
