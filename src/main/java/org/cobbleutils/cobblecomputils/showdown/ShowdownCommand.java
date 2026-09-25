package org.cobbleutils.cobblecomputils.showdown;

import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.cobbleutils.cobblecomputils.Cobblecomputils;
import org.cobbleutils.cobblecomputils.economy.Economy;
import org.cobbleutils.cobblecomputils.gui.Menus;

/**
 * {@code /showdown export}: the party in Showdown format, with click-to-copy.
 * {@code /showdown import [pokepaste link]}: reads sets from a book in hand or a
 * pokepast.es link, shows what would change on matching party Pokémon and the
 * price, and waits for {@code /showdown confirm}.
 */
public final class ShowdownCommand {
    private static final long PENDING_MILLIS = 5 * 60 * 1000;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(String text, long expiresAt) {
    }

    private ShowdownCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("showdown")
            .then(CommandManager.literal("export").executes(context -> {
                export(context.getSource().getPlayerOrThrow());
                return 1;
            }))
            .then(CommandManager.literal("import")
                .executes(context -> {
                    importFromBook(context.getSource().getPlayerOrThrow());
                    return 1;
                })
                .then(CommandManager.argument("link", StringArgumentType.greedyString()).executes(context -> {
                    importFromPokepaste(context.getSource().getPlayerOrThrow(), StringArgumentType.getString(context, "link"));
                    return 1;
                })))
            .then(CommandManager.literal("confirm").executes(context -> {
                confirm(context.getSource().getPlayerOrThrow());
                return 1;
            }))
            .then(CommandManager.literal("reload")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> {
                    ShowdownConfig.load();
                    context.getSource().sendFeedback(() -> Text.literal("Reloaded /showdown prices"), true);
                    return 1;
                })));
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    private static void export(ServerPlayerEntity player) {
        PlayerPartyStore party = ShowdownImport.party(player);
        StringBuilder team = new StringBuilder();
        MutableText line = Text.literal("Showdown export: ").formatted(Formatting.GOLD);
        for (int i = 0; i < party.size(); i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon == null) {
                continue;
            }
            String set = ShowdownFormat.export(pokemon);
            team.append(set).append('\n');
            line.append(copyButton(pokemon.getSpecies().getName(), set, Formatting.AQUA)).append(" ");
        }
        if (team.isEmpty()) {
            player.sendMessage(Text.literal("Your party is empty.").formatted(Formatting.RED), false);
            return;
        }
        player.sendMessage(line, false);
        player.sendMessage(copyButton("Copy whole team", team.toString().trim(), Formatting.GREEN)
            .append(Text.literal("  (paste into Showdown's teambuilder: Import from text)").formatted(Formatting.GRAY)), false);
    }

    private static MutableText copyButton(String label, String text, Formatting color) {
        return Text.literal("[" + label + "]").setStyle(Style.EMPTY.withColor(color)
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(text + "\n\nClick to copy"))));
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    private static void importFromBook(ServerPlayerEntity player) {
        if (!importAllowed(player)) {
            return;
        }
        ItemStack book = player.getStackInHand(Hand.MAIN_HAND);
        String text = null;
        WritableBookContentComponent writable = book.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
        WrittenBookContentComponent written = book.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (writable != null) {
            text = writable.pages().stream().map(page -> page.raw()).collect(Collectors.joining("\n"));
        } else if (written != null) {
            text = written.pages().stream().map(page -> page.raw().getString()).collect(Collectors.joining("\n"));
        }
        if (text == null || text.isBlank()) {
            player.sendMessage(Text.literal("Hold a book with your Showdown team in it, or use /showdown import <pokepast.es link>.")
                .formatted(Formatting.RED), false);
            return;
        }
        preview(player, text);
    }

    private static void importFromPokepaste(ServerPlayerEntity player, String link) {
        if (!importAllowed(player)) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(link.trim());
        } catch (IllegalArgumentException e) {
            uri = null;
        }
        String path = uri == null || uri.getPath() == null ? "" : uri.getPath().replaceAll("^/+|/+$", "");
        if (uri == null || !"pokepast.es".equalsIgnoreCase(uri.getHost()) || path.isEmpty()) {
            player.sendMessage(Text.literal("Only pokepast.es links are supported, like https://pokepast.es/abc123")
                .formatted(Formatting.RED), false);
            return;
        }
        String id = path.split("/")[0];
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://pokepast.es/" + id + "/raw"))
            .timeout(Duration.ofSeconds(10)).GET().build();
        player.sendMessage(Text.literal("Fetching " + id + " from pokepast.es...").formatted(Formatting.GRAY), false);
        // Fetch off the server thread, then continue on it.
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) ->
            player.getServer().execute(() -> {
                if (error != null || response.statusCode() != 200) {
                    player.sendMessage(Text.literal("Couldn't fetch that paste.").formatted(Formatting.RED), false);
                } else {
                    preview(player, response.body());
                }
            }));
    }

    private static boolean importAllowed(ServerPlayerEntity player) {
        if (!ShowdownConfig.get().allowImport) {
            player.sendMessage(Text.literal("Showdown import is disabled on this server.").formatted(Formatting.RED), false);
            return false;
        }
        return !Menus.refuseInBattle(player);
    }

    private static void preview(ServerPlayerEntity player, String text) {
        List<ShowdownFormat.ShowdownSet> sets = ShowdownFormat.parse(text);
        if (sets.isEmpty()) {
            player.sendMessage(Text.literal("No Showdown sets found in that text.").formatted(Formatting.RED), false);
            return;
        }
        ShowdownImport.Plan plan = ShowdownImport.plan(ShowdownImport.party(player), sets);
        player.sendMessage(Text.literal("Showdown import preview:").formatted(Formatting.GOLD), false);
        boolean anything = false;
        for (ShowdownImport.Change change : plan.changes()) {
            anything |= change.changesAnything();
            MutableText header = Text.literal(" ").append(change.pokemon.getDisplayName(false)).formatted(Formatting.AQUA);
            header.append(Text.literal(change.changesAnything() ? "" : ": already matches").formatted(Formatting.GRAY));
            player.sendMessage(header, false);
            for (String line : change.summary) {
                player.sendMessage(Text.literal("   " + line).formatted(Formatting.WHITE), false);
            }
            if (!change.skipped.isEmpty()) {
                player.sendMessage(Text.literal("   Skipped: " + String.join(", ", change.skipped)).formatted(Formatting.GRAY), false);
            }
        }
        if (!plan.unmatched().isEmpty()) {
            player.sendMessage(Text.literal(" Not in your party: " + String.join(", ", plan.unmatched())).formatted(Formatting.GRAY), false);
        }
        if (!anything) {
            player.sendMessage(Text.literal("Nothing to change.").formatted(Formatting.YELLOW), false);
            return;
        }
        PENDING.put(player.getUuid(), new Pending(text, System.currentTimeMillis() + PENDING_MILLIS));
        Economy economy = Cobblecomputils.economy();
        String cost = plan.totalCost() > 0 ? economy.format(BigInteger.valueOf(plan.totalCost())) : "free";
        player.sendMessage(Text.literal("Total: " + cost + "  ").formatted(Formatting.GOLD)
            .append(Text.literal("[Confirm]").setStyle(Style.EMPTY.withColor(Formatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/showdown confirm"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Apply these changes"))))), false);
    }

    private static void confirm(ServerPlayerEntity player) {
        Pending pending = PENDING.remove(player.getUuid());
        if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
            player.sendMessage(Text.literal("Nothing to confirm: run /showdown import first.").formatted(Formatting.RED), false);
            return;
        }
        if (!importAllowed(player)) {
            return;
        }
        // Re-plan from the current party so the price matches what actually changes now.
        ShowdownImport.Plan plan = ShowdownImport.plan(ShowdownImport.party(player), ShowdownFormat.parse(pending.text()));
        Economy economy = Cobblecomputils.economy();
        BigInteger cost = BigInteger.valueOf(plan.totalCost());
        if (cost.signum() > 0 && !economy.withdraw(player, cost)) {
            player.sendMessage(Text.literal("Not enough CobbleDollars: need " + economy.format(cost)
                + ", you have " + economy.format(economy.balance(player))).formatted(Formatting.RED), false);
            return;
        }
        int applied = 0;
        for (ShowdownImport.Change change : plan.changes()) {
            if (change.changesAnything()) {
                ShowdownImport.apply(change);
                applied++;
            }
        }
        player.sendMessage(Text.literal("Imported " + applied + " Pokémon"
            + (cost.signum() > 0 ? " for " + economy.format(cost) : "") + ".").formatted(Formatting.GREEN), false);
    }
}
