package org.cobbleutils.cobblecomputils.scout;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/** {@code /scout}: shows the teams of the next RCT trainers in the player's series. */
public final class ScoutCommand {
    private ScoutCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("scout")
            .executes(context -> {
                ScoutMenus.open(context.getSource().getPlayerOrThrow());
                return 1;
            }));
    }
}
