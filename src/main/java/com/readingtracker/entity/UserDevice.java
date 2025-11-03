package com.readingtracker.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "User_Devices", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "device_id"}))
@EntityListeners(AuditingEntityListener.class)
public class UserDevice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "device_id", nullable = false)
    private String deviceId;
    
    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;
    
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors
    public UserDevice() {
    }
    
    public UserDevice(User user, String deviceId, String deviceName, Platform platform) {
        this.user = user;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.platform = platform;
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
    
    public String getDeviceName() {
        return deviceName;
    }
    
    public Platform getPlatform() {
        return platform;
    }
    
    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
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
    
    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }
    
    public void setPlatform(Platform platform) {
        this.platform = platform;
    }
    
    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public enum Platform {
        WEB, ANDROID, IOS
    }
}

