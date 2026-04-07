package com.youngplace.iam.exception;

import com.youngplace.iam.error.AuthErrorCode;

public class AuthBusinessException extends RuntimeException {

    private final AuthErrorCode errorCode;

    public AuthBusinessException(AuthErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public AuthBusinessException(AuthErrorCode errorCode, String message) {
        super(message == null ? errorCode.getDefaultMessage() : message);
        this.errorCode = errorCode;
    }

    public AuthErrorCode getErrorCode() {
        return errorCode;
    }
}
