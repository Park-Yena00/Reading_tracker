package com.readingtracker.server.dto.requestDTO;

/**
 * Refresh Token 갱신 요청 DTO
 */
public class RefreshTokenRequest {
    
    private String refreshToken;
    
    // Constructors
    public RefreshTokenRequest() {
    }
    
    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    // Getter
    public String getRefreshToken() {
        return refreshToken;
    }
    
    // Setter
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}

