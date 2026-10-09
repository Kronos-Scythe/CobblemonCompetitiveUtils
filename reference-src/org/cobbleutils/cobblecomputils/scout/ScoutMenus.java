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
import net.minecraft.class_124;
import net.minecraft.class_1277;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import org.cobbleutils.cobblecomputils.gui.Menus;
import org.cobbleutils.cobblecomputils.integration.RctBridge;
import org.cobbleutils.cobblecomputils.integration.RctBridge.TeamMember;
import org.cobbleutils.cobblecomputils.integration.RctBridge.Trainer;

public final class ScoutMenus {
   private static final int ROWS = 6;
   private static final int MAX_TRAINERS = 5;

   private ScoutMenus() {
   }

   public static void open(class_3222 player) {
      if (!RctBridge.isLoaded()) {
         player.method_7353(class_2561.method_43470("Scouting needs Radical Cobblemon Trainers.").method_27692(class_124.field_1061), false);
      } else {
         List<Trainer> trainers = RctBridge.nextTrainers(player);
         if (trainers.isEmpty()) {
            player.method_7353(
               class_2561.method_43470("No trainers to scout: there's nobody left to beat in your series.").method_27692(class_124.field_1054), false
            );
         } else {
            if (org.cobbleutils.cobblecomputils.capture.ListNet.hasClient(player)) {
               openList(player, trainers);
               return;
            }
            class_1277 menu = Menus.framed(6);
            OptionalInt cap = RctBridge.levelCap(player);

            for (int row = 0; row < Math.min(trainers.size(), 5); row++) {
               drawTrainer(menu, row * 9, trainers.get(row), cap);
            }

            if (trainers.size() > 5) {
               menu.method_5447(
                  53,
                  Menus.named(
                     new class_1799(class_1802.field_8407), Menus.line("+" + (trainers.size() - 5) + " more trainers", class_124.field_1080), List.of()
                  )
               );
            }

            Menus.open(player, menu, 6, class_2561.method_43470("Scouting: next trainers"), (slot, button, action) -> {
            });
         }
      }
   }

   private static void openList(class_3222 player, List<Trainer> trainers) {
      OptionalInt cap = RctBridge.levelCap(player);
      org.cobbleutils.cobblecomputils.capture.ListNet.open(player, s -> {
         s.title = "Scouting: next trainers";
         s.info = cap.isPresent() ? "Your level cap: " + cap.getAsInt() : "";
         for (Trainer t : trainers) {
            int highest = t.team().stream().mapToInt(TeamMember::level).max().orElse(0);
            StringBuilder tip = new StringBuilder();
            for (TeamMember m : t.team()) tip.append(m.species()).append(" Lv. ").append(m.level()).append("\n");
            org.cobbleutils.cobblecomputils.capture.ListNet.Row row = org.cobbleutils.cobblecomputils.capture.ListNet.Row.of(
               new class_1799(class_1802.field_8448), t.name().getString(), t.team().size() + " Pokémon, up to Lv. " + highest, "", -1, 0, tip.toString().trim());
            for (int i = 0; i < Math.min(7, t.team().size()); i++) row.icons().add(memberIcon((TeamMember)t.team().get(i)));
            s.add(row, null);
         }
      });
   }

   private static void drawTrainer(class_1277 menu, int rowStart, Trainer trainer, OptionalInt cap) {
      int highest = trainer.team().stream().mapToInt(TeamMember::level).max().orElse(0);
      List<class_2561> lore = new ArrayList<>();
      lore.add(Menus.line(trainer.team().size() + " Pokémon, up to Lv. " + highest, class_124.field_1080));
      cap.ifPresent(c -> lore.add(Menus.line("Your level cap: " + c, class_124.field_1080)));
      menu.method_5447(
         rowStart, Menus.named(new class_1799(class_1802.field_8448), trainer.name().method_27661().method_10862(Menus.plain(class_124.field_1065)), lore)
      );
      menu.method_5447(rowStart + 1, Menus.named(new class_1799(class_1802.field_8871), class_2561.method_43470(" "), List.of()));

      for (int i = 0; i < 7; i++) {
         int slot = rowStart + 2 + i;
         menu.method_5447(slot, i < trainer.team().size() ? memberIcon((TeamMember)trainer.team().get(i)) : class_1799.field_8037);
      }
   }

   private static class_1799 memberIcon(TeamMember member) {
      Species species = findSpecies(member.species());
      List<class_2561> lore = List.of(Menus.line("Lv. " + member.level(), class_124.field_1080));
      if (species == null) {
         return Menus.named(new class_1799(class_1802.field_8077), Menus.line(member.species(), class_124.field_1068), lore);
      } else {
         Set<String> aspects = new HashSet<>(member.aspects());
         if (member.shiny()) {
            aspects.add("shiny");
         }

         return Menus.named(
            PokemonItem.from(species, aspects, 1, null), species.getTranslatedName().method_27661().method_10862(Menus.plain(class_124.field_1068)), lore
         );
      }
   }

   private static Species findSpecies(String name) {
      String id = name.toLowerCase(Locale.ROOT);
      if (id.contains(":")) {
         class_2960 identifier = class_2960.method_12829(id);
         return identifier == null ? null : PokemonSpecies.getByIdentifier(identifier);
      } else {
         return PokemonSpecies.getByName(id.replaceAll("[^a-z0-9]", ""));
      }
   }
}
