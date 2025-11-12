package com.readingtracker.server.controller.v1;

import com.readingtracker.server.dto.ApiResponse;
import com.readingtracker.dbms.entity.User;
import com.readingtracker.server.mapper.UserMapper;
import com.readingtracker.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "사용자", description = "사용자 관련 API")
public class UserController extends BaseV1Controller {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserMapper userMapper;
    
    /**
     * 내 프로필 조회 (인증 필요)
     * GET /api/v1/users/me
     */
    @GetMapping("/users/me")
    @Operation(
        summary = "내 프로필 조회", 
        description = "현재 로그인한 사용자의 프로필 정보를 조회합니다",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    public ApiResponse<UserProfileResponse> getMyProfile() {
        // Spring Security Context에서 현재 인증된 사용자 ID 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String loginId = (String) authentication.getPrincipal();
        
        // 사용자 조회
        User user = userService.findActiveUserByLoginId(loginId);
        
        if (user == null) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }
        
        // Entity → ResponseDTO 변환 (Mapper 사용)
        UserProfileResponse response = userMapper.toUserProfileResponse(user);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 사용자 프로필 응답 DTO
     */
    public static class UserProfileResponse {
        private Long id;
        private String loginId;
        private String email;
        private String name;
        private String role;
        private String status;
        
        // 기본 생성자 (MapStruct를 위해 필요)
        public UserProfileResponse() {
        }
        
        public UserProfileResponse(User user) {
            this.id = user.getId();
            this.loginId = user.getLoginId();
            this.email = user.getEmail();
            this.name = user.getName();
            this.role = user.getRole().name();
            this.status = user.getStatus().name();
        }
        
        // Getters
        public Long getId() { return id; }
        public String getLoginId() { return loginId; }
        public String getEmail() { return email; }
        public String getName() { return name; }
        public String getRole() { return role; }
        public String getStatus() { return status; }
        
        // Setters (MapStruct를 위해 필요)
        public void setId(Long id) { this.id = id; }
        public void setLoginId(String loginId) { this.loginId = loginId; }
        public void setEmail(String email) { this.email = email; }
        public void setName(String name) { this.name = name; }
        public void setRole(String role) { this.role = role; }
        public void setStatus(String status) { this.status = status; }
    }
}

