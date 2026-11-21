package com.innowise.orderservice.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedPaymentEvents {
    private final JdbcTemplate jdbc;
    public ProcessedPaymentEvents(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean contains(String eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from processed_payment_events where event_id=?)", Boolean.class, eventId));
    }
    public void record(String eventId, long orderId) {
        jdbc.update("insert into processed_payment_events(event_id,order_id) values (?,?)", eventId, orderId);
    }
}
