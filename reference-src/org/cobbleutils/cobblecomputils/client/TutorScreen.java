package org.cobbleutils.cobblecomputils.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.class_1074;
import net.minecraft.class_1109;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_3417;
import net.minecraft.class_437;
import net.minecraft.class_5481;
import org.cobbleutils.cobblecomputils.movetutor.TutorNet;
import org.cobbleutils.cobblecomputils.movetutor.TutorNet.MoveInfo;

/** Move tutor: party tabs, an EMI-style live search bar, filter chips and one scrolling list. */
@Environment(EnvType.CLIENT)
public final class TutorScreen extends class_437 {
   private static final int BG = 0xF0121420;
   private static final int PANEL = 0xFF1B1E2E;
   private static final int BORDER = 0xFF4A5080;
   private static final int ACCENT = 0xFF5B8CFF;
   private static final int TEXT = 0xFFFFFFFF;
   private static final int MUTED = 0xFF9AA0B8;
   private static final int GOLD = 0xFFFFC85A;
   private static final int GREEN = 0xFF6CE08A;
   private static final int RED = 0xFFFF6B6B;
   private static final int HOVER = 0x335B8CFF;
   private static final int ROW_H = 26;
   private static final String[] TYPES = {"Normal", "Fire", "Water", "Grass", "Electric", "Ice", "Fighting", "Poison", "Ground", "Flying", "Psychic", "Bug", "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy"};
   private static final String[] CATS = {"Physical", "Special", "Status"};
   private static final String[] SORTS = {"Default", "A-Z", "Power", "Type"};

   private TutorNet.Open data;
   private class_342 search;
   private String source;
   private String type;
   private String category;
   private int sort;
   private double scroll;
   private List<MoveInfo> rows = new ArrayList<>();
   private MoveInfo pending;
   private final List<int[]> hits = new ArrayList<>();
   private final List<Runnable[]> hitActions = new ArrayList<>();
   private int x0;
   private int y0;
   private int w;
   private int h;
   private String lastQuery = "";

   public TutorScreen(TutorNet.Open data) {
      super(class_2561.method_43470("Move Tutor"));
      this.data = data;
   }

   public void update(TutorNet.Open next) {
      this.data = next;
      this.pending = null;
      this.refilter();
   }

   protected void method_25426() {
      this.w = Math.min(520, this.field_22789 - 12);
      this.h = Math.min(300, this.field_22790 - 12);
      this.x0 = (this.field_22789 - this.w) / 2;
      this.y0 = (this.field_22790 - this.h) / 2;
      String keep = this.search == null ? "" : this.search.method_1882();
      this.search = new class_342(this.field_22793, this.x0 + 8, this.y0 + 44, this.w - 16, 16, class_2561.method_43470("Search"));
      this.search.method_1880(48);
      this.search.method_47404(class_2561.method_43470("Search moves, types, categories...").method_27692(net.minecraft.class_124.field_1063));
      this.search.method_1852(keep);
      this.search.method_1863(s -> this.refilter());
      this.method_37063(this.search);
      this.method_25395(this.search);
      this.search.method_25365(true);
      this.refilter();
   }

   public void method_25420(class_332 g, int mx, int my, float delta) {
   }

   public boolean method_25421() {
      return false;
   }

   // ---- text helpers ----
   private static String tr(String key, String fallback) {
      String s = class_1074.method_4662(key);
      return s.equals(key) ? fallback : s;
   }

   private static String nice(String id) {
      StringBuilder sb = new StringBuilder();
      boolean up = true;
      for (char c : id.toCharArray()) {
         if (c == '_' || c == '-') {
            sb.append(' ');
            up = true;
         } else {
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
         }
      }
      return sb.toString();
   }

   private static String name(MoveInfo m) {
      return tr("cobblemon.move." + m.id(), nice(m.id()));
   }

   private static String desc(MoveInfo m) {
      return m.desc().startsWith("cobblemon.") ? tr(m.desc(), "") : m.desc();
   }

   private static String compact(String s) {
      return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
   }

   private static int typeColor(String t) {
      return switch (t.toLowerCase(Locale.ROOT)) {
         case "normal" -> 0xFFA8A878;
         case "fire" -> 0xFFF08030;
         case "water" -> 0xFF6890F0;
         case "grass" -> 0xFF78C850;
         case "electric" -> 0xFFE0B820;
         case "ice" -> 0xFF98D8D8;
         case "fighting" -> 0xFFC03028;
         case "poison" -> 0xFFA040A0;
         case "ground" -> 0xFFE0C068;
         case "flying" -> 0xFFA890F0;
         case "psychic" -> 0xFFF85888;
         case "bug" -> 0xFFA8B820;
         case "rock" -> 0xFFB8A038;
         case "ghost" -> 0xFF705898;
         case "dragon" -> 0xFF7038F8;
         case "dark" -> 0xFF705848;
         case "steel" -> 0xFFB8B8D0;
         case "fairy" -> 0xFFEE99AC;
         default -> 0xFF888888;
      };
   }

