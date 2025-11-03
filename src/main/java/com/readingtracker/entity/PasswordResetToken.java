package com.readingtracker.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Password_ResetTokens")
public class PasswordResetToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(nullable = false, unique = true, length = 100)
    private String token;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(nullable = false)
    private Boolean used = false;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public PasswordResetToken() {
        this.createdAt = LocalDateTime.now();
        this.used = false;
    }
    
    public PasswordResetToken(Long userId, String token, LocalDateTime expiresAt) {
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
        this.used = false;
    }
    
    // Getters
    public Long getId() {
        return id;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public String getToken() {
        return token;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public Boolean getUsed() {
        return used;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    // Setters
    public void setId(Long id) {
        this.id = id;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public void setUsed(Boolean used) {
        this.used = used;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // Business methods
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isValid() {
        return !used && !isExpired();
    }
}



