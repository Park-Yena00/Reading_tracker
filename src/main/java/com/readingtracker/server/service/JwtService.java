package com.readingtracker.server.service;

import com.readingtracker.server.config.JwtConfig;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.dbms.entity.UserDevice;
import com.readingtracker.dbms.repository.UserDeviceRepository;
import com.readingtracker.dbms.repository.UserRepository;
import com.readingtracker.server.common.util.JwtUtil;
import com.readingtracker.server.service.RefreshTokenRedisService.RefreshTokenData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class JwtService {
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private RefreshTokenRedisService refreshTokenRedisService;
    
    @Autowired
    private UserDeviceRepository userDeviceRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtConfig jwtConfig;
    
    // 자기 자신 주입 (비동기 메서드 호출을 위해 필요)
    // @Lazy를 사용하여 순환 참조 방지
    @Autowired
    @Lazy
    private JwtService self;
    
    /**
     * 액세스 토큰과 리프레시 토큰 생성
     * @param user 사용자
     * @param deviceId 디바이스 ID
     * @param deviceName 디바이스 이름
     * @param platform 플랫폼
     * @return 생성된 토큰 정보것ㅇㅇ
     */
    public TokenResult generateTokens(User user, String deviceId, String deviceName, String platform) {
        // 디바이스 정보 기본값 설정
        String actualDeviceId = (deviceId != null && !deviceId.isEmpty() && !deviceId.equals("string")) ? deviceId : java.util.UUID.randomUUID().toString();
        String actualDeviceName = (deviceName != null && !deviceName.isEmpty() && !deviceName.equals("string")) ? deviceName : "Unknown Device";
        String actualPlatform = validatePlatform(platform);
        
        // 액세스 토큰 생성
        String accessToken = jwtUtil.generateAccessToken(user);
        
        // 리프레시 토큰 생성
        String refreshToken = jwtUtil.generateRefreshToken(user, actualDeviceId);
        
        // 디바이스 정보 저장/업데이트 (비동기 처리)
        // 로그인 응답 시간 단축을 위해 비동기로 처리
        self.saveOrUpdateDeviceAsync(user, actualDeviceId, actualDeviceName, actualPlatform);
        
        // 리프레시 토큰 저장 (비동기 처리)
        self.saveRefreshTokenAsync(user, actualDeviceId, refreshToken);
        
        // 디바이스 정보는 null로 반환 (비동기 처리 중이므로)
        return new TokenResult(accessToken, refreshToken, null);
    }
    
    /**
     * 리프레시 토큰으로 새 토큰 생성
     * @param refreshTokenString 리프레시 토큰
     * @param deviceId 디바이스 ID
     * @return 새 토큰 정보
     */
    public TokenResult refreshTokens(String refreshTokenString, String deviceId) {
        // 리프레시 토큰 검증
        if (!jwtUtil.isRefreshToken(refreshTokenString)) {
            throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다");
        }
        
        // 토큰에서 사용자 정보 추출
        Long userId = jwtUtil.extractUserId(refreshTokenString);
        String tokenDeviceId = jwtUtil.extractDeviceId(refreshTokenString);
        
        // 디바이스 ID 일치 확인
        if (!deviceId.equals(tokenDeviceId)) {
            throw new IllegalArgumentException("디바이스 ID가 일치하지 않습니다");
        }
        
        // 사용자 조회
        User user = new User(); // 실제로는 UserService에서 조회
        user.setId(userId);
        
        // 기존 리프레시 토큰 무효화
        revokeRefreshTokens(userId, deviceId);
        
        // 새 토큰 생성
        String newAccessToken = jwtUtil.generateAccessToken(user);
        String newRefreshToken = jwtUtil.generateRefreshToken(user, deviceId);
        
        // 새 리프레시 토큰 저장
        saveRefreshToken(user, deviceId, newRefreshToken);
        
        return new TokenResult(newAccessToken, newRefreshToken, null);
    }
    
    /**
     * 디바이스의 모든 리프레시 토큰 무효화
     * @param userId 사용자 ID
     * @param deviceId 디바이스 ID
     */
    public void revokeRefreshTokens(Long userId, String deviceId) {
        refreshTokenRedisService.revokeAllTokensByUserAndDevice(userId, deviceId);
    }
    
    /**
     * 사용자의 모든 리프레시 토큰 무효화
     * @param userId 사용자 ID
     */
    public void revokeAllRefreshTokens(Long userId) {
        refreshTokenRedisService.deleteAllTokensByUser(userId);
    }
    
    /**
     * 플랫폼 값 검증 및 기본값 설정
     * @param platform 플랫폼 문자열
     * @return 유효한 플랫폼 값 (WEB, ANDROID, IOS)
     */
    private String validatePlatform(String platform) {
        if (platform == null || platform.isEmpty() || platform.equals("string")) {
            return "WEB";
        }
        
        String upperPlatform = platform.toUpperCase();
        if (upperPlatform.equals("WEB") || upperPlatform.equals("ANDROID") || upperPlatform.equals("IOS")) {
            return upperPlatform;
        }
        
        return "WEB"; // 유효하지 않은 값은 기본값으로
    }
    
    private UserDevice saveOrUpdateDevice(User user, String deviceId, String deviceName, String platform) {
        UserDevice device = userDeviceRepository.findByUserIdAndDeviceId(user.getId(), deviceId).orElse(null);
        
        if (device == null) {
            // 새 디바이스 생성
            device = new UserDevice(user, deviceId, deviceName, UserDevice.Platform.valueOf(platform));
        } else {
            // 기존 디바이스 업데이트
            device.setDeviceName(deviceName);
            device.setLastSeenAt(LocalDateTime.now());
        }
        
        return userDeviceRepository.save(device);
    }
    
    /**
     * 디바이스 정보 저장/업데이트 (비동기 처리)
     * 로그인 응답 시간을 단축하기 위해 비동기로 처리
     */
    @Async("taskExecutor")
    @Transactional
    public void saveOrUpdateDeviceAsync(User user, String deviceId, String deviceName, String platform) {
        try {
            saveOrUpdateDevice(user, deviceId, deviceName, platform);
        } catch (Exception e) {
            // 비동기 처리 중 에러 발생 시 로깅만 수행 (로그인 응답에는 영향 없음)
            System.err.println("[JwtService] 비동기 디바이스 저장 실패: " + e.getMessage());
        }
    }
    
    private void saveRefreshToken(User user, String deviceId, String refreshToken) {
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(jwtConfig.getRefreshTokenExpiration() / 1000);
        
        // Redis에 저장
        refreshTokenRedisService.saveRefreshToken(user.getId(), deviceId, refreshToken, expiresAt);
    }
    
    /**
     * 리프레시 토큰 저장 (비동기 처리)
     * 로그인 응답 시간을 단축하기 위해 비동기로 처리
     */
    @Async("taskExecutor")
    @Transactional
    public void saveRefreshTokenAsync(User user, String deviceId, String refreshToken) {
        try {
            saveRefreshToken(user, deviceId, refreshToken);
        } catch (Exception e) {
            // 비동기 처리 중 에러 발생 시 로깅만 수행 (로그인 응답에는 영향 없음)
            System.err.println("[JwtService] 비동기 리프레시 토큰 저장 실패: " + e.getMessage());
        }
    }
    
    /**
     * Refresh Token으로 새 Access Token 및 Refresh Token 발급 (Token Rotation)
     * @param oldRefreshToken 기존 Refresh Token
     * @return 새로운 토큰 정보
     */
    public TokenResult refreshTokens(String oldRefreshToken) {
        // 1. Refresh Token 검증
        if (!jwtUtil.isRefreshToken(oldRefreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
        }
        
        // 2. 토큰에서 사용자 정보 추출
        Long userId = jwtUtil.extractUserId(oldRefreshToken);
        String deviceId = jwtUtil.extractDeviceId(oldRefreshToken);
        
        if (userId == null || deviceId == null) {
            throw new IllegalArgumentException("토큰 정보가 올바르지 않습니다.");
        }
        
        // 3. Redis에서 Refresh Token 조회
        RefreshTokenData tokenData = refreshTokenRedisService.getRefreshToken(oldRefreshToken);
        if (tokenData == null) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
        }
        
        // 4. 탈취 감지: 이미 사용된 토큰(revoked=true)이 다시 사용되는 경우
        if (Boolean.TRUE.equals(tokenData.getRevoked())) {
            // 보안 위협! 해당 사용자의 모든 Refresh Token 폐기
            refreshTokenRedisService.revokeAllTokensByUserAndDevice(userId, deviceId);
            throw new IllegalArgumentException("토큰 탈취가 감지되었습니다. 모든 세션이 종료되었습니다. 다시 로그인해주세요.");
        }
        
        // 5. 만료 확인
        if (tokenData.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("만료된 Refresh Token입니다.");
        }
        
        // 6. 사용자 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (!"ACTIVE".equals(user.getStatus().name())) {
            throw new IllegalArgumentException("비활성화된 계정입니다.");
        }
        
        // 7. 기존 Refresh Token 폐기 (revoked=true)
        refreshTokenRedisService.revokeRefreshToken(oldRefreshToken);
        
        // 8. 새 Access Token 생성
        String newAccessToken = jwtUtil.generateAccessToken(user);
        
        // 9. 새 Refresh Token 생성 (Token Rotation)
        String newRefreshToken = jwtUtil.generateRefreshToken(user, deviceId);
        
        // 10. 새 Refresh Token DB 저장
        saveRefreshToken(user, deviceId, newRefreshToken);
        
        // 11. 디바이스 정보 업데이트 (last_seen_at)
        UserDevice device = userDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
            .orElseThrow(() -> new IllegalArgumentException("디바이스 정보를 찾을 수 없습니다."));
        device.setLastSeenAt(LocalDateTime.now());
        userDeviceRepository.save(device);
        
        return new TokenResult(newAccessToken, newRefreshToken, device);
    }
    
    /**
     * 토큰 결과 클래스
     */
    public static class TokenResult {
        private String accessToken;
        private String refreshToken;
        private UserDevice device;
        
        public TokenResult(String accessToken, String refreshToken, UserDevice device) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.device = device;
        }
        
        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public UserDevice getDevice() { return device; }
    }
}

