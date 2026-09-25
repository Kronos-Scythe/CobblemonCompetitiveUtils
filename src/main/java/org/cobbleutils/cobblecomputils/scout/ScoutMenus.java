package org.cobbleutils.cobblecomputils.scout;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.cobbleutils.cobblecomputils.gui.Menus;
import org.cobbleutils.cobblecomputils.integration.RctBridge;

/**
 * /scout: the trainers the player has to beat next in their RCT series, one per
 * row, with their team's species and levels (no moves, items or abilities).
 */
public final class ScoutMenus {
    private static final int ROWS = 6;
    private static final int MAX_TRAINERS = 5;

    private ScoutMenus() {
    }

    public static void open(ServerPlayerEntity player) {
        if (!RctBridge.isLoaded()) {
            player.sendMessage(Text.literal("Scouting needs Radical Cobblemon Trainers.").formatted(Formatting.RED), false);
            return;
        }
        List<RctBridge.Trainer> trainers = RctBridge.nextTrainers(player);
        if (trainers.isEmpty()) {
            player.sendMessage(Text.literal("No trainers to scout: there's nobody left to beat in your series.")
                .formatted(Formatting.YELLOW), false);
            return;
        }

        SimpleInventory menu = Menus.framed(ROWS);
        OptionalInt cap = RctBridge.levelCap(player);
        for (int row = 0; row < Math.min(trainers.size(), MAX_TRAINERS); row++) {
            drawTrainer(menu, row * 9, trainers.get(row), cap);
        }
        if (trainers.size() > MAX_TRAINERS) {
            menu.setStack(ROWS * 9 - 1, Menus.named(new ItemStack(Items.PAPER),
                Menus.line("+" + (trainers.size() - MAX_TRAINERS) + " more trainers", Formatting.GRAY), List.of()));
        }
        Menus.open(player, menu, ROWS, Text.literal("Scouting: next trainers"), (slot, button, action) -> { });
    }

    private static void drawTrainer(SimpleInventory menu, int rowStart, RctBridge.Trainer trainer, OptionalInt cap) {
        int highest = trainer.team().stream().mapToInt(RctBridge.TeamMember::level).max().orElse(0);
        List<Text> lore = new ArrayList<>();
        lore.add(Menus.line(trainer.team().size() + " Pokémon, up to Lv. " + highest, Formatting.GRAY));
        cap.ifPresent(c -> lore.add(Menus.line("Your level cap: " + c, Formatting.GRAY)));
        menu.setStack(rowStart, Menus.named(new ItemStack(Items.NAME_TAG),
            trainer.name().copy().setStyle(Menus.plain(Formatting.GOLD)), lore));
        menu.setStack(rowStart + 1, Menus.named(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), Text.literal(" "), List.of()));

        for (int i = 0; i < 7; i++) {
            int slot = rowStart + 2 + i;
            menu.setStack(slot, i < trainer.team().size() ? memberIcon(trainer.team().get(i)) : ItemStack.EMPTY);
        }
    }

    private static ItemStack memberIcon(RctBridge.TeamMember member) {
        Species species = findSpecies(member.species());
        List<Text> lore = List.of(Menus.line("Lv. " + member.level(), Formatting.GRAY));
        if (species == null) {
            return Menus.named(new ItemStack(Items.BARRIER), Menus.line(member.species(), Formatting.WHITE), lore);
        }
        Set<String> aspects = new HashSet<>(member.aspects());
        if (member.shiny()) {
            aspects.add("shiny");
        }
        return Menus.named(PokemonItem.from(species, aspects, 1, null),
            species.getTranslatedName().copy().setStyle(Menus.plain(Formatting.WHITE)), lore);
    }

    /** RCT stores species as "garchomp" or "cobblemon:garchomp". */
    private static Species findSpecies(String name) {
        String id = name.toLowerCase(Locale.ROOT);
        if (id.contains(":")) {
            Identifier identifier = Identifier.tryParse(id);
            return identifier == null ? null : PokemonSpecies.INSTANCE.getByIdentifier(identifier);
        }
        return PokemonSpecies.INSTANCE.getByName(id.replaceAll("[^a-z0-9]", ""));
    }
}
