package com.orderflow.cart.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Optional<CartItem> findItemByProduct(Long productId) {
        return items.stream().filter(i -> i.getProductId().equals(productId)).findFirst();
    }

    public Optional<CartItem> findItem(Long itemId) {
        return items.stream().filter(i -> i.getId().equals(itemId)).findFirst();
    }

    public void addItem(Long productId, int quantity) {
        items.add(new CartItem(this, productId, quantity));
        touch();
    }

    public void removeItem(CartItem item) {
        items.remove(item);
        touch();
    }

    /** Child-only changes don't dirty the cart row, so bump the timestamp explicitly. */
    public void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<CartItem> getItems() {
        return items;
    }
}
