package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.exception.ErrorResponse;
import com.suryakiran.taskmanagementtool.exception.GlobalExceptionHandler;
import com.suryakiran.taskmanagementtool.exception.NoTasksFoundException;
import com.suryakiran.taskmanagementtool.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import org.springframework.security.core.AuthenticationException;
import com.suryakiran.taskmanagementtool.exception.TokenValidationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        globalExceptionHandler = new GlobalExceptionHandler();
        webRequest = mock(WebRequest.class);
    }

    @Test
    void testHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Resource not found");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleResourceNotFoundException(ex, webRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Resource not found", response.getBody().getMessage());
    }

    @Test
    void testHandleNoTasksFoundException() {
        NoTasksFoundException ex = new NoTasksFoundException("No tasks found");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleNoTasksFoundException(ex, webRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("No tasks found", response.getBody().getMessage());
    }

    @Test
    void testHandleGlobalException() {
        Exception ex = new Exception("An unexpected error occurred");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGlobalException(ex, webRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
    }

    @Test
    void testHandleAuthenticationException() {
        AuthenticationException ex = new AuthenticationException("Authentication error") {};
        ResponseEntity<String> response = globalExceptionHandler.handleAuthenticationException(ex, webRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Authentication error", response.getBody());
    }

    @Test
    void testHandleTokenValidationException() {
        TokenValidationException ex = new TokenValidationException("Token validation error");
        ResponseEntity<String> response = globalExceptionHandler.handleTokenValidationException(ex, webRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Token validation error", response.getBody());
    }
}