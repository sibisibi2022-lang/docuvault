package com.docuvault.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on {@code @CreatedDate} / {@code @LastModifiedDate}, so timestamps are
 * set by the framework rather than by every service remembering to call
 * {@code setCreatedAt(LocalDateTime.now())}.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
