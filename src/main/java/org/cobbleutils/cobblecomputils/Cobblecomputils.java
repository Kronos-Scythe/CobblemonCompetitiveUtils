package org.cobbleutils.cobblecomputils;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.cobbleutils.cobblecomputils.evedit.EvEditCommand;

public class Cobblecomputils implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(EvEditCommand::register);
    }
}
