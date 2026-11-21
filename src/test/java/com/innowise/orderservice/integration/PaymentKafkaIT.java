package com.innowise.orderservice.integration;

import com.innowise.orderservice.messaging.PaymentEvent;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties={"order.payment-events.enabled=true"})
class PaymentKafkaIT {
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:16-alpine");
    @Container static KafkaContainer broker=new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);
        r.add("spring.datasource.password",postgres::getPassword);r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        r.add("spring.kafka.bootstrap-servers",broker::getBootstrapServers);
        r.add("spring.kafka.consumer.group-id",()->"order-kafka-it");
        r.add("order.payment-events.enabled",()->true);
    }
    @Autowired JdbcTemplate db;
    @Autowired KafkaTemplate<String,String> kafka;
    @Autowired ObjectMapper json;
    long orderId;
    @BeforeEach void seed() {
        orderId=db.queryForObject("insert into orders(user_id,status,total_price,deleted,created_at,updated_at) values (7,'NEW',19.90,false,now(),now()) returning id",Long.class);
    }
    PaymentEvent event(String status) {var id=UUID.randomUUID().toString();return new PaymentEvent(1,"CREATE_PAYMENT",id,id,orderId,7L,status,Instant.now(),new BigDecimal("19.90"));}
    void send(PaymentEvent e) throws Exception {kafka.send("payment-events",e.orderId().toString(),json.writeValueAsString(e)).get(15,java.util.concurrent.TimeUnit.SECONDS);}
    String status() {return db.queryForObject("select status from orders where id=?",String.class,orderId);}
    void processed(PaymentEvent e) {await().atMost(Duration.ofSeconds(30)).untilAsserted(()->assertThat(db.queryForObject("select count(*) from processed_payment_events where event_id=?",Integer.class,e.eventId())).isEqualTo(1));}
    @Test void consumesSuccessAndDeduplicatesDelivery() throws Exception {
        var e=event("SUCCESS");send(e);processed(e);assertThat(status()).isEqualTo("PAID");send(e);
        var barrier=event("FAILED");send(barrier);processed(barrier);
        assertThat(db.queryForObject("select count(*) from processed_payment_events where event_id=?",Integer.class,e.eventId())).isEqualTo(1);
        assertThat(status()).isEqualTo("PAID");
    }
    @Test void failureThenSuccessSupportsRetry() throws Exception {
        var failed=event("FAILED");send(failed);processed(failed);assertThat(status()).isEqualTo("PAYMENT_FAILED");
        var success=event("SUCCESS");send(success);processed(success);assertThat(status()).isEqualTo("PAID");
    }
    KafkaConsumer<String,String> deadLetters() {
        var props=new Properties();props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,broker.getBootstrapServers());props.put(ConsumerConfig.GROUP_ID_CONFIG,"dlt-"+UUID.randomUUID());props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");
        var c=new KafkaConsumer<>(props,new StringDeserializer(),new StringDeserializer());c.subscribe(List.of("payment-events.DLT"));return c;
    }
    @Test void mismatchedAmountGoesToDltWithoutChangingOrder() throws Exception {
        var e=event("SUCCESS");var invalid=new PaymentEvent(1,e.eventType(),e.eventId(),e.paymentId(),orderId,7L,"SUCCESS",e.timestamp(),new BigDecimal("99.00"));
        try(var consumer=deadLetters()) {
            send(invalid);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(()-> {
                boolean found=false;for(var record:consumer.poll(Duration.ofMillis(500))) if(record.value().contains(e.eventId())) found=true;
                assertThat(found).isTrue();
            });
        }
        assertThat(status()).isEqualTo("NEW");assertThat(db.queryForObject("select count(*) from processed_payment_events where event_id=?",Integer.class,e.eventId())).isZero();
    }
    @Test void malformedMessageGoesToDltAndConsumerContinues() throws Exception {
        String malformed="not-json-"+UUID.randomUUID();
        try(var consumer=deadLetters()) {
            kafka.send("payment-events",Long.toString(orderId),malformed).get(15,java.util.concurrent.TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(()-> {boolean found=false;for(var record:consumer.poll(Duration.ofMillis(500))) if(malformed.equals(record.value())) found=true;assertThat(found).isTrue();});
        }
        var e=event("SUCCESS");send(e);processed(e);assertThat(status()).isEqualTo("PAID");
    }
}
