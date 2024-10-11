package com.suryakiran.taskmanagementtool.exception;

public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}