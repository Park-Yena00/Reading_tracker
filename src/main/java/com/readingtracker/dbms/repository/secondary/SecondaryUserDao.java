package com.readingtracker.dbms.repository.secondary;

import com.readingtracker.dbms.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Secondary DB 전용 User DAO
 * 
 * PrimaryDB 연결이 끊겼을 때 SecondaryDB에서 User 데이터를 읽기 위한 DAO
 * JdbcTemplate을 사용하여 SecondaryDB에 직접 접근합니다.
 */
@Repository
public class SecondaryUserDao {
    
    @Autowired
    @Qualifier("secondaryNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
    
    /**
     * User RowMapper
     */
    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setLoginId(rs.getString("login_id"));
            user.setEmail(rs.getString("email"));
            user.setName(rs.getString("name"));
            user.setPasswordHash(rs.getString("password_hash"));
            
            String roleStr = rs.getString("role");
            if (roleStr != null) {
                user.setRole(User.Role.valueOf(roleStr));
            }
            
            String statusStr = rs.getString("status");
            if (statusStr != null) {
                user.setStatus(User.Status.valueOf(statusStr));
            }
            
            user.setFailedLoginCount(rs.getInt("failed_login_count"));
            
            java.sql.Timestamp lastLoginAt = rs.getTimestamp("last_login_at");
            if (lastLoginAt != null) {
                user.setLastLoginAt(lastLoginAt.toLocalDateTime());
            }
            
            java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                user.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                user.setUpdatedAt(updatedAt.toLocalDateTime());
            }
            
            return user;
        }
    }
    
    private static final UserRowMapper USER_ROW_MAPPER = new UserRowMapper();
    
    /**
     * loginId로 User 조회
     */
    public Optional<User> findByLoginId(String loginId) {
        String sql = "SELECT id, login_id, email, name, password_hash, role, status, " +
                    "failed_login_count, last_login_at, created_at, updated_at " +
                    "FROM users WHERE login_id = :loginId";
        
        Map<String, Object> params = new HashMap<>();
        params.put("loginId", loginId);
        
        try {
            User user = secondaryNamedParameterJdbcTemplate.queryForObject(sql, params, USER_ROW_MAPPER);
            return Optional.ofNullable(user);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * email로 User 존재 여부 확인
     */
    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = :email";
        
        Map<String, Object> params = new HashMap<>();
        params.put("email", email);
        
        Integer count = secondaryNamedParameterJdbcTemplate.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }
    
    /**
     * loginId로 User 존재 여부 확인
     */
    public boolean existsByLoginId(String loginId) {
        String sql = "SELECT COUNT(*) FROM users WHERE login_id = :loginId";
        
        Map<String, Object> params = new HashMap<>();
        params.put("loginId", loginId);
        
        Integer count = secondaryNamedParameterJdbcTemplate.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }
    
    /**
     * loginId로 활성 User 조회
     */
    public Optional<User> findActiveUserByLoginId(String loginId) {
        String sql = "SELECT id, login_id, email, name, password_hash, role, status, " +
                    "failed_login_count, last_login_at, created_at, updated_at " +
                    "FROM users WHERE login_id = :loginId AND status = 'ACTIVE'";
        
        Map<String, Object> params = new HashMap<>();
        params.put("loginId", loginId);
        
        try {
            User user = secondaryNamedParameterJdbcTemplate.queryForObject(sql, params, USER_ROW_MAPPER);
            return Optional.ofNullable(user);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * email과 name으로 활성 User 조회
     */
    public Optional<User> findActiveUserByEmailAndName(String email, String name) {
        String sql = "SELECT id, login_id, email, name, password_hash, role, status, " +
                    "failed_login_count, last_login_at, created_at, updated_at " +
                    "FROM users WHERE email = :email AND name = :name AND status = 'ACTIVE'";
        
        Map<String, Object> params = new HashMap<>();
        params.put("email", email);
        params.put("name", name);
        
        try {
            User user = secondaryNamedParameterJdbcTemplate.queryForObject(sql, params, USER_ROW_MAPPER);
            return Optional.ofNullable(user);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * loginId와 email로 활성 User 조회
     */
    public Optional<User> findActiveUserByLoginIdAndEmail(String loginId, String email) {
        String sql = "SELECT id, login_id, email, name, password_hash, role, status, " +
                    "failed_login_count, last_login_at, created_at, updated_at " +
                    "FROM users WHERE login_id = :loginId AND email = :email AND status = 'ACTIVE'";
        
        Map<String, Object> params = new HashMap<>();
        params.put("loginId", loginId);
        params.put("email", email);
        
        try {
            User user = secondaryNamedParameterJdbcTemplate.queryForObject(sql, params, USER_ROW_MAPPER);
            return Optional.ofNullable(user);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * id로 User 조회
     */
    public Optional<User> findById(Long id) {
        String sql = "SELECT id, login_id, email, name, password_hash, role, status, " +
                    "failed_login_count, last_login_at, created_at, updated_at " +
                    "FROM users WHERE id = :id";
        
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        
        try {
            User user = secondaryNamedParameterJdbcTemplate.queryForObject(sql, params, USER_ROW_MAPPER);
            return Optional.ofNullable(user);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}

