package com.orderflow.common.idempotency;

import com.orderflow.common.exception.BusinessRuleException;
import com.orderflow.common.exception.ConflictException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency-Key store (blueprint §8.2).
 *
 * <p>{@link #begin} runs inside the business transaction and claims the key with
 * {@code INSERT ... ON CONFLICT DO NOTHING}. Consequences:
 * <ul>
 *   <li>First request: the insert succeeds; the business operation runs and {@link #complete} stores the response.
 *       Key, order and stock reservation commit atomically.</li>
 *   <li>Retry after success: the insert is a no-op; the stored response is replayed.</li>
 *   <li>Concurrent duplicate: PostgreSQL blocks the second insert on the unique index until the first transaction
 *       finishes, then the second sees the committed row and replays it — never a second order.</li>
 *   <li>First request failed (e.g. out of stock): its key insert rolled back too, so a retry is evaluated afresh.</li>
 *   <li>Same key, different payload: rejected with 422.</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    private final JdbcTemplate jdbc;

    public IdempotencyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return the stored response JSON if this key was already completed, or empty if the caller now owns the key */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<String> begin(Long userId, String key, String requestHash) {
        int inserted = jdbc.update("""
                INSERT INTO idempotency_keys (user_id, request_key, request_hash)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, request_key) DO NOTHING""", userId, key, requestHash);
        if (inserted == 1) {
            return Optional.empty();
        }
        StoredKey stored = jdbc.queryForObject(
                "SELECT request_hash, response_snapshot::text FROM idempotency_keys WHERE user_id = ? AND request_key = ?",
                (rs, i) -> new StoredKey(rs.getString(1), rs.getString(2)), userId, key);
        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessRuleException("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with a different request payload");
        }
        if (stored.responseSnapshot() == null) {
            throw new ConflictException("REQUEST_IN_PROGRESS", "A request with this Idempotency-Key is still in progress");
        }
        return Optional.of(stored.responseSnapshot());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Long userId, String key, String responseJson) {
        jdbc.update("UPDATE idempotency_keys SET response_snapshot = ?::jsonb WHERE user_id = ? AND request_key = ?",
                responseJson, userId, key);
    }

    private record StoredKey(String requestHash, String responseSnapshot) {
    }
}
