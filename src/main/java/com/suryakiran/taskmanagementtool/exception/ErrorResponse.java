package com.suryakiran.taskmanagementtool.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private int statusCode;
    private String message;
    private Map<String, String> fieldErrors;

    public ErrorResponse(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public ErrorResponse(int statusCode, String message, Map<String, String> fieldErrors) {
        this.statusCode = statusCode;
        this.message = message;
        this.fieldErrors = fieldErrors;
    }
}