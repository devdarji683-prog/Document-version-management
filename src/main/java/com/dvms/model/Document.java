package com.dvms.model;

import java.time.LocalDateTime;

 public class Document {
         private long id;
         private String title;
         private String description;
         private long ownerUserId;
         private Long currentVersionId;
         private LocalDateTime createdAt;
         private LocalDateTime updatedAt;
 
         public Document() {
         }
 
         public Document(
                         long id,
                         String title,
                         String description,
                         long ownerUserId,
                         Long currentVersionId,
                         LocalDateTime createdAt,
                         LocalDateTime updatedAt) {
                 this.id = id;
                 this.title = title;
                 this.description = description;
                 this.ownerUserId = ownerUserId;
                 this.currentVersionId = currentVersionId;
                 this.createdAt = createdAt;
                 this.updatedAt = updatedAt;
         }
 
         public long getId() {
                 return id;
         }
 
         public void setId(long id) {
                 this.id = id;
         }
 
         public String getTitle() {
                 return title;
         }
 
         public void setTitle(String title) {
                 this.title = title;
         }
 
         public String getDescription() {
                 return description;
         }
 
         public void setDescription(String description) {
                 this.description = description;
         }
 
         public long getOwnerUserId() {
                 return ownerUserId;
         }
 
         public void setOwnerUserId(long ownerUserId) {
                 this.ownerUserId = ownerUserId;
         }
 
         public Long getCurrentVersionId() {
                 return currentVersionId;
         }
 
         public void setCurrentVersionId(Long currentVersionId) {
                 this.currentVersionId = currentVersionId;
         }
 
         public LocalDateTime getCreatedAt() {
                 return createdAt;
         }
 
         public void setCreatedAt(LocalDateTime createdAt) {
                 this.createdAt = createdAt;
         }
 
         public LocalDateTime getUpdatedAt() {
                 return updatedAt;
         }
 
         public void setUpdatedAt(LocalDateTime updatedAt) {
                 this.updatedAt = updatedAt;
         }
 }
