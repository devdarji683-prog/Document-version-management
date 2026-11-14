package com.dvms.model;

import java.time.LocalDateTime;

 public class User {
     private long id;
     private String username;
     private String email;
     private String passwordHash;
     private String role;
     private String accountStatus;
     private LocalDateTime createdAt;
     private LocalDateTime updatedAt;
 
     public User() {
     }
 
     public User(
             long id,
             String username,
             String email,
             String passwordHash,
             String role,
             String accountStatus,
             LocalDateTime createdAt,
             LocalDateTime updatedAt) {
         this.id = id;
         this.username = username;
         this.email = email;
         this.passwordHash = passwordHash;
         this.role = role;
         this.accountStatus = accountStatus;
         this.createdAt = createdAt;
         this.updatedAt = updatedAt;
     }
 
     public long getId() {
         return id;
     }
 
     public void setId(long id) {
         this.id = id;
     }
 
     public String getUsername() {
         return username;
     }
 
     public void setUsername(String username) {
         this.username = username;
     }
 
     public String getEmail() {
         return email;
     }
 
     public void setEmail(String email) {
         this.email = email;
     }
 
     public String getPasswordHash() {
         return passwordHash;
     }
 
     public void setPasswordHash(String passwordHash) {
         this.passwordHash = passwordHash;
     }
 
     public String getRole() {
         return role;
     }
 
     public void setRole(String role) {
         this.role = role;
     }
 
     public String getAccountStatus() {
         return accountStatus;
     }
 
     public void setAccountStatus(String accountStatus) {
         this.accountStatus = accountStatus;
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
 
     @Override
     public String toString() {
         return "User[id=" + id
                 + ", username=" + username
                 + ", email=" + email
                 + ", role=" + role
                 + ", accountStatus=" + accountStatus
                 + "]";
     }
 }
