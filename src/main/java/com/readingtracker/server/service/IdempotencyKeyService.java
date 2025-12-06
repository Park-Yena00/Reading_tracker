package com.readingtracker.server.service;

import com.readingtracker.server.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 멱등성 키 관리 서비스
 * 
 * 역할:
 * - 멱등성 키의 상태 관리 (PROCESSING, COMPLETED)
 * - 캐시된 응답 조회 및 저장
 * - TTL 관리
 * 
 * Redis 저장 구조:
 * {
 *   "idempotency-key": "uuid-from-client",
 *   "status": "PROCESSING" | "COMPLETED",
 *   "response": ApiResponse<MemoResponse> (COMPLETED일 때만),
 *   "createdAt": timestamp,
 *   "ttl": expiration-time
 * }
 * 
 * TTL: 24시간 (멱등성 키는 짧은 수명)
 */
@Service
public class IdempotencyKeyService {
    
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final long TTL_HOURS = 24; // 24시간
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 캐시된 응답 조회
     * 
     * @param idempotencyKey 멱등성 키
     * @return 캐시된 응답 (COMPLETED 상태일 때만), 없으면 null
     * @throws IllegalStateException PROCESSING 상태인 경우 (동시 요청 처리 중)
     */
    @SuppressWarnings("unchecked")
    public <T> ApiResponse<T> getCachedResponse(String idempotencyKey) {
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        IdempotencyKeyData data = (IdempotencyKeyData) redisTemplate.opsForValue().get(key);
        
        if (data == null) {
            return null; // 키가 없음 (처음 요청)
        }
        
        if (STATUS_PROCESSING.equals(data.getStatus())) {
            // PROCESSING 상태: 동시 요청 처리 중
            throw new IllegalStateException("요청이 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }
        
        if (STATUS_COMPLETED.equals(data.getStatus())) {
            // COMPLETED 상태: 캐시된 응답 반환
            return (ApiResponse<T>) data.getResponse();
        }
        
        return null;
    }
    
    /**
     * PROCESSING 상태로 저장
     * 
     * 동시성 이슈 방지:
     * - Redis는 싱글 스레드로 동작하므로 동시성 이슈 없음
     * - 빠르게 저장하여 다른 요청이 같은 키로 접근하는 것을 방지
     * 
     * @param idempotencyKey 멱등성 키
     */
    public void markAsProcessing(String idempotencyKey) {
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        IdempotencyKeyData data = new IdempotencyKeyData();
        data.setStatus(STATUS_PROCESSING);
        data.setCreatedAt(System.currentTimeMillis());
        
        redisTemplate.opsForValue().set(key, data, TTL_HOURS, TimeUnit.HOURS);
    }
    
    /**
     * COMPLETED 상태로 저장 및 응답 캐싱
     * 
     * @param idempotencyKey 멱등성 키
     * @param response 응답 객체
     */
    public <T> void markAsCompleted(String idempotencyKey, ApiResponse<T> response) {
        String key = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        IdempotencyKeyData data = new IdempotencyKeyData();
        data.setStatus(STATUS_COMPLETED);
        data.setResponse(response);
        data.setCreatedAt(System.currentTimeMillis());
        
        redisTemplate.opsForValue().set(key, data, TTL_HOURS, TimeUnit.HOURS);
    }
    
    /**
     * 멱등성 키 데이터 내부 클래스
     */
    private static class IdempotencyKeyData {
        private String status;
        private Object response;
        private Long createdAt;
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public Object getResponse() {
            return response;
        }
        
        public void setResponse(Object response) {
            this.response = response;
        }
        
        public Long getCreatedAt() {
            return createdAt;
        }
        
        public void setCreatedAt(Long createdAt) {
            this.createdAt = createdAt;
        }
    }
}

