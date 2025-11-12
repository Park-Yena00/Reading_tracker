package com.readingtracker.server.dto.requestDTO;

public class LoginIdRetrievalRequest {
    
    private String email;
    private String name;
    
    // Constructors
    public LoginIdRetrievalRequest() {
    }
    
    public LoginIdRetrievalRequest(String email, String name) {
        this.email = email;
        this.name = name;
    }
    
    // Getters
    public String getEmail() {
        return email;
    }
    
    public String getName() {
        return name;
    }
    
    // Setters
    public void setEmail(String email) {
        this.email = email;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}

