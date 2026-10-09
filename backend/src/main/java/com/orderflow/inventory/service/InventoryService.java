package com.orderflow.inventory.service;

import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.NotFoundException;
import com.orderflow.inventory.domain.Inventory;
import com.orderflow.inventory.dto.AdjustInventoryRequest;
import com.orderflow.inventory.dto.InventoryResponse;
import com.orderflow.inventory.repository.InventoryRepository;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryRepository inventory;

    public InventoryService(InventoryRepository inventory) {
        this.inventory = inventory;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(Long productId, int quantity) {
        inventory.save(new Inventory(productId, quantity));
    }

    /**
     * Reserves every line or none. Must run inside the caller's transaction so that a failure on any line rolls
     * back the reservations already made. Lines are processed in product-id order so concurrent multi-item orders
     * always lock rows in the same order and cannot deadlock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Map<Long, Integer> quantities) {
        new TreeMap<>(quantities).forEach((productId, qty) -> {
            if (inventory.reserve(productId, qty) == 0) {
                throw new OutOfStockException(productId);
            }
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Map<Long, Integer> quantities) {
        new TreeMap<>(quantities).forEach((productId, qty) -> {
            if (inventory.release(productId, qty) == 0) {
                throw new IllegalStateException("Reserved stock for product " + productId + " is lower than " + qty);
            }
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void commit(Map<Long, Integer> quantities) {
        new TreeMap<>(quantities).forEach((productId, qty) -> {
            if (inventory.commit(productId, qty) == 0) {
                throw new IllegalStateException("Reserved stock for product " + productId + " is lower than " + qty);
            }
        });
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> availableQuantities(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return inventory.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Inventory::getAvailableQuantity));
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(Long productId) {
        return InventoryResponse.from(find(productId));
    }

    @Transactional
    public InventoryResponse restock(Long productId, int quantity) {
        if (inventory.restock(productId, quantity) == 0) {
            throw new NotFoundException("Inventory for product", productId);
        }
        return InventoryResponse.from(find(productId));
    }

    @Transactional
    public InventoryResponse adjust(Long productId, AdjustInventoryRequest request) {
        Inventory item = find(productId);
        if (item.getVersion() != request.expectedVersion()) {
            throw new ConflictException("STALE_INVENTORY_VERSION",
                    "Inventory changed since version " + request.expectedVersion() + "; current version is " + item.getVersion());
        }
        item.setAvailableQuantity(request.availableQuantity());
        // @Version makes the UPDATE conditional on the version we read, closing the gap after the check above.
        return InventoryResponse.from(inventory.saveAndFlush(item));
    }

    private Inventory find(Long productId) {
        return inventory.findById(productId).orElseThrow(() -> new NotFoundException("Inventory for product", productId));
    }
}
