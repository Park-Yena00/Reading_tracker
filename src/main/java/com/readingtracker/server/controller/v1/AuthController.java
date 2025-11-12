package com.readingtracker.server.controller.v1;

import com.readingtracker.server.config.JwtConfig;
import com.readingtracker.server.dto.ApiResponse;
import com.readingtracker.server.dto.requestDTO.AccountVerificationRequest;
import com.readingtracker.server.dto.requestDTO.LoginIdRetrievalRequest;
import com.readingtracker.server.dto.requestDTO.LoginRequest;
import com.readingtracker.server.dto.requestDTO.PasswordResetRequest;
import com.readingtracker.server.dto.requestDTO.RefreshTokenRequest;
import com.readingtracker.server.dto.requestDTO.RegistrationRequest;
import com.readingtracker.server.dto.responseDTO.AccountVerificationResponse;
import com.readingtracker.server.dto.responseDTO.LoginIdRetrievalResponse;
import com.readingtracker.server.dto.responseDTO.LoginResponse;
import com.readingtracker.server.dto.responseDTO.PasswordResetResponse;
import com.readingtracker.server.dto.responseDTO.RefreshTokenResponse;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.server.mapper.AuthMapper;
import com.readingtracker.server.service.AuthService;
import com.readingtracker.server.service.JwtService;
import com.readingtracker.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "인증", description = "인증 관련 API")
public class AuthController extends BaseV1Controller {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private AuthMapper authMapper;
    
    @Autowired
    private JwtConfig jwtConfig;
    
    /**
     * 로그인 ID 중복 확인
     * GET /api/v1/users/duplicate/loginId?value=...
     */
    @GetMapping("/users/duplicate/loginId")
    @Operation(summary = "로그인 ID 중복 확인", description = "로그인 ID의 중복 여부를 확인합니다")
    public ApiResponse<Boolean> checkLoginIdDuplicate(
            @Parameter(description = "확인할 로그인 ID", required = true)
            @RequestParam("value") String loginId) {
        
        boolean isDuplicate = userService.isLoginIdDuplicate(loginId);
        
        return ApiResponse.success(isDuplicate);
    }
    
    /**
     * 이메일 중복 확인
     * GET /api/v1/users/duplicate/email?value=...
     */
    @GetMapping("/users/duplicate/email")
    @Operation(summary = "이메일 중복 확인", description = "이메일의 중복 여부를 확인합니다")
    public ApiResponse<Boolean> checkEmailDuplicate(
            @Parameter(description = "확인할 이메일", required = true)
            @RequestParam("value") String email) {
        
        boolean isDuplicate = userService.isEmailDuplicate(email);
        
        return ApiResponse.success(isDuplicate);
    }
    
