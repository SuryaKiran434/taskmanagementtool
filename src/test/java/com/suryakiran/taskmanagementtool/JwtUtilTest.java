package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.service.TokenBlacklistService;
import com.suryakiran.taskmanagementtool.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
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

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        jwtUtil = new JwtUtil(tokenBlacklistService);
        Field secretField = JwtUtil.class.getDeclaredField("secret");
        secretField.setAccessible(true);
        secretField.set(jwtUtil, secret);
    }

    @ParameterizedTest
    @ValueSource(strings = {"testuser1", "testuser2", "testuser3"})
    void testGenerateToken(String username) {
        when(userDetails.getUsername()).thenReturn(username);
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());

        String token = jwtUtil.generateToken(userDetails);
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

    private String createTestToken(String username) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour
                .signWith(key)
                .compact();
    }

    private String createTestTokenWithRoles(String username, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .setSubject(username)
                .claim("roles", roles)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour
                .signWith(key)
                .compact();
    }
}