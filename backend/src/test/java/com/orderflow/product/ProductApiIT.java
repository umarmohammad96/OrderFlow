package com.orderflow.product;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductApiIT extends IntegrationTestBase {

    @Test
    void listIsPublicPaginatedAndIncludesLiveStock() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createProduct("Product " + i, i + "0.00", i);
        }
        mvc.perform(get("/api/products?page=0&size=2&sort=PRICE_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[*].name", contains("Product 1", "Product 2")))
                .andExpect(jsonPath("$.data.content[0].availableQuantity").value(1))
                .andExpect(jsonPath("$.data.content[0].inStock").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.last").value(false));
        mvc.perform(get("/api/products?page=2&size=2&sort=PRICE_ASC"))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void filtersByCategoryAndSearchText() throws Exception {
        long books = createCategory("Books");
        long toys = createCategory("Toys");
        jdbc.update("INSERT INTO products (name, price, category_id) VALUES ('Effective Java', 40, ?), ('Clean Code', 35, ?), ('Yo-yo', 5, ?)",
                books, books, toys);

        mvc.perform(get("/api/products?categoryId=" + books))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/products?q=JAVA"))
                .andExpect(jsonPath("$.data.content[*].name", contains("Effective Java")));
        mvc.perform(get("/api/products?q=%25")) // a literal '%' must not match everything
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void invalidPagingAndSortAreRejected() throws Exception {
        mvc.perform(get("/api/products?size=1000")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/products?sort=password")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.error.message").value(
                        "Invalid value 'password' for parameter 'sort'; allowed: [NEWEST, PRICE_ASC, PRICE_DESC, NAME]"));
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        mvc.perform(get("/api/products/999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void adminCreatesProductWithInitialStock() throws Exception {
        TestUser admin = createAdmin();
        long category = createCategory("Gadgets");

        postAs(admin, "/api/products", Map.of("name", "Drone", "description", "Flies", "price", "249.99",
                "categoryId", category, "initialStock", 7))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/products/1"))
                .andExpect(jsonPath("$.data.price").value(249.99))
                .andExpect(jsonPath("$.data.categoryName").value("Gadgets"));

        mvc.perform(get("/api/products/1")).andExpect(jsonPath("$.data.availableQuantity").value(7));
    }

    @Test
    void createValidatesInputAndReferences() throws Exception {
        TestUser admin = createAdmin();
        postAs(admin, "/api/products", Map.of("name", "", "price", "-1", "categoryId", 1, "initialStock", 1))
                .andExpect(status().isBadRequest());
        postAs(admin, "/api/products", Map.of("name", "X", "price", "1.00", "categoryId", 999, "initialStock", 1))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateChangesPriceAndDeactivateHidesProductFromCustomers() throws Exception {
        TestUser admin = createAdmin();
        long id = createProduct("Lamp", "20.00", 3);
        long category = jdbc.queryForObject("SELECT category_id FROM products WHERE id = ?", Long.class, id);

        putAs(admin, "/api/products/" + id, Map.of("name", "Desk Lamp", "price", "25.50", "categoryId", category, "active", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Desk Lamp"))
                .andExpect(jsonPath("$.data.price").value(25.50));

        deleteAs(admin, "/api/products/" + id).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$.data.totalElements").value(0));
        getAs(admin, "/api/products/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        // admins can list inactive products; customers asking for them still get the active catalogue only
        getAs(admin, "/api/products?includeInactive=true").andExpect(jsonPath("$.data.totalElements").value(1));
        TestUser customer = registerCustomer();
        getAs(customer, "/api/products?includeInactive=true").andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void categories() throws Exception {
        TestUser admin = createAdmin();
        postAs(admin, "/api/categories", Map.of("name", "Garden")).andExpect(status().isCreated());
        postAs(admin, "/api/categories", Map.of("name", "garden")).andExpect(status().isConflict());
        mvc.perform(get("/api/categories")).andExpect(jsonPath("$.data[*].name", contains("Garden")));
    }
}
