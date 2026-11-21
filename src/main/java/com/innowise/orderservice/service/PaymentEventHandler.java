package com.innowise.orderservice.service;

import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.messaging.*;
import com.innowise.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentEventHandler {
    private final OrderRepository orders;
    private final ProcessedPaymentEvents processed;
    public PaymentEventHandler(OrderRepository orders, ProcessedPaymentEvents processed) {
        this.orders = orders; this.processed = processed;
    }
    @Transactional
    public void handle(PaymentEvent event) {
        event.validate();
        // Serializes events for an order, including duplicate deliveries from different consumers.
        var order = orders.lockById(event.orderId()).orElseThrow(() ->
                new InvalidPaymentEventException("Order is missing or deleted"));
        if (processed.contains(event.eventId())) return;
        if (!order.getUserId().equals(event.userId()) || order.getTotalPrice().compareTo(event.paymentAmount()) != 0) {
            throw new InvalidPaymentEventException("Payment owner or amount does not match the order");
        }
        if ("SUCCESS".equals(event.status())) {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                throw new InvalidPaymentEventException("A cancelled order cannot become paid");
            }
            order.setStatus(OrderStatus.PAID);
        } else if (order.getStatus() == OrderStatus.NEW) {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
        }
        // A late FAILED event never downgrades PAID or reopens CANCELLED.
        // JDBC and JPA participate in the same PostgreSQL transaction.
        processed.record(event.eventId(), order.getId());
    }
}
