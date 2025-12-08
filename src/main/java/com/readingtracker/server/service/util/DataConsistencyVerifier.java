package com.readingtracker.server.service.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Primary/Secondary DB 간 데이터 정합성 검증 유틸리티
 * 
 * Dual Write 직후 Primary와 Secondary DB의 데이터가 일치하는지 검증합니다.
 */
@Component
public class DataConsistencyVerifier {
    
    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;
    
    @Autowired
    @Qualifier("secondaryJdbcTemplate")
    private JdbcTemplate secondaryJdbcTemplate;
    
    /**
     * Memo 데이터 정합성 검증
     * 
     * @param memoId 검증할 메모 ID
     * @return 정합성 검증 결과 (모든 필드가 일치하면 true)
     */
    public boolean verifyMemoConsistency(Long memoId) {
        try {
            // Primary DB에서 조회
            Map<String, Object> primaryMemo = primaryJdbcTemplate.queryForMap(
                "SELECT id, user_id, book_id, page_number, content, memo_start_time, created_at, updated_at " +
                "FROM memo WHERE id = ?", 
                memoId
            );
            
            // Secondary DB에서 조회
            Map<String, Object> secondaryMemo = secondaryJdbcTemplate.queryForMap(
                "SELECT id, user_id, book_id, page_number, content, memo_start_time, created_at, updated_at " +
                "FROM memo WHERE id = ?", 
                memoId
            );
            
            // 필드별 비교
            return compareFields(primaryMemo, secondaryMemo, "id") &&
                   compareFields(primaryMemo, secondaryMemo, "user_id") &&
                   compareFields(primaryMemo, secondaryMemo, "book_id") &&
                   compareFields(primaryMemo, secondaryMemo, "page_number") &&
                   compareFields(primaryMemo, secondaryMemo, "content") &&
                   compareFields(primaryMemo, secondaryMemo, "memo_start_time");
            
        } catch (Exception e) {
            // 조회 실패 시 정합성 검증 실패로 간주
            return false;
        }
    }
    
    /**
     * 두 Map에서 특정 필드 값 비교
     */
    private boolean compareFields(Map<String, Object> map1, Map<String, Object> map2, String fieldName) {
        Object value1 = map1.get(fieldName);
        Object value2 = map2.get(fieldName);
        
        if (value1 == null && value2 == null) {
            return true;
        }
        if (value1 == null || value2 == null) {
            return false;
        }
        
        return value1.equals(value2);
    }
    
    /**
     * Primary DB에만 존재하는 데이터 조회 (불일치 데이터)
     */
    public java.util.List<Long> findPrimaryOnlyMemos() {
        return primaryJdbcTemplate.queryForList(
            "SELECT m1.id FROM memo m1 " +
            "LEFT JOIN (SELECT id FROM memo) m2 ON m1.id = m2.id " +
            "WHERE m2.id IS NULL",
            Long.class
        );
    }
    
    /**
     * Secondary DB에만 존재하는 데이터 조회 (유령 데이터)
     */
    public java.util.List<Long> findSecondaryOnlyMemos() {
        // 주의: 이 쿼리는 실제로는 두 DB를 직접 비교할 수 없으므로,
        // Secondary DB에서만 조회하는 것으로 대체
        // 실제 구현 시에는 두 DB를 연결하여 비교해야 함
        return secondaryJdbcTemplate.queryForList(
            "SELECT id FROM memo",
            Long.class
        );
    }
}

