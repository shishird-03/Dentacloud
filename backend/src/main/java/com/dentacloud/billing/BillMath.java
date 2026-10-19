package com.dentacloud.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Invoice totals, identical to the Android app's BillMath: tax per line on the line amount, discount
 * off the subtotal, total = subtotal - discount + tax. Integer paise throughout.
 */
public final class BillMath {
    private BillMath() {}

    public record Line(long amountPaise, int qty, int taxBasisPoints) {}

    public record Totals(long subtotalPaise, long discountPaise, long taxPaise, long totalPaise) {}

    public static Totals compute(List<Line> lines, long discountPaise) {
        long subtotal = 0, tax = 0;
        for (Line l : lines) {
            long amount = Math.multiplyExact(l.amountPaise(), l.qty());
            subtotal = Math.addExact(subtotal, amount);
            tax = Math.addExact(tax, BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(l.taxBasisPoints()))
                    .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact());
        }
        if (discountPaise < 0 || discountPaise > subtotal) {
            throw new IllegalArgumentException("discount must be between 0 and the subtotal");
        }
        return new Totals(subtotal, discountPaise, tax, subtotal - discountPaise + tax);
    }
}
