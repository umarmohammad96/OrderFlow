package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class OrderApiIT extends IntegrationTestBase {

    @Test
    void createOrderSnapshotsPricesReservesStockAndCleansCart() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "12.50", 10);
        long pen = createProduct("Pen", "1.25", 10);
        postAs(user, "/api/cart/items", Map.of("productId", mug, "quantity", 2));
        postAs(user, "/api/cart/items", Map.of("productId", pen, "quantity", 1));

        placeOrder(user, "order-key-0001", "{\"items\":[{\"productId\":" + mug + ",\"quantity\":2}]}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/orders/1"))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.totalAmount").value(25.00))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(12.50))
                .andExpect(jsonPath("$.data.items[0].productName").value("Mug"))
                .andExpect(jsonPath("$.data.cancellable").value(true))
                .andExpect(jsonPath("$.data.payable").value(true));

        assertThat(available(mug)).isEqualTo(8);
        assertThat(reserved(mug)).isEqualTo(2);
        // only the ordered product leaves the cart
        getAs(user, "/api/cart").andExpect(jsonPath("$.data.items[*].productName", contains("Pen")));

        // later price changes do not affect the order
        jdbc.update("UPDATE products SET price = 99 WHERE id = ?", mug);
        getAs(user, "/api/orders/1").andExpect(jsonPath("$.data.totalAmount").value(25.00));
    }

    @Test
    void idempotencyKeyIsRequiredAndValidated() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "1.00", 10);
        mvc.perform(MockMvcRequestBuilders.post("/api/orders").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(mug, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        placeOrder(user, "bad key!", orderBody(mug, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        assertThat(count("orders")).isZero();
    }

    @Test
    void validationAndBusinessErrors() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "1.00", 1);
        placeOrder(user, "order-key-0002", "{\"items\":[]}").andExpect(status().isBadRequest());
        placeOrder(user, "order-key-0003", "{not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
        placeOrder(user, "order-key-0004", orderBody(9999, 1)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_UNAVAILABLE"));
        placeOrder(user, "order-key-0005", orderBody(mug, 2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OUT_OF_STOCK"));
    }

    @Test
    void multiItemOrderIsAllOrNothing() throws Exception {
        TestUser user = registerCustomer();
        long plenty = createProduct("Plenty", "1.00", 10);
        long scarce = createProduct("Scarce", "1.00", 1);

        placeOrder(user, "order-key-0006",
                "{\"items\":[{\"productId\":" + plenty + ",\"quantity\":3},{\"productId\":" + scarce + ",\"quantity\":2}]}")
                .andExpect(status().isConflict());

        // the reservation of the first line was rolled back with the failed transaction
        assertThat(available(plenty)).isEqualTo(10);
        assertThat(reserved(plenty)).isZero();
        assertThat(count("orders")).isZero();
        assertThat(count("idempotency_keys")).isZero();
    }

    @Test
    void ordersAreVisibleOnlyToTheirOwner() throws Exception {
        TestUser owner = registerCustomer();
        TestUser other = registerCustomer();
        TestUser admin = createAdmin();
        long mug = createProduct("Mug", "1.00", 10);
        long orderId = placeOrderOk(owner, mug, 1);

        getAs(owner, "/api/orders/" + orderId).andExpect(status().isOk());
        getAs(other, "/api/orders/" + orderId).andExpect(status().isNotFound());
        mvc.perform(MockMvcRequestBuilders.post("/api/orders/" + orderId + "/cancel").header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        getAs(admin, "/api/orders/" + orderId).andExpect(status().isOk());
        getAs(other, "/api/orders").andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    void listIsNewestFirstAndPaginated() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "1.00", 10);
        placeOrderOk(user, mug, 1);
        placeOrderOk(user, mug, 2);
        placeOrderOk(user, mug, 3);

        getAs(user, "/api/orders?size=2")
                .andExpect(jsonPath("$.data.content[*].itemCount", contains(3, 2)))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void cancelReleasesReservedStockAndCannotBeRepeated() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "1.00", 5);
        long orderId = placeOrderOk(user, mug, 3);
        assertThat(available(mug)).isEqualTo(2);

        postAs(user, "/api/orders/" + orderId + "/cancel", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.statusHistory[*].to", contains("CREATED", "CANCELLED")));
        assertThat(available(mug)).isEqualTo(5);
        assertThat(reserved(mug)).isZero();

        postAs(user, "/api/orders/" + orderId + "/cancel", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
        assertThat(available(mug)).isEqualTo(5);
    }

    @Test
    void fullLifecycleAndDeliveredOrderCannotBeCancelled() throws Exception {
        TestUser user = registerCustomer();
        TestUser admin = createAdmin();
        long mug = createProduct("Mug", "1.00", 5);
        long orderId = placeOrderOk(user, mug, 1);

        // fulfilment cannot start before payment
        patchAs(admin, "/api/admin/orders/" + orderId + "/status", Map.of("status", "SHIPPED"))
                .andExpect(status().isConflict());
        pay(user, orderId, "tok_visa").andExpect(status().isCreated());

        for (String next : new String[] {"PROCESSING", "SHIPPED", "DELIVERED"}) {
            patchAs(admin, "/api/admin/orders/" + orderId + "/status", Map.of("status", next))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(next));
        }
        getAs(user, "/api/orders/" + orderId).andExpect(jsonPath("$.data.statusHistory[*].to",
                contains("CREATED", "PAYMENT_PENDING", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED")));

        postAs(user, "/api/orders/" + orderId + "/cancel", "").andExpect(status().isConflict());
        postAs(admin, "/api/orders/" + orderId + "/cancel", "").andExpect(status().isConflict());
        assertThat(available(mug)).isEqualTo(4);
        assertThat(reserved(mug)).isZero();
    }

    @Test
    void adminCannotSetPaymentDrivenStatusesManually() throws Exception {
        TestUser user = registerCustomer();
        TestUser admin = createAdmin();
        long orderId = placeOrderOk(user, createProduct("Mug", "1.00", 5), 1);

        patchAs(admin, "/api/admin/orders/" + orderId + "/status", Map.of("status", "CONFIRMED"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("STATUS_NOT_MANUALLY_SETTABLE"));
        patchAs(admin, "/api/admin/orders/" + orderId + "/status", Map.of("status", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminListFiltersByStatus() throws Exception {
        TestUser user = registerCustomer();
        TestUser admin = createAdmin();
        long mug = createProduct("Mug", "1.00", 5);
        placeOrderOk(user, mug, 1);
        long paid = placeOrderOk(user, mug, 1);
        pay(user, paid, "tok_visa");

        getAs(admin, "/api/admin/orders?status=CONFIRMED")
                .andExpect(jsonPath("$.data.content[*].id", contains((int) paid)));
        getAs(admin, "/api/admin/orders").andExpect(jsonPath("$.data.totalElements").value(2));
    }
}
