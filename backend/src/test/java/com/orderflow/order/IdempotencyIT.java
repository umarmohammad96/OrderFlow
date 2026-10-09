package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.common.idempotency.IdempotencyKeyPurger;
import com.orderflow.support.IntegrationTestBase;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Blueprint §8.2: retries with the same Idempotency-Key never create a second order. */
class IdempotencyIT extends IntegrationTestBase {

    @Autowired
    IdempotencyKeyPurger purger;

    @Test
    void retryWithSameKeyReturnsOriginalOrder() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "10.00", 10);

        long first = data(placeOrder(user, "retry-key-0001", orderBody(mug, 2))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))).get("id").asLong();

        placeOrder(user, "retry-key-0001", orderBody(mug, 2))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.data.id").value(first))
                .andExpect(jsonPath("$.data.totalAmount").value(20.00));

        assertThat(count("orders")).isEqualTo(1);
        assertThat(available(mug)).isEqualTo(8); // stock taken once, not twice
    }

    @Test
    void replayReturnsOriginalSnapshotEvenAfterOrderChanged() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "10.00", 10);
        long id = data(placeOrder(user, "retry-key-0002", orderBody(mug, 1))).get("id").asLong();
        postAs(user, "/api/orders/" + id + "/cancel", "");

        placeOrder(user, "retry-key-0002", orderBody(mug, 1))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
        assertThat(count("orders")).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "10.00", 10);
        placeOrder(user, "retry-key-0003", orderBody(mug, 1)).andExpect(status().isCreated());

        placeOrder(user, "retry-key-0003", orderBody(mug, 5))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(count("orders")).isEqualTo(1);
    }

    @Test
    void equivalentPayloadWithReorderedLinesIsTreatedAsSameRequest() throws Exception {
        TestUser user = registerCustomer();
        long a = createProduct("A", "1.00", 10);
        long b = createProduct("B", "1.00", 10);
        String ab = "{\"items\":[{\"productId\":" + a + ",\"quantity\":1},{\"productId\":" + b + ",\"quantity\":2}]}";
        String ba = "{\"items\":[{\"productId\":" + b + ",\"quantity\":2},{\"productId\":" + a + ",\"quantity\":1}]}";

        placeOrder(user, "retry-key-0004", ab).andExpect(header().string("Idempotent-Replayed", "false"));
        placeOrder(user, "retry-key-0004", ba).andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void keysAreScopedPerUser() throws Exception {
        TestUser alice = registerCustomer();
        TestUser bob = registerCustomer();
        long mug = createProduct("Mug", "10.00", 10);

        long aliceOrder = data(placeOrder(alice, "shared-key-0001", orderBody(mug, 1))).get("id").asLong();
        long bobOrder = data(placeOrder(bob, "shared-key-0001", orderBody(mug, 1))).get("id").asLong();

        assertThat(bobOrder).isNotEqualTo(aliceOrder);
        assertThat(count("orders")).isEqualTo(2);
    }

    @Test
    void failedAttemptDoesNotBurnTheKey() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "10.00", 0);

        placeOrder(user, "retry-key-0005", orderBody(mug, 1)).andExpect(status().isConflict());
        jdbc.update("UPDATE inventory SET available_quantity = 1 WHERE product_id = ?", mug);
        placeOrder(user, "retry-key-0005", orderBody(mug, 1))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));
    }

    @Test
    void concurrentDuplicatesCreateExactlyOneOrder() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "10.00", 100);
        int attempts = 15;

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    go.await();
                    return httpPostOrder(user, "double-click-0001", orderBody(mug, 1));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }, pool));
        }
        go.countDown();
        List<HttpResponse<String>> responses = futures.stream().map(CompletableFuture::join).toList();
        pool.shutdown();

        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).isEqualTo(201));
        Set<Long> orderIds = responses.stream().map(r -> {
            try {
                return readTree(r.body()).at("/data/id").asLong();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).collect(Collectors.toSet());
        assertThat(orderIds).hasSize(1);
        Map<String, Long> replayed = responses.stream().collect(Collectors.groupingBy(
                r -> r.headers().firstValue("Idempotent-Replayed").orElse("?"), Collectors.counting()));
        assertThat(replayed).containsEntry("false", 1L).containsEntry("true", (long) attempts - 1);
        assertThat(count("orders")).isEqualTo(1);
        assertThat(available(mug)).isEqualTo(99);
    }

    @Test
    void expiredKeysArePurged() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "10.00", 10);
        placeOrder(user, "old-key-00000001", orderBody(mug, 1));
        placeOrder(user, "new-key-00000001", orderBody(mug, 1));
        jdbc.update("UPDATE idempotency_keys SET created_at = now() - interval '25 hours' WHERE request_key = 'old-key-00000001'");

        assertThat(purger.purgeExpired()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT request_key FROM idempotency_keys", String.class)).containsExactly("new-key-00000001");
    }
}
