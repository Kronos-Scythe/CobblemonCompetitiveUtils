package org.cobbleutils.cobblecomputils.gui;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;

/** Shared building blocks for the chest menus: framing, item labels, the party picker. */
public final class Menus {
    /** Middle row of a 3-row menu minus its centre, so the team splits 3 | 3 like a party screen. */
    public static final int[] PARTY_SLOTS = {10, 11, 12, 14, 15, 16};
    public static final int CENTER_SLOT = 13;

    private Menus() {
    }

    public static void open(ServerPlayerEntity player, SimpleInventory menu, int rows, Text title,
                            MenuScreenHandler.ClickListener listener) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInventory, p) -> new MenuScreenHandler(syncId, playerInventory, menu, rows, listener),
            title));
    }

    /** A menu filled with gray glass, the centre slot of the second row darker. */
    public static SimpleInventory framed(int rows) {
        SimpleInventory menu = new SimpleInventory(rows * 9);
        ItemStack frame = pane(Items.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < menu.size(); i++) {
            menu.setStack(i, frame.copy());
        }
        menu.setStack(CENTER_SLOT, pane(Items.BLACK_STAINED_GLASS_PANE));
        return menu;
    }

    private static ItemStack pane(net.minecraft.item.Item item) {
        return named(new ItemStack(item), Text.literal(" "), List.of());
    }

    /**
     * Shows the party (models, Poké Balls for empty slots) and calls {@code onPick}
     * with the clicked Pokémon.
     */
    public static void openPartyPicker(ServerPlayerEntity player, Text title, String clickHint,
                                       Function<Pokemon, List<Text>> details, Consumer<Pokemon> onPick) {
        if (refuseInBattle(player)) {
            return;
        }
        SimpleInventory menu = framed(3);
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (int i = 0; i < PARTY_SLOTS.length; i++) {
            Pokemon pokemon = party.get(i);
            menu.setStack(PARTY_SLOTS[i], pokemon == null
                ? named(new ItemStack(CobblemonItems.POKE_BALL), line("Empty slot", Formatting.DARK_GRAY), List.of())
                : partyIcon(pokemon, clickHint, details));
        }
        open(player, menu, 3, title, (slot, button, action) -> {
            int partySlot = indexOf(PARTY_SLOTS, slot);
            if (partySlot < 0 || action != SlotActionType.PICKUP) {
                return;
            }
            Pokemon pokemon = party.get(partySlot);
            if (pokemon != null) {
                onPick.accept(pokemon);
            }
        });
    }

    private static ItemStack partyIcon(Pokemon pokemon, String clickHint, Function<Pokemon, List<Text>> details) {
        List<Text> lore = new ArrayList<>();
        lore.add(line("Lv. " + pokemon.getLevel(), Formatting.GRAY));
        lore.addAll(details.apply(pokemon));
        lore.add(Text.empty());
        lore.add(line(clickHint, Formatting.YELLOW));
        return pokemonIcon(pokemon, lore);
    }

    public static ItemStack pokemonIcon(Pokemon pokemon, List<Text> lore) {
        return named(PokemonItem.from(pokemon), pokemon.getDisplayName(false).copy().setStyle(plain(Formatting.WHITE)), lore);
    }

    public static ItemStack backButton(String label) {
        return named(new ItemStack(Items.ARROW), line(label, Formatting.WHITE), List.of());
    }

    public static Pokemon findInParty(ServerPlayerEntity player, UUID pokemonId) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (int i = 0; i < party.size(); i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon != null && pokemon.getUuid().equals(pokemonId)) {
                return pokemon;
            }
        }
        return null;
    }

    public static boolean refuseInBattle(ServerPlayerEntity player) {
        if (BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(player) == null) {
            return false;
        }
        player.sendMessage(Text.literal("You can't do that during a battle.").formatted(Formatting.RED), false);
        return true;
    }

    public static ItemStack named(ItemStack stack, Text name, List<Text> lore) {
        stack.set(DataComponentTypes.CUSTOM_NAME, name);
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        return stack;
    }

    public static Text line(String text, Formatting color) {
        return Text.literal(text).setStyle(plain(color));
    }

    public static Style plain(Formatting color) {
        return Style.EMPTY.withItalic(false).withColor(color);
    }

    /** Splits text into lines of at most {@code width} characters, breaking at spaces. */
    public static List<Text> wrap(String text, int width, Formatting color) {
        List<Text> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() > 0 && current.length() + 1 + word.length() > width) {
                lines.add(line(current.toString(), color));
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(line(current.toString(), color));
        }
        return lines;
    }

    public static int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
