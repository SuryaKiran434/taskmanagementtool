package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.controller.UserController;
import com.suryakiran.taskmanagementtool.dto.ResetTokenDTO;
import com.suryakiran.taskmanagementtool.exception.UserNotFoundException;
import com.suryakiran.taskmanagementtool.model.User;
import com.suryakiran.taskmanagementtool.service.PasswordResetService;
import com.suryakiran.taskmanagementtool.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * POST /api/users/forgot-password must not hand out the OTP, and must not reveal
 * whether an address has an account.
 *
 * <p>Both properties were broken. The endpoint returned the freshly generated OTP in
 * its response body, and both it and /reset-password are permitAll -- so naming an
 * address was enough to read its OTP and hand it straight back, taking over any
 * account whose address was known, with no authentication anywhere in the chain. The
 * two branches also answered with different bodies (a populated otp and its own
 * wording, versus a null and different wording), so the endpoint enumerated accounts
 * even for a caller who did not want to reset anything.</p>
 */
class ForgotPasswordOtpExposureTest {

    private static final String KNOWN = "ada@example.com";
    private static final String UNKNOWN = "nobody@example.com";

    private UserService userService;
    private PasswordResetService passwordResetService;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        passwordResetService = mock(PasswordResetService.class);
        controller = new UserController(userService, passwordResetService);

        User ada = new User();
        ada.setEmail(KNOWN);
        when(userService.getUserByEmail(KNOWN)).thenReturn(ada);
        when(userService.getUserByEmail(UNKNOWN))
                .thenThrow(new UserNotFoundException("User not found"));
        when(passwordResetService.generateOtp(anyString())).thenReturn("123456");
    }

    private void exposeOtp(boolean on) {
        ReflectionTestUtils.setField(controller, "exposeOtp", on);
    }

    @Nested
    @DisplayName("with the OTP echo off, which is the default and every non-dev profile")
    class EchoOff {

        @BeforeEach
        void off() {
            exposeOtp(false);
        }

        @Test
        @DisplayName("a known address gets no OTP in the response")
        void knownAddressGetsNoOtp() {
            ResetTokenDTO body = controller.forgotPassword(KNOWN).getBody();
            assertNotNull(body);
            assertNull(body.getOtp(), "the OTP must never leave the server here");
        }

        @Test
        @DisplayName("the OTP is still generated and stored, so a mailer could send it")
        void otpIsStillGenerated() {
            controller.forgotPassword(KNOWN);
            verify(passwordResetService).generateOtp(KNOWN);
        }

        @Test
        @DisplayName("a known and an unknown address are indistinguishable")
        void responsesAreIdentical() {
            ResponseEntity<ResetTokenDTO> known = controller.forgotPassword(KNOWN);
            ResponseEntity<ResetTokenDTO> unknown = controller.forgotPassword(UNKNOWN);

            assertEquals(known.getStatusCode(), unknown.getStatusCode());
            assertEquals(known.getBody().getMessage(), unknown.getBody().getMessage(),
                    "differing wording is an enumeration oracle on its own");
            assertNull(known.getBody().getOtp());
            assertNull(unknown.getBody().getOtp());
        }

        @Test
        @DisplayName("the message does not say whether an account was found")
        void messageIsNonCommittal() {
            String message = controller.forgotPassword(KNOWN).getBody().getMessage();
            assertEquals("If an account exists for this email, a reset code has been sent.", message);
        }

        @Test
        @DisplayName("an unknown address generates no OTP at all")
        void unknownAddressGeneratesNothing() {
            controller.forgotPassword(UNKNOWN);
            verify(passwordResetService, never()).generateOtp(anyString());
        }

        @Test
        @DisplayName("an unknown address still answers 200, not 404")
        void unknownAddressAnswersOk() {
            assertEquals(200, controller.forgotPassword(UNKNOWN).getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("with the OTP echo on, which only the dev profile does")
    class EchoOn {

        @BeforeEach
        void on() {
            exposeOtp(true);
        }

        @Test
        @DisplayName("a known address gets the OTP, so a developer can complete the flow")
        void knownAddressGetsTheOtp() {
            assertEquals("123456", controller.forgotPassword(KNOWN).getBody().getOtp());
        }

        @Test
        @DisplayName("an unknown address still gets nothing, and the same wording")
        void unknownAddressStillGetsNothing() {
            ResetTokenDTO body = controller.forgotPassword(UNKNOWN).getBody();
            assertNull(body.getOtp());
            assertEquals("If an account exists for this email, a reset code has been sent.",
                    body.getMessage());
        }
    }

    @Test
    @DisplayName("the field defaults to off, so a profile that says nothing exposes nothing")
    void defaultsToOff() {
        UserController fresh = new UserController(userService, passwordResetService);
        assertNull(fresh.forgotPassword(KNOWN).getBody().getOtp(),
                "a controller with no configuration applied must not echo the OTP");
    }
}
