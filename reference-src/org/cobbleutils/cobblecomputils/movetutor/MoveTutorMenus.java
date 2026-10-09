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
import net.minecraft.class_124;
import net.minecraft.class_1277;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.class_5250;
import net.minecraft.class_7923;
import net.minecraft.class_747;
import net.minecraft.class_9334;
import org.cobbleutils.cobblecomputils.Cobblecomputils;
import org.cobbleutils.cobblecomputils.economy.Economy;
import org.cobbleutils.cobblecomputils.gui.Menus;

public final class MoveTutorMenus {
   private static final int LIST_ROWS = 6;
   private static final int[] LIST_SLOTS = listSlots();
   private static final int HEADER_SLOT = 4;
   private static final int BACK_SLOT = 45;
   private static final int PREV_SLOT = 48;
   private static final int FILTER_SLOT = 49;
   private static final int NEXT_SLOT = 50;
   private static final int[] REPLACE_SLOTS = new int[]{10, 12, 14, 16};
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

   public static void openParty(class_3222 player) {
      if (TutorNet.hasClient(player)) {
         TutorNet.open(player, null);
         return;
      }
      if (RogueTutor.available()) {
         RogueTutor.openParty(player);
         return;
      }
      Menus.openPartyPicker(
         player,
         class_2561.method_43470("Move Tutor"),
         "Click to teach moves",
         pokemon -> pokemon.getMoveSet()
               .getMoves()
               .stream()
               .map(move -> move.getTemplate().getDisplayName().method_27661().method_10862(Menus.plain(class_124.field_1075)))
               .collect(Collectors.toList()),
         pokemon -> openList(player, new ListState(pokemon.getUuid()))
      );
   }

   private static final int SEARCH_SLOT = 46;
   private static final int SORT_SLOT = 47;
   private static final int INFO_SLOT = 49;
   private static final int SRC_SLOT = 51;
   private static final int TYPE_SLOT = 52;
   private static final int CAT_SLOT = 53;
   static final String[] TYPES = {"normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"};
   static final String[] CATS = {"physical", "special", "status"};

   static class_1792 item(String id) {
      class_1792 found = (class_1792)class_7923.field_41178.method_10223(class_2960.method_60655("minecraft", id));
      return found == class_1802.field_8162 ? class_1802.field_8407 : found;
   }

   static String cycle(String[] values, String current, boolean backwards) {
      int index = -1;
      for (int i = 0; i < values.length; i++) {
         if (values[i].equals(current)) {
            index = i;
         }
      }
      int next = Math.floorMod((index + 1) + (backwards ? -1 : 1), values.length + 1);
      return next == 0 ? null : values[next - 1];
   }

   static String compact(String s) {
      return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
   }

   static String cap(String s) {
      return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
   }

   private static boolean matchesQuery(org.cobbleutils.cobblecomputils.movetutor.LearnableMove m, String query) {
      if (query == null || query.isBlank()) {
         return true;
      }
      StringBuilder hay = new StringBuilder();
      hay.append(m.move.getName()).append(' ').append(m.move.getElementalType().getName()).append(' ').append(m.move.getDamageCategory().getName());
      for (org.cobbleutils.cobblecomputils.movetutor.MoveSource s : m.sources) {
         hay.append(' ').append(s.label);
      }
      String h = compact(hay.toString());
      for (String token : query.trim().split("\\s+")) {
         if (!h.contains(compact(token))) {
            return false;
         }
      }
      return true;
   }

   static List<org.cobbleutils.cobblecomputils.movetutor.LearnableMove> applyFilters(
      List<org.cobbleutils.cobblecomputils.movetutor.LearnableMove> all, ListState state, String query
   ) {
      List<org.cobbleutils.cobblecomputils.movetutor.LearnableMove> out = all.stream()
         .filter(m -> state.filter == null || m.sources.contains(state.filter))
         .filter(m -> state.type == null || m.move.getElementalType().getName().equalsIgnoreCase(state.type))
         .filter(m -> state.category == null || m.move.getDamageCategory().getName().equalsIgnoreCase(state.category))
         .filter(m -> matchesQuery(m, query))
         .collect(Collectors.toList());
      switch (state.sort) {
         case NAME -> out.sort(java.util.Comparator.comparing(m -> m.move.getName()));
         case POWER -> out.sort(java.util.Comparator.<org.cobbleutils.cobblecomputils.movetutor.LearnableMove>comparingDouble(m -> -m.move.getPower()).thenComparing(m -> m.move.getName()));
         case TYPE -> out.sort(java.util.Comparator.<org.cobbleutils.cobblecomputils.movetutor.LearnableMove, String>comparing(m -> m.move.getElementalType().getName()).thenComparing(m -> m.move.getName()));
         default -> {
         }
      }
      return out;
   }

