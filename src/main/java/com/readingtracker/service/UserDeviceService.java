package com.readingtracker.service;

import com.readingtracker.entity.User;
import com.readingtracker.entity.UserDevice;
import com.readingtracker.repository.UserDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserDeviceService {
    
    @Autowired
    private UserDeviceRepository userDeviceRepository;
    
    /**
     * 디바이스 등록 또는 업데이트
     * @param user 사용자
     * @param deviceId 디바이스 ID
     * @param deviceName 디바이스 이름
     * @param platform 플랫폼
     * @return 저장된 디바이스 정보
     */
    public UserDevice saveOrUpdateDevice(User user, String deviceId, String deviceName, UserDevice.Platform platform) {
        Optional<UserDevice> existingDevice = userDeviceRepository.findByUserIdAndDeviceId(user.getId(), deviceId);
        
        if (existingDevice.isPresent()) {
            // 기존 디바이스 업데이트
            UserDevice device = existingDevice.get();
            device.setDeviceName(deviceName);
            device.setPlatform(platform);
            device.setLastSeenAt(LocalDateTime.now());
            return userDeviceRepository.save(device);
        } else {
            // 새 디바이스 생성
            UserDevice device = new UserDevice(user, deviceId, deviceName, platform);
            return userDeviceRepository.save(device);
        }
    }
    
    /**
     * 사용자의 디바이스 목록 조회
     * @param userId 사용자 ID
     * @return 디바이스 목록
     */
    @Transactional(readOnly = true)
    public List<UserDevice> getUserDevices(Long userId) {
        return userDeviceRepository.findByUserIdOrderByLastSeenAtDesc(userId);
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
     */
    public boolean deleteDevice(Long userId, String deviceId) {
        Optional<UserDevice> device = userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId);
        if (device.isPresent()) {
            userDeviceRepository.delete(device.get());
            return true;
        }
        return false;
    }
    
    /**
     * 사용자의 모든 디바이스 삭제
     * @param userId 사용자 ID
     */
    public void deleteAllUserDevices(Long userId) {
        userDeviceRepository.deleteAllByUserId(userId);
    }
    
    /**
     * 디바이스 접속 시간 업데이트
     * @param userId 사용자 ID
     * @param deviceId 디바이스 ID
     */
    public void updateLastSeenAt(Long userId, String deviceId) {
        Optional<UserDevice> device = userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId);
        if (device.isPresent()) {
            device.get().setLastSeenAt(LocalDateTime.now());
            userDeviceRepository.save(device.get());
        }
    }
    
    /**
     * 오래된 디바이스 정리 (30일 이상 접속하지 않은 디바이스)
     * @param userId 사용자 ID
     */
    public void cleanupOldDevices(Long userId) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        List<UserDevice> oldDevices = userDeviceRepository.findByUserIdAndLastSeenAtBefore(userId, cutoffDate);
        userDeviceRepository.deleteAll(oldDevices);
    }
}

