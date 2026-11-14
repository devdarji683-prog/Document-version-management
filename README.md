# Document Version Management System (DVMS)

## Overview

Document Version Management System (DVMS) is a Java Swing desktop application designed for secure document management, immutable version tracking, and cryptographic file integrity verification backed by a MySQL database.

---

## Architecture

DVMS follows a layered software architecture:
- `com.dvms.config`: Database configuration loading, validation, and JDBC connection lifecycle management.
- `com.dvms.dao`: Database Access Objects with prepared statements and row-level locking (`DocumentDAO`, `DocumentVersionDAO`, `UserDAO`).
- `com.dvms.model`: Domain models (`Document`, `DocumentVersion`, `User`).
- `com.dvms.service`: Business logic, authentication, cryptographic hashing, file storage, and document version lifecycle.
- `com.dvms.ui`: Swing desktop user interface with background worker threads (`SwingWorker`) for all I/O and database operations (`LoginFrame`, `DashboardFrame`, `DocumentPanel`, `VersionHistoryDialog`).
- `com.dvms.util`: Administrative tools (`CreateUserTool`, `DatabaseConnectionTest`).
- `src/main/resources/db/migration`: Versioned SQL schema migrations (V1 to V4).

---

## Key Features & Security Guarantees

### 1. Authentication & Security
- **BCrypt Password Hashing:** Passwords hashed with BCrypt (work factor 12) with cryptographically secure random salts.
- **Timing Attack Mitigation:** Authentication runs a valid work factor 12 verification even when a username does not exist or has an invalid hash, preventing username enumeration through timing analysis.
- **Generic Failure Messages:** Prevents leaking whether an account exists or which credential was invalid.
- **Account Status Enforcement:** Only accounts with status `ACTIVE` can sign in; `DISABLED` and `LOCKED` accounts are rejected.
- **Memory Hygiene:** Mutable `char[]` password arrays are securely wiped with zeroes immediately after verification.
- **Service-Layer Authorization:** Ownership and administrator permissions (`ADMIN` vs `USER`) are enforced at the service level, protecting against unauthorized access.
- **SQL Injection Prevention:** 100% prepared statements across all DAO queries.

### 2. File Storage & Integrity Verification
- **UUID-based File Isolation:** Stored files are named using random UUIDs while preserving their valid file extension.
- **Path Traversal Defense:** All relative storage paths are strictly validated to prevent directory traversal (`..`, absolute paths, leading slashes, drive letters, and backslashes).
- **Cryptographic Integrity:** Every stored version calculates and stores a SHA-256 checksum (256-bit / 32-byte binary in database, 64-character hex in application). Checksums are verified using constant-time comparison before viewing or restoration.
- **Size & Extension Limits:** Enforces a 1 GB maximum file size and validates file extensions against an approved document extension list.

### 3. Database & Transaction Concurrency
- **Row-Level Concurrency Control:** Version number generation is synchronized using `SELECT ... FOR UPDATE` row locks, preventing race conditions or duplicate version numbers during concurrent uploads.
- **Atomic Operations & Cleanup:** Document and version creation are executed in atomic database transactions. If database operations fail, orphaned files on disk are automatically deleted.
- **Immutable Version History:** Database triggers (`trg_document_versions_before_update` and `trg_document_versions_before_delete`) prevent modification or deletion of version records.
- **Composite Foreign Key:** `(documents.current_version_id, documents.id)` references `(document_versions.id, document_versions.document_id)`, guaranteeing that a document's current version pointer can only reference a version belonging to that exact document.
- **Deletion Policy:** Documents with version history cannot be deleted, preserving audit compliance and historical records.

---

## Technology Stack & Requirements

- **Java Development Kit (JDK):** JDK 25 or later (compatible with Java 25+ / 27)
- **Build Tool:** Apache Maven 3.8+
- **Database:** MySQL 8.0.16 or later (InnoDB engine, utf8mb4 charset)
- **GUI:** Standard Java Swing and AWT (`javax.swing`, `java.awt`)

---

## Database Configuration

The application determines its database connection parameters through centralized configuration in `DatabaseConfig.java`. It supports two flexible methods:

### Option 1: `db.properties` File (Recommended for Local Dev)
Copy `db.properties.example` to `db.properties` in the project root:
```properties
db.url=jdbc:mysql://localhost:3306/document_version_management
db.username=root
db.password=12345
```
*Note: `db.properties` is git-ignored and must never be committed to source control.*

### Option 2: Environment Variables / JVM System Properties
If `db.properties` is absent or omitted, the application automatically constructs the connection from environment variables:
- `DB_HOST`: Hostname of the MySQL server (default: `localhost`)
- `DB_PORT`: MySQL port (default: `3306`)
- `DB_NAME`: Database name (`document_version_management`)
- `DB_USER`: Database username (e.g. `root` or `dvms_app`)
- `DB_PASSWORD`: Database password

Alternatively, pass JVM system properties directly:
```bash
mvn exec:java -Dexec.mainClass="com.dvms.Main" -Ddb.name=document_version_management -Ddb.user=root -Ddb.password=12345
```

---

## Setup & Getting Started

### 1. Database Creation
Connect to MySQL as an administrator and execute:

