package com.readingtracker.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Users")
@EntityListeners(AuditingEntityListener.class)
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "login_id", unique = true, nullable = false, length = 50)
    private String loginId;
    
    @Column(unique = true, nullable = false, length = 100)
    private String email;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;
    
    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount = 0;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserDevice> devices = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RefreshToken> refreshTokens = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserShelfBook> userBooks = new ArrayList<>();
    
    // Constructors
    public User() {
    }
    
    public User(String loginId, String email, String name, String passwordHash) {
        this.loginId = loginId;
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.role = Role.USER;
        this.status = Status.ACTIVE;
        this.failedLoginCount = 0;
    }
    
    // Getter methods
    public Long getId() {
        return id;
    }
    
    public String getLoginId() {
        return loginId;
    }
    
    public String getEmail() {
        return email;
    }
    
    public String getName() {
        return name;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public Role getRole() {
        return role;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public Integer getFailedLoginCount() {
        return failedLoginCount;
    }
    
    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public List<UserDevice> getDevices() {
        return devices;
    }
    
    public List<RefreshToken> getRefreshTokens() {
        return refreshTokens;
    }
    
    public List<UserShelfBook> getUserBooks() {
        return userBooks;
    }
    
    // Setter methods
    public void setId(Long id) {
        this.id = id;
    }
    
    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    
    public void setRole(Role role) {
        this.role = role;
    }
    
    public void setStatus(Status status) {
        this.status = status;
    }
    
    public void setFailedLoginCount(Integer failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }
    
    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public void setDevices(List<UserDevice> devices) {
        this.devices = devices;
    }
    
    public void setRefreshTokens(List<RefreshToken> refreshTokens) {
        this.refreshTokens = refreshTokens;
    }
    
    public void setUserBooks(List<UserShelfBook> userBooks) {
        this.userBooks = userBooks;
    }
    
    public enum Role {
        USER, ADMIN
    }
    
    public enum Status {
        ACTIVE, LOCKED, DELETED
    }
}

