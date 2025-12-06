package com.readingtracker.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * RefreshToken Redis 관리 서비스
 * 
 * 역할:
 * - RefreshToken을 Redis에 저장/조회/삭제
 * - TTL 자동 관리 (만료 시간에 맞춰 자동 삭제)
 * - 토큰 무효화 관리
 * 
 * Redis 키 구조:
 * - refresh_token:{token} -> RefreshTokenData
 * - refresh_tokens:user:{userId}:device:{deviceId} -> Set<token> (빠른 조회용)
 * 
 * TTL: RefreshToken 만료 시간 (7일)
 */
@Service
public class RefreshTokenRedisService {
    
    private static final String TOKEN_KEY_PREFIX = "refresh_token:";
    private static final String USER_DEVICE_TOKENS_PREFIX = "refresh_tokens:user:";
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * RefreshToken 저장
     * 
     * @param userId 사용자 ID
     * @param deviceId 디바이스 ID
     * @param token RefreshToken 문자열
     * @param expiresAt 만료 시간
     */
    public void saveRefreshToken(Long userId, String deviceId, String token, LocalDateTime expiresAt) {
        String tokenKey = TOKEN_KEY_PREFIX + token;
        String userDeviceKey = USER_DEVICE_TOKENS_PREFIX + userId + ":device:" + deviceId;
        
        // RefreshToken 데이터 생성
        RefreshTokenData data = new RefreshTokenData();
        data.setUserId(userId);
        data.setDeviceId(deviceId);
        data.setToken(token);
        data.setExpiresAt(expiresAt);
        data.setRevoked(false);
        data.setCreatedAt(LocalDateTime.now());
        
        // TTL 계산 (만료 시간까지의 초)
        long ttlSeconds = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        if (ttlSeconds > 0) {
            // 토큰 저장 (TTL 자동 관리)
            redisTemplate.opsForValue().set(tokenKey, data, ttlSeconds, TimeUnit.SECONDS);
            
            // 사용자/디바이스별 토큰 목록에 추가
            redisTemplate.opsForSet().add(userDeviceKey, token);
            redisTemplate.expire(userDeviceKey, ttlSeconds, TimeUnit.SECONDS);
        }
    }
    
    /**
     * RefreshToken 조회
     * 
     * @param token RefreshToken 문자열
     * @return RefreshTokenData (없으면 null)
     */
    public RefreshTokenData getRefreshToken(String token) {
        String tokenKey = TOKEN_KEY_PREFIX + token;
        Object data = redisTemplate.opsForValue().get(tokenKey);
        if (data instanceof RefreshTokenData) {
            return (RefreshTokenData) data;
        }
        return null;
    }
    
    /**
     * RefreshToken 무효화 (revoked=true)
     * 
     * @param token RefreshToken 문자열
     */
    public void revokeRefreshToken(String token) {
        RefreshTokenData data = getRefreshToken(token);
        if (data != null) {
            data.setRevoked(true);
            String tokenKey = TOKEN_KEY_PREFIX + token;
            
            // TTL 유지하면서 revoked 상태만 업데이트
            long ttl = redisTemplate.getExpire(tokenKey, TimeUnit.SECONDS);
            if (ttl > 0) {
                redisTemplate.opsForValue().set(tokenKey, data, ttl, TimeUnit.SECONDS);
            }
        }
    }
    
    /**
     * 사용자/디바이스의 모든 RefreshToken 무효화
     * 
     * @param userId 사용자 ID
     * @param deviceId 디바이스 ID
     */
    public void revokeAllTokensByUserAndDevice(Long userId, String deviceId) {
        String userDeviceKey = USER_DEVICE_TOKENS_PREFIX + userId + ":device:" + deviceId;
        Set<Object> tokens = redisTemplate.opsForSet().members(userDeviceKey);
        
        if (tokens != null) {
            for (Object tokenObj : tokens) {
                String token = tokenObj != null ? tokenObj.toString() : null;
                if (token != null) {
                    revokeRefreshToken(token);
                }
            }
        }
        
        // 사용자/디바이스별 토큰 목록 삭제
        redisTemplate.delete(userDeviceKey);
    }
    
    /**
     * 사용자의 모든 RefreshToken 삭제
     * 
     * @param userId 사용자 ID
     */
    public void deleteAllTokensByUser(Long userId) {
        // 패턴으로 모든 디바이스의 토큰 목록 찾기
        String pattern = USER_DEVICE_TOKENS_PREFIX + userId + ":device:*";
        Set<String> keys = redisTemplate.keys(pattern);
        
        if (keys != null) {
            for (String key : keys) {
                // 각 디바이스의 토큰 목록에서 모든 토큰 무효화
                Set<Object> tokens = redisTemplate.opsForSet().members(key);
                if (tokens != null) {
                    for (Object tokenObj : tokens) {
                        String token = tokenObj != null ? tokenObj.toString() : null;
                        if (token != null) {
                            String tokenKey = TOKEN_KEY_PREFIX + token;
                            redisTemplate.delete(tokenKey);
                        }
                    }
                }
                // 사용자/디바이스별 토큰 목록 삭제
                redisTemplate.delete(key);
            }
        }
    }
    
    /**
     * RefreshToken 데이터 내부 클래스
     */
    public static class RefreshTokenData {
        private Long userId;
        private String deviceId;
        private String token;
        private LocalDateTime expiresAt;
        private Boolean revoked;
        private LocalDateTime createdAt;
        
        public Long getUserId() {
            return userId;
        }
        
        public void setUserId(Long userId) {
            this.userId = userId;
        }
        
        public String getDeviceId() {
            return deviceId;
        }
        
        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }
        
        public String getToken() {
            return token;
        }
        
        public void setToken(String token) {
            this.token = token;
        }
        
        public LocalDateTime getExpiresAt() {
            return expiresAt;
        }
        
        public void setExpiresAt(LocalDateTime expiresAt) {
            this.expiresAt = expiresAt;
        }
        
        public Boolean getRevoked() {
            return revoked;
        }
        
        public void setRevoked(Boolean revoked) {
            this.revoked = revoked;
        }
        
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
        
        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}

