package com.docuvault.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Every tunable the application has, bound once at startup and validated.
 * Binding failures stop the context from starting, so a missing JWT secret is a
 * boot failure rather than a silent fallback to an insecure default.
 */
@Validated
@ConfigurationProperties(prefix = "docuvault")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Upload upload) {

    /** HS256 needs at least 256 bits of key material to be worth anything. */
    private static final int MIN_SECRET_BYTES = 32;

    public AppProperties {
        if (jwt != null && jwt.secret() != null
                && jwt.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "docuvault.jwt.secret must be at least " + MIN_SECRET_BYTES
                            + " bytes. Generate one with: openssl rand -base64 48");
        }
    }

    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl) {
    }

    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    public record Storage(
            @NotBlank String type,
            String localDirectory,
            @Valid S3 s3) {

        public record S3(
                String bucket,
                String region,
                String endpoint,
                String accessKey,
                String secretKey) {
        }
    }

    public record Upload(@NotNull Set<String> allowedContentTypes) {
    }
}
