package com.orderflow.inventory.repository;

import com.orderflow.inventory.domain.Inventory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Stock mutations are single conditional UPDATE statements. The WHERE clause makes check-and-decrement atomic in
 * the database, so two transactions racing for the last unit cannot both succeed: the second one re-evaluates the
 * predicate after the first commits and updates zero rows. Each statement bumps {@code version} so that
 * optimistic-locking writers (admin stock adjustments) detect the concurrent change.
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
            update Inventory i
               set i.availableQuantity = i.availableQuantity - :qty,
                   i.reservedQuantity  = i.reservedQuantity + :qty,
                   i.version = i.version + 1
             where i.productId = :productId and i.availableQuantity >= :qty""")
    int reserve(@Param("productId") Long productId, @Param("qty") int quantity);

    /** Returns reserved stock to the available pool (order cancelled). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Inventory i
               set i.availableQuantity = i.availableQuantity + :qty,
                   i.reservedQuantity  = i.reservedQuantity - :qty,
                   i.version = i.version + 1
             where i.productId = :productId and i.reservedQuantity >= :qty""")
    int release(@Param("productId") Long productId, @Param("qty") int quantity);

    /** Converts a reservation into a sale (payment captured). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Inventory i
               set i.reservedQuantity = i.reservedQuantity - :qty,
                   i.version = i.version + 1
             where i.productId = :productId and i.reservedQuantity >= :qty""")
    int commit(@Param("productId") Long productId, @Param("qty") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Inventory i
               set i.availableQuantity = i.availableQuantity + :qty,
                   i.version = i.version + 1
             where i.productId = :productId""")
    int restock(@Param("productId") Long productId, @Param("qty") int quantity);

    List<Inventory> findByProductIdIn(Collection<Long> productIds);
}
