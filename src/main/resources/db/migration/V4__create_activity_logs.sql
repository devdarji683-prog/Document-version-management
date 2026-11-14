USE document_version_management;

CREATE TABLE activity_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    document_id BIGINT UNSIGNED NULL,
    action_type VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    details JSON NULL,
    PRIMARY KEY (id),
    KEY idx_activity_logs_user_created (user_id, created_at),
    KEY idx_activity_logs_document_created (document_id, created_at),
    KEY idx_activity_logs_action_created (action_type, created_at),
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