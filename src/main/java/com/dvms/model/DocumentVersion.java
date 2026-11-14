package com.dvms.model;

import java.time.LocalDateTime;

 public class DocumentVersion {
         private long id;
         private long documentId;
         private int versionNumber;
         private String relativeStoragePath;
         private String sha256Checksum;
         private long authorUserId;
         private String changeSummary;
         private LocalDateTime createdAt;
 
         public DocumentVersion() {
         }
 
         public DocumentVersion(
                         long id,
                         long documentId,
                         int versionNumber,
                         String relativeStoragePath,
                         String sha256Checksum,
                         long authorUserId,
                         String changeSummary,
                         LocalDateTime createdAt) {
                 this.id = id;
                 this.documentId = documentId;
                 this.versionNumber = versionNumber;
                 this.relativeStoragePath = relativeStoragePath;
                 this.sha256Checksum = sha256Checksum;
                 this.authorUserId = authorUserId;
                 this.changeSummary = changeSummary;
                 this.createdAt = createdAt;
         }
 
         public long getId() {
                 return id;
         }
 
         public void setId(long id) {
                 this.id = id;
         }
 
         public long getDocumentId() {
                 return documentId;
         }
 
         public void setDocumentId(long documentId) {
                 this.documentId = documentId;
         }
 
         public int getVersionNumber() {
                 return versionNumber;
         }
 
         public void setVersionNumber(int versionNumber) {
                 this.versionNumber = versionNumber;
         }
 
         public String getRelativeStoragePath() {
                 return relativeStoragePath;
         }
 
         public void setRelativeStoragePath(String relativeStoragePath) {
                 this.relativeStoragePath = relativeStoragePath;
         }
 
         public String getSha256Checksum() {
                 return sha256Checksum;
         }
 
         public void setSha256Checksum(String sha256Checksum) {
                 this.sha256Checksum = sha256Checksum;
         }
 
         public long getAuthorUserId() {
                 return authorUserId;
         }
 
         public void setAuthorUserId(long authorUserId) {
                 this.authorUserId = authorUserId;
         }
 
         public String getChangeSummary() {
                 return changeSummary;
         }
 
         public void setChangeSummary(String changeSummary) {
                 this.changeSummary = changeSummary;
         }
 
         public LocalDateTime getCreatedAt() {
                 return createdAt;
         }
 
         public void setCreatedAt(LocalDateTime createdAt) {
                 this.createdAt = createdAt;
         }
 }
