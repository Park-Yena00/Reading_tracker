package com.readingtracker.dbms.repository.primary;

import com.readingtracker.dbms.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    
    Optional<RefreshToken> findByToken(String token);
    
    List<RefreshToken> findByUserIdAndDeviceId(Long userId, String deviceId);
    
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId AND rt.deviceId = :deviceId AND rt.revoked = false AND rt.expiresAt > :now")
    List<RefreshToken> findValidTokensByUserAndDevice(@Param("userId") Long userId, @Param("deviceId") String deviceId, @Param("now") LocalDateTime now);
    
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.deviceId = :deviceId")
    void revokeAllTokensByUserAndDevice(@Param("userId") Long userId, @Param("deviceId") String deviceId);
    
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
    
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
    void deleteAllTokensByUser(@Param("userId") Long userId);
}

