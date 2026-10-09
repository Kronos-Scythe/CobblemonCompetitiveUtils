package org.cobbleutils.cobblecomputils.movetutor;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.class_124;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import org.CobbleUtils.cobbleroguelike.ui.Menu;
import org.cobbleutils.cobblecomputils.Cobblecomputils;
import org.cobbleutils.cobblecomputils.gui.Menus;

/** /movetutor drawn with the same client screen /rogue uses (rows, cards, footer buttons). */
final class RogueTutor {
   private static final int PAGE = 40;
   private static Boolean present;

   private RogueTutor() {
   }

   static boolean available() {
      if (present == null) {
         try {
            Class.forName("org.CobbleUtils.cobbleroguelike.ui.Menu", true, RogueTutor.class.getClassLoader());
            present = Boolean.TRUE;
         } catch (Throwable t) {
            present = Boolean.FALSE;
         }
      }
      return present;
   }

   private static class_2561 t(String s, class_124 c) {
      return class_2561.method_43470(s).method_27692(c);
   }

   static void openParty(class_3222 player) {
      if (Menus.refuseInBattle(player)) {
         return;
      }
      Menu menu = new Menu(class_2561.method_43470("Move Tutor"), 3).layout(Menu.Layout.CARDS).screenTitle(class_2561.method_43470("Move Tutor"));
      PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
      for (int i = 0; i < 6; i++) {
         Pokemon pokemon = party.get(i);
         if (pokemon == null) {
            continue;
         }
         List<class_2561> lore = new ArrayList<>();
         lore.add(t("Lv. " + pokemon.getLevel(), class_124.field_1080));
         for (Move move : pokemon.getMoveSet().getMoves()) {
            lore.add(class_2561.method_43470("- ").method_10852(move.getTemplate().getDisplayName()).method_27692(class_124.field_1075));
         }
         lore.add(t("Click to browse the moves it can learn", class_124.field_1054));
         menu.button(10 + i, Menus.pokemonIcon(pokemon, lore), p -> MoveTutorMenus.openList(p, new MoveTutorMenus.ListState(pokemon.getUuid())));
      }
      List<class_2561> info = new ArrayList<>();
      info.add(t("Level-up, evolution, egg, tutor and TM moves", class_124.field_1080));
      info.add(t("in one list. Search by name, type or category.", class_124.field_1080));
      menu.icon(4, Menu.stack("minecraft:book", t("Move Tutor", class_124.field_1068), info));
      menu.open(player);
   }

   private static String gem(MoveTemplate move) {
      return "cobblemon:" + move.getElementalType().getName().toLowerCase(java.util.Locale.ROOT) + "_gem";
   }

   private static class_2561 subtitle(MoveTemplate m) {
      String power = m.getPower() > 0.0 ? String.valueOf((int)m.getPower()) : "-";
      String acc = m.getAccuracy() > 0.0 ? (int)m.getAccuracy() + "%" : "-";
      return class_2561.method_43473()
         .method_10852(m.getElementalType().getDisplayName())
         .method_27693(" | ")
         .method_10852(m.getDamageCategory().getDisplayName())
         .method_27693(" | Pow " + power + " | Acc " + acc + " | PP " + m.getPp())
         .method_27692(class_124.field_1080);
   }

   private static class_2561 priceLine(LearnableMove l, MoveTutorConfig config) {
      if (l.known) {
         return t("Already known", class_124.field_1060);
      }
      long price = MoveTutorMenus.charging() ? l.price(config) : 0L;
      return t(price > 0L ? "Cost: " + MoveTutorMenus.money(BigInteger.valueOf(price)) : (l.benched ? "Free (known before)" : "Free"), class_124.field_1065);
   }

   private static class_2561 icon(String id, String name) {
      return class_2561.method_43470(name);
   }

