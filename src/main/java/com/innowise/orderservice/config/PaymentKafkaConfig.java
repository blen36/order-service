package com.innowise.orderservice.config;

import com.innowise.orderservice.messaging.InvalidPaymentEventException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "order.payment-events.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentKafkaConfig {
    @Bean
    NewTopic paymentEvents(@Value("${order.payment-events.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
    @Bean
    NewTopic paymentEventsDlt(@Value("${order.payment-events.topic}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(3).replicas(1).build();
    }
    @Bean
    DefaultErrorHandler paymentErrorHandler(KafkaTemplate<String, String> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 4L));
        handler.addNotRetryableExceptions(InvalidPaymentEventException.class);
        // Infrastructure failures are retained for retry instead of being silently discarded.
        handler.setBackOffFunction((record, ex) -> {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof InvalidPaymentEventException) return new FixedBackOff(0L, 0L);
            }
            return new FixedBackOff(2000L, FixedBackOff.UNLIMITED_ATTEMPTS);
        });
        return handler;
    }
}
