package org.cobbleutils.cobblecomputils.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.class_1109;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_3417;
import net.minecraft.class_437;
import org.cobbleutils.cobblecomputils.capture.MartNet;

/** Poke Mart: every merchant item in one scrolling list with a live search bar, category chips and a quantity picker. */
@Environment(EnvType.CLIENT)
public final class MartScreen extends class_437 {
    private static final int BG = 0xF0121420;
    private static final int PANEL = 0xFF1B1E2E;
    private static final int BORDER = 0xFF4A5080;
    private static final int ACCENT = 0xFF5B8CFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0B8;
    private static final int GOLD = 0xFFFFC85A;
    private static final int GREEN = 0xFF6CE08A;
    private static final int RED = 0xFFFF6B6B;
    private static final int ROW_H = 24;
    private static final int[] QTYS = {1, 8, 16, 64};

    private MartNet.Open data;
    private String balance;
    private String msg = "";
    private boolean err;
    private int group = -1; // -1 = all
    private int qty = 1;
    private double scroll;
    private class_342 search;
    private final List<Integer> rows = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> hitActions = new ArrayList<>();
    private int x0, y0, w, h;
    private long balanceRaw = -1;

    public MartScreen(MartNet.Open data) {
        super(class_2561.method_43470("Poke Mart"));
        this.setData(data);
    }

    private void setData(MartNet.Open d) {
        this.data = d;
        this.balance = d.balance();
        this.names.clear();
        for (int i = 0; i < d.stacks().size(); i++) {
            this.names.add((d.stacks().get(i).method_7964().getString() + " " + d.merchants().get(i) + " " + d.groups().get(d.group()[i])).toLowerCase(Locale.ROOT));
        }
    }

    public void update(MartNet.Open d) {
        this.setData(d);
        this.refilter();
    }

    public void result(MartNet.Result r) {
        if (!r.balance().isEmpty()) this.balance = r.balance();
        this.msg = r.msg();
        this.err = r.err();
    }

