package com.readingtracker.service.validation;

import com.readingtracker.dto.ServerDbmsDTO.CommandDto.UserCreationCommand;
import com.readingtracker.dto.ServerDbmsDTO.CommandDto.LoginIdRetrievalCommandDTO;
import com.readingtracker.dto.ServerDbmsDTO.CommandDto.PasswordResetCommand;
import com.readingtracker.dto.ServerDbmsDTO.CommandDto.AccountVerificationCommand;
import org.springframework.stereotype.Service;

/**
 * 사용자 관련 검증 서비스
 */
@Service
public class UserValidationService {
    
    /**
     * 사용자 생성 검증
     */
    public void validateUserCreation(UserCreationCommand command) {
        validateLoginId(command.getLoginId());
        validateEmail(command.getEmail());
        validateName(command.getName());
        validatePassword(command.getPassword());
    }
    
    /**
     * 로그인 ID 검증
     */
    public void validateLoginId(String loginId) {
        if (loginId == null || loginId.trim().isEmpty()) {
            throw new IllegalArgumentException("로그인 ID는 필수입니다");
        }
        if (loginId.length() < 3 || loginId.length() > 50) {
            throw new IllegalArgumentException("로그인 ID는 3-50자 사이여야 합니다");
        }
    }
    
    /**
     * 이메일 검증
     */
    public void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수입니다");
        }
        if (!isValidEmailFormat(email)) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다");
        }
    }
    
    /**
     * 이름 검증
     */
    public void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("이름은 필수입니다");
        }
        if (name.length() < 2 || name.length() > 100) {
            throw new IllegalArgumentException("이름은 2-100자 사이여야 합니다");
        }
    }
    
    /**
     * 비밀번호 검증
     */
    public void validatePassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다");
        }
        if (password.length() < 8 || password.length() > 100) {
            throw new IllegalArgumentException("비밀번호는 8-100자 사이여야 합니다");
        }
    }
    
    /**
     * 아이디 찾기 검증
     */
    public void validateFindLoginId(LoginIdRetrievalCommandDTO command) {
        validateEmail(command.getEmail());
        validateName(command.getName());
    }
    
    /**
     * 비밀번호 재설정 검증
     */
    public void validateResetPassword(PasswordResetCommand command) {
        if (command.getResetToken() == null || command.getResetToken().trim().isEmpty()) {
            throw new IllegalArgumentException("재설정 토큰은 필수입니다");
        }
        validatePassword(command.getNewPassword());
        if (command.getConfirmPassword() == null || command.getConfirmPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("비밀번호 확인은 필수입니다");
        }
        if (!command.getNewPassword().equals(command.getConfirmPassword())) {
            throw new IllegalArgumentException("새 비밀번호와 확인 비밀번호가 일치하지 않습니다");
        }
    }
    
    /**
     * 계정 확인 검증
     */
    public void validateVerifyAccount(AccountVerificationCommand command) {
        validateLoginId(command.getLoginId());
        validateEmail(command.getEmail());
    }
    
    /**
     * 이메일 형식 검증
     */
    private boolean isValidEmailFormat(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
    }
}
