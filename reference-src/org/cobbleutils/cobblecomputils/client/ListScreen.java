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
import org.cobbleutils.cobblecomputils.capture.ListNet;

/** Generic searchable list window: search bar, category chips, rows with icons, click to act. */
@Environment(EnvType.CLIENT)
public final class ListScreen extends class_437 {
    private static final int BG = 0xF0121420;
    private static final int PANEL = 0xFF1B1E2E;
    private static final int BORDER = 0xFF4A5080;
    private static final int ACCENT = 0xFF5B8CFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0B8;
    private static final int GOLD = 0xFFFFC85A;
    private static final int GREEN = 0xFF6CE08A;
    private static final int RED = 0xFFFF6B6B;
    private static final int ROW_H = 26;

    private ListNet.View data;
    private int group = -1;
    private double scroll;
    private class_342 search;
    private final List<Integer> rows = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> hitActions = new ArrayList<>();
    private final List<int[]> rowHits = new ArrayList<>();
    private int x0, y0, w, h;
    private boolean closedByServer;

    public ListScreen(ListNet.View data) {
        super(class_2561.method_43470(data.title()));
        this.setData(data);
    }

    private void setData(ListNet.View d) {
        this.data = d;
        this.names.clear();
        for (ListNet.Row r : d.rows()) {
            String g = r.group() >= 0 && r.group() < d.groups().size() ? d.groups().get(r.group()) : "";
            this.names.add((r.title() + " " + r.sub() + " " + g).toLowerCase(Locale.ROOT));
        }
        if (this.group >= d.groups().size()) this.group = -1;
    }

    public void update(ListNet.View d) {
        // moving to a different list (same window): drop the old category filter, scroll and search text
        boolean other = this.data != null && !this.data.title().equals(d.title());
        if (other) {
            this.group = -1;
            this.scroll = 0;
            if (this.search != null && !this.search.method_1882().isEmpty()) this.search.method_1852("");
        }
        this.setData(d);
        this.refilter();
    }

    public void closeFromServer() {
        this.closedByServer = true;
        this.method_25419();
    }

    public void method_25432() {
        if (!this.closedByServer && ClientPlayNetworking.canSend(ListNet.Close.ID)) ClientPlayNetworking.send(new ListNet.Close(0));
        super.method_25432();
    }

