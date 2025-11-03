package com.readingtracker.dto.ClientServerDTO.RequestDTO;

/**
 * 비밀번호 재설정 요청 DTO (Step 2)
 */
public class PasswordResetRequest {
    
    private String resetToken;
    private String newPassword;
    private String confirmPassword;
    
    // Constructors
    public PasswordResetRequest() {
    }
    
    public PasswordResetRequest(String resetToken, String newPassword, String confirmPassword) {
        this.resetToken = resetToken;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }
    
    // Getters
    public String getResetToken() {
        return resetToken;
    }
    
    public String getNewPassword() {
        return newPassword;
    }
    
    public String getConfirmPassword() {
        return confirmPassword;
    }
    
    // Setters
    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }
    
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
    
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}

