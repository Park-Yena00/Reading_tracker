package com.readingtracker.dbms.repository.secondary;

import com.readingtracker.dbms.entity.UserDevice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Secondary DB 전용 UserDevice DAO
 * 
 * PrimaryDB 연결이 끊겼을 때 SecondaryDB에서 UserDevice 데이터를 읽기 위한 DAO
 * JdbcTemplate을 사용하여 SecondaryDB에 직접 접근합니다.
 */
@Repository
@ConditionalOnProperty(name = "spring.datasource.secondary.enabled", havingValue = "true", matchIfMissing = false)
public class SecondaryUserDeviceDao {
    
    @Autowired(required = false)
    @Qualifier("secondaryNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
    
    /**
     * UserDevice RowMapper
     */
    private static class UserDeviceRowMapper implements RowMapper<UserDevice> {
        @Override
        public UserDevice mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserDevice device = new UserDevice();
            device.setId(rs.getLong("id"));
            
            // User는 LAZY 로딩이므로 ID만 저장 (필요시 나중에 조회)
            // 여기서는 User 객체를 생성하지 않음 (LAZY 로딩과 동일한 동작)
            
            device.setDeviceId(rs.getString("device_id"));
            device.setDeviceName(rs.getString("device_name"));
            
            String platformStr = rs.getString("platform");
            if (platformStr != null) {
                device.setPlatform(UserDevice.Platform.valueOf(platformStr));
            }
            
            java.sql.Timestamp lastSeenAt = rs.getTimestamp("last_seen_at");
            if (lastSeenAt != null) {
                device.setLastSeenAt(lastSeenAt.toLocalDateTime());
            }
            
            java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                device.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            return device;
        }
    }
    
    private static final UserDeviceRowMapper USER_DEVICE_ROW_MAPPER = new UserDeviceRowMapper();
    
    /**
     * userId로 UserDevice 목록 조회 (lastSeenAt 내림차순)
     */
    public List<UserDevice> findByUserIdOrderByLastSeenAtDesc(Long userId) {
        String sql = "SELECT id, user_id, device_id, device_name, platform, last_seen_at, created_at " +
                    "FROM user_devices WHERE user_id = :userId " +
                    "ORDER BY last_seen_at DESC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        
        return secondaryNamedParameterJdbcTemplate.query(sql, params, USER_DEVICE_ROW_MAPPER);
    }
}

