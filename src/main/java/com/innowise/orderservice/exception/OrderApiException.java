package com.innowise.orderservice.exception;

import org.springframework.http.HttpStatus;

public class OrderApiException extends RuntimeException {
    private final HttpStatus status;

    public OrderApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }

    public static OrderApiException missing(String resource) {
        return new OrderApiException(HttpStatus.NOT_FOUND, resource + " not found");
    }

    public static OrderApiException invalid(String message) {
        return new OrderApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static OrderApiException conflict(String message) {
        return new OrderApiException(HttpStatus.CONFLICT, message);
    }

    public static OrderApiException forbidden() {
        return new OrderApiException(HttpStatus.FORBIDDEN, "Access denied");
    }
}
