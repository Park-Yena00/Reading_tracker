package com.readingtracker.server.common.exception;

/**
 * 모든 데이터베이스 접근 실패 시 발생하는 예외
 */
public class DatabaseUnavailableException extends RuntimeException {
    
    public DatabaseUnavailableException(String message) {
        super(message);
    }
    
    public DatabaseUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

