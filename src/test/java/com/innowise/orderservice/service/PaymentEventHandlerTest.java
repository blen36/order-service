package com.innowise.orderservice.service;

import com.innowise.orderservice.entity.*;
import com.innowise.orderservice.messaging.*;
import com.innowise.orderservice.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventHandlerTest {
    @Mock OrderRepository orders;
    @Mock ProcessedPaymentEvents processed;
    PaymentEventHandler handler;
    PurchaseOrder order;
    @BeforeEach void init() {
        handler=new PaymentEventHandler(orders,processed);order=new PurchaseOrder(7L);
        ReflectionTestUtils.setField(order,"id",12L);ReflectionTestUtils.setField(order,"totalPrice",new BigDecimal("19.90"));
    }
    PaymentEvent event(String status) {
        String id=UUID.randomUUID().toString();
        return new PaymentEvent(1,"CREATE_PAYMENT",id,id,12L,7L,status,Instant.now(),new BigDecimal("19.90"));
    }
    void found() { when(orders.lockById(12)).thenReturn(Optional.of(order)); }
    @Test void successMarksPaid() { found();var e=event("SUCCESS");handler.handle(e);assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);verify(processed).record(e.eventId(),12); }
    @Test void failureMarksPaymentFailed() { found();handler.handle(event("FAILED"));assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED); }
    @Test void successfulRetryMarksPaid() { found();order.setStatus(OrderStatus.PAYMENT_FAILED);handler.handle(event("SUCCESS"));assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID); }
    @Test void lateFailureDoesNotUndoPayment() { found();order.setStatus(OrderStatus.PAID);handler.handle(event("FAILED"));assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID); }
    @Test void failureDoesNotReopenCancelledOrder() { found();order.setStatus(OrderStatus.CANCELLED);handler.handle(event("FAILED"));assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED); }
    @Test void duplicateIsIgnored() { found();var e=event("SUCCESS");when(processed.contains(e.eventId())).thenReturn(true);handler.handle(e);assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);verify(processed,never()).record(anyString(),anyLong()); }
    @Test void cancelledSuccessNeedsManualReview() { found();order.setStatus(OrderStatus.CANCELLED);assertThatThrownBy(()->handler.handle(event("SUCCESS"))).isInstanceOf(InvalidPaymentEventException.class);verify(processed,never()).record(anyString(),anyLong()); }
    @Test void wrongAmountCannotMarkPaid() { found();ReflectionTestUtils.setField(order,"totalPrice",new BigDecimal("99.00"));assertThatThrownBy(()->handler.handle(event("SUCCESS"))).isInstanceOf(InvalidPaymentEventException.class);assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW); }
    @Test void wrongOwnerCannotMarkPaid() { found();ReflectionTestUtils.setField(order,"userId",8L);assertThatThrownBy(()->handler.handle(event("SUCCESS"))).isInstanceOf(InvalidPaymentEventException.class); }
    @Test void missingOrderIsRejected() { assertThatThrownBy(()->handler.handle(event("SUCCESS"))).isInstanceOf(InvalidPaymentEventException.class); }
    @Test void malformedEventIsRejectedBeforeDatabaseAccess() { var e=new PaymentEvent(99,"OTHER","bad","bad",12L,7L,"SUCCESS",Instant.now(),BigDecimal.ONE);assertThatThrownBy(()->handler.handle(e)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(orders,processed); }
}
