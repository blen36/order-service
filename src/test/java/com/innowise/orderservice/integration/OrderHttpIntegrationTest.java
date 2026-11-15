package com.innowise.orderservice.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

// Test-only JDBC configuration starts a fresh PostgreSQL container; production Liquibase runs at startup.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:/config/application.properties")
@DirtiesContext
class OrderHttpIntegrationTest {
    private static final WireMockServer directory = new WireMockServer(wireMockConfig().dynamicPort());
    private static final RSAKey signingKey = newKey();
    private static final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry settings) {
        if (!directory.isRunning()) {
            directory.start();
        }
        settings.add("USER_SERVICE_URL", directory::baseUrl);
        settings.add("AUTH_JWKS_URI", () -> directory.baseUrl() + "/jwks");
        settings.add("AUTH_JWT_ISSUER", () -> "order-test-auth");
        settings.add("AUTH_JWT_AUDIENCE", () -> "order-test-api");
    }

    @AfterAll
    static void stopWireMock() {
        directory.stop();
    }

    @BeforeEach
    void resetStubs() {
        directory.resetAll();
        directory.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(okJson(new JWKSet(signingKey.toPublicJWK()).toString())));
    }

    @Test
    void orderLifecycleRunsThroughHttpLiquibaseAndPostgres() throws Exception {
        long buyerId = 101;
        String buyer = token(buyerId, "USER", "access");
        String admin = token(900, "ADMIN", "access");
        String stranger = token(102, "USER", "access");
        String email = stubProfile(buyerId, buyer);
        long item = createItem(admin);
        long order = createOrder(buyer, email, item, 2);
        String path = "/orders/" + order;

        JsonNode saved = body(call("GET", path, buyer, null, 200));
        assertThat(saved.path("totalPrice").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(saved.path("user").path("email").asText()).isEqualTo(email);
        assertThat(saved.path("items").get(0).path("quantity").asInt()).isEqualTo(2);
        directory.verify(getRequestedFor(urlPathEqualTo("/api/order-users"))
                .withQueryParam("email", equalTo(email)).withHeader("Authorization", equalTo("Bearer " + buyer)));

        call("GET", path, stranger, null, 403);
        call("DELETE", path, stranger, null, 403);
        call("PATCH", path + "/status", buyer, "{\"status\":\"PAID\"}", 403);
        call("PUT", path, buyer, lines(item, 0), 400);
        call("PUT", path, buyer, lines(Long.MAX_VALUE, 1), 404);
        assertThat(body(call("GET", path, buyer, null, 200)).path("totalPrice").decimalValue())
                .isEqualByComparingTo("100.00");

        JsonNode updated = body(call("PUT", path, buyer, lines(item, 3), 200));
        assertThat(updated.path("totalPrice").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(db.queryForObject("select count(*) from order_items where order_id=?", Integer.class, order)).isOne();

        call("DELETE", "/items/" + item, admin, null, 409);
        call("DELETE", path, buyer, null, 204);
        call("GET", path, buyer, null, 404);
        assertThat(db.queryForObject("select deleted from orders where id=?", Boolean.class, order)).isTrue();
        assertThat(db.queryForObject("select quantity from order_items where order_id=?", Integer.class, order)).isEqualTo(3);
        assertThat(body(call("GET", "/items/" + item, admin, null, 200)).path("id").asLong()).isEqualTo(item);
    }

    @Test
    void filtersCombineOwnerDatesStatusesAndPage() throws Exception {
        long buyerId = 201;
        String buyer = token(buyerId, "USER", "access");
        String admin = token(900, "ADMIN", "access");
        String email = stubProfile(buyerId, buyer);
        long item = createItem(admin);
        long first = createOrder(buyer, email, item, 1);
        long second = createOrder(buyer, email, item, 2);
        String list = "/orders?userId=" + buyerId;
        JsonNode page = body(call("GET", list + "&page=1&size=1", buyer, null, 200));
        assertThat(page.path("totalElements").asInt()).isEqualTo(2);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(first);
        stubProfile(buyerId, admin);
        call("PATCH", "/orders/" + first + "/status", admin, "{\"status\":\"PAID\"}", 200);
        JsonNode filtered = body(call("GET", list + "&statuses=NEW&from=2000-01-01T00:00:00Z&to=2099-01-01T00:00:00Z", admin, null, 200));
        assertThat(filtered.path("totalElements").asInt()).isOne();
        assertThat(filtered.path("content").get(0).path("id").asLong()).isEqualTo(second);
        assertThat(body(call("GET", list + "&to=2000-01-01T00:00:00Z", buyer, null, 200))
                .path("totalElements").asInt()).isZero();
        call("GET", "/orders?userId=202", buyer, null, 403);
    }

    @Test
    void securityAndCatalogUseRealJwtVerification() throws Exception {
        String admin = token(900, "ADMIN", "access");
        call("GET", "/orders", null, null, 401);
        call("GET", "/orders", token(300, "USER", "refresh"), null, 401);
        call("POST", "/items", token(300, "USER", "access"), "{\"name\":\"Mouse\",\"price\":10}", 403);
        long item = createItem(admin);
        String path = "/items/" + item;
        assertThat(body(call("PUT", path, admin, "{\"name\":\"Monitor\",\"price\":85}", 200))
                .path("name").asText()).isEqualTo("Monitor");
        call("DELETE", path, admin, null, 204);
        call("GET", path, admin, null, 404);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void directoryOutageOpensCircuitWithoutInsertingOrders() throws Exception {
        long buyerId = 301;
        String email = "unavailable@example.test";
        String buyer = token(buyerId, "USER", "access");
        long item = createItem(token(900, "ADMIN", "access"));
        directory.stubFor(get(urlPathEqualTo("/api/order-users")).willReturn(serverError()));
        for (int attempt = 0; attempt < 12; attempt++) {
            call("POST", "/orders", buyer, newOrder(email, item, 1), 503);
        }
        int forwarded = directory.findAll(getRequestedFor(urlPathEqualTo("/api/order-users"))).size();
        assertThat(forwarded).isBetween(1, 11);
        assertThat(db.queryForObject("select count(*) from orders where user_id=?", Integer.class, buyerId)).isZero();
    }

    private String stubProfile(long id, String token) {
        String email = "buyer" + id + "@example.test";
        String profile = """
                {"id":%d,"name":"Ada","surname":"Lee","email":"%s"}
                """.formatted(id, email);
        for (String field : new String[]{"id", "email"}) {
            String value = field.equals("id") ? Long.toString(id) : email;
            directory.stubFor(get(urlPathEqualTo("/api/order-users"))
                    .withHeader("Authorization", equalTo("Bearer " + token))
                    .withQueryParam(field, equalTo(value)).willReturn(okJson(profile)));
        }
        return email;
    }

    private long createItem(String admin) throws Exception {
        return body(call("POST", "/items", admin, "{\"name\":\"Keyboard\",\"price\":50.00}", 201)).path("id").asLong();
    }

    private long createOrder(String buyer, String email, long item, int quantity) throws Exception {
        var response = call("POST", "/orders", buyer, newOrder(email, item, quantity), 201);
        long id = body(response).path("id").asLong();
        assertThat(response.headers().firstValue("Location")).hasValue("/api/v1/orders/" + id);
        return id;
    }

    private static String newOrder(String email, long item, int quantity) {
        return """
                {"email":"%s","items":[{"itemId":%d,"quantity":%d}]}
                """.formatted(email, item, quantity);
    }

    private static String lines(long item, int quantity) {
        return "{\"items\":[{\"itemId\":%d,\"quantity\":%d}]}".formatted(item, quantity);
    }

    private HttpResponse<String> call(String method, String path, String token, String payload, int status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(java.time.Duration.ofSeconds(15));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (payload != null) {
            request.header("Content-Type", "application/json");
        }
        var data = payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload);
        var response = http.send(request.method(method, data).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(status);
        return response;
    }

    private JsonNode body(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String token(long id, String role, String type) throws JOSEException {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().subject(Long.toString(id)).issuer("order-test-auth")
                .audience("order-test-api").issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString()).claim("user_id", id).claim("role", role).claim("tokenType", type).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}
