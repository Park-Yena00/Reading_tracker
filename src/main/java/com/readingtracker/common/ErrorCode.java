package com.readingtracker.common;

public enum ErrorCode {
    
    // 인증 관련
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다"),
    INVALID_PASSWORD("INVALID_PASSWORD", "비밀번호가 올바르지 않습니다"),
    USER_NOT_FOUND("USER_NOT_FOUND", "사용자를 찾을 수 없습니다"),
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS", "이미 존재하는 사용자입니다"),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", "계정이 잠겨있습니다"),
    ACCOUNT_DELETED("ACCOUNT_DELETED", "삭제된 계정입니다"),
    
    // 중복 확인
    DUPLICATE_LOGIN_ID("DUPLICATE_LOGIN_ID", "이미 사용 중인 로그인 ID입니다"),
    DUPLICATE_EMAIL("DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다"),
    
    // 토큰 관련
    INVALID_TOKEN("INVALID_TOKEN", "유효하지 않은 토큰입니다"),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "만료된 토큰입니다"),
    TOKEN_REVOKED("TOKEN_REVOKED", "취소된 토큰입니다"),
    TOKEN_THEFT_DETECTED("TOKEN_THEFT_DETECTED", "토큰 탈취가 감지되었습니다. 모든 세션이 종료되었습니다. 다시 로그인해주세요."),
    REFRESH_TOKEN_NOT_FOUND("REFRESH_TOKEN_NOT_FOUND", "리프레시 토큰을 찾을 수 없습니다"),
    
    // 비밀번호 관련
    WEAK_PASSWORD("WEAK_PASSWORD", "비밀번호가 너무 약합니다"),
    PASSWORD_VALIDATION_FAILED("PASSWORD_VALIDATION_FAILED", "비밀번호 규칙에 맞지 않습니다"),
    
    // 디바이스 관련
    DEVICE_NOT_FOUND("DEVICE_NOT_FOUND", "디바이스를 찾을 수 없습니다"),
    INVALID_DEVICE("INVALID_DEVICE", "유효하지 않은 디바이스입니다"),
    
    // 일반 오류
    VALIDATION_FAILED("VALIDATION_FAILED", "입력값이 올바르지 않습니다"),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다"),
    UNAUTHORIZED("UNAUTHORIZED", "인증이 필요합니다"),
    FORBIDDEN("FORBIDDEN", "접근 권한이 없습니다"),
    NOT_FOUND("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다"),
    BAD_REQUEST("BAD_REQUEST", "잘못된 요청입니다");
    
    private final String code;
    private final String message;
    
    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
}
