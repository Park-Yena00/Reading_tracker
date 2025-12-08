package com.readingtracker.server.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정 클래스
 * 
 * 역할:
 * - Redis 연결 설정
 * - RedisTemplate 빈 생성
 * - 직렬화 설정 (String, JSON)
 * - Java 8 Date/Time 타입 (LocalDateTime 등) 직렬화 지원
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate 빈 생성
     * 
     * Key: String 직렬화
     * Value: JSON 직렬화 (GenericJackson2JsonRedisSerializer)
     * 
     * JavaTimeModule 등록:
     * - LocalDateTime, LocalDate 등 Java 8 Date/Time 타입 직렬화 지원
     * - 내 서재 정보(UserShelfBook)의 User 엔티티의 lastLoginAt 필드 직렬화 가능
     * 
     * 사용 목적:
     * - 멱등성 키 관리
     * - 세션/인증 토큰
     * - 빈번한 캐싱 (태그 데이터, 내 서재 정보)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // ObjectMapper 생성 및 설정
        ObjectMapper objectMapper = new ObjectMapper();
        
        // Java 8 Date/Time 지원
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 순환 참조 처리 설정
        // FAIL_ON_SELF_REFERENCES: 자기 자신을 참조하는 경우 실패하지 않음
        objectMapper.configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false);
        // WRITE_SELF_REFERENCES_AS_NULL: 순환 참조를 null로 처리
        objectMapper.configure(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL, true);
        
        // 타입 정보 포함 (필요한 경우)
        objectMapper.activateDefaultTyping(
            objectMapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        
        // Key 직렬화: String
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Value 직렬화: JSON (JavaTimeModule 포함)
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}