   // ---- filtering ----
   private boolean matches(MoveInfo m, String query) {
      if (this.source != null && !java.util.Arrays.asList(m.sources().split(",")).contains(this.source)) {
         return false;
      }
      if (this.type != null && !m.type().equalsIgnoreCase(this.type)) {
         return false;
      }
      if (this.category != null && !m.category().equalsIgnoreCase(this.category)) {
         return false;
      }
      if (query.isBlank()) {
         return true;
      }
      String hay = compact(name(m) + " " + m.id() + " " + m.type() + " " + m.category() + " " + m.sources());
      for (String token : query.trim().split("\\s+")) {
         if (!hay.contains(compact(token))) {
            return false;
         }
      }
      return true;
   }

   private void refilter() {
      String q = this.search == null ? "" : this.search.method_1882();
      List<MoveInfo> out = new ArrayList<>();
      for (MoveInfo m : this.data.moves()) {
         if (this.matches(m, q)) {
            out.add(m);
         }
      }
      switch (this.sort) {
         case 1 -> out.sort(Comparator.comparing(TutorScreen::name));
         case 2 -> out.sort(Comparator.<MoveInfo>comparingInt(m -> -m.power()).thenComparing(TutorScreen::name));
         case 3 -> out.sort(Comparator.comparing(MoveInfo::type).thenComparing(TutorScreen::name));
         default -> {
         }
      }
      this.rows = out;
      if (!q.equals(this.lastQuery)) {
         this.scroll = 0;
         this.lastQuery = q;
      }
      this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
   }

   private int listTop() {
      return this.y0 + 82;
   }

   private int listBottom() {
      return this.y0 + this.h - 20;
   }

   private double maxScroll() {
      return Math.max(0, this.rows.size() * ROW_H - (this.listBottom() - this.listTop()));
   }

   // ---- rendering ----
   private void box(class_332 g, int x, int y, int bw, int bh, int fill, int border) {
      g.method_25294(x, y, x + bw, y + bh, fill);
      g.method_49601(x, y, bw, bh, border);
   }

   private void hit(int x, int y, int bw, int bh, Runnable left, Runnable right) {
      this.hits.add(new int[]{x, y, bw, bh});
      this.hitActions.add(new Runnable[]{left, right});
   }

   private int chip(class_332 g, int x, int y, String label, boolean active, int mx, int my, Runnable left, Runnable right) {
      int cw = this.field_22793.method_1727(label) + 12;
      boolean over = mx >= x && mx < x + cw && my >= y && my < y + 14;
      this.box(g, x, y, cw, 14, active ? 0xFF2E4A8A : (over ? 0xFF2A2F48 : PANEL), active ? ACCENT : BORDER);
      g.method_51433(this.field_22793, label, x + 6, y + 3, active ? TEXT : MUTED, false);
      this.hit(x, y, cw, 14, left, right);
      return cw + 4;
   }

   private void pill(class_332 g, int x, int y, int pw, String t) {
      g.method_25294(x, y, x + pw, y + 12, typeColor(t));
      String label = t.length() > 8 ? t.substring(0, 8) : t;
      g.method_51433(this.field_22793, label.toUpperCase(Locale.ROOT), x + (pw - this.field_22793.method_1727(label.toUpperCase(Locale.ROOT))) / 2, y + 2, 0xFFFFFFFF, true);
   }

   private static String stats(MoveInfo m) {
      return m.category() + "  Pow " + (m.power() > 0 ? m.power() : "-") + "  Acc " + (m.accuracy() > 0 ? m.accuracy() + "%" : "-") + "  PP " + m.pp();
   }

