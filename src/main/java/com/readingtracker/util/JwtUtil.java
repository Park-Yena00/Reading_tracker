package com.readingtracker.util;

import com.readingtracker.config.JwtConfig;
import com.readingtracker.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtUtil {
    
    private final JwtConfig jwtConfig;
    
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("loginId", user.getLoginId());
        claims.put("role", user.getRole().name());
        
        return createToken(claims, user.getLoginId(), jwtConfig.getAccessTokenExpiration());
    }
    
    public String generateRefreshToken(User user, String deviceId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("deviceId", deviceId);
        claims.put("type", "refresh");
        
        return createToken(claims, user.getLoginId(), jwtConfig.getRefreshTokenExpiration());
    }
    
    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }
    
    public String extractLoginId(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }
    
    public String extractDeviceId(String token) {
        return extractClaim(token, claims -> claims.get("deviceId", String.class));
    }
    
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    public boolean isRefreshToken(String token) {
        String type = extractClaim(token, claims -> claims.get("type", String.class));
        return "refresh".equals(type);
    }
    
    public boolean validateToken(String token, String loginId) {
        try {
            String extractedLoginId = extractLoginId(token);
            return extractedLoginId.equals(loginId) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
    
    public <T> T extractClaim(String token, java.util.function.Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

