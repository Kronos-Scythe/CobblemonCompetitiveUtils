package org.cobbleutils.cobblecomputils.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.class_1109;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_3417;
import net.minecraft.class_437;
import org.cobbleutils.cobblecomputils.evedit.EvNet;
import org.cobbleutils.cobblecomputils.movetutor.TutorNet;

/** EV editor: party tabs, six stat bars with +/- buttons, same look as the move tutor. */
@Environment(EnvType.CLIENT)
public final class EvScreen extends class_437 {
   private static final int BG = 0xF0121420;
   private static final int PANEL = 0xFF1B1E2E;
   private static final int BORDER = 0xFF4A5080;
   private static final int ACCENT = 0xFF5B8CFF;
   private static final int TEXT = 0xFFFFFFFF;
   private static final int MUTED = 0xFF9AA0B8;
   private static final int GOLD = 0xFFFFC85A;
   private static final int GREEN = 0xFF6CE08A;
   private static final int RED = 0xFFFF6B6B;
   private static final int ROW_H = 28;
   private static final String[] NAMES = {"HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed"};
   private static final int[] COLORS = {0xFFFF5959, 0xFFF08030, 0xFFF8D030, 0xFF6890F0, 0xFF78C850, 0xFFF85888};

   private EvNet.Open data;
   private final List<int[]> hits = new ArrayList<>();
   private final List<Runnable[]> hitActions = new ArrayList<>();
   private int x0;
   private int y0;
   private int w;
   private int h;
   private int barX;
   private int barW;

   public EvScreen(EvNet.Open data) {
      super(class_2561.method_43470("EV Editor"));
      this.data = data;
   }

   public void update(EvNet.Open next) {
      this.data = next;
   }

   protected void method_25426() {
      this.w = Math.min(520, this.field_22789 - 12);
      this.h = Math.min(270, this.field_22790 - 12);
      this.x0 = (this.field_22789 - this.w) / 2;
      this.y0 = (this.field_22790 - this.h) / 2;
   }

   public void method_25420(class_332 g, int mx, int my, float delta) {
   }

   public boolean method_25421() {
      return false;
   }

   private int rowTop() {
      return this.y0 + 70;
   }

   private void box(class_332 g, int x, int y, int bw, int bh, int fill, int border) {
      g.method_25294(x, y, x + bw, y + bh, fill);
      g.method_49601(x, y, bw, bh, border);
   }

   private void hit(int x, int y, int bw, int bh, Runnable left) {
      this.hits.add(new int[]{x, y, bw, bh});
      this.hitActions.add(new Runnable[]{left});
   }

   private int button(class_332 g, int x, int y, int bw, String label, boolean enabled, int mx, int my, Runnable action) {
      boolean over = enabled && mx >= x && mx < x + bw && my >= y && my < y + 16;
      this.box(g, x, y, bw, 16, !enabled ? 0xFF14162A : (over ? 0xFF2E4A8A : PANEL), over ? ACCENT : BORDER);
      g.method_51433(this.field_22793, label, x + (bw - this.field_22793.method_1727(label)) / 2, y + 4, enabled ? TEXT : 0xFF555A70, false);
      if (enabled) {
         this.hit(x, y, bw, 16, action);
      }
      return bw + 3;
   }

   private void send(net.minecraft.class_8710 payload) {
      if (ClientPlayNetworking.canSend(payload.method_56479())) {
         ClientPlayNetworking.send(payload);
      }
   }

   private void set(int stat, int target, boolean cap) {
      this.send(new EvNet.SetEv(stat, target, cap));
   }

