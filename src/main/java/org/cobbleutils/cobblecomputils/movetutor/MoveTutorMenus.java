package org.cobbleutils.cobblecomputils.movetutor;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.cobbleutils.cobblecomputils.Cobblecomputils;
import org.cobbleutils.cobblecomputils.economy.Economy;
import org.cobbleutils.cobblecomputils.gui.Menus;

/**
 * /movetutor: party picker → paged list of every move the Pokémon can learn
 * (type gem icons, hover for stats and description) → pick a move to replace
 * when all four slots are full.
 */
public final class MoveTutorMenus {
    private static final int LIST_ROWS = 6;
    /** Rows 1-4, columns 1-7 of a 6-row chest: 28 moves per page. */
    private static final int[] LIST_SLOTS = listSlots();
    private static final int HEADER_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int FILTER_SLOT = 49;
    private static final int NEXT_SLOT = 50;

    /** Current moves in the replace menu (3-row chest). */
    private static final int[] REPLACE_SLOTS = {10, 12, 14, 16};
    private static final int REPLACE_BACK_SLOT = 22;

    private MoveTutorMenus() {
    }

    private static int[] listSlots() {
        int[] slots = new int[28];
        int i = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[i++] = row * 9 + col;
            }
        }
        return slots;
    }

    /** What the list is showing: which Pokémon, page and source filter (null = all). */
    private static final class ListState {
        final UUID pokemonId;
        int page;
        MoveSource filter;

        ListState(UUID pokemonId) {
            this.pokemonId = pokemonId;
        }
    }

    public static void openParty(ServerPlayerEntity player) {
        Menus.openPartyPicker(player, Text.literal("Move Tutor"), "Click to teach moves",
            pokemon -> pokemon.getMoveSet().getMoves().stream()
                .map(move -> (Text) move.getTemplate().getDisplayName().copy().setStyle(Menus.plain(Formatting.AQUA)))
                .collect(Collectors.toList()),
            pokemon -> openList(player, new ListState(pokemon.getUuid())));
    }

    // ------------------------------------------------------------------
    // Move list
    // ------------------------------------------------------------------

    private static void openList(ServerPlayerEntity player, ListState state) {
        Pokemon pokemon = Menus.findInParty(player, state.pokemonId);
        if (pokemon == null || Menus.refuseInBattle(player)) {
            return;
        }
        SimpleInventory menu = Menus.framed(LIST_ROWS);
        List<LearnableMove> shown = drawList(menu, player, pokemon, state);
        MutableText title = Text.literal("Move Tutor: ").append(pokemon.getDisplayName(false));
        // The list the slots were drawn from; refreshed on every redraw.
        List<List<LearnableMove>> visible = new ArrayList<>(List.of(shown));

        Menus.open(player, menu, LIST_ROWS, title, (slot, button, action) -> {
            if (action != SlotActionType.PICKUP) {
                return;
            }
            Pokemon current = Menus.findInParty(player, state.pokemonId);
            if (current == null || Menus.refuseInBattle(player)) {
                player.closeHandledScreen();
                return;
            }
            if (slot == BACK_SLOT) {
                openParty(player);
                return;
            }
            if (slot == PREV_SLOT || slot == NEXT_SLOT || slot == FILTER_SLOT) {
                if (slot == PREV_SLOT) {
                    state.page--;
                } else if (slot == NEXT_SLOT) {
                    state.page++;
                } else {
                    state.filter = nextFilter(state.filter, button == 1);
                    state.page = 0;
                }
                visible.set(0, drawList(menu, player, current, state));
                return;
            }
            int index = Menus.indexOf(LIST_SLOTS, slot);
            List<LearnableMove> page = visible.get(0);
            if (index < 0 || index >= page.size()) {
                return;
            }
            LearnableMove chosen = page.get(index);
            if (chosen.known) {
                player.sendMessage(Text.empty().append(current.getDisplayName(false))
                    .append(" already knows ").append(chosen.move.getDisplayName()).formatted(Formatting.RED), true);
            } else if (current.getMoveSet().hasSpace()) {
                if (teach(player, current, chosen, null)) {
                    visible.set(0, drawList(menu, player, current, state));
                }
            } else {
                openReplace(player, state, chosen.move);
            }
        });
    }

    /** Fills the list menu and returns the moves on the current page, in slot order. */
    private static List<LearnableMove> drawList(SimpleInventory menu, ServerPlayerEntity player, Pokemon pokemon,
                                                ListState state) {
        MoveTutorConfig config = MoveTutorConfig.get();
        List<LearnableMove> all = LearnableMove.of(pokemon, config);
        List<LearnableMove> filtered = all.stream()
            .filter(m -> state.filter == null || m.sources.contains(state.filter))
            .collect(Collectors.toList());
        int pages = Math.max(1, (filtered.size() + LIST_SLOTS.length - 1) / LIST_SLOTS.length);
        state.page = Math.max(0, Math.min(state.page, pages - 1));
        int from = state.page * LIST_SLOTS.length;
        List<LearnableMove> page = filtered.subList(from, Math.min(filtered.size(), from + LIST_SLOTS.length));

        menu.setStack(HEADER_SLOT, Menus.pokemonIcon(pokemon, headerLore(player, pokemon)));
        for (int i = 0; i < LIST_SLOTS.length; i++) {
            menu.setStack(LIST_SLOTS[i], i < page.size() ? moveIcon(page.get(i), config) : ItemStack.EMPTY);
        }

        ItemStack pane = Menus.named(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Text.literal(" "), List.of());
        menu.setStack(BACK_SLOT, Menus.backButton("Back to team"));
        menu.setStack(PREV_SLOT, state.page > 0
            ? Menus.named(new ItemStack(Items.PAPER), Menus.line("Previous page", Formatting.WHITE), List.of())
            : pane.copy());
        menu.setStack(NEXT_SLOT, state.page < pages - 1
            ? Menus.named(new ItemStack(Items.PAPER), Menus.line("Next page", Formatting.WHITE), List.of())
            : pane.copy());

        List<Text> filterLore = new ArrayList<>();
        filterLore.add(Menus.line("Page " + (state.page + 1) + " / " + pages + " (" + filtered.size() + " moves)", Formatting.GRAY));
        filterLore.add(Text.empty());
        filterLore.add(Menus.line(state.filter == null ? "> All" : "  All", state.filter == null ? Formatting.YELLOW : Formatting.GRAY));
        for (MoveSource source : MoveSource.values()) {
            long count = all.stream().filter(m -> m.sources.contains(source)).count();
            if (count > 0) {
                boolean selected = source == state.filter;
                filterLore.add(Menus.line((selected ? "> " : "  ") + source.label + " (" + count + ")",
                    selected ? Formatting.YELLOW : Formatting.GRAY));
            }
        }
        filterLore.add(Text.empty());
        filterLore.add(Menus.line("Left / right click: next / previous filter", Formatting.YELLOW));
        menu.setStack(FILTER_SLOT, Menus.named(new ItemStack(Items.HOPPER),
            Menus.line("Show: " + (state.filter == null ? "All" : state.filter.label), Formatting.GOLD), filterLore));
        return page;
    }

    private static MoveSource nextFilter(MoveSource current, boolean backwards) {
        MoveSource[] values = MoveSource.values();
        // Cycle through null (All) followed by every source.
        int index = current == null ? 0 : current.ordinal() + 1;
        int next = Math.floorMod(index + (backwards ? -1 : 1), values.length + 1);
        return next == 0 ? null : values[next - 1];
    }

    private static List<Text> headerLore(ServerPlayerEntity player, Pokemon pokemon) {
        List<Text> lore = new ArrayList<>();
        lore.add(Menus.line("Lv. " + pokemon.getLevel(), Formatting.GRAY));
        lore.add(Menus.line("Current moves:", Formatting.GRAY));
        for (Move move : pokemon.getMoveSet().getMoves()) {
            lore.add(Text.literal("  ").append(move.getTemplate().getDisplayName()).setStyle(Menus.plain(Formatting.AQUA)));
        }
        if (charging()) {
            lore.add(Menus.line("Balance: " + money(Cobblecomputils.economy().balance(player)), Formatting.GOLD));
        }
        return lore;
    }

    private static ItemStack moveIcon(LearnableMove learnable, MoveTutorConfig config) {
        List<Text> lore = moveDetails(learnable.move);
        lore.add(Text.empty());
        lore.add(Menus.line("Learned by: " + learnable.sources.stream()
            .map(s -> s == MoveSource.LEVEL_UP ? "Level-up (Lv. " + learnable.level + ")" : s.label)
            .collect(Collectors.joining(", ")), Formatting.DARK_AQUA));
        if (learnable.known) {
            lore.add(Menus.line("Already known", Formatting.GREEN));
        } else {
            long price = charging() ? learnable.price(config) : 0;
            lore.add(Menus.line(price > 0 ? "Cost: " + money(BigInteger.valueOf(price))
                : learnable.benched ? "Free (known before)" : "Free", Formatting.GOLD));
            lore.add(Menus.line("Click to teach", Formatting.YELLOW));
        }
        ItemStack stack = Menus.named(new ItemStack(typeItem(learnable.move)),
            learnable.move.getDisplayName().copy().setStyle(Menus.plain(learnable.known ? Formatting.GREEN : Formatting.WHITE)),
            lore);
        if (learnable.known) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    /** Type, category, power, accuracy, PP and description lines for a move. */
    private static List<Text> moveDetails(MoveTemplate move) {
        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("Type: ").append(move.getElementalType().getDisplayName())
            .append("   Category: ").append(move.getDamageCategory().getDisplayName())
            .setStyle(Menus.plain(Formatting.GRAY)));
        String power = move.getPower() > 0 ? String.valueOf((int) move.getPower()) : "—";
        String accuracy = move.getAccuracy() > 0 ? (int) move.getAccuracy() + "%" : "—";
        lore.add(Menus.line("Power: " + power + "   Accuracy: " + accuracy + "   PP: " + move.getPp(), Formatting.GRAY));
        lore.add(Text.empty());
        Text description = move.getDescription();
        String resolved = description.getString();
        if (resolved.startsWith("cobblemon.")) {
            // Not translated on this server: let the client translate it (one long line).
            lore.add(description.copy().setStyle(Menus.plain(Formatting.WHITE)));
        } else {
            lore.addAll(Menus.wrap(resolved, 40, Formatting.WHITE));
        }
        return lore;
    }

    /** The type's gem (cobblemon:fire_gem, ...), or a book for types without one. */
    private static Item typeItem(MoveTemplate move) {
        String type = move.getElementalType().getName().toLowerCase(Locale.ROOT);
        Item gem = Registries.ITEM.get(Identifier.of("cobblemon", type + "_gem"));
        return gem == Items.AIR ? Items.BOOK : gem;
    }

    // ------------------------------------------------------------------
    // Replacing a move
    // ------------------------------------------------------------------

    private static void openReplace(ServerPlayerEntity player, ListState state, MoveTemplate newMove) {
        Pokemon pokemon = Menus.findInParty(player, state.pokemonId);
        if (pokemon == null || Menus.refuseInBattle(player)) {
            return;
        }
        SimpleInventory menu = Menus.framed(3);
        List<Text> headerLore = moveDetails(newMove);
        headerLore.add(Text.empty());
        headerLore.add(Menus.line("Pick a move to forget", Formatting.YELLOW));
        menu.setStack(HEADER_SLOT, Menus.named(new ItemStack(typeItem(newMove)),
            Text.literal("Learn ").append(newMove.getDisplayName()).setStyle(Menus.plain(Formatting.GOLD)), headerLore));

        List<Move> moves = pokemon.getMoveSet().getMoves();
        for (int i = 0; i < REPLACE_SLOTS.length && i < moves.size(); i++) {
            MoveTemplate old = moves.get(i).getTemplate();
            List<Text> lore = moveDetails(old);
            lore.add(Text.empty());
            lore.add(Text.literal("Click to forget it and learn ").append(newMove.getDisplayName())
                .setStyle(Menus.plain(Formatting.YELLOW)));
            menu.setStack(REPLACE_SLOTS[i], Menus.named(new ItemStack(typeItem(old)),
                old.getDisplayName().copy().setStyle(Menus.plain(Formatting.WHITE)), lore));
        }
        menu.setStack(REPLACE_BACK_SLOT, Menus.backButton("Back to moves"));

        MutableText title = Text.literal("Replace a move: ").append(pokemon.getDisplayName(false));
        Menus.open(player, menu, 3, title, (slot, button, action) -> {
            if (action != SlotActionType.PICKUP) {
                return;
            }
            if (slot == REPLACE_BACK_SLOT) {
                openList(player, state);
                return;
            }
            int index = Menus.indexOf(REPLACE_SLOTS, slot);
            Pokemon current = Menus.findInParty(player, state.pokemonId);
            if (index < 0 || current == null || Menus.refuseInBattle(player)) {
                return;
            }
            List<Move> currentMoves = current.getMoveSet().getMoves();
            if (index >= currentMoves.size()) {
                return;
            }
            LearnableMove learnable = LearnableMove.of(current, MoveTutorConfig.get()).stream()
                .filter(m -> m.move == newMove).findFirst().orElse(null);
            if (learnable != null && !learnable.known) {
                teach(player, current, learnable, currentMoves.get(index).getTemplate());
            }
            openList(player, state);
        });
    }

    // ------------------------------------------------------------------
    // Teaching
    // ------------------------------------------------------------------

    /**
     * Charges for and teaches {@code learnable}, replacing {@code replaced} (or
     * filling an empty slot when null). Returns true if the move was learned.
     */
    private static boolean teach(ServerPlayerEntity player, Pokemon pokemon, LearnableMove learnable, MoveTemplate replaced) {
        Economy economy = Cobblecomputils.economy();
        long price = charging() ? learnable.price(MoveTutorConfig.get()) : 0;
        BigInteger cost = BigInteger.valueOf(price);
        if (price > 0 && !economy.withdraw(player, cost)) {
            player.sendMessage(Text.literal("Not enough CobbleDollars: need " + money(cost)
                + ", you have " + money(economy.balance(player))).formatted(Formatting.RED), true);
            return false;
        }

        MoveTemplate move = learnable.move;
        boolean learned;
        if (replaced != null) {
            // Moves the forgotten move to Cobblemon's relearnable list and carries the PP ratio over.
            learned = pokemon.exchangeMove(replaced, move);
        } else if (learnable.benched) {
            learned = pokemon.exchangeMove(null, move);
        } else {
            learned = pokemon.getMoveSet().add(move.create());
        }

        if (!learned) {
            if (price > 0) {
                economy.deposit(player, cost);
            }
            player.sendMessage(Text.literal("Couldn't teach that move.").formatted(Formatting.RED), true);
            return false;
        }
        MutableText message = Text.empty().append(pokemon.getDisplayName(false)).append(" learned ")
            .append(move.getDisplayName()).append("!");
        if (price > 0) {
            message.append(" (paid " + money(cost) + ")");
        }
        player.sendMessage(message.formatted(Formatting.GREEN), true);
        return true;
    }

    private static boolean charging() {
        return MoveTutorConfig.get().charge && !Cobblecomputils.economy().isFree();
    }

    private static String money(BigInteger amount) {
        return Cobblecomputils.economy().format(amount);
    }
}
