package com.readingtracker.server.service;

import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.entity.UserDevice;
import com.readingtracker.dbms.repository.primary.UserDeviceRepository;
import com.readingtracker.dbms.repository.secondary.SecondaryUserDeviceDao;
import com.readingtracker.server.service.recovery.RecoveryQueueService;
import com.readingtracker.server.service.write.DualMasterWriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
// @Transactional 제거 (Dual Write를 위해)
public class UserDeviceService {
    
    @Autowired
    private UserDeviceRepository userDeviceRepository;
    
    @Autowired
    private DualMasterWriteService dualMasterWriteService;
    
    @Autowired
    private com.readingtracker.server.service.read.DualMasterReadService dualMasterReadService;
    
    @Autowired
    private SecondaryUserDeviceDao secondaryUserDeviceDao;
    
    @Autowired
    @Qualifier("secondaryNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
    
    @Autowired
    private RecoveryQueueService recoveryQueueService;
    
    /**
     * 디바이스 등록 또는 업데이트
     * @param user 사용자
     * @param deviceId 디바이스 ID
     * @param deviceName 디바이스 이름
     * @param platform 플랫폼
     * @return 저장된 디바이스 정보
     * 
     * Dual Write 적용: Primary는 JPA Repository, Secondary는 JdbcTemplate 사용
     */
    public UserDevice saveOrUpdateDevice(User user, String deviceId, String deviceName, UserDevice.Platform platform) {
        Optional<UserDevice> existingDevice = userDeviceRepository.findByUserIdAndDeviceId(user.getId(), deviceId);
        
        if (existingDevice.isPresent()) {
            // 기존 디바이스 업데이트
            UserDevice device = existingDevice.get();
            device.setDeviceName(deviceName);
            device.setPlatform(platform);
            device.setLastSeenAt(LocalDateTime.now());
            return updateUserDeviceWithDualWrite(device);
        } else {
            // 새 디바이스 생성
            UserDevice device = new UserDevice(user, deviceId, deviceName, platform);
            return saveUserDeviceWithDualWrite(device);
        }
    }
    
    /**
     * 사용자의 디바이스 목록 조회
     * @param userId 사용자 ID
     * @return 디바이스 목록
     * 
     * Dual Read 적용: Primary에서 읽기 시도, 실패 시 Secondary로 Failover
     */
    @Transactional(readOnly = true)
    public List<UserDevice> getUserDevices(Long userId) {
        return dualMasterReadService.readWithFailover(
            () -> userDeviceRepository.findByUserIdOrderByLastSeenAtDesc(userId),
            () -> secondaryUserDeviceDao.findByUserIdOrderByLastSeenAtDesc(userId)
        );
    }
    
    /**
     * 특정 디바이스 조회
     * @param userId 사용자 ID
     * @param deviceId 디바이스 ID
     * @return 디바이스 정보
     */
    @Transactional(readOnly = true)
    public Optional<UserDevice> getDevice(Long userId, String deviceId) {
        return userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId);
    }
    
