package com.orderflow.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "CREATED, PAYMENT_PENDING",
            "CREATED, CANCELLED",
            "PAYMENT_PENDING, CONFIRMED",
            "PAYMENT_PENDING, CANCELLED",
            "CONFIRMED, PROCESSING",
            "PROCESSING, SHIPPED",
            "SHIPPED, DELIVERED"})
    void permittedTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "CREATED, CONFIRMED",
            "CREATED, SHIPPED",
            "PAYMENT_PENDING, PROCESSING",
            "CONFIRMED, CANCELLED",
            "CONFIRMED, PAYMENT_PENDING",
            "PROCESSING, DELIVERED",
            "SHIPPED, CANCELLED",
            "DELIVERED, CANCELLED",
            "DELIVERED, SHIPPED",
            "CANCELLED, CREATED",
            "CANCELLED, CONFIRMED"})
    void forbiddenTransitions(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void exactlySevenTransitionsExist() {
        long count = EnumSet.allOf(OrderStatus.class).stream().mapToLong(s -> s.allowedNext().size()).sum();
        assertThat(count).isEqualTo(7);
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void noStatusTransitionsToItself(OrderStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Test
    void onlyDeliveredAndCancelledAreTerminal() {
        Set<OrderStatus> terminal = EnumSet.noneOf(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            if (s.isTerminal()) {
                terminal.add(s);
            }
        }
        assertThat(terminal).containsExactlyInAnyOrder(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
    }

    @Test
    void reservationIsHeldOnlyBeforeConfirmation() {
        assertThat(OrderStatus.CREATED.holdsReservation()).isTrue();
        assertThat(OrderStatus.PAYMENT_PENDING.holdsReservation()).isTrue();
        assertThat(EnumSet.complementOf(EnumSet.of(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING)))
                .noneMatch(OrderStatus::holdsReservation);
    }
}
