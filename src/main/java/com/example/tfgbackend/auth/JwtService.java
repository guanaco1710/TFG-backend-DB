package com.example.tfgbackend.auth;

import com.example.tfgbackend.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues and validates JWTs for access tokens, and generates opaque refresh tokens.
 *
 * <p>Access tokens: HS256, claims = sub (email), userId, role, iat, exp, jti.
 * <p>Refresh tokens: 256-bit from {@link SecureRandom}, Base64URL-encoded, never stored raw.
 *
 * <p>The signing key is sourced from {@code app.jwt.secret} — never from source control.
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationSeconds;
    private final long refreshTokenExpirationSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration:900}") long accessTokenExpirationSeconds,
            @Value("${app.jwt.refresh-token-expiration:604800}") long refreshTokenExpirationSeconds) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    }

    /**
     * Creates a signed HS256 JWT for the given user.
     *
     * @param user the authenticated user
     * @return compact JWT string
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenExpirationSeconds);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates a cryptographically-random refresh token.
     *
     * <p>This is the <em>raw</em> value handed to the client. Store only its SHA-256 hash.
     *
     * @return Base64URL-encoded 256-bit random value
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Returns the number of seconds until a new refresh token expires.
     */
    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationSeconds;
    }

    /**
     * Validates a JWT and extracts its claims.
     *
     * @param token compact JWT string
     * @return parsed claims
     * @throws JwtException if the token is invalid, expired, or has an unsupported algorithm
     */
    public Claims validateAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Computes the SHA-256 hex digest of a raw token string.
     *
     * <p>Used as the database key so the raw value is never persisted.
     *
     * @param rawToken the plaintext token
     * @return 64-character lowercase hex string
     */
    public static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java spec — will never happen
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
