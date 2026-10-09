package com.orderflow.inventory;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InventoryApiIT extends IntegrationTestBase {

    @Test
    void restockAddsToAvailableQuantity() throws Exception {
        TestUser admin = createAdmin();
        long id = createProduct("Mug", "1.00", 2);

        postAs(admin, "/api/inventory/" + id + "/restock", Map.of("quantity", 8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableQuantity").value(10));
        postAs(admin, "/api/inventory/9999/restock", Map.of("quantity", 1)).andExpect(status().isNotFound());
    }

    @Test
    void adjustUsesOptimisticLockingOnVersion() throws Exception {
        TestUser admin = createAdmin();
        TestUser customer = registerCustomer();
        long id = createProduct("Mug", "1.00", 10);
        long version = data(getAs(admin, "/api/inventory/" + id)).get("version").asLong();

        // a customer order changes the row (and its version) after the admin read it
        placeOrderOk(customer, id, 1);

        putAs(admin, "/api/inventory/" + id, Map.of("availableQuantity", 50, "expectedVersion", version))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STALE_INVENTORY_VERSION"));

        long fresh = data(getAs(admin, "/api/inventory/" + id)).get("version").asLong();
        putAs(admin, "/api/inventory/" + id, Map.of("availableQuantity", 50, "expectedVersion", fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableQuantity").value(50))
                .andExpect(jsonPath("$.data.reservedQuantity").value(1));
    }
}
