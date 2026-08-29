package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.service.TokenBlacklistService;
import com.suryakiran.taskmanagementtool.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class JwtUtilTest {

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private JwtUtil jwtUtil;

    private final String secret = "mysecretkeymysecretkeymysecretkeymysecretkey"; // 32 characters for HMAC-SHA-256

    /** Long enough (512 bits) that jjwt would infer HS512 if the algorithm were not pinned. */
    private static final String LONG_SECRET =
            "aVeryLongJwtSigningSecretThatIsSixtyFourBytesLongForHs512Testing";

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        jwtUtil = new JwtUtil(tokenBlacklistService);
        setSecret(jwtUtil, secret);
    }

    private static void setSecret(JwtUtil util, String value) throws Exception {
        Field secretField = JwtUtil.class.getDeclaredField("secret");
        secretField.setAccessible(true);
        secretField.set(util, value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"testuser1", "testuser2", "testuser3"})
    void testGenerateToken(String username) {
        when(userDetails.getUsername()).thenReturn(username);
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());

        String token = jwtUtil.generateToken(userDetails, 1);
        assertNotNull(token);
    }

    @ParameterizedTest
    @ValueSource(strings = {"testuser1", "testuser2", "testuser3"})
    void testExtractUsername(String username) {
        String token = createTestToken(username);

        String extractedUsername = jwtUtil.extractUsername(token);
        assertEquals(username, extractedUsername);
    }

    @ParameterizedTest
    @ValueSource(strings = {"testuser1", "testuser2", "testuser3"})
    void testValidateToken(String username) {
        String token = createTestToken(username);
        when(userDetails.getUsername()).thenReturn(username);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);

        boolean isValid = jwtUtil.validateToken(token, userDetails);
        assertTrue(isValid);
    }

    @ParameterizedTest
    @ValueSource(strings = {"testuser1", "testuser2", "testuser3"})
    void testRefreshToken(String username) {
        String token = createTestToken(username);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);

        String refreshedToken = jwtUtil.refreshToken(token);
        assertNotNull(refreshedToken);
    }

    @ParameterizedTest
    @ValueSource(strings = {"testuser1", "testuser2", "testuser3"})
    void testExtractRoles(String username) {
        String token = createTestTokenWithRoles(username, List.of("ROLE_USER"));

        List<String> roles = jwtUtil.extractRoles(token);
        assertEquals(1, roles.size());
        assertEquals("ROLE_USER", roles.get(0));
    }

    // --- Behavioural guarantees that must survive the jjwt 0.11 -> 0.13 migration ---

    /**
     * Tokens this app issues must round-trip: what is signed comes back out intact,
     * including the custom claims, and the signature verifies against the same key.
     */
    @Test
    void generatedTokenRoundTripsThroughVerification() {
        when(userDetails.getUsername()).thenReturn("roundtrip@example.com");
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());

        String token = jwtUtil.generateToken(userDetails, 42);

        assertEquals("roundtrip@example.com", jwtUtil.extractUsername(token));
        assertEquals(Collections.emptyList(), jwtUtil.extractRoles(token));
        assertEquals(Integer.valueOf(42), jwtUtil.extractClaim(token, c -> c.get("userId", Integer.class)));
        assertTrue(jwtUtil.extractExpiration(token).after(new Date()));
    }

    /**
     * jjwt's single-argument signWith(key) picks the algorithm from the key length, so a
     * >= 384-bit secret would silently become HS384/HS512. Every token this app issues
     * must still say HS256.
     */
    @Test
    void allIssuedTokensStillUseHs256() throws Exception {
        setSecret(jwtUtil, LONG_SECRET);
        assertTrue(LONG_SECRET.getBytes(StandardCharsets.UTF_8).length * 8 >= 512,
                "secret must be long enough that jjwt would otherwise infer HS512");

        when(userDetails.getUsername()).thenReturn("alg@example.com");
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());
        when(tokenBlacklistService.isBlacklisted(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        String access = jwtUtil.generateToken(userDetails, 1);
        String refresh = jwtUtil.generateRefreshToken(userDetails);
        String refreshed = jwtUtil.refreshToken(access);

        assertEquals("HS256", algorithmOf(access), "access token algorithm");
        assertEquals("HS256", algorithmOf(refresh), "refresh token algorithm");
        assertEquals("HS256", algorithmOf(refreshed), "refreshed token algorithm");
    }

    /** An expired token must still be rejected, not quietly accepted. */
    @Test
    void expiredTokenIsRejected() {
        String expired = createExpiredToken("expired@example.com");

        // The parser enforces "exp" (with the configured 60s skew) before anything else.
        assertThrows(ExpiredJwtException.class, () -> jwtUtil.extractUsername(expired));
        assertThrows(ExpiredJwtException.class, () -> jwtUtil.validateToken(expired, userDetails));
    }

    /**
     * A token signed with a different key must still be rejected, and must surface as a
     * JwtException the JWT filter already handles — not as an unhandled error that would
     * turn a forged token into a 500.
     */
    @Test
    void tokenSignedWithADifferentKeyIsRejected() {
        SecretKey attackerKey = Keys.hmacShaKeyFor(
                "anAttackerControlledSecretThatIsNotOurSigningKey!".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("attacker@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(attackerKey, Jwts.SIG.HS256)
                .compact();

        SignatureException ex = assertThrows(SignatureException.class,
                () -> jwtUtil.extractUsername(forged));
        // JwtRequestFilter catches broadly, but assert the hierarchy explicitly so a
        // future jjwt reparenting cannot silently escape it.
        assertInstanceOf(JwtException.class, ex);
    }

    /** A structurally broken token must fail as a JwtException too. */
    @Test
    void malformedTokenIsRejected() {
        assertThrows(JwtException.class, () -> jwtUtil.extractUsername("not.a.valid.jwt"));
    }

    private static String algorithmOf(String token) {
        String header = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                StandardCharsets.UTF_8);
        assertTrue(header.contains("\"alg\""), "header should carry an alg: " + header);
        return header.replaceAll(".*\"alg\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private String createExpiredToken(String username) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                // Well past the 60s clock skew JwtUtil allows.
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private String createTestToken(String username) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private String createTestTokenWithRoles(String username, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
