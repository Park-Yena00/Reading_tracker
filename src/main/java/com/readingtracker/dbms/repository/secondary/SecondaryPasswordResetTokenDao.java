package com.readingtracker.dbms.repository.secondary;

import com.readingtracker.dbms.entity.PasswordResetToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Secondary DB 전용 PasswordResetToken DAO
 * 
 * PrimaryDB 연결이 끊겼을 때 SecondaryDB에서 PasswordResetToken 데이터를 읽기 위한 DAO
 * JdbcTemplate을 사용하여 SecondaryDB에 직접 접근합니다.
 */
@Repository
@ConditionalOnProperty(name = "spring.datasource.secondary.enabled", havingValue = "true", matchIfMissing = false)
public class SecondaryPasswordResetTokenDao {
    
    @Autowired(required = false)
    @Qualifier("secondaryNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
    
    /**
     * PasswordResetToken RowMapper
     */
    private static class PasswordResetTokenRowMapper implements RowMapper<PasswordResetToken> {
        @Override
        public PasswordResetToken mapRow(ResultSet rs, int rowNum) throws SQLException {
            PasswordResetToken token = new PasswordResetToken();
            token.setId(rs.getLong("id"));
            token.setUserId(rs.getLong("user_id"));
            token.setToken(rs.getString("token"));
            
            java.sql.Timestamp expiresAt = rs.getTimestamp("expires_at");
            if (expiresAt != null) {
                token.setExpiresAt(expiresAt.toLocalDateTime());
            }
            
            token.setUsed(rs.getBoolean("used"));
            
            java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                token.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            return token;
        }
    }
    
    private static final PasswordResetTokenRowMapper TOKEN_ROW_MAPPER = new PasswordResetTokenRowMapper();
    
    /**
     * 유효한 토큰 조회 (used = false AND expiresAt > now)
     */
    public Optional<PasswordResetToken> findValidToken(String token, LocalDateTime now) {
        String sql = "SELECT id, user_id, token, expires_at, used, created_at " +
                    "FROM password_reset_tokens " +
                    "WHERE token = :token AND used = false AND expires_at > :now";
        
        Map<String, Object> params = new HashMap<>();
        params.put("token", token);
        params.put("now", now);
        
        try {
            PasswordResetToken tokenEntity = secondaryNamedParameterJdbcTemplate.queryForObject(
                sql, params, TOKEN_ROW_MAPPER);
            return Optional.ofNullable(tokenEntity);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}

