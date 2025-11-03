package com.readingtracker.dto.ServerDbmsDTO.ResultDTO;

import com.readingtracker.entity.User;

import java.time.LocalDateTime;

/**
 * 사용자 조회 결과 데이터 전송 객체 (Service Layer)
 */
public class UserResult {
    
    private Long id;
    private String loginId;
    private String email;
    private String name;
    private User.Role role;
    private User.Status status;
    private LocalDateTime createdAt;
    
    // Constructors
    public UserResult() {
    }
    
    public UserResult(Long id, String loginId, String email, String name, User.Role role, User.Status status, LocalDateTime createdAt) {
        this.id = id;
        this.loginId = loginId;
        this.email = email;
        this.name = name;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
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
    
    public User.Role getRole() {
        return role;
    }
    
    public User.Status getStatus() {
        return status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
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
    
    public void setRole(User.Role role) {
        this.role = role;
    }
    
    public void setStatus(User.Status status) {
        this.status = status;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
