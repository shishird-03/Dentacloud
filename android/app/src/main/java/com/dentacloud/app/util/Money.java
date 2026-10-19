package com.dentacloud.app.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Money is always integer paise (PRD 6). Rupees only exist at the UI edge. */
public final class Money {
    private static final Locale IN = new Locale.Builder().setLanguage("en").setRegion("IN").build();

    private Money() {}

    public static String format(long paise) {
        NumberFormat f = NumberFormat.getCurrencyInstance(IN);
        return f.format(BigDecimal.valueOf(paise, 2));
    }

    /** "1,250.5" -> 125050. Throws NumberFormatException on garbage. */
    public static long parseRupees(String text) {
        String clean = text.replace(",", "").replace("₹", "").trim();
        if (clean.isEmpty()) return 0;
        return new BigDecimal(clean).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    /** amount * basisPoints / 10000, rounded half-up (1800 bp = 18%). */
    public static long percentOf(long paise, int basisPoints) {
        return BigDecimal.valueOf(paise).multiply(BigDecimal.valueOf(basisPoints))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
