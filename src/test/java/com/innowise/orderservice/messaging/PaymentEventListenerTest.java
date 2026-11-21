package com.innowise.orderservice.messaging;

import com.innowise.orderservice.service.PaymentEventHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentEventListenerTest {
    final PaymentEventHandler handler=mock(PaymentEventHandler.class);
    final JsonMapper json=JsonMapper.builder().build();
    final PaymentEventListener listener=new PaymentEventListener(json,handler);
    PaymentEvent event() {var id=UUID.randomUUID().toString();return new PaymentEvent(1,"CREATE_PAYMENT",id,id,12L,7L,"SUCCESS",Instant.now(),BigDecimal.ONE);}
    @Test void acceptsWireContract() {var e=event();listener.receive(new ConsumerRecord<>("payment-events",0,0,"12",json.writeValueAsString(e)));verify(handler).handle(e);}
    @Test void rejectsMalformedJson() {assertThatThrownBy(()->listener.receive(new ConsumerRecord<>("payment-events",0,0,"12","bad-json"))).isInstanceOf(InvalidPaymentEventException.class);verifyNoInteractions(handler);}
    @Test void rejectsWrongPartitionKey() {assertThatThrownBy(()->listener.receive(new ConsumerRecord<>("payment-events",0,0,"99",json.writeValueAsString(event())))).isInstanceOf(InvalidPaymentEventException.class);verifyNoInteractions(handler);}
    @Test void databaseFailureIsPropagatedForRetry() {var e=event();doThrow(new org.springframework.dao.DataAccessResourceFailureException("offline")).when(handler).handle(e);assertThatThrownBy(()->listener.receive(new ConsumerRecord<>("payment-events",0,0,"12",json.writeValueAsString(e)))).isInstanceOf(org.springframework.dao.DataAccessException.class);}
}
