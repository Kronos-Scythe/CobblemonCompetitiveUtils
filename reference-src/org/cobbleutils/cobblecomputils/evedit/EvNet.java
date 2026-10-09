package org.cobbleutils.cobblecomputils.evedit;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.EVs;
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
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;
import org.cobbleutils.cobblecomputils.Cobblecomputils;
import org.cobbleutils.cobblecomputils.economy.Economy;
import org.cobbleutils.cobblecomputils.gui.Menus;
import org.cobbleutils.cobblecomputils.movetutor.TutorNet;

/** Network layer for the custom /evedit screen. */
public final class EvNet implements ModInitializer {
   private static final Map<UUID, UUID> SESSIONS = new ConcurrentHashMap<>();
   private static final Stat[] STATS = new Stat[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};

   public void onInitialize() {
      PayloadTypeRegistry.playS2C().register(Open.ID, Open.CODEC);
      PayloadTypeRegistry.playC2S().register(Select.ID, Select.CODEC);
      PayloadTypeRegistry.playC2S().register(SetEv.ID, SetEv.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(Select.ID, (payload, context) -> select(context.player(), payload.slot()));
      ServerPlayNetworking.registerGlobalReceiver(SetEv.ID, (payload, context) -> set(context.player(), payload.stat(), payload.target(), payload.cap()));
   }

   public static boolean hasClient(class_3222 player) {
      return ServerPlayNetworking.canSend(player, Open.ID);
   }

   public static record Open(int selected, List<TutorNet.Mon> party, String balance, int refund, String name, int level,
                             int[] ev, int[] stat, int[] max, String[] price, String msg, boolean err) implements class_8710 {
      public static final class_9154<Open> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "ev_open"));
      public static final class_9139<class_9129, Open> CODEC = class_9139.method_56438(Open::write, Open::read);

      static void write(Open o, class_9129 buf) {
         buf.method_10804(o.selected);
         buf.method_10804(o.party.size());
         for (TutorNet.Mon m : o.party) {
            buf.method_10804(m.slot());
            buf.method_10814(m.name());
            buf.method_10804(m.level());
         }
         buf.method_10814(o.balance);
         buf.method_10804(o.refund);
         buf.method_10814(o.name);
         buf.method_10804(o.level);
         for (int i = 0; i < 6; i++) {
            buf.method_10804(o.ev[i]);
            buf.method_10804(o.stat[i]);
            buf.method_10804(o.max[i]);
            buf.method_10814(o.price[i]);
         }
         buf.method_10814(o.msg);
         buf.writeBoolean(o.err);
      }

      static Open read(class_9129 buf) {
         int selected = buf.method_10816();
         int n = buf.method_10816();
         List<TutorNet.Mon> party = new ArrayList<>();
         for (int i = 0; i < n; i++) {
            party.add(new TutorNet.Mon(buf.method_10816(), buf.method_19772(), buf.method_10816()));
         }
         String balance = buf.method_19772();
         int refund = buf.method_10816();
         String name = buf.method_19772();
         int level = buf.method_10816();
         int[] ev = new int[6];
         int[] stat = new int[6];
         int[] max = new int[6];
         String[] price = new String[6];
         for (int i = 0; i < 6; i++) {
            ev[i] = buf.method_10816();
            stat[i] = buf.method_10816();
            max[i] = buf.method_10816();
            price[i] = buf.method_19772();
         }
         return new Open(selected, party, balance, refund, name, level, ev, stat, max, price, buf.method_19772(), buf.readBoolean());
      }

