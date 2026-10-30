package dev.autoeattotem.util;

import dev.autoeattotem.config.ModConfig;
import java.util.Random;

public final class Timing {
    private static final Random R = new Random();
    private Timing() {}
    public static long now() { return System.currentTimeMillis(); }

    /** Delay to use; with human-like timing it is randomised around the base value. Never below 40 ms. */
    public static long delay(long baseMs) {
        baseMs = Math.max(baseMs, 40);
        if (!ModConfig.get().humanLikeTiming) return baseMs;
        long v = (long) (baseMs * (0.8 + R.nextDouble() * 0.6));
        if (R.nextInt(12) == 0) v += R.nextInt(80);
        return v;
    }
    public static double jitter(double amp) { return (R.nextDouble() - 0.5) * 2 * amp; }
}
