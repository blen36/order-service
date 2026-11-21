--liquibase formatted sql
--changeset innowise:002-payment-events
CREATE TABLE processed_payment_events (
    event_id VARCHAR(36) PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_processed_payment_events_order ON processed_payment_events(order_id);
