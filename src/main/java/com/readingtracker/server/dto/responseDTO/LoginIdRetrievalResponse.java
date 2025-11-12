package com.readingtracker.server.dto.responseDTO;

public class LoginIdRetrievalResponse {
    
    private String loginId;
    private String email;
    
    // Constructors
    public LoginIdRetrievalResponse() {
    }
    
    public LoginIdRetrievalResponse(String loginId, String email) {
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



