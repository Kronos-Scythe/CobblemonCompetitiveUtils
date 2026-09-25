package org.cobbleutils.cobblecomputils.movetutor;

import java.util.LinkedHashMap;
import java.util.Map;
import org.cobbleutils.cobblecomputils.config.JsonConfig;

/**
 * Prices for /movetutor, stored in {@code config/cobblecomputils/movetutor.json}.
 * Written with defaults on first start; edit it and run {@code /movetutor reload}.
 */
public final class MoveTutorConfig {
    /** Set to false to make the tutor free even with CobbleDollars installed. */
    public boolean charge = true;
    /**
     * CobbleDollars per move, by where it's learned from (level_up, evolution, egg,
     * tutor, tm, form_change). A move learnable several ways costs the cheapest.
     * Moves the Pokémon knew before (Cobblemon's relearnable list) are always free.
     */
    public Map<String, Long> prices = defaultPrices();
    /**
     * Whether level-up moves above the Pokémon's current level can be taught.
     * Off by default so the tutor doesn't bypass level progression (RCT level caps).
     */
    public boolean levelUpAboveCurrentLevel = false;

    private static MoveTutorConfig current = new MoveTutorConfig();

    public static MoveTutorConfig get() {
        return current;
    }

    public long price(MoveSource source) {
        Long price = prices.get(source.key);
        Long fallback = defaultPrices().get(source.key);
        return Math.max(0, price != null ? price : fallback);
    }

    public static void load() {
        current = JsonConfig.load("movetutor.json", MoveTutorConfig.class, MoveTutorConfig::new, current);
        if (current.prices == null) {
            current.prices = defaultPrices();
        }
    }

    private static Map<String, Long> defaultPrices() {
        Map<String, Long> prices = new LinkedHashMap<>();
        prices.put(MoveSource.LEVEL_UP.key, 0L);
        prices.put(MoveSource.EVOLUTION.key, 0L);
        prices.put(MoveSource.EGG.key, 1000L);
        prices.put(MoveSource.TUTOR.key, 500L);
        prices.put(MoveSource.TM.key, 500L);
        prices.put(MoveSource.FORM_CHANGE.key, 500L);
        return prices;
    }
}
