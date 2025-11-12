package com.readingtracker.server.dto.requestDTO;

public class LoginRequest {
    
    private String loginId;
    private String password;
    
    // Constructors
    public LoginRequest() {
    }
    
    public LoginRequest(String loginId, String password) {
        this.loginId = loginId;
        this.password = password;
    }
    
    
    // Getter methods
    public String getLoginId() {
        return loginId;
    }
    
    public String getPassword() {
        return password;
    }
    
    // Setter methods
    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}

