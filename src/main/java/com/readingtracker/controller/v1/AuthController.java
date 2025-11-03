package com.readingtracker.controller.v1;

import com.readingtracker.config.JwtConfig;
import com.readingtracker.dto.ApiResponse;
import com.readingtracker.dto.ClientServerDTO.RequestDTO.LoginIdRetrievalRequest;
import com.readingtracker.dto.ClientServerDTO.ResponseDTO.LoginIdRetrievalResponse;
import com.readingtracker.dto.ClientServerDTO.RequestDTO.LoginRequest;
import com.readingtracker.dto.ClientServerDTO.RequestDTO.RefreshTokenRequest;
import com.readingtracker.dto.ClientServerDTO.ResponseDTO.RefreshTokenResponse;
import com.readingtracker.dto.ClientServerDTO.RequestDTO.RegistrationRequest;
import com.readingtracker.dto.ClientServerDTO.RequestDTO.PasswordResetRequest;
import com.readingtracker.dto.ClientServerDTO.ResponseDTO.PasswordResetResponse;
import com.readingtracker.dto.ClientServerDTO.RequestDTO.AccountVerificationRequest;
import com.readingtracker.dto.ClientServerDTO.ResponseDTO.AccountVerificationResponse;
import com.readingtracker.dto.ServerDbmsDTO.CommandDto.UserCreationCommand;
import com.readingtracker.dto.ServerDbmsDTO.CommandDto.LoginIdRetrievalCommandDTO;
import com.readingtracker.dto.ServerDbmsDTO.CommandDto.LoginCommand;
import com.readingtracker.dto.ServerDbmsDTO.CommandDto.PasswordResetCommand;
import com.readingtracker.dto.ServerDbmsDTO.CommandDto.AccountVerificationCommand;
import com.readingtracker.dto.ServerDbmsDTO.ResultDTO.UserResult;
import com.readingtracker.entity.User;
import com.readingtracker.service.AuthService;
import com.readingtracker.service.JwtService;
import com.readingtracker.service.UserService;
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
        
        // Client Request DTO를 Command DTO로 변환
        UserCreationCommand command = new UserCreationCommand(
            request.getLoginId(),
            request.getEmail(),
            request.getName(),
            request.getPassword()
        );
        
        UserResult userResult = authService.register(command);
        RegisterResponse response = new RegisterResponse(userResult);
        
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
        
        public RegisterResponse(com.readingtracker.dto.ServerDbmsDTO.ResultDTO.UserResult user) {
            this.id = user.getId();
            this.loginId = user.getLoginId();
            this.email = user.getEmail();
            this.name = user.getName();
            this.role = user.getRole();
            this.status = user.getStatus();
        }
        
        public Long getId() { return id; }
        public String getLoginId() { return loginId; }
        public String getEmail() { return email; }
        public String getName() { return name; }
        public User.Role getRole() { return role; }
        public User.Status getStatus() { return status; }
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
        
        // Client Request DTO를 Command DTO로 변환
        LoginCommand command = new LoginCommand(
            request.getLoginId(),
            request.getPassword()
        );
        
        AuthService.LoginResult result = authService.login(command);
        
        LoginResponse response = new LoginResponse(
            result.getAccessToken(),
            result.getRefreshToken(),
            result.getUser()
        );
        
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
        
        // Client Request DTO를 Command DTO로 변환
        LoginIdRetrievalCommandDTO command = new LoginIdRetrievalCommandDTO(
            request.getEmail(),
            request.getName()
        );
        
        com.readingtracker.dto.ServerDbmsDTO.ResultDTO.LoginIdRetrievalResult result = authService.findLoginIdByEmailAndName(command);
        LoginIdRetrievalResponse response = new LoginIdRetrievalResponse(result.getLoginId(), result.getEmail());
        
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
        
        // Client Request DTO를 Command DTO로 변환
        AccountVerificationCommand command = new AccountVerificationCommand(
            request.getLoginId(),
            request.getEmail()
        );
        
        String resetToken = authService.verifyAccountForPasswordReset(command);
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
        
        // Client Request DTO를 Command DTO로 변환
        PasswordResetCommand command = new PasswordResetCommand(
            request.getResetToken(),
            request.getNewPassword(),
            request.getConfirmPassword()
        );
        
        com.readingtracker.dto.ServerDbmsDTO.ResultDTO.UserResult userResult = authService.resetPassword(command);
        PasswordResetResponse response = new PasswordResetResponse(
            "비밀번호가 성공적으로 변경되었습니다.",
            userResult.getLoginId()
        );
        
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
    
    /**
     * 로그인 응답 클래스
     */
    public static class LoginResponse {
        private String accessToken;
        private String refreshToken;
        private UserInfo user;
        
        public LoginResponse(String accessToken, String refreshToken, com.readingtracker.dto.ServerDbmsDTO.ResultDTO.UserResult user) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.user = new UserInfo(user);
        }
        
        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public UserInfo getUser() { return user; }
        
        public static class UserInfo {
            private Long id;
            private String loginId;
            private String email;
            private String name;
            private User.Role role;
            private User.Status status;
            
            public UserInfo(com.readingtracker.dto.ServerDbmsDTO.ResultDTO.UserResult user) {
                this.id = user.getId();
                this.loginId = user.getLoginId();
                this.email = user.getEmail();
                this.name = user.getName();
                this.role = user.getRole();
                this.status = user.getStatus();
            }
            
            public Long getId() { return id; }
            public String getLoginId() { return loginId; }
            public String getEmail() { return email; }
            public String getName() { return name; }
            public User.Role getRole() { return role; }
            public User.Status getStatus() { return status; }
        }
    }
}