   static void openList(class_3222 player, MoveTutorMenus.ListState state) {
      Pokemon pokemon = Menus.findInParty(player, state.pokemonId);
      if (pokemon == null || Menus.refuseInBattle(player)) {
         return;
      }
      MoveTutorConfig config = MoveTutorConfig.get();
      List<LearnableMove> all = LearnableMove.of(pokemon, config);
      List<LearnableMove> filtered = MoveTutorMenus.applyFilters(all, state, state.query);
      int pages = Math.max(1, (filtered.size() + PAGE - 1) / PAGE);
      state.page = Math.max(0, Math.min(state.page, pages - 1));
      List<LearnableMove> page = filtered.subList(state.page * PAGE, Math.min(filtered.size(), state.page * PAGE + PAGE));

      class_2561 title = class_2561.method_43470("Move Tutor - ").method_10852(pokemon.getDisplayName(false));
      Menu menu = new Menu(title, 6).layout(Menu.Layout.LIST).screenTitle(title);

      for (int i = 0; i < page.size(); i++) {
         LearnableMove l = page.get(i);
         List<class_2561> lore = new ArrayList<>();
         lore.add(subtitle(l.move));
         class_2561 desc = l.move.getDescription();
         String resolved = desc.getString();
         if (resolved.startsWith("cobblemon.")) {
            lore.add(desc.method_27661().method_27692(class_124.field_1068));
         } else {
            lore.addAll(Menus.wrap(resolved, 44, class_124.field_1068));
         }
         lore.add(t("Learned by: " + l.sources.stream().map(s -> s == MoveSource.LEVEL_UP ? "Level-up (Lv. " + l.level + ")" : s.label).collect(Collectors.joining(", ")), class_124.field_1062));
         lore.add(priceLine(l, config));
         if (!l.known) {
            lore.add(t("Click to teach", class_124.field_1054));
         }
         class_2561 name = l.move.getDisplayName().method_27661().method_27692(l.known ? class_124.field_1060 : class_124.field_1068);
         menu.button(i, Menu.stack(gem(l.move), name, lore), p -> choose(p, state, l));
      }
      if (page.isEmpty()) {
         menu.icon(0, Menu.stack("minecraft:barrier", t("No moves match", class_124.field_1061), List.of(t("Change the search or reset the filters.", class_124.field_1080))));
      }

      // info boxes
      menu.icon(40, Menus.pokemonIcon(pokemon, MoveTutorMenus.headerLore(player, pokemon)));
      List<class_2561> f = new ArrayList<>();
      f.add(t(state.query.isBlank() ? "Search: none" : "Search: \"" + state.query + "\"", class_124.field_1080));
      f.add(t("Source: " + (state.filter == null ? "All" : state.filter.label), class_124.field_1080));
      f.add(t("Type: " + (state.type == null ? "All" : MoveTutorMenus.cap(state.type)), class_124.field_1080));
      f.add(t("Category: " + (state.category == null ? "All" : MoveTutorMenus.cap(state.category)), class_124.field_1080));
      f.add(t("Sort: " + state.sort.label, class_124.field_1080));
      menu.icon(41, Menu.stack("minecraft:compass", t("Filters", class_124.field_1065), f));
      menu.icon(42, Menu.stack("minecraft:book", t("Page " + (state.page + 1) + " / " + pages, class_124.field_1068), List.of(t(filtered.size() + " of " + all.size() + " moves shown", class_124.field_1080))));

      // footer controls (left click: next, right click: previous)
      List<class_2561> sl = List.of(t("Left click: open the search box", class_124.field_1054), t("Right click: clear the search", class_124.field_1054));
      control(menu, 45, Menu.stack("minecraft:compass", t(state.query.isBlank() ? "Search" : "Search: " + state.query, class_124.field_1065), sl), (p, right) -> {
         if (right) {
            state.query = "";
            state.page = 0;
            openList(p, state);
         } else {
            MoveTutorMenus.openSearch(p, state);
         }
      });
      cycleControl(menu, 46, "minecraft:comparator", "Sort: " + state.sort.label, (p, right) -> {
         state.sort = state.sort.next(right);
         state.page = 0;
         openList(p, state);
      });
      cycleControl(menu, 47, "minecraft:hopper", "Source: " + (state.filter == null ? "All" : state.filter.label), (p, right) -> {
         MoveSource[] values = MoveSource.values();
         int index = state.filter == null ? 0 : state.filter.ordinal() + 1;
         int next = Math.floorMod(index + (right ? -1 : 1), values.length + 1);
         state.filter = next == 0 ? null : values[next - 1];
         state.page = 0;
         openList(p, state);
      });
      String typeId = state.type == null ? "minecraft:ender_eye" : "cobblemon:" + state.type + "_gem";
      cycleControl(menu, 48, typeId, "Type: " + (state.type == null ? "All" : MoveTutorMenus.cap(state.type)), (p, right) -> {
         state.type = MoveTutorMenus.cycle(MoveTutorMenus.TYPES, state.type, right);
         state.page = 0;
         openList(p, state);
      });
      String catId = state.category == null ? "minecraft:paper" : state.category.equals("physical") ? "minecraft:iron_sword" : state.category.equals("special") ? "minecraft:blaze_rod" : "minecraft:feather";
      cycleControl(menu, 49, catId, "Category: " + (state.category == null ? "All" : MoveTutorMenus.cap(state.category)), (p, right) -> {
         state.category = MoveTutorMenus.cycle(MoveTutorMenus.CATS, state.category, right);
         state.page = 0;
         openList(p, state);
      });
      if (state.page > 0) {
         control(menu, 50, Menu.stack("minecraft:arrow", t("Previous page", class_124.field_1068), List.of()), (p, right) -> {
            state.page--;
            openList(p, state);
         });
      }
      if (state.page < pages - 1) {
         control(menu, 51, Menu.stack("minecraft:arrow", t("Next page", class_124.field_1068), List.of()), (p, right) -> {
            state.page++;
            openList(p, state);
         });
      }
      control(menu, 52, Menu.stack("minecraft:barrier", t("Reset all", class_124.field_1061), List.of(t("Clear search, filters and sort", class_124.field_1080))), (p, right) -> {
         state.query = "";
         state.filter = null;
         state.type = null;
         state.category = null;
         state.sort = MoveTutorMenus.Sort.DEFAULT;
         state.page = 0;
         openList(p, state);
      });
      menu.back(53, "Back to team", MoveTutorMenus::openParty);
      menu.open(player);
   }