    /**
     * 회원가입
     * POST /api/v1/auth/signup
     */
    @PostMapping("/auth/signup")
    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다")
    public ApiResponse<RegisterResponse> signup(
            @Parameter(description = "회원가입 정보", required = true)
            @RequestBody RegistrationRequest request) {
        
        // Service 호출 (RequestDTO → Entity)
        User user = authService.register(request);
        
        // Entity → ResponseDTO 변환 (Mapper 사용)
        RegisterResponse response = authMapper.toRegisterResponse(user);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 회원가입 응답 클래스
     */
    public static class RegisterResponse {
        private Long id;
        private String loginId;
        private String email;
        private String name;
        private User.Role role;
        private User.Status status;
        
        // 기본 생성자 (MapStruct를 위해 필요)
        public RegisterResponse() {
        }
        
        public RegisterResponse(Long id, String loginId, String email, String name, User.Role role, User.Status status) {
            this.id = id;
            this.loginId = loginId;
            this.email = email;
            this.name = name;
            this.role = role;
            this.status = status;
        }
        
        // Getters
        public Long getId() { return id; }
        public String getLoginId() { return loginId; }
        public String getEmail() { return email; }
        public String getName() { return name; }
        public User.Role getRole() { return role; }
        public User.Status getStatus() { return status; }
        
        // Setters (MapStruct를 위해 필요)
        public void setId(Long id) { this.id = id; }
        public void setLoginId(String loginId) { this.loginId = loginId; }
        public void setEmail(String email) { this.email = email; }
        public void setName(String name) { this.name = name; }
        public void setRole(User.Role role) { this.role = role; }
        public void setStatus(User.Status status) { this.status = status; }
    }
    
    /**
     * 로그인
     * POST /api/v1/auth/login
     */
    @PostMapping("/auth/login")
    @Operation(summary = "로그인", description = "사용자 로그인을 처리합니다")
    public ApiResponse<LoginResponse> login(
            @Parameter(description = "로그인 정보", required = true)
            @RequestBody LoginRequest request) {
        
        // Service 호출 (RequestDTO → Entity)
        AuthService.LoginResult result = authService.login(request);
        
        // Entity → ResponseDTO 변환 (Mapper 사용)
        LoginResponse.UserInfo userInfo = authMapper.toLoginUserInfo(result.getUser());
        
        LoginResponse response = new LoginResponse();
        response.setAccessToken(result.getAccessToken());
        response.setRefreshToken(result.getRefreshToken());
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtConfig.getAccessTokenExpiration());
        response.setUser(userInfo);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 아이디 찾기 (이메일 + 이름으로 로그인 ID 찾기)
     * POST /api/v1/auth/find-login-id
     */
    @PostMapping("/auth/find-login-id")
    @Operation(summary = "아이디 찾기", description = "이메일과 이름으로 로그인 ID를 찾습니다")
    public ApiResponse<LoginIdRetrievalResponse> findLoginId(
            @Parameter(description = "아이디 찾기 정보 (이메일, 이름)", required = true)
            @RequestBody LoginIdRetrievalRequest request) {
        
        // Service 호출 (RequestDTO → Entity)
        User user = authService.findLoginIdByEmailAndName(request);
        
        // Entity → ResponseDTO 변환 (Mapper 사용)
        LoginIdRetrievalResponse response = authMapper.toLoginIdRetrievalResponse(user);
        
        return ApiResponse.success(response);
    }
    
    /**
     * Step 1: 계정 확인 및 재설정 토큰 발급 (비밀번호 재설정 - 1단계)
     * POST /api/v1/auth/verify-account
     */
    @PostMapping("/auth/verify-account")
    @Operation(summary = "계정 확인 및 토큰 발급", description = "비밀번호 재설정을 위한 계정 확인 후 임시 토큰 발급 (Step 1)")
    public ApiResponse<AccountVerificationResponse> verifyAccount(
            @Parameter(description = "계정 확인 정보 (loginId, email)", required = true)
            @RequestBody AccountVerificationRequest request) {
        
        // Service 호출 (RequestDTO → String 토큰)
        String resetToken = authService.verifyAccountForPasswordReset(request);
        
        // ResponseDTO 생성
        AccountVerificationResponse response = new AccountVerificationResponse(
            "계정이 확인되었습니다. 새 비밀번호를 입력해주세요.",
            resetToken
        );
        
        return ApiResponse.success(response);
    }
    
    /**
     * Step 2: 비밀번호 변경 (비밀번호 재설정 - 2단계)
     * POST /api/v1/auth/reset-password
     */
    @PostMapping("/auth/reset-password")
    @Operation(summary = "비밀번호 변경", description = "토큰으로 계정 확인 후 새 비밀번호로 변경 (Step 2)")
    public ApiResponse<PasswordResetResponse> resetPassword(
            @Parameter(description = "비밀번호 재설정 정보 (resetToken, newPassword, confirmPassword)", required = true)
            @RequestBody PasswordResetRequest request) {
        
        // Service 호출 (RequestDTO → Entity)
        User user = authService.resetPassword(request);
        
        // Entity → ResponseDTO 변환 (Mapper 사용)
        PasswordResetResponse response = authMapper.toPasswordResetResponse(user);
        
        return ApiResponse.success(response);
    }
    
    /**
     * Refresh Token으로 Access Token 갱신
     * POST /api/v1/auth/refresh
     */
    @PostMapping("/auth/refresh")
    @Operation(summary = "토큰 갱신", description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급받습니다 (Token Rotation)")
    public ApiResponse<RefreshTokenResponse> refreshToken(
            @Parameter(description = "Refresh Token", required = true)
            @RequestBody RefreshTokenRequest request) {
        
        JwtService.TokenResult result = authService.refreshAccessToken(request.getRefreshToken());
        
        RefreshTokenResponse response = new RefreshTokenResponse(
            result.getAccessToken(),
            result.getRefreshToken(),
            "Bearer",
            jwtConfig.getAccessTokenExpiration()
        );
        
        return ApiResponse.success(response);
    }
    
}
