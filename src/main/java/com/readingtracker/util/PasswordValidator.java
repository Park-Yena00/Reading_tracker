package com.readingtracker.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class PasswordValidator {
    
    private static final int MIN_LENGTH = 8;
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");
    
    public void validate(String password) {
        List<String> errors = new ArrayList<>();
        
        // 길이 검증
        if (password == null || password.length() < MIN_LENGTH) {
            errors.add("비밀번호는 최소 8자 이상이어야 합니다");
        }
        
        // 특수문자 검증
        if (password != null && !SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            errors.add("비밀번호는 최소 1개의 특수문자를 포함해야 합니다");
        }
        
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
    }
    
    public boolean isValid(String password) {
        try {
            validate(password);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    public List<String> getValidationErrors(String password) {
        List<String> errors = new ArrayList<>();
        
        if (password == null || password.length() < MIN_LENGTH) {
            errors.add("비밀번호는 최소 8자 이상이어야 합니다");
        }
        
        if (password != null && !SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            errors.add("비밀번호는 최소 1개의 특수문자를 포함해야 합니다");
        }
        
        return errors;
    }
}