   public void method_25394(class_332 g, int mx, int my, float delta) {
      g.method_25294(0, 0, this.field_22789, this.field_22790, 0x90000000);
      this.hits.clear();
      this.hitActions.clear();
      boolean modal = this.pending != null;
      int mouseX = modal ? -1 : mx;
      int mouseY = modal ? -1 : my;
      // frame
      this.box(g, this.x0, this.y0, this.w, this.h, BG, BORDER);
      g.method_25294(this.x0 + 1, this.y0 + 1, this.x0 + this.w - 1, this.y0 + 20, 0xFF23274A);
      g.method_51433(this.field_22793, "Move Tutor", this.x0 + 8, this.y0 + 6, TEXT, true);
      if (!this.data.balance().isEmpty()) {
         String bal = this.data.balance();
         g.method_51433(this.field_22793, bal, this.x0 + this.w - 8 - this.field_22793.method_1727(bal), this.y0 + 6, GOLD, true);
      }
      // party tabs
      int n = Math.max(1, this.data.party().size());
      int tabW = (this.w - 16 - (n - 1) * 3) / n;
      for (int i = 0; i < this.data.party().size(); i++) {
         TutorNet.Mon mon = this.data.party().get(i);
         int tx = this.x0 + 8 + i * (tabW + 3);
         boolean sel = i == this.data.selected();
         boolean over = mouseX >= tx && mouseX < tx + tabW && mouseY >= this.y0 + 24 && mouseY < this.y0 + 40;
         this.box(g, tx, this.y0 + 24, tabW, 16, sel ? 0xFF2E4A8A : (over ? 0xFF2A2F48 : PANEL), sel ? ACCENT : BORDER);
         MonIcon.draw(g, mon.sid(), mon.aspects(), tx + 2, this.y0 + 24, 16, 35.0F, mouseX, mouseY, delta);
         String label = mon.name() + " Lv" + mon.level();
         while (label.length() > 3 && this.field_22793.method_1727(label) > tabW - 24) {
            label = label.substring(0, label.length() - 2) + ".";
         }
         g.method_51433(this.field_22793, label, tx + 20, this.y0 + 28, sel ? TEXT : MUTED, false);
         int slot = mon.slot();
         this.hit(tx, this.y0 + 24, tabW, 16, () -> this.send(new TutorNet.Select(slot)), null);
      }
      // search bar decoration (count + clear)
      String count = this.rows.size() + " / " + this.data.moves().size();
      g.method_51433(this.field_22793, count, this.x0 + this.w - 12 - this.field_22793.method_1727(count), this.y0 + 48, MUTED, false);
      // chips
      int cx = this.x0 + 8;
      int cy = this.y0 + 64;
      cx += this.chip(g, cx, cy, "All", this.source == null, mouseX, mouseY, () -> this.source = null, null);
      Set<String> labels = new LinkedHashSet<>();
      for (MoveInfo m : this.data.moves()) {
         for (String s : m.sources().split(",")) {
            if (!s.isEmpty()) {
               labels.add(s);
            }
         }
      }
      for (String s : labels) {
         cx += this.chip(g, cx, cy, s, s.equals(this.source), mouseX, mouseY, () -> this.source = s.equals(this.source) ? null : s, null);
      }
      cx += 6;
      cx += this.chip(g, cx, cy, "Type: " + (this.type == null ? "Any" : this.type), this.type != null, mouseX, mouseY, () -> this.type = cycle(TYPES, this.type, false), () -> this.type = cycle(TYPES, this.type, true));
      cx += this.chip(g, cx, cy, "Cat: " + (this.category == null ? "Any" : this.category), this.category != null, mouseX, mouseY, () -> this.category = cycle(CATS, this.category, false), () -> this.category = cycle(CATS, this.category, true));
      this.chip(g, cx, cy, "Sort: " + SORTS[this.sort], this.sort != 0, mouseX, mouseY, () -> this.sort = (this.sort + 1) % SORTS.length, () -> this.sort = (this.sort + SORTS.length - 1) % SORTS.length);
      // list
      int top = this.listTop();
      int bottom = this.listBottom();
      int lx = this.x0 + 8;
      int lw = this.w - 16;
      g.method_25294(lx, top, lx + lw, bottom, 0xFF0F111C);
      g.method_49601(lx, top, lw, bottom - top, BORDER);
      this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
      g.method_44379(lx + 1, top + 1, lx + lw - 1, bottom - 1);
      MoveInfo hovered = null;
      int first = (int)(this.scroll / ROW_H);
      for (int i = first; i < this.rows.size(); i++) {
         int ry = top + 1 + i * ROW_H - (int)this.scroll;
         if (ry > bottom) {
            break;
         }
         MoveInfo m = this.rows.get(i);
         boolean over = mouseX >= lx && mouseX < lx + lw - 6 && mouseY >= Math.max(ry, top) && mouseY < Math.min(ry + ROW_H, bottom) && mouseY >= top && mouseY < bottom;
         if (over) {
            hovered = m;
            g.method_25294(lx + 1, ry, lx + lw - 1, ry + ROW_H, HOVER);
         } else if (i % 2 == 1) {
            g.method_25294(lx + 1, ry, lx + lw - 1, ry + ROW_H, 0x14FFFFFF);
         }
         this.pill(g, lx + 6, ry + 7, 50, m.type());
         g.method_51433(this.field_22793, name(m), lx + 62, ry + 4, m.known() ? MUTED : TEXT, true);
         String sub = stats(m);
         g.method_51433(this.field_22793, sub, lx + 62, ry + 15, MUTED, false);
         String right;
         int rc;
         if (m.known()) {
            right = "Known";
            rc = GREEN;
         } else if (m.price() > 0) {
            right = String.format("%,d", m.price());
            rc = GOLD;
         } else {
            right = "Free";
            rc = GREEN;
         }
         g.method_51433(this.field_22793, right, lx + lw - 12 - this.field_22793.method_1727(right), ry + 4, rc, true);
         String src = m.sources().replace(",", " / ");
         if (m.level() >= 0 && m.sources().contains("Level-up")) {
            src = src.replace("Level-up", "Lv " + m.level());
         }
         g.method_51433(this.field_22793, src, lx + lw - 12 - this.field_22793.method_1727(src), ry + 15, MUTED, false);
      }
      g.method_44380();
      if (this.rows.isEmpty()) {
         String none = "No moves match your search";
         g.method_51433(this.field_22793, none, lx + (lw - this.field_22793.method_1727(none)) / 2, top + 30, MUTED, false);
      }
      // scrollbar
      double max = this.maxScroll();
      if (max > 0) {
         int trackH = bottom - top - 2;
         int thumbH = Math.max(14, (int)(trackH * (trackH / (double)(trackH + max))));
         int thumbY = top + 1 + (int)((trackH - thumbH) * (this.scroll / max));
         g.method_25294(lx + lw - 5, top + 1, lx + lw - 1, bottom - 1, 0xFF1B1E2E);
         g.method_25294(lx + lw - 5, thumbY, lx + lw - 1, thumbY + thumbH, ACCENT);
      }
      g.method_51433(this.field_22793, "Click a move to learn it   |   Wheel to scroll   |   Right-click the search bar to clear   |   Esc to close", this.x0 + 8, this.y0 + this.h - 13, MUTED, false);
      // widgets (search field)
      if (modal) {
         this.search.method_25365(false);
      }
      super.method_25394(g, mouseX, mouseY, delta);
      // tooltip
      if (hovered != null && !modal) {
         this.tooltip(g, hovered, mx, my);
      }
      if (modal) {
         this.overlay(g, mx, my);
      }
   }

