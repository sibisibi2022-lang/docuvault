package com.docuvault.security;

import com.docuvault.shared.config.AppProperties;
import com.docuvault.user.Role;
import com.docuvault.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies access tokens.
 *
 * <p>The signing key comes from configuration and the application refuses to
 * start without one — the original shipped a hardcoded secret in a public
 * repository, which let anyone forge a token for any account.
 *
 * <p>The role is a signed claim, so an authenticated request needs no database
 * round trip to know what the caller may do. The cost is that a role change or
 * a disabled account only takes effect when the current token expires; with a
 * one-hour TTL that window is acceptable, and shortening the TTL shortens it.
 */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final String issuer;
    private final long ttlSeconds;

    public JwtService(AppProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.jwt().issuer();
        this.ttlSeconds = properties.jwt().accessTokenTtl().toSeconds();
    }

    public String issueToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(issuer)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_NAME, user.getName())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public long expiresInSeconds() {
        return ttlSeconds;
    }

    /**
     * Verifies the signature, issuer and expiry, then rebuilds the caller.
     *
     * @throws JwtException if the token is forged, altered, expired or malformed
     */
    public AuthenticatedUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return AuthenticatedUser.fromToken(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }
}
