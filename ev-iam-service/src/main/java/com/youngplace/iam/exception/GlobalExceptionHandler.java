package com.youngplace.iam.exception;

import com.youngplace.common.api.ApiResponse;
import com.youngplace.iam.error.AuthErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthBusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthBusinessException(AuthBusinessException ex) {
        AuthErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.fail(errorCode.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError == null ? AuthErrorCode.VALIDATION_ERROR.getDefaultMessage() : fieldError.getDefaultMessage();
        return ResponseEntity.status(AuthErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.fail(AuthErrorCode.VALIDATION_ERROR.getCode(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = AuthErrorCode.VALIDATION_ERROR.getDefaultMessage();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            message = violation.getMessage();
            break;
        }
        return ResponseEntity.status(AuthErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.fail(AuthErrorCode.VALIDATION_ERROR.getCode(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(AuthErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.fail(AuthErrorCode.VALIDATION_ERROR.getCode(), "request body is invalid"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(AuthErrorCode.INTERNAL_ERROR.getCode(), AuthErrorCode.INTERNAL_ERROR.getDefaultMessage()));
    }
}
