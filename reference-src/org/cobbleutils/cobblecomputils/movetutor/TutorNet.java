package org.cobbleutils.cobblecomputils.movetutor;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.class_124;
import net.minecraft.class_2540;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;
import org.cobbleutils.cobblecomputils.gui.Menus;

/** Network layer for the custom /movetutor screen (live search, one scrolling list). */
public final class TutorNet implements ModInitializer {
   private static final Map<UUID, UUID> SESSIONS = new ConcurrentHashMap<>();

   public void onInitialize() {
      PayloadTypeRegistry.playS2C().register(Open.ID, Open.CODEC);
      PayloadTypeRegistry.playC2S().register(Select.ID, Select.CODEC);
      PayloadTypeRegistry.playC2S().register(Learn.ID, Learn.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(Select.ID, (payload, context) -> select(context.player(), payload.slot()));
      ServerPlayNetworking.registerGlobalReceiver(Learn.ID, (payload, context) -> learn(context.player(), payload.moveId(), payload.replace()));
   }

   public static boolean hasClient(class_3222 player) {
      return ServerPlayNetworking.canSend(player, Open.ID);
   }

   // ---- payloads ----
   public static record MoveInfo(String id, String type, String category, int power, int accuracy, int pp, String desc, String sources, int level, long price, boolean known, boolean benched) {
      void write(class_2540 buf) {
         buf.method_10814(id);
         buf.method_10814(type);
         buf.method_10814(category);
         buf.method_10804(power);
         buf.method_10804(accuracy);
         buf.method_10804(pp);
         buf.method_10814(desc);
         buf.method_10814(sources);
         buf.method_10804(level + 1);
         buf.writeLong(price);
         buf.writeBoolean(known);
         buf.writeBoolean(benched);
      }

      static MoveInfo read(class_2540 buf) {
         return new MoveInfo(buf.method_19772(), buf.method_19772(), buf.method_19772(), buf.method_10816(), buf.method_10816(), buf.method_10816(), buf.method_19772(), buf.method_19772(), buf.method_10816() - 1, buf.readLong(), buf.readBoolean(), buf.readBoolean());
      }
   }

   public static String sidOf(Pokemon p) {
      try { return p.getSpecies().getResourceIdentifier().toString(); } catch (Throwable t) { return ""; }
   }

   public static String aspOf(Pokemon p) {
      try { return String.join(",", p.getAspects()); } catch (Throwable t) { return ""; }
   }

   public static record Mon(int slot, String name, int level, String sid, String aspects) {
   }

   public static record Open(int selected, List<Mon> party, String balance, List<MoveInfo> known, List<MoveInfo> moves) implements class_8710 {
      public static final class_9154<Open> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "tutor_open"));
      public static final class_9139<class_9129, Open> CODEC = class_9139.method_56438(Open::write, Open::read);

      static void write(Open o, class_9129 buf) {
         buf.method_10804(o.selected);
         buf.method_10804(o.party.size());
         for (Mon m : o.party) {
            buf.method_10804(m.slot());
            buf.method_10814(m.name());
            buf.method_10804(m.level());
            buf.method_10814(m.sid());
            buf.method_10814(m.aspects());
         }
         buf.method_10814(o.balance);
         buf.method_10804(o.known.size());
         o.known.forEach(k -> k.write(buf));
         buf.method_10804(o.moves.size());
         o.moves.forEach(k -> k.write(buf));
      }

      static Open read(class_9129 buf) {
         int selected = buf.method_10816();
         int n = buf.method_10816();
         List<Mon> party = new ArrayList<>();
         for (int i = 0; i < n; i++) {
            party.add(new Mon(buf.method_10816(), buf.method_19772(), buf.method_10816(), buf.method_19772(), buf.method_19772()));
         }
         String balance = buf.method_19772();
         n = buf.method_10816();
         List<MoveInfo> known = new ArrayList<>();
         for (int i = 0; i < n; i++) {
            known.add(MoveInfo.read(buf));
         }
         n = buf.method_10816();
         List<MoveInfo> moves = new ArrayList<>();
         for (int i = 0; i < n; i++) {
            moves.add(MoveInfo.read(buf));
         }
         return new Open(selected, party, balance, known, moves);
      }

