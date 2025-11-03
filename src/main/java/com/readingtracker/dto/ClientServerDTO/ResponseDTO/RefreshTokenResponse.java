package com.readingtracker.dto.ClientServerDTO.ResponseDTO;

public class RefreshTokenResponse {
    
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    
    // Constructors
    public RefreshTokenResponse() {
    }
    
    public RefreshTokenResponse(String accessToken, String refreshToken, String tokenType, Long expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }
    
    // Getter methods
    public String getAccessToken() {
        return accessToken;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public String getTokenType() {
        return tokenType;
    }
    
    public Long getExpiresIn() {
        return expiresIn;
    }
    
    // Setter methods
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
    
    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }
}

