package com.orderflow.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.order.service.PricingService.Line;
import com.orderflow.order.service.PricingService.PricedLine;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private final PricingService pricing = new PricingService();

    @Test
    void computesLineTotalsAndOrderTotal() {
        List<PricedLine> lines = pricing.price(List.of(
                new Line(1L, "Keyboard", 2, new BigDecimal("129.00")),
                new Line(2L, "Book", 3, new BigDecimal("38.75"))));

        assertThat(lines).extracting(PricedLine::lineTotal)
                .containsExactly(new BigDecimal("258.00"), new BigDecimal("116.25"));
        assertThat(pricing.total(lines)).isEqualByComparingTo("374.25").hasScaleOf(2);
    }

    @Test
    void avoidsBinaryFloatingPointErrors() {
        // 0.1 * 3 in double arithmetic is 0.30000000000000004
        List<PricedLine> lines = pricing.price(List.of(new Line(1L, "Gum", 3, new BigDecimal("0.10"))));
        assertThat(pricing.total(lines)).isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    void usesBankersRoundingForSubCentPrices() {
        assertThat(PricingService.lineTotal(new BigDecimal("0.125"), 1)).isEqualTo(new BigDecimal("0.12"));
        assertThat(PricingService.lineTotal(new BigDecimal("0.135"), 1)).isEqualTo(new BigDecimal("0.14"));
    }

    @Test
    void emptyOrderTotalsZero() {
        assertThat(pricing.total(List.of())).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> pricing.price(List.of(new Line(1L, "X", 0, BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePrice() {
        assertThatThrownBy(() -> pricing.price(List.of(new Line(1L, "X", 1, new BigDecimal("-1")))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