      public class_9154<? extends class_8710> method_56479() {
         return ID;
      }
   }

   public static record Select(int slot) implements class_8710 {
      public static final class_9154<Select> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "tutor_select"));
      public static final class_9139<class_9129, Select> CODEC = class_9139.method_56438((p, buf) -> buf.method_10804(p.slot()), buf -> new Select(buf.method_10816()));

      public class_9154<? extends class_8710> method_56479() {
         return ID;
      }
   }

   public static record Learn(String moveId, int replace) implements class_8710 {
      public static final class_9154<Learn> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "tutor_learn"));
      public static final class_9139<class_9129, Learn> CODEC = class_9139.method_56438((p, buf) -> {
         buf.method_10814(p.moveId());
         buf.method_10804(p.replace() + 1);
      }, buf -> new Learn(buf.method_19772(), buf.method_10816() - 1));

      public class_9154<? extends class_8710> method_56479() {
         return ID;
      }
   }

   // ---- server side ----
   private static MoveInfo info(MoveTemplate m, LearnableMove l, MoveTutorConfig config) {
      String desc = m.getDescription().getString();
      long price = 0L;
      if (l != null && !l.known && MoveTutorMenus.charging()) {
         price = l.price(config);
      }
      StringBuilder sources = new StringBuilder();
      if (l != null) {
         for (MoveSource s : l.sources) {
            if (sources.length() > 0) {
               sources.append(',');
            }
            sources.append(s.label);
         }
      }
      return new MoveInfo(
         m.getName(), MoveTutorMenus.cap(m.getElementalType().getName()), MoveTutorMenus.cap(m.getDamageCategory().getName()),
         (int)m.getPower(), (int)m.getAccuracy(), m.getPp(), desc, sources.toString(), l == null ? -1 : l.level,
         price, l != null && l.known, l != null && l.benched
      );
   }

   /** Opens (or refreshes) the tutor screen; pokemonId null = first party member. */
   public static void open(class_3222 player, UUID pokemonId) {
      if (Menus.refuseInBattle(player)) {
         return;
      }
      PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
      List<Mon> mons = new ArrayList<>();
      Pokemon selected = null;
      int selectedIndex = 0;
      for (int i = 0; i < 6; i++) {
         Pokemon p = party.get(i);
         if (p == null) {
            continue;
         }
         if (selected == null && (pokemonId == null || p.getUuid().equals(pokemonId))) {
            selected = p;
            selectedIndex = mons.size();
         }
         mons.add(new Mon(i, p.getDisplayName(false).getString(), p.getLevel(), sidOf(p), aspOf(p)));
      }
      if (selected == null || mons.isEmpty()) {
         player.method_7353(class_2561.method_43470("You have no Pokemon to teach.").method_27692(class_124.field_1061), true);
         return;
      }
      MoveTutorConfig config = MoveTutorConfig.get();
      SESSIONS.put(player.method_5667(), selected.getUuid());
      List<MoveInfo> known = new ArrayList<>();
      for (Move move : selected.getMoveSet().getMoves()) {
         known.add(info(move.getTemplate(), null, config));
      }
      List<MoveInfo> moves = new ArrayList<>();
      for (LearnableMove l : LearnableMove.of(selected, config)) {
         moves.add(info(l.move, l, config));
      }
      String balance = MoveTutorMenus.charging() ? MoveTutorMenus.money(org.cobbleutils.cobblecomputils.Cobblecomputils.economy().balance(player)) : "";
      ServerPlayNetworking.send(player, new Open(selectedIndex, mons, balance, known, moves));
   }

   private static void select(class_3222 player, int slot) {
      Pokemon p = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot);
      if (p != null) {
         open(player, p.getUuid());
      }
   }

   private static void learn(class_3222 player, String moveId, int replace) {
      UUID id = SESSIONS.get(player.method_5667());
      Pokemon pokemon = id == null ? null : Menus.findInParty(player, id);
      if (pokemon == null || Menus.refuseInBattle(player)) {
         return;
      }
      LearnableMove chosen = LearnableMove.of(pokemon, MoveTutorConfig.get()).stream().filter(m -> m.move.getName().equals(moveId)).findFirst().orElse(null);
      if (chosen == null) {
         open(player, id);
         return;
      }
      if (chosen.known) {
         player.method_7353(class_2561.method_43473().method_10852(pokemon.getDisplayName(false)).method_27693(" already knows ").method_10852(chosen.move.getDisplayName()).method_27692(class_124.field_1061), true);
      } else if (pokemon.getMoveSet().hasSpace()) {
         MoveTutorMenus.teach(player, pokemon, chosen, null);
      } else {
         List<Move> moves = pokemon.getMoveSet().getMoves();
         if (replace >= 0 && replace < moves.size()) {
            MoveTutorMenus.teach(player, pokemon, chosen, moves.get(replace).getTemplate());
         }
      }
      open(player, id);
   }
}