   private static void control(Menu menu, int slot, net.minecraft.class_1799 stack, Menu.ClickAction action) {
      menu.clickButton(slot, stack, action);
      menu.role(slot, Menu.Role.FOOTER);
   }

   private static void cycleControl(Menu menu, int slot, String itemId, String label, Menu.ClickAction action) {
      List<class_2561> lore = List.of(t("Left click: next", class_124.field_1054), t("Right click: previous", class_124.field_1054));
      control(menu, slot, Menu.stack(itemId, t(label, class_124.field_1065), lore), action);
   }

   private static void choose(class_3222 player, MoveTutorMenus.ListState state, LearnableMove chosen) {
      Pokemon current = Menus.findInParty(player, state.pokemonId);
      if (current == null || Menus.refuseInBattle(player)) {
         return;
      }
      if (chosen.known) {
         player.method_7353(class_2561.method_43473().method_10852(current.getDisplayName(false)).method_27693(" already knows ").method_10852(chosen.move.getDisplayName()).method_27692(class_124.field_1061), true);
         return;
      }
      if (current.getMoveSet().hasSpace()) {
         MoveTutorMenus.teach(player, current, chosen, null);
         openList(player, state);
      } else {
         openReplace(player, state, chosen.move);
      }
   }

   static void openReplace(class_3222 player, MoveTutorMenus.ListState state, MoveTemplate newMove) {
      Pokemon pokemon = Menus.findInParty(player, state.pokemonId);
      if (pokemon == null || Menus.refuseInBattle(player)) {
         return;
      }
      Menu menu = new Menu(class_2561.method_43470("Replace which move?"), 3).layout(Menu.Layout.CARDS).screenTitle(class_2561.method_43470("Replace a move"));
      List<class_2561> head = MoveTutorMenus.moveDetails(newMove);
      head.add(t("Pick a move to forget", class_124.field_1054));
      menu.icon(4, Menu.stack(gem(newMove), class_2561.method_43470("Learn ").method_10852(newMove.getDisplayName()).method_27692(class_124.field_1065), head));
      int[] slots = {10, 12, 14, 16};
      List<Move> moves = pokemon.getMoveSet().getMoves();
      for (int i = 0; i < slots.length && i < moves.size(); i++) {
         MoveTemplate old = moves.get(i).getTemplate();
         List<class_2561> lore = MoveTutorMenus.moveDetails(old);
         lore.add(class_2561.method_43470("Click to forget it and learn ").method_10852(newMove.getDisplayName()).method_27692(class_124.field_1054));
         int index = i;
         menu.button(slots[i], Menu.stack(gem(old), old.getDisplayName().method_27661().method_27692(class_124.field_1068), lore), p -> {
            Pokemon cur = Menus.findInParty(p, state.pokemonId);
            if (cur != null && !Menus.refuseInBattle(p)) {
               List<Move> now = cur.getMoveSet().getMoves();
               if (index < now.size()) {
                  LearnableMove learnable = LearnableMove.of(cur, MoveTutorConfig.get()).stream().filter(m -> m.move == newMove).findFirst().orElse(null);
                  if (learnable != null && !learnable.known) {
                     MoveTutorMenus.teach(p, cur, learnable, now.get(index).getTemplate());
                  }
               }
            }
            openList(p, state);
         });
      }
      menu.back(22, "Cancel", p -> openList(p, state));
      menu.open(player);
   }
}
