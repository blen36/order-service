package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.OrderDtos.UserInfo;
import com.innowise.orderservice.exception.OrderApiException;
import io.github.resilience4j.circuitbreaker.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class UserDirectory {
    private final RestClient client;
    private final CircuitBreaker breaker;

    public UserDirectory(@Value("${USER_SERVICE_URL:http://localhost:8081}") String baseUrl) {
        var transport = new SimpleClientHttpRequestFactory();
        transport.setConnectTimeout(Duration.ofSeconds(2));
        transport.setReadTimeout(Duration.ofSeconds(3));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(transport).build();
        var config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .permittedNumberOfCallsInHalfOpenState(2)
                .ignoreExceptions(OrderApiException.class)
                .build();
        breaker = CircuitBreaker.of("user-directory", config);
    }

    public UserInfo byEmail(String email, String token) {
        return lookup("email", email, token);
    }

    public UserInfo byId(long id, String token) {
        return lookup("id", Long.toString(id), token);
    }

    private UserInfo lookup(String key, String value, String token) {
        try {
            return breaker.executeSupplier(() -> {
                var user = client.get()
                        .uri(builder -> builder.path("/api/order-users").queryParam(key, "{value}").build(value))
                        .headers(headers -> headers.setBearerAuth(token))
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError(), (request, response) -> {
                            switch (response.getStatusCode().value()) {
                                case 404 -> throw OrderApiException.missing("User profile");
                                case 401 -> throw new OrderApiException(HttpStatus.UNAUTHORIZED, "Access token rejected");
                                case 403 -> throw OrderApiException.forbidden();
                                default -> throw new RestClientException("Unexpected User Service response");
                            }
                        })
                        .body(UserInfo.class);
                if (user == null || user.id() == null || user.email() == null) {
                    throw new RestClientException("Incomplete User Service response");
                }
                return user;
            });
        } catch (CallNotPermittedException | RestClientException ex) {
            throw new OrderApiException(HttpStatus.SERVICE_UNAVAILABLE, "User Service temporarily unavailable");
        }
    }
}