   static void openSearch(class_3222 player, ListState state) {
      Pokemon pokemon = Menus.findInParty(player, state.pokemonId);
      if (pokemon == null) {
         return;
      }
      org.cobbleutils.cobblecomputils.movetutor.MoveTutorConfig config = org.cobbleutils.cobblecomputils.movetutor.MoveTutorConfig.get();
      java.util.function.ToIntFunction<String> counter = q -> {
         Pokemon current = Menus.findInParty(player, state.pokemonId);
         return current == null ? 0 : applyFilters(org.cobbleutils.cobblecomputils.movetutor.LearnableMove.of(current, config), state, q).size();
      };
      player.method_17355(
         new class_747(
            (syncId, inv, p) -> new MoveSearchPrompt(
               syncId, inv, state.query, counter,
               q -> {
                  state.query = q == null ? "" : q.trim();
                  state.page = 0;
                  openList(player, state);
               },
               () -> openList(player, state)
            ),
            class_2561.method_43470("Search moves: type a name, type or category")
         )
      );
   }

   static void openList(class_3222 player, ListState state) {
      if (TutorNet.hasClient(player)) {
         TutorNet.open(player, state.pokemonId);
         return;
      }
      if (RogueTutor.available()) {
         RogueTutor.openList(player, state);
         return;
      }
      openChestList(player, state);
   }

   private static void openChestList(class_3222 player, ListState state) {
      Pokemon pokemon = Menus.findInParty(player, state.pokemonId);
      if (pokemon != null && !Menus.refuseInBattle(player)) {
         class_1277 menu = Menus.framed(6);
         List<org.cobbleutils.cobblecomputils.movetutor.LearnableMove> shown = drawList(menu, player, pokemon, state);
         class_5250 title = class_2561.method_43470("Move Tutor: ").method_10852(pokemon.getDisplayName(false));
         if (!state.query.isBlank()) {
            title = title.method_27693("  [" + state.query + "]");
         }
         List<List<org.cobbleutils.cobblecomputils.movetutor.LearnableMove>> visible = new ArrayList<>(List.of(shown));
         Menus.open(
            player,
            menu,
            6,
            title,
            (slot, button, action) -> {
               if (action != class_1713.field_7790) {
                  return;
               }
               Pokemon current = Menus.findInParty(player, state.pokemonId);
               if (current == null || Menus.refuseInBattle(player)) {
                  player.method_7346();
                  return;
               }
               if (slot == 45) {
                  openParty(player);
                  return;
               }
               boolean right = button == 1;
               if (slot == 48 || slot == 50 || slot == SEARCH_SLOT || slot == SORT_SLOT || slot == INFO_SLOT || slot == SRC_SLOT || slot == TYPE_SLOT || slot == CAT_SLOT) {
                  if (slot == 48) {
                     state.page--;
                  } else if (slot == 50) {
                     state.page++;
                  } else if (slot == SEARCH_SLOT) {
                     if (right) {
                        state.query = "";
                        state.page = 0;
                     } else {
                        openSearch(player, state);
                        return;
                     }
                  } else if (slot == SORT_SLOT) {
                     state.sort = state.sort.next(right);
                     state.page = 0;
                  } else if (slot == INFO_SLOT) {
                     state.query = "";
                     state.filter = null;
                     state.type = null;
                     state.category = null;
                     state.sort = Sort.DEFAULT;
                     state.page = 0;
                  } else if (slot == SRC_SLOT) {
                     state.filter = nextFilter(state.filter, right);
                     state.page = 0;
                  } else if (slot == TYPE_SLOT) {
                     state.type = cycle(TYPES, state.type, right);
                     state.page = 0;
                  } else {
                     state.category = cycle(CATS, state.category, right);
                     state.page = 0;
                  }
                  visible.set(0, drawList(menu, player, current, state));
                  return;
               }
               int index = Menus.indexOf(LIST_SLOTS, slot);
               List<org.cobbleutils.cobblecomputils.movetutor.LearnableMove> page = visible.get(0);
               if (index >= 0 && index < page.size()) {
                  org.cobbleutils.cobblecomputils.movetutor.LearnableMove chosen = page.get(index);
                  if (chosen.known) {
                     player.method_7353(
                        class_2561.method_43473()
                           .method_10852(current.getDisplayName(false))
                           .method_27693(" already knows ")
                           .method_10852(chosen.move.getDisplayName())
                           .method_27692(class_124.field_1061),
                        true
                     );
                  } else if (current.getMoveSet().hasSpace()) {
                     if (teach(player, current, chosen, null)) {
                        visible.set(0, drawList(menu, player, current, state));
                     }
                  } else {
                     openReplace(player, state, chosen.move);
                  }
               }
            }
         );
      }
   }

