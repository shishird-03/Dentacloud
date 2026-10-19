package com.dentacloud.app.util;

import java.util.List;

/**
 * Invoice totals. Same rules as the backend's BillMath:
 * tax is per line on the line amount, discount comes off the subtotal, total = subtotal - discount + tax.
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
            tax = Math.addExact(tax, Money.percentOf(amount, l.taxBasisPoints()));
        }
        if (discountPaise < 0 || discountPaise > subtotal) {
            throw new IllegalArgumentException("discount must be between 0 and the subtotal");
        }
        return new Totals(subtotal, discountPaise, tax, subtotal - discountPaise + tax);
    }
}
