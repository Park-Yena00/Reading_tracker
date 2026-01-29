package com.readingtracker.dbms.repository.secondary;

import com.readingtracker.dbms.entity.Memo;
import com.readingtracker.dbms.entity.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Secondary DB 전용 Memo DAO
 * 
 * PrimaryDB 연결이 끊겼을 때 SecondaryDB에서 Memo 데이터를 읽기 위한 DAO
 * JdbcTemplate을 사용하여 SecondaryDB에 직접 접근합니다.
 * 
 * 주의: Tag는 LAZY 로딩이므로 별도로 조회해야 합니다.
 */
@Repository
@ConditionalOnProperty(name = "spring.datasource.secondary.enabled", havingValue = "true", matchIfMissing = false)
public class SecondaryMemoDao {
    
    @Autowired(required = false)
    @Qualifier("secondaryNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
    
    /**
     * Memo RowMapper (Tag는 제외, LAZY 로딩과 동일한 동작)
     */
    private static class MemoRowMapper implements RowMapper<Memo> {
        @Override
        public Memo mapRow(ResultSet rs, int rowNum) throws SQLException {
            Memo memo = new Memo();
            memo.setId(rs.getLong("id"));
            
            // User와 UserShelfBook은 LAZY 로딩이므로 ID만 저장
            // 필요시 별도로 조회해야 함
            
            memo.setPageNumber(rs.getInt("page_number"));
            memo.setContent(rs.getString("content"));
            
            java.sql.Timestamp memoStartTime = rs.getTimestamp("memo_start_time");
            if (memoStartTime != null) {
                memo.setMemoStartTime(memoStartTime.toLocalDateTime());
            }
            
            java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                memo.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                memo.setUpdatedAt(updatedAt.toLocalDateTime());
            }
            
            // Tag는 LAZY 로딩이므로 빈 리스트로 초기화 (필요시 별도 조회)
            memo.setTags(new ArrayList<>());
            
            return memo;
        }
    }
    
    private static final MemoRowMapper MEMO_ROW_MAPPER = new MemoRowMapper();
    
    /**
     * id로 Memo 조회
     */
    public Memo findById(Long id) {
        String sql = "SELECT id, user_id, book_id, page_number, content, memo_start_time, created_at, updated_at " +
                    "FROM memo WHERE id = :id";
        
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        
        try {
            return secondaryNamedParameterJdbcTemplate.queryForObject(sql, params, MEMO_ROW_MAPPER);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    /**
     * 특정 사용자의 특정 날짜의 메모 조회 (책별 그룹화)
     */
    public List<Memo> findByUserIdAndDateOrderByBookAndMemoStartTimeAsc(Long userId, LocalDateTime startOfDay, LocalDateTime startOfNextDay) {
        String sql = "SELECT id, user_id, book_id, page_number, content, memo_start_time, created_at, updated_at " +
                    "FROM memo " +
                    "WHERE user_id = :userId " +
                    "AND memo_start_time >= :startOfDay " +
                    "AND memo_start_time < :startOfNextDay " +
                    "ORDER BY book_id ASC, memo_start_time ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("startOfDay", startOfDay);
        params.put("startOfNextDay", startOfNextDay);
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, MEMO_ROW_MAPPER);
    }
    
    /**
     * 특정 사용자의 특정 날짜의 메모 조회 (시간순)
     */
    public List<Memo> findByUserIdAndDateOrderByMemoStartTimeAsc(Long userId, LocalDateTime startOfDay, LocalDateTime startOfNextDay) {
        String sql = "SELECT id, user_id, book_id, page_number, content, memo_start_time, created_at, updated_at " +
                    "FROM memo " +
                    "WHERE user_id = :userId " +
                    "AND memo_start_time >= :startOfDay " +
                    "AND memo_start_time < :startOfNextDay " +
                    "ORDER BY memo_start_time ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("startOfDay", startOfDay);
        params.put("startOfNextDay", startOfNextDay);
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, MEMO_ROW_MAPPER);
    }
    
