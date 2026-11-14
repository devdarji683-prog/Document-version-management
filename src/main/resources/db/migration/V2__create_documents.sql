USE document_version_management;

CREATE TABLE documents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    owner_user_id BIGINT UNSIGNED NOT NULL,
    current_version_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_documents_owner_updated (owner_user_id, updated_at),
    KEY idx_documents_current_version (current_version_id, id),
    CONSTRAINT fk_documents_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- The current-version foreign key is added in V3 after document_versions exists.