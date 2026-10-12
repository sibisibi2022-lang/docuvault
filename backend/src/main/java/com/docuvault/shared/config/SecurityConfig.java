package com.docuvault.shared.config;

import com.docuvault.security.AppUserDetailsService;
import com.docuvault.security.JwtAuthenticationFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppUserDetailsService userDetailsService;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AppUserDetailsService userDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(auth -> auth

                        // Public authentication endpoints
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        ).permitAll()

                        // Profile: any authenticated user
                        .requestMatchers("/api/users/profile")
                        .authenticated()

                        // User management: ADMIN only
                        .requestMatchers("/api/users/**")
                        .hasRole("ADMIN")

                        // Documents: view and upload
                        .requestMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/api/documents/**"
                        ).hasAnyRole("ADMIN", "MANAGER", "EMPLOYEE")

                        .requestMatchers(
                                org.springframework.http.HttpMethod.POST,
                                "/api/documents/**"
                        ).hasAnyRole("ADMIN", "MANAGER", "EMPLOYEE")

                        // Documents: edit and delete
                        .requestMatchers(
                                org.springframework.http.HttpMethod.PUT,
                                "/api/documents/**"
                        ).hasAnyRole("ADMIN", "MANAGER")

                        .requestMatchers(
                                org.springframework.http.HttpMethod.DELETE,
                                "/api/documents/**"
                        ).hasAnyRole("ADMIN", "MANAGER")

                        // Categories: read
                        .requestMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/api/categories/**"
                        ).hasAnyRole("ADMIN", "MANAGER", "EMPLOYEE")

                        // Categories: create, update and delete
                        .requestMatchers(
                                org.springframework.http.HttpMethod.POST,
                                "/api/categories/**"
                        ).hasAnyRole("ADMIN", "MANAGER")

                        .requestMatchers(
                                org.springframework.http.HttpMethod.PUT,
                                "/api/categories/**"
                        ).hasAnyRole("ADMIN", "MANAGER")

                        .requestMatchers(
                                org.springframework.http.HttpMethod.DELETE,
                                "/api/categories/**"
                        ).hasAnyRole("ADMIN", "MANAGER")

                        // Audit logs
                        .requestMatchers("/api/audit-logs/**")
                        .hasAnyRole("ADMIN", "MANAGER")

                        // Document versions
                        .requestMatchers("/api/document-versions/**")
                        .hasAnyRole("ADMIN", "MANAGER", "EMPLOYEE")

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of("http://localhost:4200"));

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}