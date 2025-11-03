package com.readingtracker.dto.ServerDbmsDTO.CommandDto;

/**
 * 아이디 찾기 명령 데이터 전송 객체 (Service Layer)
 */
public class LoginIdRetrievalCommandDTO {
    
    private String email;
    private String name;
    
    // Constructors
    public LoginIdRetrievalCommandDTO() {
    }
    
    public LoginIdRetrievalCommandDTO(String email, String name) {
        this.email = email;
        this.name = name;
    }
    
    // Getter methods
    public String getEmail() {
        return email;
    }
    
    public String getName() {
        return name;
    }
    
    // Setter methods
    public void setEmail(String email) {
        this.email = email;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}
