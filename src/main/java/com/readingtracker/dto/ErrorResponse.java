package com.readingtracker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    private String code;
    private String message;
    private List<FieldError> fields;
    
    // Constructors
    public ErrorResponse() {
    }
    
    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public ErrorResponse(String code, String message, List<FieldError> fields) {
        this.code = code;
        this.message = message;
        this.fields = fields;
    }
    
    // Getter methods
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public List<FieldError> getFields() {
        return fields;
    }
    
    // Setter methods
    public void setCode(String code) {
        this.code = code;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public void setFields(List<FieldError> fields) {
        this.fields = fields;
    }
    
    // FieldError inner class
    public static class FieldError {
        private String name;
        private String reason;
        
        // Constructors
        public FieldError() {
        }
        
        public FieldError(String name, String reason) {
            this.name = name;
            this.reason = reason;
        }
        
        // Getter methods
        public String getName() {
            return name;
        }
        
        public String getReason() {
            return reason;
        }
        
        // Setter methods
        public void setName(String name) {
            this.name = name;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}