    /**
     * 디바이스 삭제
     * @param userId 사용자 ID
     * @param deviceId 디바이스 ID
     * @return 삭제 성공 여부
     * 
     * Dual Write 적용: Primary는 JPA Repository, Secondary는 JdbcTemplate 사용
     */
    public boolean deleteDevice(Long userId, String deviceId) {
        Optional<UserDevice> device = userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId);
        if (device.isPresent()) {
            deleteUserDeviceWithDualWrite(device.get());
            return true;
        }
        return false;
    }
    
    /**
     * 사용자의 모든 디바이스 삭제
     * @param userId 사용자 ID
     * 
     * Dual Write 적용: Primary는 JPA Repository, Secondary는 JdbcTemplate 사용
     */
    public void deleteAllUserDevices(Long userId) {
        List<UserDevice> devices = userDeviceRepository.findByUserIdOrderByLastSeenAtDesc(userId);
        for (UserDevice device : devices) {
            deleteUserDeviceWithDualWrite(device);
        }
    }
    
    /**
     * 디바이스 접속 시간 업데이트
     * @param userId 사용자 ID
     * @param deviceId 디바이스 ID
     * 
     * Dual Write 적용: Primary는 JPA Repository, Secondary는 JdbcTemplate 사용
     */
    public void updateLastSeenAt(Long userId, String deviceId) {
        Optional<UserDevice> device = userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId);
        if (device.isPresent()) {
            device.get().setLastSeenAt(LocalDateTime.now());
            updateUserDeviceWithDualWrite(device.get());
        }
    }
    
    /**
     * 오래된 디바이스 정리 (30일 이상 접속하지 않은 디바이스)
     * @param userId 사용자 ID
     * 
     * Dual Write 적용: Primary는 JPA Repository, Secondary는 JdbcTemplate 사용
     */
    public void cleanupOldDevices(Long userId) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        List<UserDevice> oldDevices = userDeviceRepository.findByUserIdAndLastSeenAtBefore(userId, cutoffDate);
        for (UserDevice device : oldDevices) {
            deleteUserDeviceWithDualWrite(device);
        }
    }
    
    /**
     * UserDevice 저장을 Dual Write로 처리하는 헬퍼 메서드
     */
    private UserDevice saveUserDeviceWithDualWrite(UserDevice device) {
        return dualMasterWriteService.writeWithDualWrite(
            // Primary: JPA Repository 사용
            () -> userDeviceRepository.save(device),
            
            // Secondary: JdbcTemplate 사용
            (jdbcTemplate, savedDevice) -> {
                String insertDeviceSql = "INSERT INTO user_devices (id, user_id, device_id, device_name, platform, last_seen_at, created_at) " +
                                       "VALUES (:id, :userId, :deviceId, :deviceName, :platform, :lastSeenAt, :createdAt)";
                
                LocalDateTime now = LocalDateTime.now();
                Map<String, Object> deviceParams = new HashMap<>();
                deviceParams.put("id", savedDevice.getId());
                deviceParams.put("userId", savedDevice.getUser() != null ? savedDevice.getUser().getId() : null);
                deviceParams.put("deviceId", savedDevice.getDeviceId());
                deviceParams.put("deviceName", savedDevice.getDeviceName());
                deviceParams.put("platform", savedDevice.getPlatform() != null ? savedDevice.getPlatform().name() : null);
                deviceParams.put("lastSeenAt", savedDevice.getLastSeenAt());
                deviceParams.put("createdAt", savedDevice.getCreatedAt() != null ? savedDevice.getCreatedAt() : now);
                
                secondaryNamedParameterJdbcTemplate.update(insertDeviceSql, deviceParams);
                return null;
            },
            
            // 보상 트랜잭션: Primary에서 DELETE
            (savedDevice) -> {
                if (savedDevice != null && savedDevice.getId() != null) {
                    userDeviceRepository.deleteById(savedDevice.getId());
                }
                return null;
            },
            "UserDevice"  // 엔티티 타입 (Recovery Queue 발행용)
        );
    }
    
    /**
     * UserDevice 업데이트를 Dual Write로 처리하는 헬퍼 메서드
     */
    private UserDevice updateUserDeviceWithDualWrite(UserDevice device) {
        // 이전 상태 저장 (보상 트랜잭션용)
        UserDevice originalState = new UserDevice();
        originalState.setId(device.getId());
        originalState.setDeviceName(device.getDeviceName());
        originalState.setPlatform(device.getPlatform());
        originalState.setLastSeenAt(device.getLastSeenAt());
        
        return dualMasterWriteService.writeWithDualWrite(
            // Primary: JPA Repository 사용
            () -> userDeviceRepository.save(device),
            
            // Secondary: JdbcTemplate 사용
            (jdbcTemplate, updatedDevice) -> {
                String updateDeviceSql = "UPDATE user_devices SET device_name = :deviceName, " +
                                       "platform = :platform, last_seen_at = :lastSeenAt " +
                                       "WHERE id = :id";
                
                Map<String, Object> updateParams = new HashMap<>();
                updateParams.put("id", updatedDevice.getId());
                updateParams.put("deviceName", updatedDevice.getDeviceName());
                updateParams.put("platform", updatedDevice.getPlatform() != null ? updatedDevice.getPlatform().name() : null);
                updateParams.put("lastSeenAt", updatedDevice.getLastSeenAt());
                
                secondaryNamedParameterJdbcTemplate.update(updateDeviceSql, updateParams);
                return null;
            },
            
            // 보상 트랜잭션: Primary에서 원래 상태로 복구
            (updatedDevice) -> {
                if (updatedDevice != null) {
                    device.setDeviceName(originalState.getDeviceName());
                    device.setPlatform(originalState.getPlatform());
                    device.setLastSeenAt(originalState.getLastSeenAt());
                    userDeviceRepository.save(device);
                }
                return null;
            },
            "UserDevice"  // 엔티티 타입 (Recovery Queue 발행용)
        );
    }
    
    /**
     * UserDevice 삭제를 Dual Write로 처리하는 헬퍼 메서드
     */
    private void deleteUserDeviceWithDualWrite(UserDevice device) {
        Long deviceId = device.getId();
        
        dualMasterWriteService.writeWithDualWrite(
            // Primary: JPA Repository 사용
            () -> {
                userDeviceRepository.delete(device);
                return null;
            },
            
            // Secondary: JdbcTemplate 사용
            (jdbcTemplate, result) -> {
                String deleteDeviceSql = "DELETE FROM user_devices WHERE id = :id";
                Map<String, Object> deleteParams = new HashMap<>();
                deleteParams.put("id", deviceId);
                secondaryNamedParameterJdbcTemplate.update(deleteDeviceSql, deleteParams);
                return null;
            },
            
            // 보상 트랜잭션: DELETE의 보상은 복구가 어려우므로 Recovery Queue에 발행
            (result) -> {
                com.readingtracker.server.service.recovery.CompensationFailureEvent event = 
                    new com.readingtracker.server.service.recovery.CompensationFailureEvent(
                        "DELETE_SECONDARY_CLEANUP",
                        deviceId,
                        "UserDevice",
                        "Secondary",
                        java.time.Instant.now(),
                        "Primary DELETE 성공 후 Secondary DELETE 실패로 인한 유령 데이터 정리 필요"
                    );
                
                recoveryQueueService.publish(event);
                org.slf4j.LoggerFactory.getLogger(UserDeviceService.class)
                    .warn("DELETE 보상 트랜잭션: Secondary 유령 데이터 정리를 위해 Recovery Queue에 발행 (deviceId: {})", deviceId);
                
                return null;
            },
            "UserDevice"  // 엔티티 타입 (Recovery Queue 발행용)
        );
    }
}

