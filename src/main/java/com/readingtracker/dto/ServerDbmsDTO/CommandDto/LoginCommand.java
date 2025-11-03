package com.readingtracker.dto.ServerDbmsDTO.CommandDto;

/**
 * 로그인 명령 데이터 전송 객체 (Controller → Service)
 */
public class LoginCommand {
    
    private String loginId;
    private String password;
    
    // Constructors
    public LoginCommand() {
    }
    
    public LoginCommand(String loginId, String password) {
        this.loginId = loginId;
        this.password = password;
    }
    
    // Getter methods
    public String getLoginId() {
        return loginId;
    }
    
    public String getPassword() {
        return password;
    }
    
    // Setter methods
    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}
