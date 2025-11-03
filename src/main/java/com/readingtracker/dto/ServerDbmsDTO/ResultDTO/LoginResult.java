package com.readingtracker.dto.ServerDbmsDTO.ResultDTO;

/**
 * 로그인 결과 데이터 전송 객체 (Service Layer)
 */
public class LoginResult {
    
    private String accessToken;
    private String refreshToken;
    private UserResult user;
    
    // Constructors
    public LoginResult() {
    }
    
    public LoginResult(String accessToken, String refreshToken, UserResult user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.user = user;
    }
    
    // Getter methods
    public String getAccessToken() {
        return accessToken;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public UserResult getUser() {
        return user;
    }
    
    // Setter methods
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    public void setUser(UserResult user) {
        this.user = user;
    }
}
