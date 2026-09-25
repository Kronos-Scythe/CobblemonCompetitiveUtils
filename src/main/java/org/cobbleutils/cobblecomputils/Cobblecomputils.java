package org.cobbleutils.cobblecomputils;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.cobbleutils.cobblecomputils.capture.CaptureConfig;
import org.cobbleutils.cobblecomputils.capture.LevelCapCapture;
import org.cobbleutils.cobblecomputils.economy.Economy;
import org.cobbleutils.cobblecomputils.evedit.EvEditCommand;
import org.cobbleutils.cobblecomputils.evedit.EvEditConfig;
import org.cobbleutils.cobblecomputils.movetutor.MoveTutorCommand;
import org.cobbleutils.cobblecomputils.movetutor.MoveTutorConfig;
import org.cobbleutils.cobblecomputils.scout.ScoutCommand;
import org.cobbleutils.cobblecomputils.showdown.ShowdownCommand;
import org.cobbleutils.cobblecomputils.showdown.ShowdownConfig;
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
        MoveTutorConfig.load();
        ShowdownConfig.load();
        CaptureConfig.load();
        LevelCapCapture.register();
        CommandRegistrationCallback.EVENT.register(EvEditCommand::register);
        CommandRegistrationCallback.EVENT.register(MoveTutorCommand::register);
        CommandRegistrationCallback.EVENT.register(ScoutCommand::register);
        CommandRegistrationCallback.EVENT.register(ShowdownCommand::register);
    }
}