   private static class_1799 control(String itemId, String name, class_124 color, List<class_2561> lore) {
      return Menus.named(new class_1799(item(itemId)), Menus.line(name, color), lore);
   }

   private static List<org.cobbleutils.cobblecomputils.movetutor.LearnableMove> drawList(
      class_1277 menu, class_3222 player, Pokemon pokemon, ListState state
   ) {
      org.cobbleutils.cobblecomputils.movetutor.MoveTutorConfig config = org.cobbleutils.cobblecomputils.movetutor.MoveTutorConfig.get();
      List<org.cobbleutils.cobblecomputils.movetutor.LearnableMove> all = org.cobbleutils.cobblecomputils.movetutor.LearnableMove.of(pokemon, config);
      List<org.cobbleutils.cobblecomputils.movetutor.LearnableMove> filtered = applyFilters(all, state, state.query);
      int pages = Math.max(1, (filtered.size() + LIST_SLOTS.length - 1) / LIST_SLOTS.length);
      state.page = Math.max(0, Math.min(state.page, pages - 1));
      int from = state.page * LIST_SLOTS.length;
      List<org.cobbleutils.cobblecomputils.movetutor.LearnableMove> page = filtered.subList(from, Math.min(filtered.size(), from + LIST_SLOTS.length));
      menu.method_5447(4, Menus.pokemonIcon(pokemon, headerLore(player, pokemon)));

      for (int i = 0; i < LIST_SLOTS.length; i++) {
         menu.method_5447(LIST_SLOTS[i], i < page.size() ? moveIcon(page.get(i), config) : class_1799.field_8037);
      }
      if (page.isEmpty()) {
         menu.method_5447(22, control("barrier", "No moves match", class_124.field_1061, List.of(
            Menus.line("Try a different search or clear the filters.", class_124.field_1080),
            Menus.line("Click the book to reset everything.", class_124.field_1054))));
      }

      class_1799 pane = Menus.named(new class_1799(class_1802.field_8871), class_2561.method_43470(" "), List.of());
      menu.method_5447(45, Menus.backButton("Back to team"));

      List<class_2561> searchLore = new ArrayList<>();
      searchLore.add(Menus.line(state.query.isBlank() ? "No search active" : "Searching: \"" + state.query + "\"", state.query.isBlank() ? class_124.field_1080 : class_124.field_1065));
      searchLore.add(class_2561.method_43473());
      searchLore.add(Menus.line("Type part of a move name, a type", class_124.field_1080));
      searchLore.add(Menus.line("(fire) or a category (status).", class_124.field_1080));
      searchLore.add(Menus.line("Left click: open the search box", class_124.field_1054));
      searchLore.add(Menus.line("Right click: clear the search", class_124.field_1054));
      menu.method_5447(SEARCH_SLOT, control("compass", "Search", class_124.field_1065, searchLore));

      List<class_2561> sortLore = new ArrayList<>();
      sortLore.add(class_2561.method_43473());
      for (Sort s : Sort.values()) {
         sortLore.add(Menus.line((s == state.sort ? "> " : "  ") + s.label, s == state.sort ? class_124.field_1054 : class_124.field_1080));
      }
      sortLore.add(class_2561.method_43473());
      sortLore.add(Menus.line("Left / right click: next / previous", class_124.field_1054));
      menu.method_5447(SORT_SLOT, control("comparator", "Sort: " + state.sort.label, class_124.field_1065, sortLore));

      menu.method_5447(48, state.page > 0 ? Menus.named(new class_1799(class_1802.field_8407), Menus.line("Previous page", class_124.field_1068), List.of()) : pane.method_7972());
      menu.method_5447(50, state.page < pages - 1 ? Menus.named(new class_1799(class_1802.field_8407), Menus.line("Next page", class_124.field_1068), List.of()) : pane.method_7972());

      List<class_2561> infoLore = new ArrayList<>();
      infoLore.add(Menus.line(filtered.size() + " of " + all.size() + " moves shown", class_124.field_1080));
      infoLore.add(class_2561.method_43473());
      infoLore.add(Menus.line("Click to reset search, filters and sort", class_124.field_1054));
      menu.method_5447(INFO_SLOT, control("book", "Page " + (state.page + 1) + " / " + pages, class_124.field_1068, infoLore));

      List<class_2561> filterLore = new ArrayList<>();
      filterLore.add(class_2561.method_43473());
      filterLore.add(Menus.line(state.filter == null ? "> All" : "  All", state.filter == null ? class_124.field_1054 : class_124.field_1080));
      for (org.cobbleutils.cobblecomputils.movetutor.MoveSource source : org.cobbleutils.cobblecomputils.movetutor.MoveSource.values()) {
         long count = all.stream().filter(m -> m.sources.contains(source)).count();
         if (count > 0L) {
            boolean selected = source == state.filter;
            filterLore.add(Menus.line((selected ? "> " : "  ") + source.label + " (" + count + ")", selected ? class_124.field_1054 : class_124.field_1080));
         }
      }
      filterLore.add(class_2561.method_43473());
      filterLore.add(Menus.line("Left / right click: next / previous", class_124.field_1054));
      menu.method_5447(SRC_SLOT, control("hopper", "Source: " + (state.filter == null ? "All" : state.filter.label), class_124.field_1065, filterLore));

      List<class_2561> typeLore = new ArrayList<>();
      typeLore.add(Menus.line(state.type == null ? "Showing every type" : "Only " + cap(state.type) + " moves", class_124.field_1080));
      typeLore.add(class_2561.method_43473());
      typeLore.add(Menus.line("Left / right click: next / previous", class_124.field_1054));
      class_1799 typeStack = state.type == null
         ? control("ender_eye", "Type: All", class_124.field_1065, typeLore)
         : Menus.named(new class_1799(gemItem(state.type)), Menus.line("Type: " + cap(state.type), class_124.field_1065), typeLore);
      menu.method_5447(TYPE_SLOT, typeStack);

      List<class_2561> catLore = new ArrayList<>();
      catLore.add(Menus.line(state.category == null ? "Physical, special and status" : "Only " + state.category + " moves", class_124.field_1080));
      catLore.add(class_2561.method_43473());
      catLore.add(Menus.line("Left / right click: next / previous", class_124.field_1054));
      String catItem = state.category == null ? "paper" : state.category.equals("physical") ? "iron_sword" : state.category.equals("special") ? "blaze_rod" : "feather";
      menu.method_5447(CAT_SLOT, control(catItem, "Category: " + (state.category == null ? "All" : cap(state.category)), class_124.field_1065, catLore));
      return page;
   }

