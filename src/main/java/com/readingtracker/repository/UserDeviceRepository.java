package com.readingtracker.repository;

import com.readingtracker.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    
    Optional<UserDevice> findByUserIdAndDeviceId(Long userId, String deviceId);
    
    List<UserDevice> findByUserId(Long userId);
    
    @Query("SELECT ud FROM UserDevice ud WHERE ud.user.id = :userId AND ud.deviceId = :deviceId")
    Optional<UserDevice> findActiveDeviceByUserAndDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);
    
    void deleteByUserIdAndDeviceId(Long userId, String deviceId);
    
    List<UserDevice> findByUserIdOrderByLastSeenAtDesc(Long userId);
    
    void deleteAllByUserId(Long userId);
    
    List<UserDevice> findByUserIdAndLastSeenAtBefore(Long userId, java.time.LocalDateTime cutoffDate);
}



