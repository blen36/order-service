package com.innowise.orderservice.messaging;

public class InvalidPaymentEventException extends RuntimeException {
    public InvalidPaymentEventException(String message) { super(message); }
}
