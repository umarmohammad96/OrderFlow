package com.orderflow.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/** Money arithmetic for orders: BigDecimal, 2 decimal places, banker's rounding. */
@Service
public class PricingService {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public record PricedLine(Long productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record Line(Long productId, String productName, int quantity, BigDecimal unitPrice) {
    }

    public List<PricedLine> price(List<Line> lines) {
        return lines.stream().map(l -> {
            if (l.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for product " + l.productId());
            }
            if (l.unitPrice() == null || l.unitPrice().signum() < 0) {
                throw new IllegalArgumentException("Unit price must be non-negative for product " + l.productId());
            }
            BigDecimal unit = l.unitPrice().setScale(SCALE, ROUNDING);
            return new PricedLine(l.productId(), l.productName(), l.quantity(), unit, lineTotal(unit, l.quantity()));
        }).toList();
    }

    public BigDecimal total(List<PricedLine> lines) {
        return lines.stream().map(PricedLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, ROUNDING);
    }
}
