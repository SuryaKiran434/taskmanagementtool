package com.suryakiran.taskmanagementtool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.io.Decoders;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
     * Reads the JWT's {@code exp} claim without verifying the signature.
     * Safe here because we only need the expiry timestamp, not to trust the payload.
     *
     * <p>This decodes the payload segment directly rather than going through the jjwt
     * parser. jjwt 0.12 removed the ability to parse a signed token with its signature
     * stripped off: {@code parseUnsecuredClaims} now rejects any token whose header
     * names a signature algorithm with
     * {@code MalformedJwtException: The JWS header references signature algorithm
     * 'HS256' but the compact JWE string is missing the required signature},
     * even with {@code .unsecured()} enabled. Routing through it would make
     * {@link #blacklistToken(String)} a silent no-op and stop logout from revoking
     * anything.</p>
     */
    private Instant extractExpiry(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            byte[] payload = Decoders.BASE64URL.decode(parts[1]);
            JsonNode exp = OBJECT_MAPPER.readTree(payload).get("exp");
            // "exp" is a NumericDate: seconds since the epoch.
            return exp != null && exp.canConvertToLong() ? Instant.ofEpochSecond(exp.asLong()) : null;
        } catch (Exception e) {
            logger.warn("Could not parse token expiry: {}", e.getMessage());
            return null;
        }
    }
}
