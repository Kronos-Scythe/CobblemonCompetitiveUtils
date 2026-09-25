package org.cobbleutils.cobblecomputils.evedit;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.EVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.cobbleutils.cobblecomputils.Cobblecomputils;
import org.cobbleutils.cobblecomputils.economy.Economy;
import org.cobbleutils.cobblecomputils.gui.Menus;

/**
 * The two /evedit menus: the party picker and the per-Pokémon EV editor
 * (3-row chest, glass frame, one power item per stat in the party slots).
 */
public final class EvEditMenus {
    private static final int[] CONTENT_SLOTS = Menus.PARTY_SLOTS;
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
        Menus.openPartyPicker(player, Text.literal("EV Editor"), "Click to edit EVs",
            pokemon -> List.of(
                line(spreadSummary(pokemon.getEvs()), Formatting.AQUA),
                line("Total EVs: " + pokemon.getEvs().total() + " / " + EVs.MAX_TOTAL_VALUE, Formatting.GRAY)),
            pokemon -> openStats(player, pokemon.getUuid()));
    }

    // ------------------------------------------------------------------
    // EV editor for one Pokémon
    // ------------------------------------------------------------------

    private static void openStats(ServerPlayerEntity player, UUID pokemonId) {
        Pokemon pokemon = Menus.findInParty(player, pokemonId);
        if (pokemon == null || Menus.refuseInBattle(player)) {
            return;
        }
        SimpleInventory menu = Menus.framed(3);
        drawStats(menu, player, pokemon);
        MutableText title = Text.literal("EVs: ").append(pokemon.getDisplayName(false));
        Menus.open(player, menu, 3, title, (slot, button, action) -> {
            if (slot == BACK_SLOT && action == SlotActionType.PICKUP) {
                openParty(player);
                return;
            }
            int statIndex = Menus.indexOf(CONTENT_SLOTS, slot);
            if (statIndex < 0) {
                return;
            }
            // Re-resolve on every click: the Pokémon may have left the party or a battle may have started.
            Pokemon current = Menus.findInParty(player, pokemonId);
            if (current == null || Menus.refuseInBattle(player)) {
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
        return Cobblecomputils.economy().format(amount);
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
        menu.setStack(HEADER_SLOT, Menus.pokemonIcon(pokemon, headerLore));

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

        menu.setStack(BACK_SLOT, Menus.backButton("Back to team"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ItemStack named(ItemStack stack, Text name, List<Text> lore) {
        return Menus.named(stack, name, lore);
    }

    private static Text line(String text, Formatting color) {
        return Menus.line(text, color);
    }

    private static Style plain(Formatting color) {
        return Menus.plain(color);
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
}
