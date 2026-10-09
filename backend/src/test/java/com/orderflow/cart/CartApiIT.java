package com.orderflow.cart;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CartApiIT extends IntegrationTestBase {

    @Test
    void emptyCartForNewUser() throws Exception {
        TestUser user = registerCustomer();
        getAs(user, "/api/cart").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.subtotal").value(0.0));
    }

    @Test
    void addMergesQuantitiesAndComputesTotals() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "12.50", 10);
        long pen = createProduct("Pen", "1.25", 10);

        postAs(user, "/api/cart/items", Map.of("productId", mug, "quantity", 1)).andExpect(status().isOk());
        postAs(user, "/api/cart/items", Map.of("productId", pen, "quantity", 4)).andExpect(status().isOk());
        postAs(user, "/api/cart/items", Map.of("productId", mug, "quantity", 2))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3))
                .andExpect(jsonPath("$.data.items[0].lineTotal").value(37.50))
                .andExpect(jsonPath("$.data.totalQuantity").value(7))
                .andExpect(jsonPath("$.data.subtotal").value(42.50));
    }

    @Test
    void updateAndRemoveItems() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "10.00", 10);
        long itemId = data(postAs(user, "/api/cart/items", Map.of("productId", mug, "quantity", 1))).at("/items/0/id").asLong();

        patchAs(user, "/api/cart/items/" + itemId, Map.of("quantity", 5))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.subtotal").value(50.0));
        deleteAs(user, "/api/cart/items/" + itemId)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(0)));
        deleteAs(user, "/api/cart/items/" + itemId).andExpect(status().isNotFound());
    }

    @Test
    void cannotTouchAnotherUsersCartItem() throws Exception {
        TestUser owner = registerCustomer();
        TestUser intruder = registerCustomer();
        long mug = createProduct("Mug", "10.00", 10);
        long itemId = data(postAs(owner, "/api/cart/items", Map.of("productId", mug, "quantity", 1))).at("/items/0/id").asLong();

        patchAs(intruder, "/api/cart/items/" + itemId, Map.of("quantity", 9)).andExpect(status().isNotFound());
        deleteAs(intruder, "/api/cart/items/" + itemId).andExpect(status().isNotFound());
        getAs(owner, "/api/cart").andExpect(jsonPath("$.data.items[0].quantity").value(1));
    }

    @Test
    void quantityLimitsAndUnknownProducts() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "10.00", 500);

        postAs(user, "/api/cart/items", Map.of("productId", mug, "quantity", 0)).andExpect(status().isBadRequest());
        postAs(user, "/api/cart/items", Map.of("productId", mug, "quantity", 60)).andExpect(status().isOk());
        postAs(user, "/api/cart/items", Map.of("productId", mug, "quantity", 60))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CART_QUANTITY_LIMIT"));
        postAs(user, "/api/cart/items", Map.of("productId", 9999, "quantity", 1)).andExpect(status().isNotFound());
    }

    @Test
    void cartFlagsItemsThatExceedStock() throws Exception {
        TestUser user = registerCustomer();
        long mug = createProduct("Mug", "10.00", 2);
        postAs(user, "/api/cart/items", Map.of("productId", mug, "quantity", 3))
                .andExpect(jsonPath("$.data.items[0].availableQuantity").value(2))
                .andExpect(jsonPath("$.data.items[0].purchasable").value(false));
    }
}
