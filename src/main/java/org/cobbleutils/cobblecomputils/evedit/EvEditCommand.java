package org.cobbleutils.cobblecomputils.evedit;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/** {@code /evedit}: opens the EV editor for the player's current party. */
public final class EvEditCommand {
    private EvEditCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("evedit")
            .requires(ServerCommandSource::isExecutedByPlayer)
            .executes(context -> {
                EvEditMenus.openParty(context.getSource().getPlayerOrThrow());
                return 1;
            }));
    }
}
