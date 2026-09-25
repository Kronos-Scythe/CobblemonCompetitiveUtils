package org.cobbleutils.cobblecomputils.evedit;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.cobbleutils.cobblecomputils.config.JsonConfig;

/**
 * Prices for /evedit, stored in {@code config/cobblecomputils/evedit.json}.
 * Written with defaults on first start; edit it and run {@code /evedit reload}.
 */
public final class EvEditConfig {
    private static final long DEFAULT_PRICE = 10;

    /** Set to false to make EV editing free even with CobbleDollars installed. */
    public boolean charge = true;
    /** CobbleDollars per EV point added, by stat (hp, attack, defence, special_attack, special_defence, speed). */
    public Map<String, Long> pricePerEv = defaultPrices();
    /** Share of the price paid back when EVs are lowered, 0-100. 0 means lowering is free but refunds nothing. */
    public int refundPercent = 0;

    private static EvEditConfig current = new EvEditConfig();

    public static EvEditConfig get() {
        return current;
    }

    public long price(Stat stat) {
        Long price = pricePerEv.get(stat.getIdentifier().getPath());
        return price == null ? DEFAULT_PRICE : Math.max(0, price);
    }

    public int refundPercent() {
        return Math.max(0, Math.min(100, refundPercent));
    }

    /** Loads the file, creating it with defaults if missing. Keeps the previous config if the file is broken. */
    public static void load() {
        current = JsonConfig.load("evedit.json", EvEditConfig.class, EvEditConfig::new, current);
        if (current.pricePerEv == null) {
            current.pricePerEv = defaultPrices();
        }
    }

    private static Map<String, Long> defaultPrices() {
        Map<String, Long> prices = new LinkedHashMap<>();
        for (String stat : new String[] {"hp", "attack", "defence", "special_attack", "special_defence", "speed"}) {
            prices.put(stat, DEFAULT_PRICE);
        }
        return prices;
    }
}
