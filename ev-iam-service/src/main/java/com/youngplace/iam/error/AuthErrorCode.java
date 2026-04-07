package com.youngplace.iam.error;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode {
    VALIDATION_ERROR(40001, HttpStatus.BAD_REQUEST, "request validation failed"),
    CAPTCHA_DISABLED(40011, HttpStatus.BAD_REQUEST, "captcha is disabled"),
    CAPTCHA_INVALID(40111, HttpStatus.UNAUTHORIZED, "captcha is invalid or expired"),
    INVALID_CREDENTIALS(40121, HttpStatus.UNAUTHORIZED, "username or password is invalid"),
    MISSING_AUTHORIZATION(40131, HttpStatus.UNAUTHORIZED, "missing Authorization Bearer token"),
    TOKEN_INVALID(40132, HttpStatus.UNAUTHORIZED, "invalid or expired token"),
    REFRESH_TOKEN_INVALID(40141, HttpStatus.UNAUTHORIZED, "refresh token is invalid"),
    ACCOUNT_DISABLED(40311, HttpStatus.FORBIDDEN, "account is disabled"),
    ACCESS_DENIED(40321, HttpStatus.FORBIDDEN, "access denied"),
    ACCOUNT_LOCKED(42311, HttpStatus.LOCKED, "account is locked"),
    INTERNAL_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR, "internal server error");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    AuthErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
