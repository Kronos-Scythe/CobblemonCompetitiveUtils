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
import net.minecraft.class_124;
import net.minecraft.class_1277;
import net.minecraft.class_1713;
import net.minecraft.class_1799;
import net.minecraft.class_1935;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_3222;
import net.minecraft.class_5250;
import net.minecraft.class_9334;
import org.cobbleutils.cobblecomputils.Cobblecomputils;
import org.cobbleutils.cobblecomputils.economy.Economy;
import org.cobbleutils.cobblecomputils.gui.Menus;

public final class EvEditMenus {
   private static final int[] CONTENT_SLOTS = Menus.PARTY_SLOTS;
   private static final int HEADER_SLOT = 4;
   private static final int BACK_SLOT = 22;
   private static final Stat[] STATS = new Stat[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
   private static final class_1935[] STAT_ITEMS = new class_1935[]{
      CobblemonItems.POWER_WEIGHT,
      CobblemonItems.POWER_BRACER,
      CobblemonItems.POWER_BELT,
      CobblemonItems.POWER_LENS,
      CobblemonItems.POWER_BAND,
      CobblemonItems.POWER_ANKLET
   };
   private static final String[] STAT_SHORT = new String[]{"HP", "Atk", "Def", "SpA", "SpD", "Spe"};

   private EvEditMenus() {
   }

   public static void openParty(class_3222 player) {
      if (EvNet.hasClient(player)) {
         EvNet.open(player, null);
         return;
      }
      Menus.openPartyPicker(
         player,
         class_2561.method_43470("EV Editor"),
         "Click to edit EVs",
         pokemon -> List.of(
               line(spreadSummary(pokemon.getEvs()), class_124.field_1075), line("Total EVs: " + pokemon.getEvs().total() + " / 510", class_124.field_1080)
            ),
         pokemon -> openStats(player, pokemon.getUuid())
      );
   }

   private static void openStats(class_3222 player, UUID pokemonId) {
      Pokemon pokemon = Menus.findInParty(player, pokemonId);
      if (pokemon != null && !Menus.refuseInBattle(player)) {
         class_1277 menu = Menus.framed(3);
         drawStats(menu, player, pokemon);
         class_5250 title = class_2561.method_43470("EVs: ").method_10852(pokemon.getDisplayName(false));
         Menus.open(player, menu, 3, title, (slot, button, action) -> {
            if (slot == 22 && action == class_1713.field_7790) {
               openParty(player);
            } else {
               int statIndex = Menus.indexOf(CONTENT_SLOTS, slot);
               if (statIndex >= 0) {
                  Pokemon current = Menus.findInParty(player, pokemonId);
                  if (current != null && !Menus.refuseInBattle(player)) {
                     if (applyClick(player, current, STATS[statIndex], button, action)) {
                        drawStats(menu, player, current);
                     }
                  } else {
                     player.method_7346();
                  }
               }
            }
         });
      }
   }

   private static boolean applyClick(class_3222 player, Pokemon pokemon, Stat stat, int button, class_1713 action) {
      EVs evs = pokemon.getEvs();
      int current = evs.getOrDefault(stat);
      int max = maxAllowed(evs, stat);
      int target;
      if (action == class_1713.field_7790) {
         target = current + (button == 0 ? 1 : -1);
      } else if (action == class_1713.field_7794) {
         target = current + (button == 0 ? 4 : -4);
      } else {
         if (action != class_1713.field_7795) {
            return false;
         }

         target = button == 1 ? max : 0;
      }

      target = Math.max(0, Math.min(max, target));
      if (target == current) {
         return false;
      } else {
         Economy economy = Cobblecomputils.economy();
         long price = pricePerEv(stat);
         BigInteger cost = BigInteger.ZERO;
         if (target > current && price > 0L) {
            BigInteger balance = economy.balance(player);
            if (action == class_1713.field_7795) {
               long affordable = balance.divide(BigInteger.valueOf(price)).min(BigInteger.valueOf((long)(target - current))).longValue();
               target = current + (int)affordable;
            }

            cost = BigInteger.valueOf(price).multiply(BigInteger.valueOf((long)(target - current)));
            if (target == current || !economy.withdraw(player, cost)) {
               BigInteger needed = BigInteger.valueOf(price).max(cost);
               player.method_7353(
                  class_2561.method_43470("Not enough CobbleDollars: need " + money(needed) + ", you have " + money(balance))
                     .method_27692(class_124.field_1061),
                  true
               );
               return false;
            }
         }

         evs.set(stat, target);
         if (evs.getOrDefault(stat) != target) {
            if (cost.signum() > 0) {
               economy.deposit(player, cost);
            }

            return false;
         } else {
            if (cost.signum() > 0) {
               player.method_7353(class_2561.method_43470("Paid " + money(cost)).method_27692(class_124.field_1065), true);
            } else if (target < current && price > 0L) {
               BigInteger refund = BigInteger.valueOf(price)
                  .multiply(BigInteger.valueOf((long)(current - target)))
                  .multiply(BigInteger.valueOf((long)org.cobbleutils.cobblecomputils.evedit.EvEditConfig.get().refundPercent()))
                  .divide(BigInteger.valueOf(100L));
               if (refund.signum() > 0) {
                  economy.deposit(player, refund);
                  player.method_7353(class_2561.method_43470("Refunded " + money(refund)).method_27692(class_124.field_1065), true);
               }
            }

            if (pokemon.getCurrentHealth() > pokemon.getMaxHealth()) {
               pokemon.setCurrentHealth(pokemon.getMaxHealth());
            }

            return true;
         }
      }
   }

   private static long pricePerEv(Stat stat) {
      org.cobbleutils.cobblecomputils.evedit.EvEditConfig config = org.cobbleutils.cobblecomputils.evedit.EvEditConfig.get();
      return config.charge && !Cobblecomputils.economy().isFree() ? config.price(stat) : 0L;
   }

   private static String money(BigInteger amount) {
      return Cobblecomputils.economy().format(amount);
   }

   private static int maxAllowed(EVs evs, Stat stat) {
      int current = evs.getOrDefault(stat);
      int left = 510 - evs.total();
      return Math.min(252, current + left);
   }

   private static void drawStats(class_1277 menu, class_3222 player, Pokemon pokemon) {
      EVs evs = pokemon.getEvs();
      int total = evs.total();
      List<class_2561> headerLore = new ArrayList<>();
      headerLore.add(line("Lv. " + pokemon.getLevel(), class_124.field_1080));
      headerLore.add(line(spreadSummary(evs), class_124.field_1075));
      headerLore.add(line("Total EVs: " + total + " / 510 (" + (510 - total) + " left)", class_124.field_1080));
      if (!Cobblecomputils.economy().isFree() && org.cobbleutils.cobblecomputils.evedit.EvEditConfig.get().charge) {
         headerLore.add(line("Balance: " + money(Cobblecomputils.economy().balance(player)), class_124.field_1065));
      }

      menu.method_5447(4, Menus.pokemonIcon(pokemon, headerLore));

      for (int i = 0; i < STATS.length; i++) {
         Stat stat = STATS[i];
         int value = evs.getOrDefault(stat);
         List<class_2561> lore = new ArrayList<>();
         lore.add(line("EVs: " + value + " / 252", value > 0 ? class_124.field_1060 : class_124.field_1080));
         lore.add(line("Stat: " + pokemon.getStat(stat), class_124.field_1080));
         lore.add(line("Can go up to " + maxAllowed(evs, stat), class_124.field_1063));
         long price = pricePerEv(stat);
         if (price > 0L) {
            lore.add(line("Cost: " + money(BigInteger.valueOf(price)) + " per EV", class_124.field_1065));
            int refund = org.cobbleutils.cobblecomputils.evedit.EvEditConfig.get().refundPercent();
            if (refund > 0) {
               lore.add(line("Lowering refunds " + refund + "%", class_124.field_1065));
            }
         }

         lore.add(class_2561.method_43473());
         lore.add(line("Left / right click: +1 / -1", class_124.field_1054));
         lore.add(line("Shift + left / right click: +4 / -4", class_124.field_1054));
         lore.add(line("Ctrl + Q: max    Q: reset to 0", class_124.field_1054));
         class_1799 stack = named(new class_1799(STAT_ITEMS[i]), stat.getDisplayName().method_27661().method_10862(plain(class_124.field_1065)), lore);
         if (value >= 252) {
            stack.method_57379(class_9334.field_49641, true);
         }

         menu.method_5447(CONTENT_SLOTS[i], stack);
      }

      menu.method_5447(22, Menus.backButton("Back to team"));
   }

   private static class_1799 named(class_1799 stack, class_2561 name, List<class_2561> lore) {
      return Menus.named(stack, name, lore);
   }

   private static class_2561 line(String text, class_124 color) {
      return Menus.line(text, color);
   }

   private static class_2583 plain(class_124 color) {
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
