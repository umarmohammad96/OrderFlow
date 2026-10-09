package com.orderflow.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.auth.dto.RegisterRequest;
import com.orderflow.cart.dto.AddCartItemRequest;
import com.orderflow.order.dto.CreateOrderRequest;
import com.orderflow.order.dto.OrderItemRequest;
import com.orderflow.product.dto.CreateProductRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void registerRequestRules() {
        assertThat(invalidFields(new RegisterRequest("Asha", "asha@example.com", "password123"))).isEmpty();
        assertThat(invalidFields(new RegisterRequest("", "not-an-email", "short")))
                .containsExactlyInAnyOrder("name", "email", "password");
        assertThat(invalidFields(new RegisterRequest("Asha", "a@b.co", "x".repeat(73)))).containsExactly("password");
    }

    @Test
    void productRequestRules() {
        assertThat(invalidFields(new CreateProductRequest("Mug", null, new BigDecimal("9.99"), 1L, 10))).isEmpty();
        assertThat(invalidFields(new CreateProductRequest(" ", null, new BigDecimal("0.00"), null, -1)))
                .containsExactlyInAnyOrder("name", "price", "categoryId", "initialStock");
        assertThat(invalidFields(new CreateProductRequest("Mug", null, new BigDecimal("9.999"), 1L, 1))).containsExactly("price");
    }

    @Test
    void orderRequestRules() {
        assertThat(invalidFields(new CreateOrderRequest(List.of(new OrderItemRequest(1L, 1))))).isEmpty();
        assertThat(invalidFields(new CreateOrderRequest(List.of()))).containsExactly("items");
        assertThat(invalidFields(new CreateOrderRequest(List.of(new OrderItemRequest(null, 0)))))
                .containsExactlyInAnyOrder("items[0].productId", "items[0].quantity");
    }

    @Test
    void cartQuantityBounds() {
        assertThat(invalidFields(new AddCartItemRequest(1L, 99))).isEmpty();
        assertThat(invalidFields(new AddCartItemRequest(1L, 100))).containsExactly("quantity");
        assertThat(invalidFields(new AddCartItemRequest(1L, 0))).containsExactly("quantity");
    }
}