```sql
CREATE DATABASE IF NOT EXISTS document_version_management
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

### 2. Schema Migrations
Execute the migrations located in `src/main/resources/db/migration/` in sequential order:
1. `V1__create_users.sql`: Creates `users` table with roles (`USER`, `ADMIN`) and statuses (`ACTIVE`, `DISABLED`, `LOCKED`).
2. `V2__create_documents.sql`: Creates `documents` table with owner foreign key.
3. `V3__create_document_versions.sql`: Creates `document_versions` table, adds composite FK `fk_documents_current_version`, and triggers enforcing version immutability.
4. `V4__create_activity_logs.sql`: Creates `activity_logs` table.

Alternatively, execute `schema.sql` which applies the entire baseline schema in one step.

### 3. Production Least-Privilege MySQL User Setup
For production deployments, create a dedicated application user with least privileges:

```sql
CREATE USER 'dvms_app'@'localhost' IDENTIFIED BY 'StrongPassword123!';
GRANT SELECT, INSERT, UPDATE, DELETE ON document_version_management.* TO 'dvms_app'@'localhost';
FLUSH PRIVILEGES;
```

### 4. Verify Database Connection
Run the connection verification utility (`SELECT 1` and metadata validation):

```bash
mvn exec:java -Dexec.mainClass="com.dvms.util.DatabaseConnectionTest"
```

Expected output:
```
Testing database connection...
Connected database: document_version_management
Database connection test succeeded.
```

### 5. Create the Initial Administrator Account
Use the interactive administrative CLI tool:

```bash
mvn exec:java -Dexec.mainClass="com.dvms.util.CreateUserTool"
```
Follow the interactive prompts to enter username, email, password, and select role `ADMIN`.

---

## Running the Application

### Via Maven
Launch the desktop Swing GUI:

```bash
mvn compile exec:java
```

### Via IntelliJ IDEA
1. Open IntelliJ IDEA and select **Open** -> Navigate to the project root directory.
2. Ensure Maven syncs the `pom.xml`.
3. In **File -> Project Structure -> Project**, ensure the SDK is set to Java 25 or later.
4. Create a new **Application** Run Configuration:
   - **Main class:** `com.dvms.Main`
   - **Working directory:** `$MODULE_WORKING_DIR$` (or the absolute path to the project root directory where `db.properties` resides).
   - **Environment variables (optional):** `DB_HOST=localhost;DB_PORT=3306;DB_NAME=document_version_management;DB_USER=root;DB_PASSWORD=12345` (if not using `db.properties`).
5. Click **Run** or press `Shift + F10`.

---

## Running Automated Tests

Run the complete test suite:

```bash
mvn clean test
```

Package the production executable JAR:

```bash
mvn package
```

The compiled JAR is generated at `target/DocumentVersionManagementSystem-1.0.0.jar`.

### Test Suite Coverage
The automated test suite contains 53 tests covering:
- **`AuthServiceTest`**: Authentication success/failure, inactive accounts, timing attack dummy hash execution, invalid hash handling.
- **`PasswordServiceTest`**: BCrypt work factor 12 hashing, salt randomness, verification, BCrypt format validation regex.
- **`AuthResultTest`**: Success and failure result wrappers.
- **`SessionManagerTest`**: Thread-safe session management, login, logout, validation.
- **`FileStorageServiceTest`**: File extension retention, SHA-256 calculation, constant-time verification, max size limits, path traversal prevention, file corruption detection, atomic deletion, cleanup.
- **`DocumentServiceTest`**: Document creation, versioning, row-level locking concurrency, version restoration, immutable audit preservation, ownership and admin authorization, transaction rollback with storage cleanup.
- **`DatabaseConfigTest`**: Property loading, missing key validation, configuration path overrides, discrete environment/system properties.

---

## Troubleshooting Guide

### 1. Connection Refused / Communications Exception
- **Symptom:** `CommunicationsException: Communications link failure` or `Connection refused`.
- **Cause:** MySQL server is not running, or listening on a different port.
- **Solution:**
  - On Windows: Open Services (`services.msc`) and ensure `MySQL80` (or your MySQL service) is **Running**. Or run in PowerShell: `Get-Service *mysql* | Start-Service`.
  - Verify that MySQL is listening on port 3306: `netstat -ano | findstr :3306`.

### 2. Access Denied (`ERROR 1045`)
- **Symptom:** `java.sql.SQLException: Access denied for user 'root'@'localhost' (using password: YES)`.
- **Cause:** Incorrect username or password in `db.properties` or environment variables.
- **Solution:** Verify your credentials in `db.properties`. Verify with MySQL CLI: `mysql -u root -p12345`.

### 3. Unknown Database
- **Symptom:** `java.sql.SQLSyntaxErrorException: Unknown database 'document_version_management'`.
- **Cause:** Database has not been created yet.
- **Solution:** Run `CREATE DATABASE document_version_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` and apply `schema.sql`.

### 4. Configuration File Not Found
- **Symptom:** `IOException: Database configuration file not found: db.properties`.
- **Cause:** Working directory is set to a subdirectory or IDE launch configuration did not set the working directory to the project root.
- **Solution:** Copy `db.properties.example` to `db.properties` in the project root, or set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` environment variables.

### 5. Missing JDBC Driver
- **Symptom:** `java.sql.SQLException: No suitable driver found for jdbc:mysql:...`.
- **Cause:** MySQL Connector/J is missing from the classpath.
- **Solution:** Ensure Maven has downloaded dependencies (`mvn dependency:resolve`). If running outside Maven, pass `-cp "target/classes;target/dependency/*"`.

---

## Backup & Disaster Recovery Guidance

1. **Database Backup:**
   ```bash
   mysqldump -u [username] -p --routines --triggers document_version_management > dvms_backup_$(date +%F).sql
   ```
2. **File Storage Backup:**
   Back up the configured `document-storage/` directory alongside the database dump.
3. **Database Restore:**
   ```bash
   mysql -u [username] -p document_version_management < dvms_backup_YYYY-MM-DD.sql
   ```
   Ensure the restored database dump and `document-storage/` directory correspond to the same point in time to maintain referential and checksum consistency.