    /**
     * 특정 사용자의 특정 책의 특정 날짜의 메모 조회
     */
    public List<Memo> findByUserIdAndUserShelfBookIdAndDate(Long userId, Long userShelfBookId, LocalDateTime startOfDay, LocalDateTime startOfNextDay) {
        String sql = "SELECT id, user_id, book_id, page_number, content, memo_start_time, created_at, updated_at " +
                    "FROM memo " +
                    "WHERE user_id = :userId " +
                    "AND book_id = :userShelfBookId " +
                    "AND memo_start_time >= :startOfDay " +
                    "AND memo_start_time < :startOfNextDay " +
                    "ORDER BY memo_start_time ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("userShelfBookId", userShelfBookId);
        params.put("startOfDay", startOfDay);
        params.put("startOfNextDay", startOfNextDay);
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, MEMO_ROW_MAPPER);
    }
    
    /**
     * 특정 사용자의 특정 책의 메모 조회 (시간순)
     */
    public List<Memo> findByUserIdAndUserShelfBookIdOrderByMemoStartTimeAsc(Long userId, Long userShelfBookId) {
        String sql = "SELECT id, user_id, book_id, page_number, content, memo_start_time, created_at, updated_at " +
                    "FROM memo " +
                    "WHERE user_id = :userId " +
                    "AND book_id = :userShelfBookId " +
                    "ORDER BY memo_start_time ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("userShelfBookId", userShelfBookId);
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, MEMO_ROW_MAPPER);
    }
    
    /**
     * 최근 기간 내에 메모가 작성된 책별 최신 메모 작성 시간 조회
     */
    public List<Object[]> findUserShelfBookIdsWithLastMemoTime(Long userId, LocalDateTime startDate) {
        String sql = "SELECT book_id, MAX(memo_start_time) as lastMemoTime " +
                    "FROM memo " +
                    "WHERE user_id = :userId " +
                    "AND memo_start_time >= :startDate " +
                    "GROUP BY book_id " +
                    "ORDER BY MAX(memo_start_time) DESC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("startDate", startDate);
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Object[] result = new Object[2];
            result[0] = rs.getLong("book_id");
            result[1] = rs.getTimestamp("lastMemoTime").toLocalDateTime();
            return result;
        });
    }
    
    /**
     * 특정 사용자의 특정 년/월에 메모가 작성된 날짜 목록 조회
     */
    public List<LocalDateTime> findMemoStartTimesByUserIdAndYearAndMonth(Long userId, LocalDateTime startOfMonth, LocalDateTime startOfNextMonth) {
        String sql = "SELECT memo_start_time " +
                    "FROM memo " +
                    "WHERE user_id = :userId " +
                    "AND memo_start_time >= :startOfMonth " +
                    "AND memo_start_time < :startOfNextMonth " +
                    "ORDER BY memo_start_time ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("startOfMonth", startOfMonth);
        params.put("startOfNextMonth", startOfNextMonth);
        
            return secondaryNamedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> 
            rs.getTimestamp("memo_start_time").toLocalDateTime()
        );
    }
    
    /**
     * Memo의 Tag 목록 조회
     */
    public List<Tag> findTagsByMemoId(Long memoId) {
        String sql = "SELECT t.id, t.category, t.code, t.sort_order, t.is_active, t.created_at, t.updated_at " +
                    "FROM memo_tags mt " +
                    "JOIN tags t ON mt.tag_id = t.id " +
                    "WHERE mt.memo_id = :memoId " +
                    "ORDER BY t.sort_order ASC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("memoId", memoId);
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Tag tag = new Tag();
            tag.setId(rs.getLong("id"));
            
            String categoryStr = rs.getString("category");
            if (categoryStr != null) {
                tag.setCategory(com.readingtracker.dbms.entity.TagCategory.valueOf(categoryStr));
            }
            
            tag.setCode(rs.getString("code"));
            
            Integer sortOrder = rs.getInt("sort_order");
            if (!rs.wasNull()) {
                tag.setSortOrder(sortOrder);
            }
            
            tag.setIsActive(rs.getBoolean("is_active"));
            
            java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                tag.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                tag.setUpdatedAt(updatedAt.toLocalDateTime());
            }
            
            return tag;
        });
    }
    
    /**
     * Memo 조회 후 Tag 로드 (헬퍼 메서드)
     */
    public Memo findByIdWithTags(Long id) {
        Memo memo = findById(id);
        if (memo != null) {
            List<Tag> tags = findTagsByMemoId(id);
            memo.setTags(tags);
        }
        return memo;
    }
    
    /**
     * Memo 목록 조회 후 각 Memo의 Tag 로드 (헬퍼 메서드)
     */
    public List<Memo> loadTagsForMemos(List<Memo> memos) {
        for (Memo memo : memos) {
            if (memo != null && memo.getId() != null) {
                List<Tag> tags = findTagsByMemoId(memo.getId());
                memo.setTags(tags);
            }
        }
        return memos;
    }
}