    protected void method_25426() {
        this.w = Math.min(560, this.field_22789 - 12);
        this.h = Math.min(320, this.field_22790 - 12);
        this.x0 = (this.field_22789 - this.w) / 2;
        this.y0 = (this.field_22790 - this.h) / 2;
        String keep = this.search == null ? "" : this.search.method_1882();
        this.search = new class_342(this.field_22793, this.x0 + 8, this.y0 + 26, this.w - 16, 16, class_2561.method_43470("Search"));
        this.search.method_1880(48);
        this.search.method_47404(class_2561.method_43470("Search...").method_27692(net.minecraft.class_124.field_1063));
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
            if (this.group >= 0 && this.data.rows().get(i).group() != this.group) continue;
            boolean ok = true;
            for (String t : terms) if (!this.names.get(i).contains(t)) { ok = false; break; }
            if (ok) this.rows.add(i);
        }
        this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
    }

    private int listTop() { return this.y0 + (this.data.groups().isEmpty() ? 48 : 66); }
    private int listBottom() { return this.y0 + this.h - 20; }
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

    private void send(int index) { this.send(index, 0); }

    private void send(int index, int button) {
        if (ClientPlayNetworking.canSend(ListNet.Click.ID)) {
            click();
            ClientPlayNetworking.send(new ListNet.Click(index, button));
        }
    }

    public void method_25394(class_332 g, int mx, int my, float delta) {
        g.method_25294(0, 0, this.field_22789, this.field_22790, 0x90000000);
        this.hits.clear();
        this.hitActions.clear();
        this.rowHits.clear();
        this.box(g, this.x0, this.y0, this.w, this.h, BG, BORDER);
        g.method_25294(this.x0 + 1, this.y0 + 1, this.x0 + this.w - 1, this.y0 + 20, 0xFF23274A);
        int tx = this.x0 + 8;
        if (this.data.back()) {
            int bw = this.field_22793.method_1727("< Menu") + 10;
            boolean over = mx >= tx && mx < tx + bw && my >= this.y0 + 4 && my < this.y0 + 17;
            this.box(g, tx, this.y0 + 4, bw, 13, over ? 0xFF2A3050 : PANEL, over ? ACCENT : BORDER);
            g.method_51433(this.field_22793, "< Menu", tx + 5, this.y0 + 7, TEXT, false);
            this.hit(tx, this.y0 + 4, bw, 13, () -> this.send(-1));
            tx += bw + 8;
        }
        g.method_51433(this.field_22793, this.data.title(), tx, this.y0 + 6, TEXT, true);
        String info = this.data.info();
        if (!info.isEmpty()) g.method_51433(this.field_22793, info, this.x0 + this.w - 8 - this.field_22793.method_1727(info), this.y0 + 6, GOLD, true);
        String count = this.rows.size() + " shown";
        g.method_51433(this.field_22793, count, this.x0 + this.w - 12 - this.field_22793.method_1727(count), this.y0 + 30, MUTED, false);
        if (!this.data.groups().isEmpty()) {
            int cx = this.x0 + 8, cy = this.y0 + 46;
            cx += this.chip(g, cx, cy, "All", this.group < 0, mx, my, () -> { this.group = -1; this.scroll = 0; this.refilter(); });
            for (int i = 0; i < this.data.groups().size(); i++) {
                final int gi = i;
                String label = this.data.groups().get(i);
                int cw = this.field_22793.method_1727(label) + 16;
                if (cx + cw > this.x0 + this.w - 6) break;
                cx += this.chip(g, cx, cy, label, this.group == i, mx, my, () -> { this.group = gi; this.scroll = 0; this.refilter(); });
            }
        }
        int top = this.listTop(), bottom = this.listBottom();
        int lx = this.x0 + 8, lw = this.w - 16;
        g.method_44379(lx, top, lx + lw, bottom);
        int hover = -1;
        for (int r = 0; r < this.rows.size(); r++) {
            int ry = top + r * ROW_H - (int) this.scroll;
            if (ry + ROW_H < top || ry > bottom) continue;
            int i = this.rows.get(r);
            ListNet.Row row = this.data.rows().get(i);
            boolean over = mx >= lx && mx < lx + lw && my >= Math.max(ry, top) && my < Math.min(ry + ROW_H, bottom);
            if (over) hover = i;
            int border = row.state() == 2 ? 0xFF2F7A47 : (row.state() == 4 ? GREEN : (over ? ACCENT : 0xFF2A2E48));
            this.box(g, lx, ry, lw - 6, ROW_H - 2, over ? 0xFF262C48 : PANEL, border);
            g.method_51427(row.stack(), lx + 5, ry + 4);
            int titleColor = row.state() == 1 ? MUTED : (row.state() == 2 ? GREEN : TEXT);
            g.method_51433(this.field_22793, row.title(), lx + 28, ry + 4, titleColor, true);
            if (!row.sub().isEmpty()) g.method_51433(this.field_22793, row.sub(), lx + 28, ry + 14, row.state() == 1 ? RED : MUTED, false);
            int rx = lx + lw - 12;
            if (!row.icons().isEmpty()) {
                for (int k = row.icons().size() - 1; k >= 0; k--) {
                    rx -= 18;
                    g.method_51427(row.icons().get(k), rx, ry + 4);
                }
            } else if (!row.right().isEmpty()) {
                int col = row.state() == 3 || row.state() == 1 ? RED : (row.state() == 2 || row.state() == 4 ? GREEN : GOLD);
                g.method_51433(this.field_22793, row.right(), rx - this.field_22793.method_1727(row.right()), ry + 9, col, true);
            }
            final int idx = i;
            this.rowHits.add(new int[]{lx, Math.max(ry, top), lw - 6, Math.min(ry + ROW_H, bottom) - Math.max(ry, top), idx});
            this.hit(lx, Math.max(ry, top), lw - 6, Math.min(ry + ROW_H, bottom) - Math.max(ry, top), () -> this.send(idx));
        }
        g.method_44380();
        if (this.rows.isEmpty()) {
            String none = "Nothing matches your search";
            g.method_51433(this.field_22793, none, lx + (lw - this.field_22793.method_1727(none)) / 2, top + 30, MUTED, false);
        }
        if (this.maxScroll() > 0) {
            int barH = Math.max(14, (int) ((bottom - top) * ((bottom - top) / (double) (this.rows.size() * ROW_H))));
            int barY = top + (int) ((bottom - top - barH) * (this.scroll / this.maxScroll()));
            g.method_25294(lx + lw - 4, top, lx + lw, bottom, 0xFF14162A);
            g.method_25294(lx + lw - 4, barY, lx + lw, barY + barH, ACCENT);
        }
        String m = this.data.footer().isEmpty() ? (this.data.hint().isEmpty() ? "Click a row  |  Wheel to scroll  |  Right-click the search bar to clear" : this.data.hint()) : this.data.footer();
        int maxW = this.w - 16;
        while (m.length() > 4 && this.field_22793.method_1727(m) > maxW) m = m.substring(0, m.length() - 4) + "...";
        g.method_51433(this.field_22793, m, this.x0 + 8, this.y0 + this.h - 12, this.data.footer().isEmpty() ? MUTED : (this.data.err() ? RED : GREEN), false);
        if (hover >= 0) {
            ListNet.Row row = this.data.rows().get(hover);
            List<class_2561> tip = new ArrayList<>();
            tip.add(class_2561.method_43470(row.title()).method_27692(net.minecraft.class_124.field_1068));
            if (!row.tip().isEmpty()) for (String l : row.tip().split("\n")) tip.add(class_2561.method_43470(l).method_27692(net.minecraft.class_124.field_1080));
            g.method_51434(this.field_22793, tip, mx, my);
        }
        super.method_25394(g, mx, my, delta);
    }

    public boolean method_25402(double mx, double my, int button) {
        if (button == 1 && this.search != null && mx >= this.search.method_46426() && mx < this.search.method_46426() + this.search.method_25368()
            && my >= this.search.method_46427() && my < this.search.method_46427() + 16) {
            this.search.method_1852("");
            this.search.method_25365(true);
            return true;
        }
        if (button == 1) {
            for (int i = 0; i < this.rowHits.size(); i++) {
                int[] r = this.rowHits.get(i);
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) { this.send(r[4], 1); return true; }
            }
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
