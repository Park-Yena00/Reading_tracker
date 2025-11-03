package com.readingtracker.repository;

import com.readingtracker.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    
    Optional<PasswordResetToken> findByToken(String token);
    
    @Query("SELECT t FROM PasswordResetToken t WHERE t.token = :token AND t.used = false AND t.expiresAt > :now")
    Optional<PasswordResetToken> findValidToken(@Param("token") String token, @Param("now") LocalDateTime now);
    
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
    
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
}



