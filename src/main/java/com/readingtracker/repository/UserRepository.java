package com.readingtracker.repository;

import com.readingtracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByLoginId(String loginId);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByLoginId(String loginId);
    
    boolean existsByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.loginId = :loginId AND u.status = 'ACTIVE'")
    Optional<User> findActiveUserByLoginId(@Param("loginId") String loginId);
    
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.status = 'ACTIVE'")
    Optional<User> findActiveUserByEmail(@Param("email") String email);
    
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.name = :name AND u.status = 'ACTIVE'")
    Optional<User> findActiveUserByEmailAndName(@Param("email") String email, @Param("name") String name);
    
    @Query("SELECT u FROM User u WHERE u.loginId = :loginId AND u.email = :email AND u.status = 'ACTIVE'")
    Optional<User> findActiveUserByLoginIdAndEmail(@Param("loginId") String loginId, @Param("email") String email);
}


