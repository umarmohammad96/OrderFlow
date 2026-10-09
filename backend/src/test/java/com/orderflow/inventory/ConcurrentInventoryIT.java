package com.orderflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.support.IntegrationTestBase;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Blueprint §8.1: many customers race for the same stock through the real HTTP stack. Every request is released at
 * the same instant by a latch so the database sees genuinely concurrent transactions.
 */
class ConcurrentInventoryIT extends IntegrationTestBase {

    @Test
    void onlyOneOfManyBuyersGetsTheLastUnit() throws Exception {
        long console = createProduct("Limited Console", "499.00", 1);
        List<TestUser> buyers = buyers(20);

        Map<Integer, Long> statuses = race(buyers, (i) -> () -> httpPostOrder(buyers.get(i), "last-unit-" + i, orderBody(console, 1)));

        assertThat(statuses).containsEntry(201, 1L).containsEntry(409, 19L).hasSize(2);
        assertThat(available(console)).isZero();
        assertThat(reserved(console)).isEqualTo(1);
        assertThat(count("orders")).isEqualTo(1);
    }

    @Test
    void stockIsNeverOversoldUnderContention() throws Exception {
        long widget = createProduct("Widget", "5.00", 10);
        List<TestUser> buyers = buyers(30);

        Map<Integer, Long> statuses = race(buyers, (i) -> () -> httpPostOrder(buyers.get(i), "widget-" + i, orderBody(widget, 1)));

        assertThat(statuses.get(201)).isEqualTo(10L);
        assertThat(statuses.get(409)).isEqualTo(20L);
        assertThat(available(widget)).isZero();
        assertThat(reserved(widget)).isEqualTo(10);
        Integer orderedUnits = jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM order_items WHERE product_id = ?",
                Integer.class, widget);
        assertThat(orderedUnits).isEqualTo(10);
    }

    @Test
    void opposingMultiItemOrdersDoNotDeadlock() throws Exception {
        long a = createProduct("A", "1.00", 100);
        long b = createProduct("B", "1.00", 100);
        List<TestUser> buyers = buyers(20);
        String ab = "{\"items\":[{\"productId\":" + a + ",\"quantity\":1},{\"productId\":" + b + ",\"quantity\":1}]}";
        String ba = "{\"items\":[{\"productId\":" + b + ",\"quantity\":1},{\"productId\":" + a + ",\"quantity\":1}]}";

        Map<Integer, Long> statuses = race(buyers,
                (i) -> () -> httpPostOrder(buyers.get(i), "multi-key-" + i, i % 2 == 0 ? ab : ba));

        // Lines are reserved in product-id order regardless of request order, so no deadlock (which would be a 500).
        assertThat(statuses).containsOnlyKeys(201).containsEntry(201, 20L);
        assertThat(available(a)).isEqualTo(80);
        assertThat(available(b)).isEqualTo(80);
    }

    @Test
    void cancelAndReorderRaceKeepsStockConsistent() throws Exception {
        long item = createProduct("Item", "1.00", 5);
        TestUser holder = registerCustomer();
        long orderId = placeOrderOk(holder, item, 5);
        List<TestUser> buyers = buyers(10);

        List<Callable<HttpResponse<String>>> tasks = new ArrayList<>();
        tasks.add(() -> httpPost(holder, "/api/orders/" + orderId + "/cancel", ""));
        for (int i = 0; i < buyers.size(); i++) {
            int n = i;
            tasks.add(() -> httpPostOrder(buyers.get(n), "reorder-" + n, orderBody(item, 1)));
        }
        runConcurrently(tasks);

        int sold = jdbc.queryForObject("""
                SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi JOIN orders o ON o.id = oi.order_id
                 WHERE oi.product_id = ? AND o.status <> 'CANCELLED'""", Integer.class, item);
        // Conservation: available + reserved always equals initial stock minus what live orders hold.
        assertThat(available(item) + reserved(item)).isEqualTo(5);
        assertThat(reserved(item)).isEqualTo(sold);
        assertThat(available(item)).isGreaterThanOrEqualTo(0);
    }

    // ------------------------------------------------------------------ helpers

    private List<TestUser> buyers(int n) throws Exception {
        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            users.add(registerCustomer());
        }
        return users;
    }

    private Map<Integer, Long> race(List<TestUser> buyers, Function<Integer, Callable<HttpResponse<String>>> taskFor)
            throws Exception {
        List<Callable<HttpResponse<String>>> tasks = new ArrayList<>();
        for (int i = 0; i < buyers.size(); i++) {
            tasks.add(taskFor.apply(i));
        }
        return runConcurrently(tasks).stream().collect(Collectors.groupingBy(HttpResponse::statusCode, Collectors.counting()));
    }

    private List<HttpResponse<String>> runConcurrently(List<Callable<HttpResponse<String>>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (Callable<HttpResponse<String>> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<HttpResponse<String>> responses = new ArrayList<>();
            for (Future<HttpResponse<String>> f : futures) {
                responses.add(f.get());
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }
}