   static class_1792 gemItem(String type) {
      class_1792 gem = (class_1792)class_7923.field_41178.method_10223(class_2960.method_60655("cobblemon", type + "_gem"));
      return gem == class_1802.field_8162 ? class_1802.field_8529 : gem;
   }

   static org.cobbleutils.cobblecomputils.movetutor.MoveSource nextFilter(
      org.cobbleutils.cobblecomputils.movetutor.MoveSource current, boolean backwards
   ) {
      org.cobbleutils.cobblecomputils.movetutor.MoveSource[] values = org.cobbleutils.cobblecomputils.movetutor.MoveSource.values();
      int index = current == null ? 0 : current.ordinal() + 1;
      int next = Math.floorMod(index + (backwards ? -1 : 1), values.length + 1);
      return next == 0 ? null : values[next - 1];
   }

   static List<class_2561> headerLore(class_3222 player, Pokemon pokemon) {
      List<class_2561> lore = new ArrayList<>();
      lore.add(Menus.line("Lv. " + pokemon.getLevel(), class_124.field_1080));
      lore.add(Menus.line("Current moves:", class_124.field_1080));

      for (Move move : pokemon.getMoveSet().getMoves()) {
         lore.add(class_2561.method_43470("  ").method_10852(move.getTemplate().getDisplayName()).method_10862(Menus.plain(class_124.field_1075)));
      }

      if (charging()) {
         lore.add(Menus.line("Balance: " + money(Cobblecomputils.economy().balance(player)), class_124.field_1065));
      }

      return lore;
   }

