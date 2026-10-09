package com.orderflow.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void newOrderStartsCreatedWithHistory() {
        Order order = new Order(7L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getHistory()).singleElement().satisfies(h -> {
            assertThat(h.getFromStatus()).isNull();
            assertThat(h.getToStatus()).isEqualTo(OrderStatus.CREATED);
        });
    }

    @Test
    void transitionsAreRecordedInHistory() {
        Order order = new Order(7L);
        order.transitionTo(OrderStatus.PAYMENT_PENDING);
        order.transitionTo(OrderStatus.CONFIRMED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getHistory()).extracting(OrderStatusHistory::getToStatus)
                .containsExactly(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING, OrderStatus.CONFIRMED);
    }

    @Test
    void illegalTransitionIsRejectedAndStateUnchanged() {
        Order order = new Order(7L);
        order.transitionTo(OrderStatus.PAYMENT_PENDING);
        order.transitionTo(OrderStatus.CONFIRMED);
        order.transitionTo(OrderStatus.PROCESSING);
        order.transitionTo(OrderStatus.SHIPPED);
        order.transitionTo(OrderStatus.DELIVERED);

        assertThatThrownBy(() -> order.transitionTo(OrderStatus.CANCELLED))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("DELIVERED cannot transition to CANCELLED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getHistory()).hasSize(6);
    }

    @Test
    void quantitiesAreAggregatedPerProduct() {
        Order order = new Order(7L);
        order.addItem(1L, "A", 2, new BigDecimal("1.00"));
        order.addItem(2L, "B", 3, new BigDecimal("1.00"));

        assertThat(order.quantitiesByProduct()).containsEntry(1L, 2).containsEntry(2L, 3);
    }

    @Test
    void ownershipCheck() {
        Order order = new Order(7L);
        assertThat(order.isOwnedBy(7L)).isTrue();
        assertThat(order.isOwnedBy(8L)).isFalse();
    }
}
