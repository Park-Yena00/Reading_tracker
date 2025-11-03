package com.readingtracker.dto.ClientServerDTO.ResponseDTO;

public class LoginResponse {
    
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private UserInfo user;
    
    // Constructors
    public LoginResponse() {
    }
    
    public LoginResponse(String accessToken, String refreshToken, String tokenType, Long expiresIn, UserInfo user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.user = user;
    }
    
    // Getter methods
    public String getAccessToken() {
        return accessToken;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public String getTokenType() {
        return tokenType;
    }
    
    public Long getExpiresIn() {
        return expiresIn;
    }
    
    public UserInfo getUser() {
        return user;
    }
    
    // Setter methods
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
    
    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }
    
    public void setUser(UserInfo user) {
        this.user = user;
    }
    
    // UserInfo inner class
    public static class UserInfo {
        private Long id;
        private String loginId;
        private String email;
        private String name;
        private String role;
        
        // Constructors
        public UserInfo() {
        }
        
        public UserInfo(Long id, String loginId, String email, String name, String role) {
            this.id = id;
            this.loginId = loginId;
            this.email = email;
            this.name = name;
            this.role = role;
        }
        
        // Getter methods
        public Long getId() {
            return id;
        }
        
        public String getLoginId() {
            return loginId;
        }
        
        public String getEmail() {
            return email;
        }
        
        public String getName() {
            return name;
        }
        
        public String getRole() {
            return role;
        }
        
        // Setter methods
        public void setId(Long id) {
            this.id = id;
        }
        
        public void setLoginId(String loginId) {
            this.loginId = loginId;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public void setRole(String role) {
            this.role = role;
        }
    }
}

