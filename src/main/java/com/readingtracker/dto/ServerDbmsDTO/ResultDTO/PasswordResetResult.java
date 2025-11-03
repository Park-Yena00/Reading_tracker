package com.readingtracker.dto.ServerDbmsDTO.ResultDTO;

/**
 * 비밀번호 재설정 결과 데이터 전송 객체 (Service Layer)
 */
public class PasswordResetResult {
    
    private String message;
    private String loginId;
    
    // Constructors
    public PasswordResetResult() {
    }
    
    public PasswordResetResult(String message, String loginId) {
        this.message = message;
        this.loginId = loginId;
    }
    
    // Getter methods
    public String getMessage() {
        return message;
    }
    
    public String getLoginId() {
        return loginId;
    }
    
    // Setter methods
    public void setMessage(String message) {
        this.message = message;
    }
    
    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }
}