   private static class_1799 moveIcon(
      org.cobbleutils.cobblecomputils.movetutor.LearnableMove learnable, org.cobbleutils.cobblecomputils.movetutor.MoveTutorConfig config
   ) {
      List<class_2561> lore = moveDetails(learnable.move);
      lore.add(class_2561.method_43473());
      lore.add(
         Menus.line(
            "Learned by: "
               + learnable.sources
                  .stream()
                  .map(s -> s == org.cobbleutils.cobblecomputils.movetutor.MoveSource.LEVEL_UP ? "Level-up (Lv. " + learnable.level + ")" : s.label)
                  .collect(Collectors.joining(", ")),
            class_124.field_1062
         )
      );
      if (learnable.known) {
         lore.add(Menus.line("Already known", class_124.field_1060));
      } else {
         long price = charging() ? learnable.price(config) : 0L;
         lore.add(
            Menus.line(price > 0L ? "Cost: " + money(BigInteger.valueOf(price)) : (learnable.benched ? "Free (known before)" : "Free"), class_124.field_1065)
         );
         lore.add(Menus.line("Click to teach", class_124.field_1054));
      }

      class_1799 stack = Menus.named(
         new class_1799(typeItem(learnable.move)),
         learnable.move.getDisplayName().method_27661().method_10862(Menus.plain(learnable.known ? class_124.field_1060 : class_124.field_1068)),
         lore
      );
      if (learnable.known) {
         stack.method_57379(class_9334.field_49641, true);
      }

      return stack;
   }

   static List<class_2561> moveDetails(MoveTemplate move) {
      List<class_2561> lore = new ArrayList<>();
      lore.add(
         class_2561.method_43470("Type: ")
            .method_10852(move.getElementalType().getDisplayName())
            .method_27693("   Category: ")
            .method_10852(move.getDamageCategory().getDisplayName())
            .method_10862(Menus.plain(class_124.field_1080))
      );
      String power = move.getPower() > 0.0 ? String.valueOf((int)move.getPower()) : "—";
      String accuracy = move.getAccuracy() > 0.0 ? (int)move.getAccuracy() + "%" : "—";
      lore.add(Menus.line("Power: " + power + "   Accuracy: " + accuracy + "   PP: " + move.getPp(), class_124.field_1080));
      lore.add(class_2561.method_43473());
      class_2561 description = move.getDescription();
      String resolved = description.getString();
      if (resolved.startsWith("cobblemon.")) {
         lore.add(description.method_27661().method_10862(Menus.plain(class_124.field_1068)));
      } else {
         lore.addAll(Menus.wrap(resolved, 40, class_124.field_1068));
      }

      return lore;
   }

   static class_1792 typeItem(MoveTemplate move) {
      String type = move.getElementalType().getName().toLowerCase(Locale.ROOT);
      class_1792 gem = (class_1792)class_7923.field_41178.method_10223(class_2960.method_60655("cobblemon", type + "_gem"));
      return gem == class_1802.field_8162 ? class_1802.field_8529 : gem;
   }

