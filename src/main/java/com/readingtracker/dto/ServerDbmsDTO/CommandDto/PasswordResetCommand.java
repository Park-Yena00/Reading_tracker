package com.readingtracker.dto.ServerDbmsDTO.CommandDto;

/**
 * 비밀번호 재설정 명령 데이터 전송 객체 (Service Layer)
 */
public class PasswordResetCommand {
    
    private String resetToken;
    private String newPassword;
    private String confirmPassword;
    
    // Constructors
    public PasswordResetCommand() {
    }
    
    public PasswordResetCommand(String resetToken, String newPassword, String confirmPassword) {
        this.resetToken = resetToken;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }
    
    // Getter methods
    public String getResetToken() {
        return resetToken;
    }
    
    public String getNewPassword() {
        return newPassword;
    }
    
    public String getConfirmPassword() {
        return confirmPassword;
    }
    
    // Setter methods
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
