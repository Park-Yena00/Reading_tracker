package com.readingtracker.dto.ClientServerDTO.RequestDTO;

public class RegistrationRequest {
    
    private String loginId;
    private String email;
    private String name;
    private String password;
    
    // Constructors
    public RegistrationRequest() {
    }
    
    public RegistrationRequest(String loginId, String email, String name, String password) {
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

