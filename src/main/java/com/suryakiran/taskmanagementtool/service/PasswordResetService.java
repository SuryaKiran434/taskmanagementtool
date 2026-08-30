package com.suryakiran.taskmanagementtool.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.suryakiran.taskmanagementtool.util.LogSanitizer.maskEmail;

/**
 * Manages OTP-based password reset tokens.
 * Each OTP is a 6-digit code valid for 15 minutes.
 * Expired entries are pruned on every access.
 *
 * <p>The address is the only handle this service has on a user — there is no repository
 * here and so no id to log instead — so every statement below masks it. The store is still
 * keyed on the full address; only what reaches the log is reduced.</p>
 */
@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final long OTP_TTL_SECONDS = 15 * 60; // 15 minutes
    private static final int OTP_LENGTH = 6;

    private final SecureRandom secureRandom = new SecureRandom();

    // email -> (otp, expiry)
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    /**
     * Generates a 6-digit OTP for the given email and stores it with a 15-minute TTL.
     * Any previous OTP for the same email is overwritten.
     */
    public String generateOtp(String email) {
        // Stated rather than assumed: both callers bind this from a required @RequestParam,
        // so a null here is a programming error, not a request the user can make. Without the
        // precondition the toLowerCase below is an unguarded dereference.
        Objects.requireNonNull(email, "email must not be null");
        pruneExpired();
        String otp = String.format("%0" + OTP_LENGTH + "d",
                secureRandom.nextInt((int) Math.pow(10, OTP_LENGTH)));
        otpStore.put(email.toLowerCase(), new OtpEntry(otp, Instant.now().plusSeconds(OTP_TTL_SECONDS)));
        logger.info("OTP generated for email: {}", maskEmail(email));
        return otp;
    }

    /**
     * Validates the OTP for the given email.
     * Returns true only if the OTP matches and has not expired.
     * Removes the entry on successful validation (one-time use).
     */
    public boolean validateOtp(String email, String otp) {
        Objects.requireNonNull(email, "email must not be null");
        pruneExpired();
        OtpEntry entry = otpStore.get(email.toLowerCase());
        if (entry == null) {
            logger.warn("OTP validation failed: no OTP found for email: {}", maskEmail(email));
            return false;
        }
        if (Instant.now().isAfter(entry.expiry())) {
            otpStore.remove(email.toLowerCase());
            logger.warn("OTP validation failed: OTP expired for email: {}", maskEmail(email));
            return false;
        }
        if (!entry.otp().equals(otp)) {
            logger.warn("OTP validation failed: incorrect OTP for email: {}", maskEmail(email));
            return false;
        }
        otpStore.remove(email.toLowerCase()); // one-time use
        logger.info("OTP validated successfully for email: {}", maskEmail(email));
        return true;
    }

    private void pruneExpired() {
        Instant now = Instant.now();
        otpStore.entrySet().removeIf(e -> now.isAfter(e.getValue().expiry()));
    }

    private record OtpEntry(String otp, Instant expiry) {}
}
