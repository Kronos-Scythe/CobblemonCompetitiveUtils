package org.cobbleutils.cobblecomputils.capture;

import org.cobbleutils.cobblecomputils.config.JsonConfig;

/** {@code config/cobblecomputils/capture.json}. */
public final class CaptureConfig {
    /** Wild Pokémon above the player's RCT level cap break free of every ball. */
    public boolean blockAboveLevelCap = true;
    /** Give the thrown ball back when a capture is blocked. */
    public boolean returnBall = true;

    private static CaptureConfig current = new CaptureConfig();

    public static CaptureConfig get() {
        return current;
    }

    public static void load() {
        current = JsonConfig.load("capture.json", CaptureConfig.class, CaptureConfig::new, current);
    }
}
