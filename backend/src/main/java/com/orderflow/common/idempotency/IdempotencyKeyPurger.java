package com.orderflow.common.idempotency;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keys only need to outlive the client's retry window; purge them so the table does not grow forever. */
@Component
public class IdempotencyKeyPurger {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyPurger.class);

    private final JdbcTemplate jdbc;
    private final Duration retention;

    public IdempotencyKeyPurger(JdbcTemplate jdbc, @Value("${orderflow.idempotency.retention}") Duration retention) {
        this.jdbc = jdbc;
        this.retention = retention;
    }

    @Scheduled(cron = "${orderflow.idempotency.purge-cron}")
    public int purgeExpired() {
        int deleted = jdbc.update("DELETE FROM idempotency_keys WHERE created_at < now() - (? * interval '1 second')",
                retention.toSeconds());
        if (deleted > 0) {
            log.info("Purged {} expired idempotency keys", deleted);
        }
        return deleted;
    }
}
