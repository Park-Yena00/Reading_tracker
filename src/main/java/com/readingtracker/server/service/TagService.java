package com.readingtracker.server.service;

import com.readingtracker.dbms.entity.Tag;
import com.readingtracker.dbms.entity.TagCategory;
import com.readingtracker.dbms.repository.TagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 태그 서비스
 * 
 * 역할:
 * - 태그 데이터 조회
 * - Redis 캐싱 (Purger-Driven Invalidation + Long TTL: 7일)
 * - 태그 변경 시 수동 캐시 무효화
 */
@Service
public class TagService {
    
    private static final String CACHE_KEY_PREFIX = "tags:";
    private static final String CACHE_KEY_ALL = CACHE_KEY_PREFIX + "all";
    private static final long TTL_DAYS = 7; // 7일
    
    @Autowired
    private TagRepository tagRepository;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 모든 활성 태그 조회 (Redis 캐싱)
     * 
     * @return 태그 목록
     */
    @SuppressWarnings("unchecked")
    public List<Tag> getAllTags() {
        // 1. Redis 캐시 확인
        List<Tag> cachedTags = (List<Tag>) redisTemplate.opsForValue().get(CACHE_KEY_ALL);
        if (cachedTags != null) {
            return cachedTags;
        }
        
        // 2. DB 조회
        List<Tag> tags = tagRepository.findByIsActiveTrueOrderBySortOrderAsc();
        
        // 3. Redis에 저장 (TTL: 7일)
        redisTemplate.opsForValue().set(CACHE_KEY_ALL, tags, TTL_DAYS, TimeUnit.DAYS);
        
        return tags;
    }
    
    /**
     * 카테고리별 태그 조회 (Redis 캐싱)
     * 
     * @param category 태그 카테고리 (TYPE 또는 TOPIC)
     * @return 태그 목록
     */
    @SuppressWarnings("unchecked")
    public List<Tag> getTagsByCategory(TagCategory category) {
        String cacheKey = CACHE_KEY_PREFIX + category.name().toLowerCase();
        
        // 1. Redis 캐시 확인
        List<Tag> cachedTags = (List<Tag>) redisTemplate.opsForValue().get(cacheKey);
        if (cachedTags != null) {
            return cachedTags;
        }
        
        // 2. DB 조회
        List<Tag> tags = tagRepository.findByCategoryAndIsActiveTrueOrderBySortOrderAsc(category);
        
        // 3. Redis에 저장 (TTL: 7일)
        redisTemplate.opsForValue().set(cacheKey, tags, TTL_DAYS, TimeUnit.DAYS);
        
        return tags;
    }
    
    /**
     * 태그 캐시 무효화 (Purger-Driven Invalidation)
     * 
     * 개발자가 태그 DB를 변경한 후 수동으로 호출
     * 모든 태그 캐시를 삭제
     */
    public void invalidateTagCache() {
        // 모든 태그 캐시 키 삭제
        redisTemplate.delete(CACHE_KEY_ALL);
        redisTemplate.delete(CACHE_KEY_PREFIX + TagCategory.TYPE.name().toLowerCase());
        redisTemplate.delete(CACHE_KEY_PREFIX + TagCategory.TOPIC.name().toLowerCase());
    }
}