      public class_9154<? extends class_8710> method_56479() {
         return ID;
      }
   }

   public static record Select(int slot) implements class_8710 {
      public static final class_9154<Select> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "ev_select"));
      public static final class_9139<class_9129, Select> CODEC = class_9139.method_56438((p, buf) -> buf.method_10804(p.slot()), buf -> new Select(buf.method_10816()));

      public class_9154<? extends class_8710> method_56479() {
         return ID;
      }
   }

   /** stat = 0..5, or -1 to reset every stat; cap = limit the raise to what the player can afford. */
   public static record SetEv(int stat, int target, boolean cap) implements class_8710 {
      public static final class_9154<SetEv> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "ev_set"));
      public static final class_9139<class_9129, SetEv> CODEC = class_9139.method_56438((p, buf) -> {
         buf.method_10804(p.stat() + 1);
         buf.method_10804(p.target());
         buf.writeBoolean(p.cap());
      }, buf -> new SetEv(buf.method_10816() - 1, buf.method_10816(), buf.readBoolean()));

      public class_9154<? extends class_8710> method_56479() {
         return ID;
      }
   }

   // ---- server side ----
   private static long pricePerEv(Stat stat) {
      EvEditConfig config = EvEditConfig.get();
      return config.charge && !Cobblecomputils.economy().isFree() ? config.price(stat) : 0L;
   }

   private static String money(BigInteger amount) {
      return Cobblecomputils.economy().format(amount);
   }

   private static int maxAllowed(EVs evs, Stat stat) {
      int current = evs.getOrDefault(stat);
      return Math.min(252, current + (510 - evs.total()));
   }

   public static void open(class_3222 player, UUID pokemonId) {
      open(player, pokemonId, "", false);
   }

   public static void open(class_3222 player, UUID pokemonId, String msg, boolean err) {
      if (Menus.refuseInBattle(player)) {
         return;
      }
      PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
      List<TutorNet.Mon> mons = new ArrayList<>();
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
         mons.add(new TutorNet.Mon(i, p.getDisplayName(false).getString(), p.getLevel()));
      }
      if (selected == null || mons.isEmpty()) {
         player.method_7353(class_2561.method_43470("You have no Pokemon to edit.").method_27692(class_124.field_1061), true);
         return;
      }
      SESSIONS.put(player.method_5667(), selected.getUuid());
      EVs evs = selected.getEvs();
      int[] ev = new int[6];
      int[] stat = new int[6];
      int[] max = new int[6];
      String[] price = new String[6];
      for (int i = 0; i < 6; i++) {
         ev[i] = evs.getOrDefault(STATS[i]);
         stat[i] = selected.getStat(STATS[i]);
         max[i] = maxAllowed(evs, STATS[i]);
         long p = pricePerEv(STATS[i]);
         price[i] = p > 0L ? money(BigInteger.valueOf(p)) : "";
      }
      boolean charging = EvEditConfig.get().charge && !Cobblecomputils.economy().isFree();
      String balance = charging ? money(Cobblecomputils.economy().balance(player)) : "";
      net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new Open(selectedIndex, mons, balance,
         EvEditConfig.get().refundPercent(), selected.getDisplayName(false).getString(), selected.getLevel(), ev, stat, max, price, msg, err));
   }

   private static void select(class_3222 player, int slot) {
      Pokemon p = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot);
      if (p != null) {
         open(player, p.getUuid());
      }
   }

   /** 0 = nothing changed, 1 = changed, -1 = could not afford. net accumulates +refund / -paid. */
   private static int setOne(class_3222 player, Pokemon pokemon, int si, int target, boolean cap, BigInteger[] net) {
      Stat stat = STATS[si];
      EVs evs = pokemon.getEvs();
      int current = evs.getOrDefault(stat);
      int max = maxAllowed(evs, stat);
      target = Math.max(0, Math.min(max, target));
      if (target == current) {
         return 0;
      }
      Economy economy = Cobblecomputils.economy();
      long price = pricePerEv(stat);
      BigInteger cost = BigInteger.ZERO;
      if (target > current && price > 0L) {
         BigInteger balance = economy.balance(player);
         if (cap) {
            long affordable = balance.divide(BigInteger.valueOf(price)).min(BigInteger.valueOf((long)(target - current))).longValue();
            target = current + (int)affordable;
         }
         cost = BigInteger.valueOf(price).multiply(BigInteger.valueOf((long)(target - current)));
         if (target == current || !economy.withdraw(player, cost)) {
            return -1;
         }
      }
      evs.set(stat, target);
      if (evs.getOrDefault(stat) != target) {
         if (cost.signum() > 0) {
            economy.deposit(player, cost);
         }
         return 0;
      }
      if (cost.signum() > 0) {
         net[0] = net[0].subtract(cost);
      } else if (target < current && price > 0L) {
         BigInteger refund = BigInteger.valueOf(price).multiply(BigInteger.valueOf((long)(current - target)))
            .multiply(BigInteger.valueOf((long)EvEditConfig.get().refundPercent())).divide(BigInteger.valueOf(100L));
         if (refund.signum() > 0) {
            economy.deposit(player, refund);
            net[0] = net[0].add(refund);
         }
      }
      if (pokemon.getCurrentHealth() > pokemon.getMaxHealth()) {
         pokemon.setCurrentHealth(pokemon.getMaxHealth());
      }
      return 1;
   }

   private static void set(class_3222 player, int si, int target, boolean cap) {
      UUID id = SESSIONS.get(player.method_5667());
      Pokemon pokemon = id == null ? null : Menus.findInParty(player, id);
      if (pokemon == null || Menus.refuseInBattle(player)) {
         return;
      }
      BigInteger[] net = {BigInteger.ZERO};
      String msg = "";
      boolean err = false;
      if (si == -1) {
         int changed = 0;
         for (int i = 0; i < 6; i++) {
            if (setOne(player, pokemon, i, 0, false, net) == 1) {
               changed++;
            }
         }
         msg = changed == 0 ? "" : "All EVs reset";
      } else if (si >= 0 && si < 6) {
         int r = setOne(player, pokemon, si, target, cap, net);
         if (r == -1) {
            long price = pricePerEv(STATS[si]);
            msg = "Not enough CobbleDollars: need " + money(BigInteger.valueOf(Math.max(1L, price))) + ", you have " + money(Cobblecomputils.economy().balance(player));
            err = true;
         }
      }
      if (!err && net[0].signum() != 0) {
         msg = (msg.isEmpty() ? "" : msg + ".  ") + (net[0].signum() < 0 ? "Paid " + money(net[0].negate()) : "Refunded " + money(net[0]));
      }
      open(player, id, msg, err);
   }
}
