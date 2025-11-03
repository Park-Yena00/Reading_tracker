package com.readingtracker.dto.ServerDbmsDTO.CommandDto;

/**
 * 사용자 생성 명령 데이터 전송 객체 (Controller → Service)
 */
public class UserCreationCommand {
    
    private String loginId;
    private String email;
    private String name;
    private String password;
    
    // Constructors
    public UserCreationCommand() {
    }
    
    public UserCreationCommand(String loginId, String email, String name, String password) {
        this.loginId = loginId;
        this.email = email;
        this.name = name;
        this.password = password;
    }
    
    // Getter methods
    public String getLoginId() {
        return loginId;
    }
    
    public String getEmail() {
        return email;
    }
    
    public String getName() {
        return name;
    }
    
    public String getPassword() {
        return password;
    }
    
    // Setter methods
    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}