   private void tooltip(class_332 g, MoveInfo m, int mx, int my) {
      List<class_5481> lines = new ArrayList<>();
      String d = desc(m);
      int tw = 210;
      if (!d.isEmpty()) {
         lines.addAll(this.field_22793.method_1728(class_2561.method_43470(d), tw));
      }
      int th = 14 + 12 + lines.size() * 10 + 12 + 12;
      int x = mx + 12;
      int y = my + 8;
      if (x + tw + 12 > this.field_22789) {
         x = mx - tw - 16;
      }
      if (y + th > this.field_22790) {
         y = this.field_22790 - th - 4;
      }
      this.box(g, x, y, tw + 12, th, 0xF0100818, 0xFF6A4CC0);
      g.method_51433(this.field_22793, name(m), x + 6, y + 5, TEXT, true);
      g.method_51433(this.field_22793, m.type() + "  " + stats(m), x + 6, y + 17, MUTED, false);
      int ly = y + 29;
      for (class_5481 line : lines) {
         g.method_51430(this.field_22793, line, x + 6, ly, 0xFFDDDDDD, false);
         ly += 10;
      }
      ly += 2;
      String src = m.sources().replace(",", ", ");
      g.method_51433(this.field_22793, "Learned by: " + src, x + 6, ly, 0xFF7FC8FF, false);
      String foot = m.known() ? "Already known" : (m.price() > 0 ? "Cost: " + String.format("%,d", m.price()) : "Free") + "  -  click to learn";
      g.method_51433(this.field_22793, foot, x + 6, ly + 11, m.known() ? GREEN : GOLD, false);
   }

   private int ox;
   private int oy;
   private int ow;

