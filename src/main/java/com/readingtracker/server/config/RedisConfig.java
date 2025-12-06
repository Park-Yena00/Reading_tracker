package com.readingtracker.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
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
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate 빈 생성
     * 
     * Key: String 직렬화
     * Value: JSON 직렬화 (GenericJackson2JsonRedisSerializer)
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
        
        // Key 직렬화: String
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Value 직렬화: JSON
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.afterPropertiesSet();
        return template;
    }
}