    protected void method_25426() {
        this.w = Math.min(560, this.field_22789 - 12);
        this.h = Math.min(320, this.field_22790 - 12);
        this.x0 = (this.field_22789 - this.w) / 2;
        this.y0 = (this.field_22790 - this.h) / 2;
        String keep = this.search == null ? "" : this.search.method_1882();
        this.search = new class_342(this.field_22793, this.x0 + 8, this.y0 + 26, this.w - 16, 16, class_2561.method_43470("Search"));
        this.search.method_1880(48);
        this.search.method_47404(class_2561.method_43470("Search items, merchants, categories...").method_27692(net.minecraft.class_124.field_1063));
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

    private void refilter() {
        this.rows.clear();
        String q = this.search == null ? "" : this.search.method_1882().trim().toLowerCase(Locale.ROOT);
        String[] terms = q.isEmpty() ? new String[0] : q.split("\\s+");
        for (int i = 0; i < this.names.size(); i++) {
            if (this.group >= 0 && this.data.group()[i] != this.group) continue;
            boolean ok = true;
            for (String t : terms) if (!this.names.get(i).contains(t)) { ok = false; break; }
            if (ok) this.rows.add(i);
        }
        this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
    }

    private int listTop() { return this.y0 + 66; }
    private int listBottom() { return this.y0 + this.h - 36; }
    private double maxScroll() { return Math.max(0, this.rows.size() * ROW_H - (this.listBottom() - this.listTop())); }

    private void box(class_332 g, int x, int y, int bw, int bh, int fill, int border) {
        g.method_25294(x, y, x + bw, y + bh, fill);
        g.method_49601(x, y, bw, bh, border);
    }

    private void hit(int x, int y, int bw, int bh, Runnable r) {
        this.hits.add(new int[]{x, y, bw, bh});
        this.hitActions.add(r);
    }

    private int chip(class_332 g, int x, int y, String label, boolean active, int mx, int my, Runnable r) {
        int cw = this.field_22793.method_1727(label) + 12;
        boolean over = mx >= x && mx < x + cw && my >= y && my < y + 14;
        this.box(g, x, y, cw, 14, active ? 0xFF2E4A8A : (over ? 0xFF2A2F48 : PANEL), active ? ACCENT : BORDER);
        g.method_51433(this.field_22793, label, x + 6, y + 3, active ? TEXT : MUTED, false);
        this.hit(x, y, cw, 14, r);
        return cw + 4;
    }

    private static void click() {
        class_310.method_1551().method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1.0F));
    }

    private static String price(long p) { return String.format("%,d", p); }

    public void method_25394(class_332 g, int mx, int my, float delta) {
        g.method_25294(0, 0, this.field_22789, this.field_22790, 0x90000000);
        this.hits.clear();
        this.hitActions.clear();
        this.box(g, this.x0, this.y0, this.w, this.h, BG, BORDER);
        g.method_25294(this.x0 + 1, this.y0 + 1, this.x0 + this.w - 1, this.y0 + 20, 0xFF23274A);
        g.method_51433(this.field_22793, "Poke Mart", this.x0 + 8, this.y0 + 6, TEXT, true);
        if (!this.balance.isEmpty()) {
            g.method_51433(this.field_22793, this.balance, this.x0 + this.w - 8 - this.field_22793.method_1727(this.balance), this.y0 + 6, GOLD, true);
        }
        String count = this.rows.size() + " items";
        g.method_51433(this.field_22793, count, this.x0 + this.w - 12 - this.field_22793.method_1727(count), this.y0 + 30, MUTED, false);
        // category chips
        int cx = this.x0 + 8, cy = this.y0 + 46;
        cx += this.chip(g, cx, cy, "All", this.group < 0, mx, my, () -> { this.group = -1; this.scroll = 0; this.refilter(); });
        for (int i = 0; i < this.data.groups().size(); i++) {
            final int gi = i;
            String label = this.data.groups().get(i);
            int cw = this.field_22793.method_1727(label) + 16;
            if (cx + cw > this.x0 + this.w - 6) break;
            cx += this.chip(g, cx, cy, label, this.group == i, mx, my, () -> { this.group = gi; this.scroll = 0; this.refilter(); });
        }
        // list
        int top = this.listTop(), bottom = this.listBottom();
        int lx = this.x0 + 8, lw = this.w - 16;
        g.method_44379(lx, top, lx + lw, bottom);
        int hover = -1;
        long bal = -1;
        for (int r = 0; r < this.rows.size(); r++) {
            int ry = top + r * ROW_H - (int) this.scroll;
            if (ry + ROW_H < top || ry > bottom) continue;
            int i = this.rows.get(r);
            boolean over = mx >= lx && mx < lx + lw && my >= Math.max(ry, top) && my < Math.min(ry + ROW_H, bottom);
            if (over) hover = i;
            this.box(g, lx, ry, lw - 6, ROW_H - 2, over ? 0xFF262C48 : PANEL, over ? ACCENT : 0xFF2A2E48);
            class_1799 st = this.data.stacks().get(i);
            g.method_51427(st, lx + 4, ry + 3);
            g.method_51433(this.field_22793, st.method_7964().getString(), lx + 26, ry + 3, TEXT, true);
            g.method_51433(this.field_22793, this.data.merchants().get(i) + "  |  " + this.data.groups().get(this.data.group()[i]), lx + 26, ry + 13, MUTED, false);
            String p = price(this.data.prices()[i] * this.qty);
            g.method_51433(this.field_22793, p, lx + lw - 12 - this.field_22793.method_1727(p), ry + 8, GOLD, true);
            final int idx = i;
            this.hit(lx, Math.max(ry, top), lw - 6, Math.min(ry + ROW_H, bottom) - Math.max(ry, top), () -> this.buy(idx));
        }
        g.method_44380();
        if (this.rows.isEmpty()) {
            String none = "No items match your search";
            g.method_51433(this.field_22793, none, lx + (lw - this.field_22793.method_1727(none)) / 2, top + 30, MUTED, false);
        }
        // scrollbar
        if (this.maxScroll() > 0) {
            int barH = Math.max(14, (int) ((bottom - top) * ((bottom - top) / (double) (this.rows.size() * ROW_H))));
            int barY = top + (int) ((bottom - top - barH) * (this.scroll / this.maxScroll()));
            g.method_25294(lx + lw - 4, top, lx + lw, bottom, 0xFF14162A);
            g.method_25294(lx + lw - 4, barY, lx + lw, barY + barH, ACCENT);
        }
        // footer: quantity + message
        int fy = this.y0 + this.h - 30;
        g.method_51433(this.field_22793, "Buy amount:", this.x0 + 8, fy + 4, MUTED, false);
        int qx = this.x0 + 8 + this.field_22793.method_1727("Buy amount:") + 6;
        for (int q : QTYS) {
            final int qq = q;
            qx += this.chip(g, qx, fy, "x" + q, this.qty == q, mx, my, () -> this.qty = qq);
        }
        String m = this.msg.isEmpty() ? "Click an item to buy it   |   Wheel to scroll   |   Right-click the search bar to clear" : this.msg;
        int maxW = this.w - 16;
        while (m.length() > 4 && this.field_22793.method_1727(m) > maxW) m = m.substring(0, m.length() - 4) + "...";
        g.method_51433(this.field_22793, m, this.x0 + 8, this.y0 + this.h - 12, this.msg.isEmpty() ? MUTED : (this.err ? RED : GREEN), false);
        // tooltip
        if (hover >= 0) {
            class_1799 st = this.data.stacks().get(hover);
            List<class_2561> tip = new ArrayList<>(st.method_7950(net.minecraft.class_1792.class_9635.field_51353, class_310.method_1551().field_1724, net.minecraft.class_1836.field_41070));
            tip.add(class_2561.method_43470("Sold by " + this.data.merchants().get(hover)).method_27692(net.minecraft.class_124.field_1080));
            g.method_51434(this.field_22793, tip, mx, my);
        }
        super.method_25394(g, mx, my, delta);
    }

    private void buy(int index) {
        if (ClientPlayNetworking.canSend(MartNet.Buy.ID)) {
            click();
            ClientPlayNetworking.send(new MartNet.Buy(index, this.qty));
        }
    }

    public boolean method_25402(double mx, double my, int button) {
        if (button == 1 && this.search != null && mx >= this.search.method_46426() && mx < this.search.method_46426() + this.search.method_25368()
            && my >= this.search.method_46427() && my < this.search.method_46427() + 16) {
            this.search.method_1852("");
            this.search.method_25365(true);
            return true;
        }
        if (button == 0) {
            for (int i = 0; i < this.hits.size(); i++) {
                int[] r = this.hits.get(i);
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    this.hitActions.get(i).run();
                    return true;
                }
            }
        }
        return super.method_25402(mx, my, button);
    }

    public boolean method_25401(double mx, double my, double horizontal, double vertical) {
        this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - vertical * ROW_H * 1.5));
        return true;
    }
}
