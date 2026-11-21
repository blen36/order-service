package com.innowise.orderservice.messaging;

import com.innowise.orderservice.service.PaymentEventHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "order.payment-events.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentEventListener {
    private final ObjectMapper json;
    private final PaymentEventHandler handler;
    public PaymentEventListener(ObjectMapper json, PaymentEventHandler handler) { this.json = json; this.handler = handler; }
    @KafkaListener(topics = "${order.payment-events.topic}")
    public void receive(ConsumerRecord<String, String> record) {
        PaymentEvent event;
        try {
            event = json.readValue(record.value(), PaymentEvent.class);
            if (event == null) throw new IllegalArgumentException("Null event");
            event.validate();
            if (!event.orderId().toString().equals(record.key())) throw new IllegalArgumentException("Wrong order key");
        } catch (Exception ex) {
            throw new InvalidPaymentEventException("Malformed CREATE_PAYMENT event");
        }
        handler.handle(event);
    }
}
