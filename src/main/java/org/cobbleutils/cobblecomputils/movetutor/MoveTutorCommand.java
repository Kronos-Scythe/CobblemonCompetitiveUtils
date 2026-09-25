package org.cobbleutils.cobblecomputils.movetutor;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** {@code /movetutor} opens the move tutor for the player's party; {@code /movetutor reload} re-reads the prices. */
public final class MoveTutorCommand {
    public static final String NAME = "movetutor";

    private MoveTutorCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal(NAME)
            .executes(context -> {
                MoveTutorMenus.openParty(context.getSource().getPlayerOrThrow());
                return 1;
            })
            .then(CommandManager.literal("reload")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> {
                    MoveTutorConfig.load();
                    context.getSource().sendFeedback(() -> Text.literal("Reloaded /" + NAME + " prices"), true);
                    return 1;
                })));
    }
}
