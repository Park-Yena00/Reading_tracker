package com.readingtracker.dto.ServerDbmsDTO.ResultDTO;

/**
 * 계정 확인 결과 데이터 전송 객체 (Service Layer)
 */
public class AccountVerificationResult {
    
    private String message;
    private String resetToken;
    
    // Constructors
    public AccountVerificationResult() {
    }
    
    public AccountVerificationResult(String message, String resetToken) {
        this.message = message;
        this.resetToken = resetToken;
    }
    
    // Getter methods
    public String getMessage() {
        return message;
    }
    
    public String getResetToken() {
        return resetToken;
    }
    
    // Setter methods
    public void setMessage(String message) {
        this.message = message;
    }
    
    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }
}
