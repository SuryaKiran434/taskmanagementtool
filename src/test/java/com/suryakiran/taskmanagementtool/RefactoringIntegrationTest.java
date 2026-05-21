package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.service.PerformanceMonitoringService;
import com.suryakiran.taskmanagementtool.service.TaskConversionService;
import com.suryakiran.taskmanagementtool.service.UserConversionService;
import com.suryakiran.taskmanagementtool.service.UserValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify that all refactored components work together correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
class RefactoringIntegrationTest {

    @Autowired
    private TaskConversionService taskConversionService;

    @Autowired
    private UserConversionService userConversionService;

    @Autowired
    private UserValidationService userValidationService;

    @Autowired
    private PerformanceMonitoringService performanceMonitoringService;

    @Test
    void testRefactoredComponentsAreLoaded() {
        // Verify that all refactored services are properly loaded
        assertNotNull(taskConversionService, "TaskConversionService should be loaded");
        assertNotNull(userConversionService, "UserConversionService should be loaded");
        assertNotNull(userValidationService, "UserValidationService should be loaded");
        assertNotNull(performanceMonitoringService, "PerformanceMonitoringService should be loaded");
    }

    @Test
    void testPerformanceMonitoringService() {
        // Test the performance monitoring functionality
        long startTime = performanceMonitoringService.startOperation("testOperation");
        assertTrue(startTime > 0, "Start time should be positive");
        
        performanceMonitoringService.endOperation("testOperation", startTime);
        
        var stats = performanceMonitoringService.getOperationStats("testOperation");
        assertNotNull(stats, "Operation stats should not be null");
        assertEquals(1, stats.requestCount, "Should have recorded 1 operation");
        assertTrue(stats.averageResponseTime >= 0, "Average response time should be non-negative");
    }

    @Test
    void testUserValidationService() {
        // Test the user validation service
        assertThrows(IllegalArgumentException.class, () -> {
            userValidationService.validatePassword("weak");
        }, "Weak password should be rejected");
        
        assertThrows(IllegalArgumentException.class, () -> {
            userValidationService.validatePassword("12345678");
        }, "Password without uppercase should be rejected");
        
        assertThrows(IllegalArgumentException.class, () -> {
            userValidationService.validatePassword("ABCDEFGH");
        }, "Password without lowercase should be rejected");
        
        // Valid password should not throw exception
        assertDoesNotThrow(() -> {
            userValidationService.validatePassword("ValidPass1!");
        }, "Valid password should be accepted");
    }

    @Test
    void testEmailValidation() {
        // Test email validation
        assertTrue(userValidationService.isValidEmail("test@example.com"), "Valid email should pass validation");
        assertFalse(userValidationService.isValidEmail("invalid-email"), "Invalid email should fail validation");
        assertFalse(userValidationService.isValidEmail(""), "Empty email should fail validation");
        assertFalse(userValidationService.isValidEmail(null), "Null email should fail validation");
    }
}
