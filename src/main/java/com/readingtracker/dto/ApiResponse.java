package com.readingtracker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private boolean ok;
    private T data;
    private ErrorResponse error;
    
    // Constructors
    public ApiResponse() {
    }
    
    public ApiResponse(boolean ok, T data, ErrorResponse error) {
        this.ok = ok;
        this.data = data;
        this.error = error;
    }
    
    // Getter methods
    public boolean isOk() {
        return ok;
    }
    
    public T getData() {
        return data;
    }
    
    public ErrorResponse getError() {
        return error;
    }
    
    // Setter methods
    public void setOk(boolean ok) {
        this.ok = ok;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public void setError(ErrorResponse error) {
        this.error = error;
    }
    
    // Static factory methods
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setOk(true);
        response.setData(data);
        return response;
    }
    
    public static <T> ApiResponse<T> success() {
        ApiResponse<T> response = new ApiResponse<>();
        response.setOk(true);
        return response;
    }
    
    public static <T> ApiResponse<T> error(ErrorResponse error) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setOk(false);
        response.setError(error);
        return response;
    }
    
    public static <T> ApiResponse<T> error(String code, String message) {
        ErrorResponse error = new ErrorResponse();
        error.setCode(code);
        error.setMessage(message);
        
        ApiResponse<T> response = new ApiResponse<>();
        response.setOk(false);
        response.setError(error);
        return response;
    }
}

