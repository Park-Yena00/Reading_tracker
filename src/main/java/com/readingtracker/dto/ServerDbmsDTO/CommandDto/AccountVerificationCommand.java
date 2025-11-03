package com.readingtracker.dto.ServerDbmsDTO.CommandDto;

/**
 * 계정 확인 명령 데이터 전송 객체 (Service Layer)
 */
public class AccountVerificationCommand {
    
    private String loginId;
    private String email;
    
    // Constructors
    public AccountVerificationCommand() {
    }
    
    public AccountVerificationCommand(String loginId, String email) {
        this.loginId = loginId;
        this.email = email;
    }
    
    // Getter methods
    public String getLoginId() {
        return loginId;
    }
    
    public String getEmail() {
        return email;
    }
    
    // Setter methods
    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
}
