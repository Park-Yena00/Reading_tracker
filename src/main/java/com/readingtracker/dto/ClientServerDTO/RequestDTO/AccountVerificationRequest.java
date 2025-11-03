package com.readingtracker.dto.ClientServerDTO.RequestDTO;

/**
 * 계정 확인 요청 DTO (비밀번호 재설정 Step 1)
 */
public class AccountVerificationRequest {
    
    private String loginId;
    private String email;
    
    // Constructors
    public AccountVerificationRequest() {
    }
    
    public AccountVerificationRequest(String loginId, String email) {
        this.loginId = loginId;
        this.email = email;
    }
    
    // Getters
    public String getLoginId() {
        return loginId;
    }
    
    public String getEmail() {
        return email;
    }
    
    // Setters
    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
}



