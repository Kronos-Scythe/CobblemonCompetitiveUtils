package org.cobbleutils.cobblecomputils.evedit;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** {@code /evedit} opens the EV editor for the player's party; {@code /evedit reload} re-reads the prices. */
public final class EvEditCommand {
    private EvEditCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("evedit")
            .executes(context -> {
                EvEditMenus.openParty(context.getSource().getPlayerOrThrow());
                return 1;
            })
            .then(CommandManager.literal("reload")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> {
                    EvEditConfig.load();
                    context.getSource().sendFeedback(() -> Text.literal("Reloaded /evedit prices"), true);
                    return 1;
                })));
    }
}
