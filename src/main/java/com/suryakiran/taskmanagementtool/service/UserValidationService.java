package com.suryakiran.taskmanagementtool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service responsible for validating user data.
 * Implements Strategy pattern for validation logic.
 */
@Service
public class UserValidationService {

    private static final Logger logger = LoggerFactory.getLogger(UserValidationService.class);
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()]");

    /**
     * Validates required fields for user registration
     * @param user the user to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validateRequiredFields(com.suryakiran.taskmanagementtool.model.User user) {
        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            logger.error("Validation failed: Email is required");
            throw new IllegalArgumentException("Email is required");
        }
        if (user.getFirstName() == null || user.getFirstName().isEmpty()) {
            logger.error("Validation failed: First name is required");
            throw new IllegalArgumentException("First name is required");
        }
        if (user.getLastName() == null || user.getLastName().isEmpty()) {
            logger.error("Validation failed: Last name is required");
            throw new IllegalArgumentException("Last name is required");
        }
    }

    /**
     * Validates password strength
     * @param password the password to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            logger.error("Password validation failed: Password must be at least {} characters long", MIN_PASSWORD_LENGTH);
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            logger.error("Password validation failed: Password must contain at least one uppercase letter");
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            logger.error("Password validation failed: Password must contain at least one lowercase letter");
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        if (!SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            logger.error("Password validation failed: Password must contain at least one special character");
            throw new IllegalArgumentException("Password must contain at least one special character");
        }
    }

    /**
     * Validates email format
     * @param email the email to validate
     * @return true if email is valid
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // Basic email validation pattern
        String emailPattern = "^[A-Za-z0-9+_.-]+@(.+)$";
        return Pattern.matches(emailPattern, email);
    }
}
