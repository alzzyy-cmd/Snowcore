package me.snownw.core.util;

import java.math.BigDecimal;
import java.util.Locale;

public final class MoneyFormat {
    private MoneyFormat() {}

    public static String nicest(double value) {
        double abs = Math.abs(value);
        if (abs >= 1e15) return compact(value / 1e15, "Q");
        if (abs >= 1e12) return compact(value / 1e12, "T");
        if (abs >= 1e9) return compact(value / 1e9, "B");
        if (abs >= 1e6) return compact(value / 1e6, "M");
        if (abs >= 1e3) return compact(value / 1e3, "K");
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static String compact(double v, String suffix) {
        if (Math.abs(v - Math.rint(v)) < 0.05) {
            return String.format(Locale.US, "%.0f%s", Math.rint(v), suffix);
        }
        return String.format(Locale.US, "%.1f%s", v, suffix);
    }

    /** Parse 50b, 1.5m, 1t, 1q, 1000 – case insensitive. */
    public static double parse(String raw) {
        if (raw == null || raw.isBlank()) throw new NumberFormatException("empty");
        String s = raw.trim().replace(",", "").replace("_", "").toLowerCase(Locale.ROOT);
        BigDecimal mult = BigDecimal.ONE;
        if (s.endsWith("q")) {
            mult = new BigDecimal("1000000000000000");
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("t")) {
            mult = new BigDecimal("1000000000000");
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("b")) {
            mult = new BigDecimal("1000000000");
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            mult = new BigDecimal("1000000");
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("k")) {
            mult = new BigDecimal("1000");
            s = s.substring(0, s.length() - 1);
        }
        if (s.isBlank()) throw new NumberFormatException("empty number");
        return new BigDecimal(s).multiply(mult).doubleValue();
    }
}
