package com.readingtracker.dto.ClientServerDTO.ResponseDTO;

/**
 * 계정 확인 응답 DTO (비밀번호 재설정 Step 1)
 */
public class AccountVerificationResponse {
    
    private String message;
    private String resetToken;  // 비밀번호 재설정용 임시 토큰
    
    // Constructors
    public AccountVerificationResponse() {
    }
    
    public AccountVerificationResponse(String message, String resetToken) {
        this.message = message;
        this.resetToken = resetToken;
    }
    
    // Getters
    public String getMessage() {
        return message;
    }
    
    public String getResetToken() {
        return resetToken;
    }
    
    // Setters
    public void setMessage(String message) {
        this.message = message;
    }
    
    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }
}

