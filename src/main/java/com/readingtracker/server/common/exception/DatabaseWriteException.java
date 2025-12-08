package com.readingtracker.server.common.exception;

/**
 * 데이터베이스 쓰기 작업 실패 시 발생하는 예외
 */
public class DatabaseWriteException extends RuntimeException {
    
    public DatabaseWriteException(String message) {
        super(message);
    }
    
    public DatabaseWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}

