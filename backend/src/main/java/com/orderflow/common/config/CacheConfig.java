package com.orderflow.common.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.response.PageResponse;
import com.orderflow.product.dto.ProductResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Redis cache for product catalogue reads.
 * <ul>
 *   <li>Each cache has a typed JSON serializer (records are final, so polymorphic default typing would not work).</li>
 *   <li>Entries expire after a TTL and are evicted on every product write.</li>
 *   <li>The manager is transaction-aware: evictions run after commit, so a concurrent reader cannot re-cache
 *       pre-commit data.</li>
 *   <li>{@code allEntries} eviction uses SCAN rather than KEYS so it never blocks Redis.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper,
                                   @Value("${orderflow.cache.product-ttl}") Duration productTtl) {
        ObjectMapper mapper = objectMapper.copy();
        JavaType pageType = mapper.getTypeFactory().constructParametricType(PageResponse.class, ProductResponse.class);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(productTtl)
                .disableCachingNullValues()
                .prefixCacheNameWith("orderflow:");

        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(500));
        return RedisCacheManager.builder(writer)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(Map.of(
                        CacheNames.PRODUCT, base.serializeValuesWith(SerializationPair.fromSerializer(
                                new Jackson2JsonRedisSerializer<>(mapper, ProductResponse.class))),
                        CacheNames.PRODUCT_PAGES, base.serializeValuesWith(SerializationPair.fromSerializer(
                                new Jackson2JsonRedisSerializer<>(mapper, pageType)))))
                .enableStatistics()
                .transactionAware()
                .build();
    }
}
