package com.dentacloud.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

public class MoneyMathTest {
    @Test
    public void parsesRupeesToPaise() {
        assertEquals(125050, Money.parseRupees("1,250.5"));
        assertEquals(30000, Money.parseRupees("₹ 300"));
        assertEquals(0, Money.parseRupees(""));
        assertEquals(1, Money.parseRupees("0.005")); // half-up
    }

    @Test
    public void percentRoundsHalfUp() {
        assertEquals(18000, Money.percentOf(100000, 1800));
        assertEquals(1, Money.percentOf(5, 1000)); // 0.5 paise -> 1
    }

    @Test
    public void billTotals() {
        BillMath.Totals t = BillMath.compute(List.of(
                new BillMath.Line(1_500_00, 2, 0),
                new BillMath.Line(1_000_00, 1, 1800)), 500_00);
        assertEquals(4_000_00, t.subtotalPaise());
        assertEquals(180_00, t.taxPaise());
        assertEquals(500_00, t.discountPaise());
        assertEquals(3_680_00, t.totalPaise());
    }

    @Test
    public void discountCannotExceedSubtotal() {
        assertThrows(IllegalArgumentException.class,
                () -> BillMath.compute(List.of(new BillMath.Line(100, 1, 0)), 101));
    }

    @Test
    public void calculatorOps() {
        BigDecimal a = new BigDecimal("1200"), b = new BigDecimal("3");
        assertEquals("1203.00", Calculator.apply(a, b, Calculator.Op.ADD).toPlainString());
        assertEquals("1197.00", Calculator.apply(a, b, Calculator.Op.SUB).toPlainString());
        assertEquals("3600.00", Calculator.apply(a, b, Calculator.Op.MUL).toPlainString());
        assertEquals("400.00", Calculator.apply(a, b, Calculator.Op.DIV).toPlainString());
        assertThrows(ArithmeticException.class, () -> Calculator.apply(a, BigDecimal.ZERO, Calculator.Op.DIV));
    }

    @Test
    public void uuid7IsTimeOrdered() {
        String a = Ids.uuid7(), b = Ids.uuid7();
        assertEquals('7', a.charAt(14));
        assertEquals(-1, Integer.signum(a.compareTo(b)));
    }
}
