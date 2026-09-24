package org.cobbleutils.cobblecomputils.evedit;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.cobbleutils.cobblecomputils.Cobblecomputils;

/**
 * Prices for /evedit, stored in {@code config/cobblecomputils/evedit.json}.
 * Written with defaults on first start; edit it and run {@code /evedit reload}.
 */
public final class EvEditConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
        .resolve(Cobblecomputils.MOD_ID).resolve("evedit.json");
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
        try {
            if (Files.notExists(PATH)) {
                Files.createDirectories(PATH.getParent());
                try (Writer writer = Files.newBufferedWriter(PATH)) {
                    GSON.toJson(new EvEditConfig(), writer);
                }
            }
            try (Reader reader = Files.newBufferedReader(PATH)) {
                EvEditConfig loaded = GSON.fromJson(reader, EvEditConfig.class);
                if (loaded == null) {
                    throw new JsonParseException("empty file");
                }
                if (loaded.pricePerEv == null) {
                    loaded.pricePerEv = defaultPrices();
                }
                current = loaded;
            }
        } catch (IOException | JsonParseException e) {
            Cobblecomputils.LOGGER.error("Could not read {}, keeping previous prices", PATH, e);
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
