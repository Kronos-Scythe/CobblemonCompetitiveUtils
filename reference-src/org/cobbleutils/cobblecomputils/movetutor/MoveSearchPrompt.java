package org.cobbleutils.cobblecomputils.movetutor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import net.minecraft.class_124;
import net.minecraft.class_1661;
import net.minecraft.class_1657;
import net.minecraft.class_1706;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3914;
import net.minecraft.class_7923;
import org.cobbleutils.cobblecomputils.gui.Menus;

/** A real in-GUI text box (the anvil rename field) used as a search prompt. Nothing can be taken out of it. */
public final class MoveSearchPrompt extends class_1706 {
   private final ToIntFunction<String> counter;
   private final Consumer<String> onSubmit;
   private final Runnable onCancel;
   private String text;
   private boolean done;

   public MoveSearchPrompt(int syncId, class_1661 inventory, String initial, ToIntFunction<String> counter, Consumer<String> onSubmit, Runnable onCancel) {
      super(syncId, inventory, class_3914.field_17304);
      this.counter = counter;
      this.onSubmit = onSubmit;
      this.onCancel = onCancel;
      this.text = initial == null ? "" : initial;
      this.method_7611(0).method_7673(Menus.named(new class_1799(item("paper")), class_2561.method_43470(this.text.isEmpty() ? " " : this.text), List.of()));
      List<class_2561> cancel = new ArrayList<>();
      cancel.add(Menus.line("Go back to the move list", class_124.field_1080));
      this.method_7611(1).method_7673(Menus.named(new class_1799(item("barrier")), Menus.line("Cancel", class_124.field_1061), cancel));
      this.method_24928();
   }

   private static class_1792 item(String id) {
      class_1792 found = (class_1792)class_7923.field_41178.method_10223(class_2960.method_60655("minecraft", id));
      return found == class_1802.field_8162 ? class_1802.field_8407 : found;
   }

   @Override
   public void method_24928() {
      String query = this.text == null ? "" : this.text.trim();
      int count;
      try {
         count = this.counter.applyAsInt(query);
      } catch (RuntimeException e) {
         count = 0;
      }
      List<class_2561> lore = new ArrayList<>();
      lore.add(Menus.line(count + (count == 1 ? " move matches" : " moves match"), count > 0 ? class_124.field_1060 : class_124.field_1061));
      lore.add(class_2561.method_43473());
      lore.add(Menus.line(query.isEmpty() ? "Click to show every move" : "Click to show these moves", class_124.field_1054));
      class_1799 out = Menus.named(new class_1799(item("compass")), Menus.line(query.isEmpty() ? "Show all moves" : "Search: " + query, class_124.field_1065), lore);
      this.method_7611(2).method_7673(out);
   }

   @Override
   public boolean method_7625(String name) {
      this.text = name == null ? "" : name;
      this.method_24928();
      this.method_7623();
      return true;
   }

   @Override
   public boolean method_7597(class_1657 player) {
      return !this.done;
   }

   @Override
   public void method_7593(int slotIndex, int button, class_1713 action, class_1657 player) {
      if (!this.done && (action == class_1713.field_7790) && (slotIndex == 2 || slotIndex == 1)) {
         this.done = true;
         if (slotIndex == 2) {
            this.onSubmit.accept(this.text == null ? "" : this.text.trim());
         } else {
            this.onCancel.run();
         }
         return;
      }
      if (player.field_7512 == this) {
         this.method_34252();
      }
   }

   @Override
   public class_1799 method_7601(class_1657 player, int slot) {
      return class_1799.field_8037;
   }

   @Override
   public void method_7595(class_1657 player) {
      this.method_7611(0).method_7673(class_1799.field_8037);
      this.method_7611(1).method_7673(class_1799.field_8037);
      this.method_7611(2).method_7673(class_1799.field_8037);
      super.method_7595(player);
   }
}