   private static void click() {
      class_310.method_1551().method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1.0F));
   }

   public void method_25394(class_332 g, int mx, int my, float delta) {
      g.method_25294(0, 0, this.field_22789, this.field_22790, 0x90000000);
      this.hits.clear();
      this.hitActions.clear();
      EvNet.Open d = this.data;
      this.box(g, this.x0, this.y0, this.w, this.h, BG, BORDER);
      g.method_25294(this.x0 + 1, this.y0 + 1, this.x0 + this.w - 1, this.y0 + 20, 0xFF23274A);
      g.method_51433(this.field_22793, "EV Editor", this.x0 + 8, this.y0 + 6, TEXT, true);
      if (!d.balance().isEmpty()) {
         g.method_51433(this.field_22793, d.balance(), this.x0 + this.w - 8 - this.field_22793.method_1727(d.balance()), this.y0 + 6, GOLD, true);
      }
      // party tabs
      int n = Math.max(1, d.party().size());
      int tabW = (this.w - 16 - (n - 1) * 3) / n;
      for (int i = 0; i < d.party().size(); i++) {
         TutorNet.Mon mon = d.party().get(i);
         int tx = this.x0 + 8 + i * (tabW + 3);
         boolean sel = i == d.selected();
         boolean over = mx >= tx && mx < tx + tabW && my >= this.y0 + 24 && my < this.y0 + 40;
         this.box(g, tx, this.y0 + 24, tabW, 16, sel ? 0xFF2E4A8A : (over ? 0xFF2A2F48 : PANEL), sel ? ACCENT : BORDER);
         String label = mon.name() + " Lv" + mon.level();
         while (label.length() > 3 && this.field_22793.method_1727(label) > tabW - 6) {
            label = label.substring(0, label.length() - 2) + ".";
         }
         g.method_51433(this.field_22793, label, tx + 4, this.y0 + 28, sel ? TEXT : MUTED, false);
         int slot = mon.slot();
         this.hit(tx, this.y0 + 24, tabW, 16, () -> this.send(new EvNet.Select(slot)));
      }
      // total strip
      int total = 0;
      for (int v : d.ev()) {
         total += v;
      }
      int lx = this.x0 + 8;
      int lw = this.w - 16;
      g.method_51433(this.field_22793, d.name() + "  Lv " + d.level(), lx, this.y0 + 46, TEXT, true);
      String tot = "Total EVs " + total + " / 510   (" + (510 - total) + " left)";
      g.method_51433(this.field_22793, tot, lx + this.field_22793.method_1727(d.name() + "  Lv " + d.level()) + 14, this.y0 + 46, total >= 510 ? GOLD : MUTED, false);
      this.button(g, lx + lw - 70, this.y0 + 43, 70, "Reset all", total > 0, mx, my, () -> this.set(-1, 0, false));
      int tbW = lw - 78;
      g.method_25294(lx, this.y0 + 61, lx + tbW, this.y0 + 65, 0xFF0F111C);
      g.method_25294(lx, this.y0 + 61, lx + (int)(tbW * (total / 510.0)), this.y0 + 65, total >= 510 ? GOLD : ACCENT);
      g.method_49601(lx, this.y0 + 60, tbW, 6, BORDER);
      // rows
      this.barX = lx + 84;
      this.barW = 188;
      int hovered = -1;
      for (int i = 0; i < 6; i++) {
         int ry = this.rowTop() + i * ROW_H;
         boolean rowOver = mx >= lx && mx < lx + lw && my >= ry && my < ry + ROW_H - 2;
         this.box(g, lx, ry, lw, ROW_H - 2, rowOver ? 0xFF1E2238 : 0xFF161827, rowOver ? ACCENT : 0xFF2A2E48);
         if (rowOver) {
            hovered = i;
         }
         g.method_25294(lx + 1, ry + 1, lx + 4, ry + ROW_H - 3, COLORS[i]);
         g.method_51433(this.field_22793, NAMES[i], lx + 9, ry + 4, TEXT, true);
         String pr = d.price()[i].isEmpty() ? "free" : d.price()[i] + " / EV";
         g.method_51433(this.field_22793, pr, lx + 9, ry + 15, d.price()[i].isEmpty() ? GREEN : GOLD, false);
         // bar
         int by = ry + 7;
         g.method_25294(this.barX, by, this.barX + this.barW, by + 12, 0xFF0F111C);
         int maxPx = (int)(this.barW * (d.max()[i] / 252.0));
         g.method_25294(this.barX + maxPx, by, this.barX + this.barW, by + 12, 0xFF2A1B1B);
         int fill = (int)(this.barW * (d.ev()[i] / 252.0));
         g.method_25294(this.barX, by, this.barX + fill, by + 12, COLORS[i]);
         g.method_49601(this.barX, by, this.barW, 12, d.ev()[i] >= 252 ? GOLD : BORDER);
         String ev = d.ev()[i] + " / 252";
         g.method_51433(this.field_22793, ev, this.barX + (this.barW - this.field_22793.method_1727(ev)) / 2, by + 2, TEXT, true);
         final int si = i;
         final int bxx = this.barX;
         final int bww = this.barW;
         this.hits.add(new int[]{this.barX, by, this.barW, 12});
         this.hitActions.add(new Runnable[]{() -> {
            double frac = (this.lastMouseX - bxx) / (double)bww;
            this.set(si, (int)Math.round(Math.max(0, Math.min(1, frac)) * 252), true);
         }});
         // stat value
         String st = "Stat " + d.stat()[i];
         g.method_51433(this.field_22793, st, this.barX + this.barW + 8, ry + 9, MUTED, false);
         // buttons
         int bx = lx + 332;
         int by2 = ry + 5;
         int ev0 = d.ev()[i];
         int mxEv = d.max()[i];
         bx += this.button(g, bx, by2, 22, "-4", ev0 > 0, mx, my, () -> this.set(si, ev0 - 4, false));
         bx += this.button(g, bx, by2, 22, "-1", ev0 > 0, mx, my, () -> this.set(si, ev0 - 1, false));
         bx += this.button(g, bx, by2, 22, "+1", ev0 < mxEv, mx, my, () -> this.set(si, ev0 + 1, false));
         bx += this.button(g, bx, by2, 22, "+4", ev0 < mxEv, mx, my, () -> this.set(si, ev0 + 4, true));
         bx += this.button(g, bx, by2, 28, "Max", ev0 < mxEv, mx, my, () -> this.set(si, mxEv, true));
         this.button(g, bx, by2, 16, "0", ev0 > 0, mx, my, () -> this.set(si, 0, false));
      }
      // footer / message
      if (!d.msg().isEmpty()) {
         g.method_51433(this.field_22793, d.msg(), lx, this.y0 + this.h - 13, d.err() ? RED : GREEN, false);
      } else {
         String help = "Click a bar to set EVs   |   Wheel over a row: +/-1 (Shift: +/-4)   |   Esc to close";
         if (d.refund() > 0) {
            help = "Lowering EVs refunds " + d.refund() + "%   |   " + help;
         }
         g.method_51433(this.field_22793, help, lx, this.y0 + this.h - 13, MUTED, false);
      }
      if (hovered >= 0) {
         this.tooltip(g, hovered, mx, my);
      }
      super.method_25394(g, -1, -1, delta);
   }

   private int lastMouseX;

   private void tooltip(class_332 g, int i, int mx, int my) {
      EvNet.Open d = this.data;
      List<String> lines = new ArrayList<>();
      lines.add(NAMES[i] + ": " + d.ev()[i] + " / 252 EVs");
      lines.add("Current stat: " + d.stat()[i] + "  (+1 per 4 EVs at Lv 100)");
      lines.add("Can go up to " + d.max()[i]);
      lines.add(d.price()[i].isEmpty() ? "No cost" : "Cost " + d.price()[i] + " per EV");
      int tw = 0;
      for (String s : lines) {
         tw = Math.max(tw, this.field_22793.method_1727(s));
      }
      int th = lines.size() * 11 + 10;
      int x = mx + 12;
      int y = my + 10;
      if (x + tw + 14 > this.field_22789) {
         x = mx - tw - 18;
      }
      if (y + th > this.field_22790) {
         y = this.field_22790 - th - 4;
      }
      this.box(g, x, y, tw + 12, th, 0xF0100818, 0xFF6A4CC0);
      for (int k = 0; k < lines.size(); k++) {
         g.method_51433(this.field_22793, lines.get(k), x + 6, y + 5 + k * 11, k == 0 ? TEXT : (k == 3 && !d.price()[i].isEmpty() ? GOLD : MUTED), k == 0);
      }
   }

   public boolean method_25402(double mx, double my, int button) {
      this.lastMouseX = (int)mx;
      if (button == 0 || button == 1) {
         for (int i = this.hits.size() - 1; i >= 0; i--) {
            int[] r = this.hits.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
               click();
               this.hitActions.get(i)[0].run();
               return true;
            }
         }
      }
      return super.method_25402(mx, my, button);
   }

   public void method_16014(double mx, double my) {
      this.lastMouseX = (int)mx;
   }

   public boolean method_25401(double mx, double my, double horizontal, double vertical) {
      int lx = this.x0 + 8;
      int lw = this.w - 16;
      if (mx >= lx && mx < lx + lw && my >= this.rowTop() && my < this.rowTop() + 6 * ROW_H) {
         int i = (int)((my - this.rowTop()) / ROW_H);
         if (i >= 0 && i < 6) {
            boolean shift = class_437.method_25442();
            int step = (shift ? 4 : 1) * (vertical > 0 ? 1 : -1);
            this.set(i, this.data.ev()[i] + step, step > 0);
         }
      }
      return true;
   }
}
