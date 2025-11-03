package com.readingtracker.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtConfig {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;
    
    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;
    
    // Getter methods
    public String getSecret() {
        return secret;
    }
    
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }
    
    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}

