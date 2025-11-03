package com.readingtracker.dto.ServerDbmsDTO.ResultDTO;

/**
 * 아이디 찾기 결과 데이터 전송 객체 (Service Layer)
 */
public class LoginIdRetrievalResult {
    
    private String loginId;
    private String email;
    
    // Constructors
    public LoginIdRetrievalResult() {
    }
    
    public LoginIdRetrievalResult(String loginId, String email) {
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
