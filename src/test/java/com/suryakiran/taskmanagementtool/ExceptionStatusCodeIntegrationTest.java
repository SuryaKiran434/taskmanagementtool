package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.controller.UserController;
import com.suryakiran.taskmanagementtool.exception.AuthenticationFailedException;
import com.suryakiran.taskmanagementtool.exception.AuthenticationRequiredException;
import com.suryakiran.taskmanagementtool.exception.GlobalExceptionHandler;
import com.suryakiran.taskmanagementtool.exception.NoTasksFoundException;
import com.suryakiran.taskmanagementtool.exception.ResourceNotFoundException;
import com.suryakiran.taskmanagementtool.exception.TaskNotFoundException;
import com.suryakiran.taskmanagementtool.exception.TokenValidationException;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.service.PasswordResetService;
import com.suryakiran.taskmanagementtool.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts the HTTP status an exception thrown out of a controller actually produces,
 * end to end through Spring MVC's exception resolution and GlobalExceptionHandler.
 *
 * <p>TaskNotFoundException, UserNotFoundException, AuthenticationFailedException and
 * AuthenticationRequiredException all extended plain RuntimeException with no
 * {@code @ExceptionHandler}, so they fell through to the {@code Exception} catch-all and
 * were served as 500 despite what their names promise. Unit-testing the advice methods
 * directly cannot catch that class of bug, because a handler that does not exist has no
 * method to call — only routing a real request through the resolver proves the mapping.</p>
 *
 * <p>These use standalone MockMvc rather than a full {@code @SpringBootTest}: the test
 * profile sets
 * {@code spring.autoconfigure.exclude=...SecurityAutoConfiguration}, so in a full context
 * the security filter chain is absent and a 401 could never be attributed to the advice
 * with any confidence. Standalone setup wires the real
 * {@code ExceptionHandlerExceptionResolver} with the real advice and no security at all,
 * so the status observed is unambiguously the one the advice produced.</p>
 */
class ExceptionStatusCodeIntegrationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- The four exceptions this change fixes ---

    @Test
    void taskNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/throw/task-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.message").value("Task not found"));
    }

    @Test
    void userNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/throw/user-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void authenticationFailedReturns401() throws Exception {
        mockMvc.perform(get("/throw/authentication-failed"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Incorrect email or password"));
    }

    @Test
    void authenticationRequiredReturns401() throws Exception {
        mockMvc.perform(get("/throw/authentication-required"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Authentication required"));
    }

    // --- Regression cover for the mappings that already worked ---

    @Test
    void resourceNotFoundStillReturns404() throws Exception {
        mockMvc.perform(get("/throw/resource-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found"));
    }

    @Test
    void noTasksFoundStillReturns404() throws Exception {
        mockMvc.perform(get("/throw/no-tasks-found"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenValidationStillReturns401() throws Exception {
        mockMvc.perform(get("/throw/token-validation"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void illegalArgumentStillReturns400() throws Exception {
        mockMvc.perform(get("/throw/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    /**
     * The catch-all must keep catching. Adding specific handlers must not make a genuinely
     * unexpected failure stop being a 500.
     */
    @Test
    void unexpectedExceptionStillReturns500() throws Exception {
        mockMvc.perform(get("/throw/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.statusCode").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    /**
     * UserController catches UserNotFoundException itself on /forgot-password and answers
     * 200 on purpose, so an attacker cannot probe which addresses are registered. A
     * controller-local catch takes precedence over the advice, but assert it explicitly:
     * the new 404 mapping must not turn this into a user-enumeration oracle.
     */
    @Test
    void forgotPasswordStillReturns200ForAnUnknownEmail() throws Exception {
        UserService userService = mock(UserService.class);
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        when(userService.getUserByEmail(anyString()))
                .thenThrow(new UserNotFoundException("User not found with email: nobody@example.com"));

        MockMvc userMockMvc = MockMvcBuilders.standaloneSetup(
                        new UserController(userService, passwordResetService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        userMockMvc.perform(post("/api/users/forgot-password").param("email", "nobody@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    /**
     * The same controller answers 404 from its own catch on /reset-password. That status
     * is the controller's, not the advice's, and must stay as it is.
     */
    @Test
    void resetPasswordStillReturns404FromItsOwnCatch() throws Exception {
        UserService userService = mock(UserService.class);
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        when(passwordResetService.validateOtp(anyString(), anyString())).thenReturn(true);
        org.mockito.Mockito.doThrow(new UserNotFoundException("User not found with email: nobody@example.com"))
                .when(userService).resetPassword(anyString(), anyString());

        MockMvc userMockMvc = MockMvcBuilders.standaloneSetup(
                        new UserController(userService, passwordResetService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        userMockMvc.perform(post("/api/users/reset-password")
                        .param("email", "nobody@example.com")
                        .param("token", "123456")
                        .param("newPassword", "NewPassw0rd!"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("User not found with the provided email."));
    }

    @RestController
    @RequestMapping("/throw")
    static class ThrowingController {

        @GetMapping("/task-not-found")
        void taskNotFound() {
            throw new TaskNotFoundException("Task not found");
        }

        @GetMapping("/user-not-found")
        void userNotFound() {
            throw new UserNotFoundException("User not found");
        }

        @GetMapping("/authentication-failed")
        void authenticationFailed() {
            throw new AuthenticationFailedException("Incorrect email or password",
                    new IllegalStateException("bad credentials"));
        }

        @GetMapping("/authentication-required")
        void authenticationRequired() {
            throw new AuthenticationRequiredException("Authentication required");
        }

        @GetMapping("/resource-not-found")
        void resourceNotFound() {
            throw new ResourceNotFoundException("Project not found");
        }

        @GetMapping("/no-tasks-found")
        void noTasksFound() {
            throw new NoTasksFoundException("No tasks found");
        }

        @GetMapping("/token-validation")
        void tokenValidation() {
            throw new TokenValidationException("Token validation error");
        }

        @GetMapping("/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("Invalid status");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("something genuinely broke");
        }
    }
}
