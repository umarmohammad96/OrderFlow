package com.orderflow.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.auth.domain.Role;
import com.orderflow.auth.domain.User;
import com.orderflow.auth.repository.UserRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Full application on a random port, wired to real PostgreSQL, Redis and RabbitMQ containers. All subclasses share
 * one Spring context. Every test starts from empty tables and an empty cache.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = SharedContainers.POSTGRES;

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = SharedContainers.REDIS;

    @ServiceConnection
    static final RabbitMQContainer RABBIT = SharedContainers.RABBIT;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected CacheManager cacheManager;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @LocalServerPort
    protected int port;

    protected final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void resetState() {
        jdbc.execute("""
                TRUNCATE users, categories, products, inventory, carts, cart_items, orders, order_items,
                         order_status_history, payments, idempotency_keys, outbox_events, notifications
                RESTART IDENTITY CASCADE""");
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    // ---------------------------------------------------------------- users

    public record TestUser(long id, String email, String token) {
    }

    protected TestUser registerCustomer() throws Exception {
        String email = "customer" + SEQUENCE.incrementAndGet() + "@example.com";
        JsonNode data = data(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Test Customer", "email", email, "password", "password123")))));
        return new TestUser(data.at("/user/id").asLong(), email, data.get("accessToken").asText());
    }

    protected TestUser createAdmin() throws Exception {
        String email = "admin" + SEQUENCE.incrementAndGet() + "@example.com";
        User admin = userRepository.save(new User("Admin", email, passwordEncoder.encode("admin-password"), Role.ADMIN));
        JsonNode data = data(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", "admin-password")))));
        return new TestUser(admin.getId(), email, data.get("accessToken").asText());
    }

    // ---------------------------------------------------------------- catalogue fixtures (direct SQL: fast)

    protected long createCategory(String name) {
        return jdbc.queryForObject("INSERT INTO categories (name) VALUES (?) RETURNING id", Long.class, name);
    }

    protected long createProduct(String name, String price, int stock) {
        long categoryId = jdbc.query("SELECT id FROM categories ORDER BY id LIMIT 1", rs -> rs.next() ? rs.getLong(1) : -1L);
        if (categoryId < 0) {
            categoryId = createCategory("General");
        }
        long productId = jdbc.queryForObject(
                "INSERT INTO products (name, description, price, category_id) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, name, name + " description", new BigDecimal(price), categoryId);
        jdbc.update("INSERT INTO inventory (product_id, available_quantity) VALUES (?, ?)", productId, stock);
        return productId;
    }

    protected int available(long productId) {
        return jdbc.queryForObject("SELECT available_quantity FROM inventory WHERE product_id = ?", Integer.class, productId);
    }

    protected int reserved(long productId) {
        return jdbc.queryForObject("SELECT reserved_quantity FROM inventory WHERE product_id = ?", Integer.class, productId);
    }

    protected int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    // ---------------------------------------------------------------- orders

    protected static String orderBody(long productId, int quantity) {
        return "{\"items\":[{\"productId\":" + productId + ",\"quantity\":" + quantity + "}]}";
    }

    protected ResultActions placeOrder(TestUser user, String idempotencyKey, String body) throws Exception {
        return mvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected long placeOrderOk(TestUser user, long productId, int quantity) throws Exception {
        return data(placeOrder(user, "key-" + SEQUENCE.incrementAndGet() + "-xxxxxxxx", orderBody(productId, quantity)))
                .get("id").asLong();
    }

    protected ResultActions pay(TestUser user, long orderId, String token) throws Exception {
        return mvc.perform(post("/api/payments/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentToken\":\"" + token + "\"}"));
    }

    // ---------------------------------------------------------------- HTTP helpers

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, TestUser user) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer(user));
    }

    protected ResultActions getAs(TestUser user, String url) throws Exception {
        return mvc.perform(user == null ? get(url) : authed(get(url), user));
    }

    protected ResultActions postAs(TestUser user, String url, Object body) throws Exception {
        MockHttpServletRequestBuilder req = post(url).contentType(MediaType.APPLICATION_JSON).content(json(body));
        return mvc.perform(user == null ? req : authed(req, user));
    }

    protected ResultActions putAs(TestUser user, String url, Object body) throws Exception {
        return mvc.perform(authed(put(url).contentType(MediaType.APPLICATION_JSON).content(json(body)), user));
    }

    protected ResultActions patchAs(TestUser user, String url, Object body) throws Exception {
        return mvc.perform(authed(patch(url).contentType(MediaType.APPLICATION_JSON).content(json(body)), user));
    }

    protected ResultActions deleteAs(TestUser user, String url) throws Exception {
        return mvc.perform(authed(delete(url), user));
    }

    /** Real HTTP call through Tomcat — used by concurrency tests where requests must truly run in parallel. */
    protected HttpResponse<String> httpPostOrder(TestUser user, String idempotencyKey, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/orders"))
                .header("Authorization", bearer(user))
                .header("Idempotency-Key", idempotencyKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPost(TestUser user, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", bearer(user))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected String json(Object value) throws Exception {
        return value instanceof String s ? s : objectMapper.writeValueAsString(value);
    }

    protected JsonNode data(ResultActions result) throws Exception {
        return body(result.andReturn()).get("data");
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode readTree(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    protected static String bearer(TestUser user) {
        return "Bearer " + user.token();
    }
}
