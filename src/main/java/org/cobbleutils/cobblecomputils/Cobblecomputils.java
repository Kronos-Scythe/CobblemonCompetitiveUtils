package org.cobbleutils.cobblecomputils;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.cobbleutils.cobblecomputils.economy.Economy;
import org.cobbleutils.cobblecomputils.evedit.EvEditCommand;
import org.cobbleutils.cobblecomputils.evedit.EvEditConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cobblecomputils implements ModInitializer {
    public static final String MOD_ID = "cobblecomputils";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Economy economy;

    public static Economy economy() {
        return economy;
    }

    @Override
    public void onInitialize() {
        economy = Economy.detect();
        EvEditConfig.load();
        CommandRegistrationCallback.EVENT.register(EvEditCommand::register);
    }
}
