package com.orderflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.inventory.domain.Inventory;
import com.orderflow.inventory.repository.InventoryRepository;
import com.orderflow.support.SharedContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Repository slice test against real PostgreSQL (Flyway schema, CHECK constraints, conditional updates). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryRepositoryIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = SharedContainers.POSTGRES;

    @Autowired
    InventoryRepository repository;
    @Autowired
    JdbcTemplate jdbc;

    long productId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE products, categories, inventory RESTART IDENTITY CASCADE");
        long category = jdbc.queryForObject("INSERT INTO categories (name) VALUES ('C') RETURNING id", Long.class);
        productId = jdbc.queryForObject("INSERT INTO products (name, price, category_id) VALUES ('P', 1, ?) RETURNING id",
                Long.class, category);
        repository.saveAndFlush(new Inventory(productId, 5));
    }

    private Inventory reload() {
        return jdbc.queryForObject("SELECT available_quantity, reserved_quantity, version FROM inventory WHERE product_id = ?",
                (rs, i) -> {
                    Inventory inv = new Inventory(productId, rs.getInt(1));
                    return inv;
                }, productId);
    }

    private int reservedQty() {
        return jdbc.queryForObject("SELECT reserved_quantity FROM inventory WHERE product_id = ?", Integer.class, productId);
    }

    private long version() {
        return jdbc.queryForObject("SELECT version FROM inventory WHERE product_id = ?", Long.class, productId);
    }

    @Test
    void reserveSucceedsOnlyWhenEnoughStock() {
        assertThat(repository.reserve(productId, 3)).isEqualTo(1);
        assertThat(reload().getAvailableQuantity()).isEqualTo(2);
        assertThat(reservedQty()).isEqualTo(3);

        assertThat(repository.reserve(productId, 3)).isZero(); // only 2 left: no rows updated
        assertThat(reload().getAvailableQuantity()).isEqualTo(2);
        assertThat(repository.reserve(productId, 2)).isEqualTo(1);
        assertThat(reload().getAvailableQuantity()).isZero();
    }

    @Test
    void releaseAndCommitMoveReservedStock() {
        repository.reserve(productId, 4);
        assertThat(repository.release(productId, 1)).isEqualTo(1);
        assertThat(repository.commit(productId, 3)).isEqualTo(1);
        assertThat(reload().getAvailableQuantity()).isEqualTo(2);
        assertThat(reservedQty()).isZero();
        assertThat(repository.commit(productId, 1)).isZero(); // nothing reserved any more
    }

    @Test
    void everyMutationBumpsVersion() {
        long before = version();
        repository.reserve(productId, 1);
        repository.release(productId, 1);
        repository.restock(productId, 1);
        assertThat(version()).isEqualTo(before + 3);
    }

    @Test
    void databaseRejectsNegativeStockEvenWithoutTheGuard() {
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory SET available_quantity = -1 WHERE product_id = ?", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownProductUpdatesNothing() {
        assertThat(repository.reserve(9999L, 1)).isZero();
    }
}
