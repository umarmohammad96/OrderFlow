package com.orderflow.common.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /** SKIP LOCKED lets several app instances relay concurrently without publishing the same event twice. */
    @Query(value = """
            SELECT * FROM outbox_events
             WHERE published_at IS NULL
             ORDER BY created_at
             LIMIT :limit
               FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<OutboxEvent> lockUnpublished(@Param("limit") int limit);

    long countByPublishedAtIsNull();
}
