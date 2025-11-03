package com.readingtracker.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "RefreshTokens")
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "device_id", nullable = false)
    private String deviceId;
    
    @Column(nullable = false)
    private String token;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(nullable = false)
    private Boolean revoked = false;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public RefreshToken() {
        this.revoked = false;
    }
    
    public RefreshToken(User user, String deviceId, String token, LocalDateTime expiresAt) {
        this.user = user;
        this.deviceId = deviceId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }
    
    // Getter methods
    public Long getId() {
        return id;
    }
    
    public User getUser() {
        return user;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public String getToken() {
        return token;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public Boolean getRevoked() {
        return revoked;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    // Setter methods
    public void setId(Long id) {
        this.id = id;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public void setRevoked(Boolean revoked) {
        this.revoked = revoked;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
