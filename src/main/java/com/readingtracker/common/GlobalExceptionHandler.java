package com.readingtracker.common;

import com.readingtracker.dto.ApiResponse;
import com.readingtracker.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Validation 오류 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()));
        }
        
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setCode(ErrorCode.VALIDATION_FAILED.getCode());
        errorResponse.setMessage(ErrorCode.VALIDATION_FAILED.getMessage());
        errorResponse.setFields(fieldErrors);
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(errorResponse));
    }
    
    /**
     * IllegalArgumentException 처리 (비밀번호 검증 등)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setCode(ErrorCode.PASSWORD_VALIDATION_FAILED.getCode());
        errorResponse.setMessage(ex.getMessage());
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(errorResponse));
    }
    
    /**
     * 일반적인 RuntimeException 처리
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        // TODO: 프로덕션 환경에서는 로깅으로 변경
        ex.printStackTrace();
        
        ErrorResponse errorResponse = new ErrorResponse();
        
        // 알라딘 API 관련 오류인 경우 메시지 포함
        String message = ex.getMessage();
        if (message != null && message.contains("알라딘 API")) {
            errorResponse.setCode("ALADIN_API_ERROR");
            errorResponse.setMessage(message);
        } else {
            errorResponse.setCode(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
            // 개발 환경에서는 상세 에러 메시지 포함
            errorResponse.setMessage(message != null ? message : ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(errorResponse));
    }
    
    /**
     * 모든 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        // TODO: 프로덕션 환경에서는 로깅으로 변경
        ex.printStackTrace();
        
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setCode(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
        errorResponse.setMessage(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(errorResponse));
    }
}
