package com.innowise.orderservice.config;

import com.innowise.orderservice.security.JwtSupport;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class OrderSecurityConfig {
    @Bean
    JwtDecoder orderDecoder(@Value("${AUTH_JWKS_URI:http://localhost:8082/.well-known/jwks.json}") String uri,
                            @Value("${AUTH_JWT_ISSUER:authentication-service}") String issuer,
                            @Value("${AUTH_JWT_AUDIENCE:java-advanced-api}") String audience) {
        return JwtSupport.decoder(NimbusJwtDecoder.withJwkSetUri(uri).build(), issuer, audience, "access");
    }

    @Bean
    SecurityFilterChain orderApi(HttpSecurity http, ObjectMapper json) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        org.springframework.security.web.AuthenticationEntryPoint unauthorized = (req, res, ex) -> {
            res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            writeError(res, json, HttpStatus.UNAUTHORIZED, "A valid access token is required");
        };
        org.springframework.security.web.access.AccessDeniedHandler forbidden =
                (req, res, ex) -> writeError(res, json, HttpStatus.FORBIDDEN, "Access denied");
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers("/api/v1/items", "/api/v1/items/**").hasRole("ADMIN")
                        .anyRequest().hasAnyRole("USER", "ADMIN"))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .oauth2ResourceServer(r -> r.jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper json, HttpStatus status,
                                   String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, message));
    }
}