   static void openReplace(class_3222 player, ListState state, MoveTemplate newMove) {
      Pokemon pokemon = Menus.findInParty(player, state.pokemonId);
      if (pokemon != null && !Menus.refuseInBattle(player)) {
         class_1277 menu = Menus.framed(3);
         List<class_2561> headerLore = moveDetails(newMove);
         headerLore.add(class_2561.method_43473());
         headerLore.add(Menus.line("Pick a move to forget", class_124.field_1054));
         menu.method_5447(
            4,
            Menus.named(
               new class_1799(typeItem(newMove)),
               class_2561.method_43470("Learn ").method_10852(newMove.getDisplayName()).method_10862(Menus.plain(class_124.field_1065)),
               headerLore
            )
         );
         List<Move> moves = pokemon.getMoveSet().getMoves();

         for (int i = 0; i < REPLACE_SLOTS.length && i < moves.size(); i++) {
            MoveTemplate old = moves.get(i).getTemplate();
            List<class_2561> lore = moveDetails(old);
            lore.add(class_2561.method_43473());
            lore.add(
               class_2561.method_43470("Click to forget it and learn ").method_10852(newMove.getDisplayName()).method_10862(Menus.plain(class_124.field_1054))
            );
            menu.method_5447(
               REPLACE_SLOTS[i],
               Menus.named(new class_1799(typeItem(old)), old.getDisplayName().method_27661().method_10862(Menus.plain(class_124.field_1068)), lore)
            );
         }

         menu.method_5447(22, Menus.backButton("Back to moves"));
         class_5250 title = class_2561.method_43470("Replace a move: ").method_10852(pokemon.getDisplayName(false));
         Menus.open(
            player,
            menu,
            3,
            title,
            (slot, button, action) -> {
               if (action == class_1713.field_7790) {
                  if (slot == 22) {
                     openList(player, state);
                  } else {
                     int index = Menus.indexOf(REPLACE_SLOTS, slot);
                     Pokemon current = Menus.findInParty(player, state.pokemonId);
                     if (index >= 0 && current != null && !Menus.refuseInBattle(player)) {
                        List<Move> currentMoves = current.getMoveSet().getMoves();
                        if (index < currentMoves.size()) {
                           org.cobbleutils.cobblecomputils.movetutor.LearnableMove learnable = org.cobbleutils.cobblecomputils.movetutor.LearnableMove.of(
                                 current, org.cobbleutils.cobblecomputils.movetutor.MoveTutorConfig.get()
                              )
                              .stream()
                              .filter(m -> m.move == newMove)
                              .findFirst()
                              .orElse(null);
                           if (learnable != null && !learnable.known) {
                              teach(player, current, learnable, currentMoves.get(index).getTemplate());
                           }

                           openList(player, state);
                        }
                     }
                  }
               }
            }
         );
      }
   }

   static boolean teach(class_3222 player, Pokemon pokemon, org.cobbleutils.cobblecomputils.movetutor.LearnableMove learnable, MoveTemplate replaced) {
      Economy economy = Cobblecomputils.economy();
      long price = charging() ? learnable.price(org.cobbleutils.cobblecomputils.movetutor.MoveTutorConfig.get()) : 0L;
      BigInteger cost = BigInteger.valueOf(price);
      if (price > 0L && !economy.withdraw(player, cost)) {
         player.method_7353(
            class_2561.method_43470("Not enough CobbleDollars: need " + money(cost) + ", you have " + money(economy.balance(player)))
               .method_27692(class_124.field_1061),
            true
         );
         return false;
      } else {
         MoveTemplate move = learnable.move;
         boolean learned;
         if (replaced != null) {
            learned = pokemon.exchangeMove(replaced, move);
         } else if (learnable.benched) {
            learned = pokemon.exchangeMove(null, move);
         } else {
            learned = pokemon.getMoveSet().add(move.create());
         }

         if (!learned) {
            if (price > 0L) {
               economy.deposit(player, cost);
            }

            player.method_7353(class_2561.method_43470("Couldn't teach that move.").method_27692(class_124.field_1061), true);
            return false;
         } else {
            class_5250 message = class_2561.method_43473()
               .method_10852(pokemon.getDisplayName(false))
               .method_27693(" learned ")
               .method_10852(move.getDisplayName())
               .method_27693("!");
            if (price > 0L) {
               message.method_27693(" (paid " + money(cost) + ")");
            }

            org.cobbleutils.cobblecomputils.capture.MoveGuard.record(pokemon, move);
            player.method_7353(message.method_27692(class_124.field_1060), true);
            return true;
         }
      }
   }

   static boolean charging() {
      return org.cobbleutils.cobblecomputils.movetutor.MoveTutorConfig.get().charge && !Cobblecomputils.economy().isFree();
   }

   static String money(BigInteger amount) {
      return Cobblecomputils.economy().format(amount);
   }

   enum Sort {
      DEFAULT("Default order"),
      NAME("A to Z"),
      POWER("Strongest first"),
      TYPE("By type");

      final String label;

      Sort(String label) {
         this.label = label;
      }

      Sort next(boolean backwards) {
         Sort[] v = values();
         return v[Math.floorMod(this.ordinal() + (backwards ? -1 : 1), v.length)];
      }
   }

   static final class ListState {
      final UUID pokemonId;
      int page;
      org.cobbleutils.cobblecomputils.movetutor.MoveSource filter;
      String query = "";
      Sort sort = Sort.DEFAULT;
      String type;
      String category;

      ListState(UUID pokemonId) {
         this.pokemonId = pokemonId;
      }
   }
}
