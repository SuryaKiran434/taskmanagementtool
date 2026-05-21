package com.suryakiran.taskmanagementtool.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks revoked JWT tokens until their natural expiry.
 * Uses ConcurrentHashMap<token, expiry> so expired entries
 * are pruned on every access — no unbounded memory growth.
 */
@Service
public class TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistService.class);

    // token -> expiry (when the JWT itself expires)
    private final Map<String, Instant> blacklistedTokens = new ConcurrentHashMap<>();

    /**
     * Adds a token to the blacklist until its natural JWT expiry.
     */
    public void blacklistToken(String token) {
        Instant expiry = extractExpiry(token);
        if (expiry != null && Instant.now().isBefore(expiry)) {
            blacklistedTokens.put(token, expiry);
            logger.info("Token blacklisted, expires at: {}", expiry);
        } else {
            logger.debug("Token already expired, skipping blacklist");
        }
    }

    /**
     * Returns true if the token has been explicitly revoked and has not yet expired.
     * Also prunes any expired entries from the map on each call.
     */
    public boolean isBlacklisted(String token) {
        pruneExpired();
        return blacklistedTokens.containsKey(token);
    }

    private void pruneExpired() {
        Instant now = Instant.now();
        blacklistedTokens.entrySet().removeIf(e -> now.isAfter(e.getValue()));
    }

    /**
     * Parses the JWT without verifying the signature to extract its expiry claim.
     * Safe here because we only need the expiry timestamp, not to trust the payload.
     */
    private Instant extractExpiry(String token) {
        try {
            // Split the token and decode the payload without signature validation
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            Claims claims = Jwts.parserBuilder()
                    .build()
                    .parseClaimsJwt(parts[0] + "." + parts[1] + ".")
                    .getBody();
            return claims.getExpiration() != null
                    ? claims.getExpiration().toInstant()
                    : null;
        } catch (ExpiredJwtException e) {
            // Token is already expired — expiry is still available in the exception
            return e.getClaims().getExpiration() != null
                    ? e.getClaims().getExpiration().toInstant()
                    : null;
        } catch (Exception e) {
            logger.warn("Could not parse token expiry: {}", e.getMessage());
            return null;
        }
    }
}
