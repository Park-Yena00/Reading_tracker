package com.readingtracker.server.service;

import com.readingtracker.server.common.constant.ErrorCode;
import com.readingtracker.dbms.entity.PasswordResetToken;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.repository.primary.PasswordResetTokenRepository;
import com.readingtracker.dbms.repository.primary.UserRepository;
import com.readingtracker.dbms.repository.secondary.SecondaryPasswordResetTokenDao;
import com.readingtracker.dbms.repository.secondary.SecondaryUserDao;
import com.readingtracker.server.common.util.PasswordValidator;
import com.readingtracker.server.service.read.DualMasterReadService;
import com.readingtracker.server.service.write.DualMasterWriteService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
// @Transactional 제거 (Dual Write를 위해)
public class AuthService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private PasswordValidator passwordValidator;
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    
    @Autowired
    private DualMasterWriteService dualMasterWriteService;
    
    @Autowired
    private DualMasterReadService dualMasterReadService;
    
    @Autowired(required = false)
    private SecondaryUserDao secondaryUserDao;
    
    @Autowired(required = false)
    private SecondaryPasswordResetTokenDao secondaryPasswordResetTokenDao;
    
    @Autowired
    @Qualifier("secondaryNamedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate secondaryNamedParameterJdbcTemplate;
    
    /**
     * 회원가입 - Controller에서 호출
     * @param user 사용자 Entity (Mapper를 통해 DTO에서 변환됨)
     * @param password 평문 비밀번호 (암호화 전)
     * @return 생성된 사용자 Entity
     */
    public User register(User user, String password) {
        return executeRegister(user, password);
    }
    
    /**
     * 회원가입 실행
     * @param user 사용자 Entity
     * @param password 평문 비밀번호
     * @return 생성된 사용자 엔티티
     * 
     * Dual Write 적용: Primary는 JPA Repository, Secondary는 JdbcTemplate 사용
     */
    private User executeRegister(User user, String password) {
        // 1. 중복 확인 (Dual Read Failover 적용)
        if (dualMasterReadService.readWithFailover(
            () -> userRepository.existsByLoginId(user.getLoginId()),
            () -> secondaryUserDao != null ? secondaryUserDao.existsByLoginId(user.getLoginId()) : false)) {
            throw new IllegalArgumentException(ErrorCode.DUPLICATE_LOGIN_ID.getMessage());
        }
        
        if (dualMasterReadService.readWithFailover(
            () -> userRepository.existsByEmail(user.getEmail()),
            () -> secondaryUserDao != null ? secondaryUserDao.existsByEmail(user.getEmail()) : false)) {
            throw new IllegalArgumentException(ErrorCode.DUPLICATE_EMAIL.getMessage());
        }
        
        // 2. 비밀번호 검증
        passwordValidator.validate(password);
        
        // 3. 비밀번호 암호화 및 설정
        String encodedPassword = passwordEncoder.encode(password);
        user.setPasswordHash(encodedPassword);
        
        // 4. 저장 (Dual Write)
        return dualMasterWriteService.writeWithDualWrite(
            // Primary: JPA Repository 사용
            () -> userRepository.save(user),
            
            // Secondary: JdbcTemplate 사용
            (jdbcTemplate, savedUser) -> {
                String insertUserSql = "INSERT INTO users (id, login_id, email, name, password_hash, role, status, " +
                                     "failed_login_count, last_login_at, created_at, updated_at) " +
                                     "VALUES (:id, :loginId, :email, :name, :passwordHash, :role, :status, " +
                                     ":failedLoginCount, :lastLoginAt, :createdAt, :updatedAt)";
                
                LocalDateTime now = LocalDateTime.now();
                Map<String, Object> userParams = new HashMap<>();
                userParams.put("id", savedUser.getId());
                userParams.put("loginId", savedUser.getLoginId());
                userParams.put("email", savedUser.getEmail());
                userParams.put("name", savedUser.getName());
                userParams.put("passwordHash", savedUser.getPasswordHash());
                userParams.put("role", savedUser.getRole() != null ? savedUser.getRole().name() : "USER");
                userParams.put("status", savedUser.getStatus() != null ? savedUser.getStatus().name() : "ACTIVE");
                userParams.put("failedLoginCount", savedUser.getFailedLoginCount() != null ? savedUser.getFailedLoginCount() : 0);
                userParams.put("lastLoginAt", savedUser.getLastLoginAt());
                userParams.put("createdAt", savedUser.getCreatedAt() != null ? savedUser.getCreatedAt() : now);
                userParams.put("updatedAt", savedUser.getUpdatedAt() != null ? savedUser.getUpdatedAt() : now);
                
                secondaryNamedParameterJdbcTemplate.update(insertUserSql, userParams);
                return null;
            },
            
            // 보상 트랜잭션: Primary에서 DELETE
            (savedUser) -> {
                if (savedUser != null && savedUser.getId() != null) {
                    userRepository.deleteById(savedUser.getId());
                }
                return null;
            },
            "User"  // 엔티티 타입 (Recovery Queue 발행용)
        );
    }
    
    /**
     * 로그인 - Controller에서 호출
     * @param loginId 로그인 ID
     * @param password 평문 비밀번호
     * @return 로그인 결과 (토큰 정보 및 사용자 Entity 포함)
     */
    public LoginResult login(String loginId, String password) {
        return executeLogin(loginId, password);
    }
    
    /**
     * 로그인 실행
     * @param loginId 로그인 ID
     * @param password 평문 비밀번호
     * @return 로그인 결과 (토큰 정보 및 사용자 Entity 포함)
     */
    private LoginResult executeLogin(String loginId, String password) {
        // 1. 사용자 조회 (loginId만 허용) - Dual Read Failover 적용
        java.util.Optional<User> userOpt = dualMasterReadService.readWithFailover(
            () -> userRepository.findByLoginId(loginId),
            () -> secondaryUserDao != null ? secondaryUserDao.findByLoginId(loginId) : java.util.Optional.<User>empty());
        User user = userOpt.orElseThrow(() -> new IllegalArgumentException(ErrorCode.USER_NOT_FOUND.getMessage()));
        
        // 2. 계정 상태 확인
        if (user.getStatus() == User.Status.LOCKED) {
            throw new IllegalArgumentException(ErrorCode.ACCOUNT_LOCKED.getMessage());
        }
        
        if (user.getStatus() == User.Status.DELETED) {
            throw new IllegalArgumentException(ErrorCode.ACCOUNT_DELETED.getMessage());
        }
        
        // 3. 비밀번호 확인
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // 로그인 실패 횟수 증가
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            
            // 5회 실패 시 계정 잠금
            if (user.getFailedLoginCount() >= 5) {
                user.setStatus(User.Status.LOCKED);
            }
            
            // Dual Write 적용
            updateUserWithDualWrite(user);
            throw new IllegalArgumentException(ErrorCode.INVALID_PASSWORD.getMessage());
        }
        
        // 4. 로그인 성공 처리
        user.setFailedLoginCount(0); // 실패 횟수 초기화
        user.setLastLoginAt(LocalDateTime.now());
        updateUserWithDualWrite(user);
        
        // 5. 토큰 생성 (디바이스 정보는 자동 생성)
        JwtService.TokenResult tokenResult = jwtService.generateTokens(
            user,
            null,  // deviceId - 자동 생성
            null,  // deviceName - "Unknown Device"
            null   // platform - "WEB"
        );
        
        return new LoginResult(tokenResult.getAccessToken(), tokenResult.getRefreshToken(), user);
    }
    
    /**
     * 아이디 찾기 - Controller에서 호출
     * @param email 이메일
     * @param name 이름
     * @return 사용자 Entity
     */
    public User findLoginIdByEmailAndName(String email, String name) {
        return executeFindLoginId(email, name);
    }
    
    /**
     * 아이디 찾기 실행
     * @param email 이메일
     * @param name 이름
     * @return 사용자 엔티티
     */
    private User executeFindLoginId(String email, String name) {
        // 이메일 + 이름으로 활성 사용자 조회 (둘 다 일치해야 함) - Dual Read Failover 적용
        java.util.Optional<User> userOpt = dualMasterReadService.readWithFailover(
            () -> userRepository.findActiveUserByEmailAndName(email, name),
            () -> secondaryUserDao != null ? secondaryUserDao.findActiveUserByEmailAndName(email, name) : java.util.Optional.<User>empty());
        User user = userOpt.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다."));
        
        return user;
    }
    
    /**
     * Step 1: 계정 확인 및 재설정 토큰 발급 - Controller에서 호출
     * @param loginId 로그인 ID
     * @param email 이메일
     * @return 재설정 토큰
     */
    public String verifyAccountForPasswordReset(String loginId, String email) {
        return executeVerifyAccount(loginId, email);
    }
    
    /**
     * 계정 확인 실행
     * @param loginId 로그인 ID
     * @param email 이메일
     * @return 재설정 토큰
     */
    private String executeVerifyAccount(String loginId, String email) {
        // 1. loginId + email로 활성 사용자 조회 (둘 다 일치해야 함) - Dual Read Failover 적용
        java.util.Optional<User> userOpt = dualMasterReadService.readWithFailover(
            () -> userRepository.findActiveUserByLoginIdAndEmail(loginId, email),
            () -> secondaryUserDao != null ? secondaryUserDao.findActiveUserByLoginIdAndEmail(loginId, email) : java.util.Optional.<User>empty());
        User user = userOpt.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다."));
        
        // 2. 기존 토큰 삭제 (같은 사용자의 이전 재설정 토큰)
        passwordResetTokenRepository.deleteAllByUserId(user.getId());
        
        // 3. 새 토큰 생성 (UUID 기반 랜덤 토큰)
        String resetToken = UUID.randomUUID().toString();
        
        // 4. 토큰 만료 시간 설정 (5분)
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        
        // 5. 토큰 저장
        PasswordResetToken tokenEntity = new PasswordResetToken(user.getId(), resetToken, expiresAt);
        passwordResetTokenRepository.save(tokenEntity);
        
        return resetToken;
    }
    
    /**
     * Step 2: 비밀번호 변경 - Controller에서 호출
     * @param resetToken 재설정 토큰
     * @param newPassword 새 비밀번호
     * @param confirmPassword 확인 비밀번호
     * @return 변경된 사용자 Entity
     */
    public User resetPassword(String resetToken, String newPassword, String confirmPassword) {
        return executeResetPassword(resetToken, newPassword, confirmPassword);
    }
    
    /**
     * 비밀번호 변경 실행
     * @param resetToken 재설정 토큰
     * @param newPassword 새 비밀번호
     * @param confirmPassword 확인 비밀번호
     * @return 변경된 사용자 Entity
     */
    private User executeResetPassword(String resetToken, String newPassword, String confirmPassword) {
        // 1. 토큰 검증 - Dual Read Failover 적용
        java.util.Optional<PasswordResetToken> tokenOpt = dualMasterReadService.readWithFailover(
            () -> passwordResetTokenRepository.findValidToken(resetToken, LocalDateTime.now()),
            () -> secondaryPasswordResetTokenDao != null ? secondaryPasswordResetTokenDao.findValidToken(resetToken, LocalDateTime.now()) : java.util.Optional.<PasswordResetToken>empty());
        PasswordResetToken tokenEntity = tokenOpt.orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 토큰입니다."));
        
        // 2. 토큰으로 사용자 조회 - Dual Read Failover 적용
        Long userId = tokenEntity.getUserId();
        if (userId == null) {
            throw new IllegalArgumentException("토큰에 사용자 ID가 없습니다.");
        }
        java.util.Optional<User> userOpt = dualMasterReadService.readWithFailover(
            () -> userRepository.findById(userId),
            () -> secondaryUserDao != null ? secondaryUserDao.findById(userId) : java.util.Optional.<User>empty());
        User user = userOpt.orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        // 3. 사용자 상태 확인 (ACTIVE만 허용)
        if (!"ACTIVE".equals(user.getStatus().name())) {
            throw new IllegalArgumentException("비활성화된 계정입니다.");
        }
        
        // 4. 새 비밀번호와 확인 비밀번호 일치 검증
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
        }
        
        // 5. 새 비밀번호 강도 검증
        passwordValidator.validate(newPassword);
        
        // 6. 기존 비밀번호와 동일한지 확인
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("기존 비밀번호와 동일합니다.");
        }
        
        // 7. 새 비밀번호 암호화 및 설정
        String newPasswordHash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(newPasswordHash);
        
        // 8. 토큰 사용 처리 (재사용 방지)
        tokenEntity.setUsed(true);
        passwordResetTokenRepository.save(tokenEntity);
        
        // 9. 사용자 저장 (Dual Write)
        return updateUserWithDualWrite(user);
    }
    
    /**
     * Refresh Token으로 Access Token 갱신 (Token Rotation)
     * @param refreshToken 기존 Refresh Token
     * @return 새로운 Access Token 및 Refresh Token
     */
    public JwtService.TokenResult refreshAccessToken(String refreshToken) {
        return jwtService.refreshTokens(refreshToken);
    }
    
    /**
     * User 업데이트를 Dual Write로 처리하는 헬퍼 메서드
     * 
     * @param user 업데이트할 User 엔티티
     * @return 업데이트된 User 엔티티
     */
    private User updateUserWithDualWrite(User user) {
        // 이전 상태 저장 (보상 트랜잭션용)
        User originalState = new User();
        originalState.setId(user.getId());
        originalState.setPasswordHash(user.getPasswordHash());
        originalState.setFailedLoginCount(user.getFailedLoginCount());
        originalState.setStatus(user.getStatus());
        originalState.setLastLoginAt(user.getLastLoginAt());
        originalState.setUpdatedAt(user.getUpdatedAt());
        
        return dualMasterWriteService.writeWithDualWrite(
            // Primary: JPA Repository 사용
            () -> {
                user.setUpdatedAt(LocalDateTime.now());
                return userRepository.save(user);
            },
            
            // Secondary: JdbcTemplate 사용
            (jdbcTemplate, updatedUser) -> {
                String updateUserSql = "UPDATE users SET password_hash = :passwordHash, " +
                                     "failed_login_count = :failedLoginCount, status = :status, " +
                                     "last_login_at = :lastLoginAt, updated_at = :updatedAt " +
                                     "WHERE id = :id";
                
                Map<String, Object> updateParams = new HashMap<>();
                updateParams.put("id", updatedUser.getId());
                updateParams.put("passwordHash", updatedUser.getPasswordHash());
                updateParams.put("failedLoginCount", updatedUser.getFailedLoginCount() != null ? updatedUser.getFailedLoginCount() : 0);
                updateParams.put("status", updatedUser.getStatus() != null ? updatedUser.getStatus().name() : "ACTIVE");
                updateParams.put("lastLoginAt", updatedUser.getLastLoginAt());
                updateParams.put("updatedAt", LocalDateTime.now());
                
                secondaryNamedParameterJdbcTemplate.update(updateUserSql, updateParams);
                return null;
            },
            
            // 보상 트랜잭션: Primary에서 원래 상태로 복구
            (updatedUser) -> {
                if (updatedUser != null) {
                    user.setPasswordHash(originalState.getPasswordHash());
                    user.setFailedLoginCount(originalState.getFailedLoginCount());
                    user.setStatus(originalState.getStatus());
                    user.setLastLoginAt(originalState.getLastLoginAt());
                    user.setUpdatedAt(originalState.getUpdatedAt());
                    userRepository.save(user);
                }
                return null;
            },
            "User"  // 엔티티 타입 (Recovery Queue 발행용)
        );
    }
    
    /**
     * 로그인 결과 클래스
     */
    public static class LoginResult {
        private String accessToken;
        private String refreshToken;
        private User user;
        
        public LoginResult(String accessToken, String refreshToken, User user) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.user = user;
        }
        
        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public User getUser() { return user; }
    }

}

