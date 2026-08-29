package com.suryakiran.taskmanagementtool.util;

import com.suryakiran.taskmanagementtool.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    private final TokenBlacklistService tokenBlacklistService;

    private static final long CLOCK_SKEW = 60000;

    public JwtUtil(TokenBlacklistService tokenBlacklistService) {
        this.tokenBlacklistService = tokenBlacklistService;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .clockSkewSeconds(CLOCK_SKEW / 1000)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date(System.currentTimeMillis() - CLOCK_SKEW));
    }

    public String generateToken(UserDetails userDetails, int userId) {
        Map<String, Object> claims = new HashMap<>();
        Collection<? extends GrantedAuthority> roles = userDetails.getAuthorities();
        logger.debug("User roles being added to JWT: {}", roles);
        claims.put("roles", roles.stream().map(GrantedAuthority::getAuthority).toList());
        claims.put("userId", userId);
        return createToken(claims, userDetails.getUsername());
    }


    private String createToken(Map<String, Object> claims, String subject) {
        logger.debug("Creating JWT token for subject: {}", subject);
        final long expiration = 3600000; // 1 hour in milliseconds
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                // HS256 is named explicitly on purpose. jjwt's single-argument
                // signWith(key) derives the algorithm from the key length, which for the
                // secrets this app uses (>= 384 bits) would silently upgrade tokens to
                // HS384 and invalidate every token already in circulation.
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 604800000)) // 7 days in milliseconds
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token) && !tokenBlacklistService.isBlacklisted(token));
    }

    public String refreshToken(String token) {
        if (isTokenExpired(token) || tokenBlacklistService.isBlacklisted(token)) {
            throw new IllegalArgumentException("Token is invalid or blacklisted");
        }
        final Claims claims = extractAllClaims(token);
        // Claims became immutable in jjwt 0.12, so the old claims.setIssuedAt(..) /
        // claims.setExpiration(..) mutation is copied into a fresh map instead.
        Map<String, Object> refreshedClaims = new HashMap<>(claims);
        refreshedClaims.remove(Claims.ISSUED_AT);
        refreshedClaims.remove(Claims.EXPIRATION);
        return Jwts.builder()
                .claims(refreshedClaims)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour in milliseconds
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public List<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("roles", List.class);
    }
}
