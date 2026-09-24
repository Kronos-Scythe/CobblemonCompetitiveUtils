package org.cobbleutils.cobblecomputils.evedit;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.EVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;
import org.cobbleutils.cobblecomputils.Cobblecomputils;
import org.cobbleutils.cobblecomputils.economy.Economy;
import org.cobbleutils.cobblecomputils.gui.MenuScreenHandler;

/**
 * The two /evedit menus: the party picker and the per-Pokémon EV editor.
 *
 * Layout (3-row chest, glass frame): the six content slots are the middle row
 * minus its centre, which keeps the team split 3 | 3 like a party screen.
 */
public final class EvEditMenus {
    private static final int[] CONTENT_SLOTS = {10, 11, 12, 14, 15, 16};
    private static final int CENTER_SLOT = 13;
    private static final int HEADER_SLOT = 4;
    private static final int BACK_SLOT = 22;

    private static final Stat[] STATS = {
        Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    };
    // The power item that boosts each stat's EV gain.
    private static final ItemConvertible[] STAT_ITEMS = {
        CobblemonItems.POWER_WEIGHT, CobblemonItems.POWER_BRACER, CobblemonItems.POWER_BELT,
        CobblemonItems.POWER_LENS, CobblemonItems.POWER_BAND, CobblemonItems.POWER_ANKLET
    };
    private static final String[] STAT_SHORT = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};

    private EvEditMenus() {
    }

    // ------------------------------------------------------------------
    // Party picker
    // ------------------------------------------------------------------

    public static void openParty(ServerPlayerEntity player) {
        if (refuseInBattle(player)) {
            return;
        }
        SimpleInventory menu = framedInventory();
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            Pokemon pokemon = party.get(i);
            menu.setStack(CONTENT_SLOTS[i], pokemon == null ? emptyPartySlot() : partyIcon(pokemon));
        }
        open(player, menu, Text.literal("EV Editor"), (slot, button, action) -> {
            int partySlot = indexOf(CONTENT_SLOTS, slot);
            if (partySlot < 0 || action != SlotActionType.PICKUP) {
                return;
            }
            Pokemon pokemon = party.get(partySlot);
            if (pokemon != null) {
                openStats(player, pokemon.getUuid());
            }
        });
    }

    private static ItemStack partyIcon(Pokemon pokemon) {
        ItemStack stack = PokemonItem.from(pokemon);
        EVs evs = pokemon.getEvs();
        List<Text> lore = new ArrayList<>();
        lore.add(line("Lv. " + pokemon.getLevel(), Formatting.GRAY));
        lore.add(line(spreadSummary(evs), Formatting.AQUA));
        lore.add(line("Total EVs: " + evs.total() + " / " + EVs.MAX_TOTAL_VALUE, Formatting.GRAY));
        lore.add(Text.empty());
        lore.add(line("Click to edit EVs", Formatting.YELLOW));
        return named(stack, pokemon.getDisplayName(false).copy().setStyle(plain(Formatting.WHITE)), lore);
    }

    private static ItemStack emptyPartySlot() {
        return named(new ItemStack(CobblemonItems.POKE_BALL), line("Empty slot", Formatting.DARK_GRAY), List.of());
    }

    // ------------------------------------------------------------------
    // EV editor for one Pokémon
    // ------------------------------------------------------------------

    private static void openStats(ServerPlayerEntity player, UUID pokemonId) {
        Pokemon pokemon = findInParty(player, pokemonId);
        if (pokemon == null || refuseInBattle(player)) {
            return;
        }
        SimpleInventory menu = framedInventory();
        drawStats(menu, player, pokemon);
        MutableText title = Text.literal("EVs: ").append(pokemon.getDisplayName(false));
        open(player, menu, title, (slot, button, action) -> {
            if (slot == BACK_SLOT && action == SlotActionType.PICKUP) {
                openParty(player);
                return;
            }
            int statIndex = indexOf(CONTENT_SLOTS, slot);
            if (statIndex < 0) {
                return;
            }
            // Re-resolve on every click: the Pokémon may have left the party or a battle may have started.
            Pokemon current = findInParty(player, pokemonId);
            if (current == null || refuseInBattle(player)) {
                player.closeHandledScreen();
                return;
            }
            if (applyClick(player, current, STATS[statIndex], button, action)) {
                drawStats(menu, player, current);
            }
        });
    }

    /** Maps a click to an EV change and charges for it. Returns true if the EVs changed. */
    private static boolean applyClick(ServerPlayerEntity player, Pokemon pokemon, Stat stat,
                                      int button, SlotActionType action) {
        EVs evs = pokemon.getEvs();
        int current = evs.getOrDefault(stat);
        int max = maxAllowed(evs, stat);
        int target;
        if (action == SlotActionType.PICKUP) {                 // left / right click
            target = current + (button == 0 ? 1 : -1);
        } else if (action == SlotActionType.QUICK_MOVE) {      // shift + left / right click
            target = current + (button == 0 ? 4 : -4);
        } else if (action == SlotActionType.THROW) {           // Q resets, Ctrl+Q maxes
            target = button == 1 ? max : 0;
        } else {
            return false;
        }
        target = Math.max(0, Math.min(max, target));
        if (target == current) {
            return false;
        }

        Economy economy = Cobblecomputils.economy();
        long price = pricePerEv(stat);
        BigInteger cost = BigInteger.ZERO;
        if (target > current && price > 0) {
            BigInteger balance = economy.balance(player);
            if (action == SlotActionType.THROW) {
                // Ctrl+Q buys as many EVs as the player can afford, up to the max.
                long affordable = balance.divide(BigInteger.valueOf(price)).min(BigInteger.valueOf(target - current)).longValue();
                target = current + (int) affordable;
            }
            cost = BigInteger.valueOf(price).multiply(BigInteger.valueOf(target - current));
            if (target == current || !economy.withdraw(player, cost)) {
                BigInteger needed = BigInteger.valueOf(price).max(cost);
                player.sendMessage(Text.literal("Not enough CobbleDollars: need " + money(needed)
                    + ", you have " + money(balance)).formatted(Formatting.RED), true);
                return false;
            }
        }
        evs.set(stat, target);
        if (evs.getOrDefault(stat) != target) {
            // EVs.set refused the value; give the money back.
            if (cost.signum() > 0) {
                economy.deposit(player, cost);
            }
            return false;
        }
        if (cost.signum() > 0) {
            player.sendMessage(Text.literal("Paid " + money(cost)).formatted(Formatting.GOLD), true);
        } else if (target < current && price > 0) {
            BigInteger refund = BigInteger.valueOf(price).multiply(BigInteger.valueOf(current - target))
                .multiply(BigInteger.valueOf(EvEditConfig.get().refundPercent())).divide(BigInteger.valueOf(100));
            if (refund.signum() > 0) {
                economy.deposit(player, refund);
                player.sendMessage(Text.literal("Refunded " + money(refund)).formatted(Formatting.GOLD), true);
            }
        }
        // Lowering HP EVs lowers max HP; keep current HP within it.
        if (pokemon.getCurrentHealth() > pokemon.getMaxHealth()) {
            pokemon.setCurrentHealth(pokemon.getMaxHealth());
        }
        return true;
    }

    /** Price of one EV of this stat, or 0 when nothing is charged. */
    private static long pricePerEv(Stat stat) {
        EvEditConfig config = EvEditConfig.get();
        return config.charge && !Cobblecomputils.economy().isFree() ? config.price(stat) : 0;
    }

    private static String money(BigInteger amount) {
        return String.format("%,d %s", amount, Cobblecomputils.economy().symbol()).trim();
    }

    /** Highest value this stat can take: 252, or less if the 510 total would be exceeded. */
    private static int maxAllowed(EVs evs, Stat stat) {
        int current = evs.getOrDefault(stat);
        int left = EVs.MAX_TOTAL_VALUE - evs.total();
        return Math.min(EVs.MAX_STAT_VALUE, current + left);
    }

    private static void drawStats(SimpleInventory menu, ServerPlayerEntity player, Pokemon pokemon) {
        EVs evs = pokemon.getEvs();
        int total = evs.total();

        List<Text> headerLore = new ArrayList<>();
        headerLore.add(line("Lv. " + pokemon.getLevel(), Formatting.GRAY));
        headerLore.add(line(spreadSummary(evs), Formatting.AQUA));
        headerLore.add(line("Total EVs: " + total + " / " + EVs.MAX_TOTAL_VALUE
            + " (" + (EVs.MAX_TOTAL_VALUE - total) + " left)", Formatting.GRAY));
        if (!Cobblecomputils.economy().isFree() && EvEditConfig.get().charge) {
            headerLore.add(line("Balance: " + money(Cobblecomputils.economy().balance(player)), Formatting.GOLD));
        }
        menu.setStack(HEADER_SLOT, named(PokemonItem.from(pokemon),
            pokemon.getDisplayName(false).copy().setStyle(plain(Formatting.WHITE)), headerLore));

        for (int i = 0; i < STATS.length; i++) {
            Stat stat = STATS[i];
            int value = evs.getOrDefault(stat);
            List<Text> lore = new ArrayList<>();
            lore.add(line("EVs: " + value + " / " + EVs.MAX_STAT_VALUE, value > 0 ? Formatting.GREEN : Formatting.GRAY));
            lore.add(line("Stat: " + pokemon.getStat(stat), Formatting.GRAY));
            lore.add(line("Can go up to " + maxAllowed(evs, stat), Formatting.DARK_GRAY));
            long price = pricePerEv(stat);
            if (price > 0) {
                lore.add(line("Cost: " + money(BigInteger.valueOf(price)) + " per EV", Formatting.GOLD));
                int refund = EvEditConfig.get().refundPercent();
                if (refund > 0) {
                    lore.add(line("Lowering refunds " + refund + "%", Formatting.GOLD));
                }
            }
            lore.add(Text.empty());
            lore.add(line("Left / right click: +1 / -1", Formatting.YELLOW));
            lore.add(line("Shift + left / right click: +4 / -4", Formatting.YELLOW));
            lore.add(line("Ctrl + Q: max    Q: reset to 0", Formatting.YELLOW));
            ItemStack stack = named(new ItemStack(STAT_ITEMS[i]),
                stat.getDisplayName().copy().setStyle(plain(Formatting.GOLD)), lore);
            if (value >= EVs.MAX_STAT_VALUE) {
                stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            menu.setStack(CONTENT_SLOTS[i], stack);
        }

        menu.setStack(BACK_SLOT, named(new ItemStack(Items.ARROW), line("Back to team", Formatting.WHITE), List.of()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void open(ServerPlayerEntity player, SimpleInventory menu, Text title,
                             MenuScreenHandler.ClickListener listener) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInventory, p) -> new MenuScreenHandler(syncId, playerInventory, menu, listener),
            title));
    }

    private static SimpleInventory framedInventory() {
        SimpleInventory menu = new SimpleInventory(MenuScreenHandler.SIZE);
        ItemStack frame = named(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Text.literal(" "), List.of());
        for (int i = 0; i < MenuScreenHandler.SIZE; i++) {
            menu.setStack(i, frame.copy());
        }
        menu.setStack(CENTER_SLOT, named(new ItemStack(Items.BLACK_STAINED_GLASS_PANE), Text.literal(" "), List.of()));
        return menu;
    }

    private static Pokemon findInParty(ServerPlayerEntity player, UUID pokemonId) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (int i = 0; i < party.size(); i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon != null && pokemon.getUuid().equals(pokemonId)) {
                return pokemon;
            }
        }
        return null;
    }

    private static boolean refuseInBattle(ServerPlayerEntity player) {
        if (BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(player) == null) {
            return false;
        }
        player.sendMessage(Text.literal("You can't edit EVs during a battle.").formatted(Formatting.RED), false);
        return true;
    }

    private static String spreadSummary(EVs evs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < STATS.length; i++) {
            if (i > 0) {
                sb.append(" / ");
            }
            sb.append(evs.getOrDefault(STATS[i])).append(' ').append(STAT_SHORT[i]);
        }
        return sb.toString();
    }

    private static ItemStack named(ItemStack stack, Text name, List<Text> lore) {
        stack.set(DataComponentTypes.CUSTOM_NAME, name);
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        return stack;
    }

    private static Text line(String text, Formatting color) {
        return Text.literal(text).setStyle(plain(color));
    }

    private static Style plain(Formatting color) {
        return Style.EMPTY.withItalic(false).withColor(color);
    }

    private static int indexOf(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
