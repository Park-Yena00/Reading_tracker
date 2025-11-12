package com.readingtracker.server.dto.responseDTO;

/**
 * 비밀번호 재설정 응답 DTO
 */
public class PasswordResetResponse {
    
    private String message;
    private String loginId;
    
    // Constructors
    public PasswordResetResponse() {
    }
    
    public PasswordResetResponse(String message, String loginId) {
        this.message = message;
        this.loginId = loginId;
    }
    
    // Getters
    public String getMessage() {
        return message;
    }
    
    public String getLoginId() {
        return loginId;
    }
    
    // Setters
    public void setMessage(String message) {
        this.message = message;
    }
    
    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }
}



