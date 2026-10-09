package com.orderflow.cart.repository;

import com.orderflow.cart.domain.Cart;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<Cart> findByUserId(Long userId);

    /** Race-free "get or create": concurrent first requests for the same user cannot create two carts. */
    @Modifying
    @Query(value = "INSERT INTO carts (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING", nativeQuery = true)
    void createIfAbsent(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from CartItem ci
             where ci.cart.id = (select c.id from Cart c where c.userId = :userId)
               and ci.productId in :productIds""")
    int removeProducts(@Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);
}
