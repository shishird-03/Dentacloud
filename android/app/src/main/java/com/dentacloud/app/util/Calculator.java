package com.dentacloud.app.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Part A #2: the billing screen calculator (ADD, SUB, MUL, DIV) on rupee amounts. */
public final class Calculator {
    public enum Op { ADD, SUB, MUL, DIV }

    private Calculator() {}

    /** Returns the result rounded to 2 decimals; throws ArithmeticException on divide by zero. */
    public static BigDecimal apply(BigDecimal a, BigDecimal b, Op op) {
        BigDecimal r = switch (op) {
            case ADD -> a.add(b);
            case SUB -> a.subtract(b);
            case MUL -> a.multiply(b);
            case DIV -> {
                if (b.signum() == 0) throw new ArithmeticException("Cannot divide by zero");
                yield a.divide(b, 2, RoundingMode.HALF_UP);
            }
        };
        return r.setScale(2, RoundingMode.HALF_UP);
    }
}