   private void overlay(class_332 g, int mx, int my) {
      g.method_25294(0, 0, this.field_22789, this.field_22790, 0xB0000000);
      this.ow = 240;
      int oh = 44 + this.data.known().size() * 24 + 26;
      this.ox = (this.field_22789 - this.ow) / 2;
      this.oy = (this.field_22790 - oh) / 2;
      this.box(g, this.ox, this.oy, this.ow, oh, 0xFF14172A, ACCENT);
      g.method_51433(this.field_22793, "Learn " + name(this.pending) + "?", this.ox + 8, this.oy + 7, TEXT, true);
      g.method_51433(this.field_22793, "Pick a move to forget:", this.ox + 8, this.oy + 22, MUTED, false);
      for (int i = 0; i < this.data.known().size(); i++) {
         MoveInfo k = this.data.known().get(i);
         int ry = this.oy + 36 + i * 24;
         boolean over = mx >= this.ox + 6 && mx < this.ox + this.ow - 6 && my >= ry && my < ry + 22;
         this.box(g, this.ox + 6, ry, this.ow - 12, 22, over ? 0xFF2E3A66 : PANEL, over ? ACCENT : BORDER);
         this.pill(g, this.ox + 12, ry + 5, 50, k.type());
         g.method_51433(this.field_22793, name(k), this.ox + 68, ry + 7, TEXT, true);
      }
      int cy = this.oy + 36 + this.data.known().size() * 24 + 2;
      boolean over = mx >= this.ox + 6 && mx < this.ox + this.ow - 6 && my >= cy && my < cy + 16;
      this.box(g, this.ox + 6, cy, this.ow - 12, 16, over ? 0xFF4A2A2A : PANEL, over ? RED : BORDER);
      String c = "Cancel";
      g.method_51433(this.field_22793, c, this.ox + (this.ow - this.field_22793.method_1727(c)) / 2, cy + 4, RED, false);
   }

   private static String cycle(String[] values, String current, boolean back) {
      int idx = -1;
      for (int i = 0; i < values.length; i++) {
         if (values[i].equals(current)) {
            idx = i;
         }
      }
      int next = Math.floorMod(idx + 1 + (back ? -1 : 1), values.length + 1) - 1;
      return next < 0 ? null : values[next];
   }

   // ---- input ----
   private void send(net.minecraft.class_8710 payload) {
      if (ClientPlayNetworking.canSend(payload.method_56479())) {
         ClientPlayNetworking.send(payload);
      }
   }

   private static void click() {
      class_310.method_1551().method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1.0F));
   }

   public boolean method_25402(double mx, double my, int button) {
      if (this.pending != null) {
         if (button == 0) {
            for (int i = 0; i < this.data.known().size(); i++) {
               int ry = this.oy + 36 + i * 24;
               if (mx >= this.ox + 6 && mx < this.ox + this.ow - 6 && my >= ry && my < ry + 22) {
                  click();
                  this.send(new TutorNet.Learn(this.pending.id(), i));
                  this.pending = null;
                  return true;
               }
            }
            int cy = this.oy + 36 + this.data.known().size() * 24 + 2;
            if (mx >= this.ox + 6 && mx < this.ox + this.ow - 6 && my >= cy && my < cy + 16) {
               click();
            }
         }
         this.pending = null;
         return true;
      }
      if (button == 1 && this.search != null && mx >= this.search.method_46426() && mx < this.search.method_46426() + this.search.method_25368() && my >= this.search.method_46427() && my < this.search.method_46427() + 16) {
         this.search.method_1852("");
         this.search.method_25365(true);
         return true;
      }
      if (button == 0 || button == 1) {
         for (int i = 0; i < this.hits.size(); i++) {
            int[] r = this.hits.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
               Runnable action = button == 1 && this.hitActions.get(i)[1] != null ? this.hitActions.get(i)[1] : this.hitActions.get(i)[0];
               if (action != null) {
                  click();
                  action.run();
                  this.scroll = 0;
                  this.refilter();
               }
               return true;
            }
         }
      }
      int lx = this.x0 + 8;
      int lw = this.w - 16;
      if (button == 0 && mx >= lx && mx < lx + lw - 6 && my >= this.listTop() && my < this.listBottom()) {
         int index = (int)((my - this.listTop() - 1 + this.scroll) / ROW_H);
         if (index >= 0 && index < this.rows.size()) {
            MoveInfo m = this.rows.get(index);
            if (!m.known()) {
               click();
               if (this.data.known().size() >= 4) {
                  this.pending = m;
               } else {
                  this.send(new TutorNet.Learn(m.id(), -1));
               }
            }
            return true;
         }
      }
      return super.method_25402(mx, my, button);
   }

   public boolean method_25401(double mx, double my, double horizontal, double vertical) {
      if (this.pending == null) {
         this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - vertical * ROW_H * 1.5));
      }
      return true;
   }

   public boolean method_25404(int key, int scan, int mods) {
      if (key == 256 && this.pending != null) {
         this.pending = null;
         return true;
      }
      return super.method_25404(key, scan, mods);
   }
}
