package org.cobbleutils.cobblecomputils.showdown;

import org.cobbleutils.cobblecomputils.config.JsonConfig;

/**
 * {@code config/cobblecomputils/showdown.json}. EVs and moves are priced by
 * evedit.json and movetutor.json; these are the extras only import can change.
 */
public final class ShowdownConfig {
    /** Set to false to allow export only. */
    public boolean allowImport = true;
    public long naturePrice = 500;
    public long abilityPrice = 1000;
    public long teraTypePrice = 500;

    private static ShowdownConfig current = new ShowdownConfig();

    public static ShowdownConfig get() {
        return current;
    }

    public static void load() {
        current = JsonConfig.load("showdown.json", ShowdownConfig.class, ShowdownConfig::new, current);
    }
}
