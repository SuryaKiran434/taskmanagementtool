package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.service.TokenBlacklistService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the logout / token-revocation path.
 *
 * <p>The service reads a token's "exp" claim to decide how long to keep it revoked. It
 * used to do that by handing jjwt the token with its signature stripped off, which jjwt
 * 0.12 stopped supporting — a change that would have turned {@code blacklistToken} into a
 * silent no-op (every call swallowed by its catch-all) without breaking compilation or
 * any other test. These tests fail loudly if that ever happens again.</p>
 */
class TokenBlacklistServiceTest {

    private static final String SECRET = "testSecretKeyForTestingPurposesOnly1234567890ABCDEF";

    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new TokenBlacklistService();
    }

    @Test
    void blacklistedTokenIsReportedAsBlacklisted() {
        String token = signedToken(System.currentTimeMillis() + 3600000);

        assertFalse(service.isBlacklisted(token), "token should not start out revoked");
        service.blacklistToken(token);
        assertTrue(service.isBlacklisted(token), "logout must actually revoke the token");
    }

    @Test
    void anUnrelatedTokenIsNotBlacklisted() {
        service.blacklistToken(signedToken(System.currentTimeMillis() + 3600000));

        assertFalse(service.isBlacklisted(signedToken(System.currentTimeMillis() + 7200000)));
    }

    @Test
    void alreadyExpiredTokenIsNotRetained() {
        String expired = signedToken(System.currentTimeMillis() - 3600000);

        service.blacklistToken(expired);

        // Nothing to revoke: it is already invalid, so it must not sit in the map forever.
        assertFalse(service.isBlacklisted(expired));
    }

    @Test
    void garbageInputIsIgnoredRatherThanThrowing() {
        service.blacklistToken("not-a-jwt");
        assertFalse(service.isBlacklisted("not-a-jwt"));
    }

    private static String signedToken(long expiryMillis) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("user-" + expiryMillis)
                .issuedAt(new Date())
                .expiration(new Date(expiryMillis))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
