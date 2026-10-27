package com.tygabeast.kakugane.data;

/** Small helpers for turning serial numbers into stable, well-spread choices and display text. */
public final class Hashing {
    /** Scrambles an int so neighbouring serials (12, 13, 14...) don't get neighbouring results. */
    public static int mix(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] NUMERALS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    /** 44 -> "XLIV". */
    public static String roman(int n) {
        if (n <= 0) return "?";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < VALUES.length; i++) {
            while (n >= VALUES[i]) {
                n -= VALUES[i];
                sb.append(NUMERALS[i]);
            }
        }
        return sb.toString();
    }

    private Hashing() {}
}
