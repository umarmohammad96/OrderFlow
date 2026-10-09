package com.orderflow.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.orderflow.common.config.CacheNames;
import com.orderflow.support.IntegrationTestBase;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Blueprint §8.3: product reads are served from Redis, expire after a TTL and are evicted on writes. */
class ProductCacheIT extends IntegrationTestBase {

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void resetStatistics() {
        redisCache(CacheNames.PRODUCT).clearStatistics();
        redisCache(CacheNames.PRODUCT_PAGES).clearStatistics();
    }

    @Test
    void productDetailIsCachedInRedisWithTtl() throws Exception {
        long id = createProduct("Lamp", "20.00", 3);

        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.data.name").value("Lamp"));
        }

        CacheStatistics stats = redisCache(CacheNames.PRODUCT).getStatistics();
        assertThat(stats.getMisses()).isEqualTo(1);
        assertThat(stats.getHits()).isEqualTo(9);
        String key = "orderflow:product::" + id;
        assertThat(redis.hasKey(key)).isTrue();
        assertThat(redis.getExpire(key)).isBetween(1L, 600L);
    }

    @Test
    void cachedValueIsServedUntilAWriteEvictsIt() throws Exception {
        TestUser admin = createAdmin();
        long id = createProduct("Lamp", "20.00", 3);
        long category = jdbc.queryForObject("SELECT category_id FROM products WHERE id = ?", Long.class, id);
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.data.price").value(20.00));

        // Change behind the application's back: the cache (not the DB) answers the next read.
        jdbc.update("UPDATE products SET price = 99 WHERE id = ?", id);
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.data.price").value(20.00));

        // A write through the API evicts the entry; the next read is fresh.
        putAs(admin, "/api/products/" + id, Map.of("name", "Lamp", "price", "25.00", "categoryId", category, "active", true));
        assertThat(redis.hasKey("orderflow:product::" + id)).isFalse();
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.data.price").value(25.00));
    }

    @Test
    void listPagesAreCachedAndEvictedWhenCatalogueChanges() throws Exception {
        TestUser admin = createAdmin();
        createProduct("First", "1.00", 1);
        long category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", Long.class);

        mvc.perform(get("/api/products?size=5")).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/products?size=5"));
        assertThat(redisCache(CacheNames.PRODUCT_PAGES).getStatistics().getHits()).isEqualTo(1);
        assertThat(pageKeys()).hasSize(1);

        postAs(admin, "/api/products", Map.of("name", "Second", "price", "2.00", "categoryId", category, "initialStock", 1));

        assertThat(pageKeys()).isEmpty();
        mvc.perform(get("/api/products?size=5")).andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void stockIsLiveEvenWhenProductDataIsCached() throws Exception {
        TestUser customer = registerCustomer();
        long id = createProduct("Lamp", "20.00", 5);
        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.data.availableQuantity").value(5));

        placeOrderOk(customer, id, 2);

        mvc.perform(get("/api/products/" + id)).andExpect(jsonPath("$.data.availableQuantity").value(3));
        mvc.perform(get("/api/products")).andExpect(jsonPath("$.data.content[0].availableQuantity").value(3));
        assertThat(redisCache(CacheNames.PRODUCT).getStatistics().getHits()).isEqualTo(1);
    }

    @Test
    void deactivationEvictsDetailAndPages() throws Exception {
        TestUser admin = createAdmin();
        long id = createProduct("Lamp", "20.00", 5);
        mvc.perform(get("/api/products/" + id));
        mvc.perform(get("/api/products"));

        deleteAs(admin, "/api/products/" + id);

        assertThat(redis.hasKey("orderflow:product::" + id)).isFalse();
        assertThat(pageKeys()).isEmpty();
    }

    private Set<String> pageKeys() {
        return redis.keys("orderflow:productPages::*");
    }

    private RedisCache redisCache(String name) {
        Cache cache = cacheManager.getCache(name);
        return (RedisCache) (cache instanceof TransactionAwareCacheDecorator d ? d.getTargetCache() : cache);
    }
}
