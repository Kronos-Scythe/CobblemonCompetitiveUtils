package org.cobbleutils.cobblecomputils.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.class_1109;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_3417;
import net.minecraft.class_437;
import org.cobbleutils.cobblecomputils.capture.ListNet;

/**
 * Icon tile hub: a large "next gym" card with one big Fight button on top, then square icon tiles grouped in sections.
 * Driven by the same list payload as {@link ListScreen}: row 0 is the card, every other row is a tile, rows are sectioned by group.
 */
@Environment(EnvType.CLIENT)
public final class HubScreen extends class_437 {
    private static final int BG = 0xF0121420;
    private static final int PANEL = 0xFF1B1E2E;
    private static final int BORDER = 0xFF4A5080;
    private static final int ACCENT = 0xFF5B8CFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0B8;
    private static final int GOLD = 0xFFFFC85A;
    private static final int GREEN = 0xFF6CE08A;
    private static final int RED = 0xFFFF6B6B;
    private static final int TW = 102, TH = 52, GAP = 6, LABEL_H = 14;

    private ListNet.View data;
    private double scroll;
    private int x0, y0, w, h;
    private boolean closedByServer;
    private final List<int[]> hits = new ArrayList<>();

    public HubScreen(ListNet.View data) {
        super(class_2561.method_43470(data.title()));
        this.data = data;
    }

    public void update(ListNet.View d) { this.data = d; }

    public void markReplaced() { this.closedByServer = true; }

    public void closeFromServer() {
        this.closedByServer = true;
        this.method_25419();
    }

    public void method_25432() {
        if (!this.closedByServer && ClientPlayNetworking.canSend(ListNet.Close.ID)) ClientPlayNetworking.send(new ListNet.Close(0));
        super.method_25432();
    }

    protected void method_25426() {
        this.w = Math.min(580, this.field_22789 - 12);
        this.h = Math.min(430, this.field_22790 - 12);
        this.x0 = (this.field_22789 - this.w) / 2;
        this.y0 = (this.field_22790 - this.h) / 2;
    }

    public void method_25420(class_332 g, int mx, int my, float delta) { }

    public boolean method_25421() { return false; }

    private int cols() { return Math.max(1, (this.w - 16 + GAP) / (TW + GAP)); }

    private int gridLeft() {
        int c = this.cols();
        int used = c * TW + (c - 1) * GAP;
        return this.x0 + (this.w - used) / 2;
    }

    private int top() { return this.y0 + 86; }
    private int bottom() { return this.y0 + this.h - 18; }

    /** Tile rows of one section, in order. */
    private List<Integer> section(int group) {
        List<Integer> l = new ArrayList<>();
        for (int i = 1; i < this.data.rows().size(); i++) {
            int gr = this.data.rows().get(i).group();
            if (gr == group) l.add(i);
        }
        return l;
    }

    private int contentHeight() {
        int hh = 0, c = this.cols();
        List<Integer> groups = new ArrayList<>();
        for (int gi = -1; gi < this.data.groups().size(); gi++) groups.add(gi);
        for (int gi : groups) {
            int n = this.section(gi).size();
            if (n == 0) continue;
            hh += LABEL_H + ((n + c - 1) / c) * (TH + GAP) + 2;
        }
        return hh;
    }

    private double maxScroll() { return Math.max(0, this.contentHeight() - (this.bottom() - this.top())); }

    private void box(class_332 g, int x, int y, int bw, int bh, int fill, int border) {
        g.method_25294(x, y, x + bw, y + bh, fill);
        g.method_49601(x, y, bw, bh, border);
    }

    private String fit(String s, int max) {
        if (this.field_22793.method_1727(s) <= max) return s;
        while (s.length() > 2 && this.field_22793.method_1727(s + "..") > max) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private void drawItem(class_332 g, class_1799 st, int x, int y, float scale) {
        var ps = g.method_51448();
        ps.method_22903();
        ps.method_46416(x, y, 0);
        ps.method_22905(scale, scale, 1.0F);
        g.method_51427(st, 0, 0);
        ps.method_22909();
    }

    private static void click() {
        class_310.method_1551().method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1.0F));
    }

    private void send(int index) {
        if (ClientPlayNetworking.canSend(ListNet.Click.ID)) {
            click();
            ClientPlayNetworking.send(new ListNet.Click(index, 0));
        }
    }

    public void method_25394(class_332 g, int mx, int my, float delta) {
        g.method_25294(0, 0, this.field_22789, this.field_22790, 0x90000000);
        this.hits.clear();
        this.box(g, this.x0, this.y0, this.w, this.h, BG, BORDER);
        g.method_25294(this.x0 + 1, this.y0 + 1, this.x0 + this.w - 1, this.y0 + 20, 0xFF23274A);
        g.method_51433(this.field_22793, this.data.title(), this.x0 + 8, this.y0 + 6, TEXT, true);
        String info = this.data.info();
        if (!info.isEmpty()) g.method_51433(this.field_22793, fit(info, this.w - 140), this.x0 + this.w - 8 - Math.min(this.field_22793.method_1727(info), this.w - 140), this.y0 + 6, GOLD, true);

        int hover = -1;
        // ---- featured card (row 0)
        if (!this.data.rows().isEmpty()) {
            ListNet.Row card = this.data.rows().get(0);
            int cx = this.x0 + 8, cy = this.y0 + 26, cw = this.w - 16, ch = 54;
            boolean over = mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;
            if (over) hover = 0;
            this.box(g, cx, cy, cw, ch, over ? 0xFF262C48 : PANEL, over ? ACCENT : 0xFFB8892E);
            g.method_25294(cx + 1, cy + 1, cx + 4, cy + ch - 1, 0xFFFFC85A);
            this.box(g, cx + 10, cy + 9, 36, 36, 0xFF14162A, 0xFF3A4070);
            this.drawItem(g, card.stack(), cx + 14, cy + 13, 1.75F);
            int btnW = 116;
            g.method_51433(this.field_22793, "NEXT FIGHT", cx + 56, cy + 8, MUTED, false);
            g.method_51433(this.field_22793, fit(card.title().startsWith("Next: ") ? card.title().substring(6) : card.title(), cw - btnW - 76), cx + 56, cy + 20, TEXT, true);
            g.method_51433(this.field_22793, fit(card.sub(), cw - btnW - 76), cx + 56, cy + 34, MUTED, false);
            int bx = cx + cw - btnW - 10, by = cy + 11, bh = 32;
            boolean bo = mx >= bx && mx < bx + btnW && my >= by && my < by + bh;
            this.box(g, bx, by, btnW, bh, bo ? 0xFF3FA862 : 0xFF2F8A4E, 0xFF7BE39B);
            String lab = card.right().isEmpty() ? "FIGHT" : card.right();
            g.method_51433(this.field_22793, lab, bx + (btnW - this.field_22793.method_1727(lab)) / 2, by + 12, TEXT, true);
            this.hits.add(new int[]{cx, cy, cw, ch, 0});
        }

        // ---- tile sections
        int top = this.top(), bottom = this.bottom();
        g.method_44379(this.x0 + 2, top, this.x0 + this.w - 2, bottom);
        int c = this.cols(), left = this.gridLeft();
        int y = top - (int) this.scroll;
        for (int gi = -1; gi < this.data.groups().size(); gi++) {
            List<Integer> rows = this.section(gi);
            if (rows.isEmpty()) continue;
            String label = gi < 0 ? "More" : this.data.groups().get(gi);
            if (y + LABEL_H > top && y < bottom) {
                g.method_51433(this.field_22793, label.toUpperCase(), left, y + 2, MUTED, false);
                int lw = this.field_22793.method_1727(label.toUpperCase());
                g.method_25294(left + lw + 6, y + 6, this.x0 + this.w - 12, y + 7, 0xFF2A2E48);
            }
            y += LABEL_H;
            for (int k = 0; k < rows.size(); k++) {
                int ri = rows.get(k);
                ListNet.Row row = this.data.rows().get(ri);
                int tx = left + (k % c) * (TW + GAP), ty = y + (k / c) * (TH + GAP);
                if (ty + TH < top || ty > bottom) continue;
                boolean over = mx >= tx && mx < tx + TW && my >= Math.max(ty, top) && my < Math.min(ty + TH, bottom);
                if (over) hover = ri;
                int st = row.state();
                int border = st == 1 ? 0xFF6B3A3F : (st == 2 ? 0xFF2F7A47 : (st == 4 ? GREEN : (over ? ACCENT : 0xFF2A2E48)));
                this.box(g, tx, ty, TW, TH, over ? 0xFF262C48 : (st == 1 ? 0xFF171926 : PANEL), border);
                this.drawItem(g, row.stack(), tx + 7, ty + 7, 1.5F);
                int tc = st == 1 ? MUTED : (st == 2 ? GREEN : TEXT);
                g.method_51433(this.field_22793, fit(row.title(), TW - 12), tx + 6, ty + TH - 14, tc, true);
                String r = row.right();
                if (!r.isEmpty() && !r.equals(">") && !r.equals("OPEN")) {
                    int col = st == 3 || st == 1 ? RED : (st == 2 || st == 4 ? GREEN : GOLD);
                    String rr = fit(r, TW - 40);
                    int rw = this.field_22793.method_1727(rr);
                    g.method_25294(tx + TW - rw - 10, ty + 5, tx + TW - 4, ty + 16, 0xFF14162A);
                    g.method_51433(this.field_22793, rr, tx + TW - rw - 7, ty + 7, col, false);
                }
                this.hits.add(new int[]{tx, Math.max(ty, top), TW, Math.min(ty + TH, bottom) - Math.max(ty, top), ri});
            }
            y += ((rows.size() + c - 1) / c) * (TH + GAP) + 2;
        }
        g.method_44380();
        if (this.maxScroll() > 0) {
            int span = bottom - top;
            int barH = Math.max(14, (int) (span * (span / (double) this.contentHeight())));
            int barY = top + (int) ((span - barH) * (this.scroll / this.maxScroll()));
            g.method_25294(this.x0 + this.w - 6, top, this.x0 + this.w - 2, bottom, 0xFF14162A);
            g.method_25294(this.x0 + this.w - 6, barY, this.x0 + this.w - 2, barY + barH, ACCENT);
        }
        String m = this.data.footer().isEmpty() ? this.data.hint() : this.data.footer();
        if (m.isEmpty()) m = "Click a tile  |  Wheel to scroll";
        g.method_51433(this.field_22793, fit(m, this.w - 16), this.x0 + 8, this.y0 + this.h - 12, this.data.footer().isEmpty() ? MUTED : (this.data.err() ? RED : GREEN), false);

        if (hover >= 0) {
            ListNet.Row row = this.data.rows().get(hover);
            List<class_2561> tip = new ArrayList<>();
            tip.add(class_2561.method_43470(row.title()).method_27692(net.minecraft.class_124.field_1068));
            if (!row.sub().isEmpty() && hover > 0) tip.add(class_2561.method_43470(row.sub()).method_27692(net.minecraft.class_124.field_1080));
            if (!row.tip().isEmpty()) for (String l : row.tip().split("\n")) tip.add(class_2561.method_43470(l).method_27692(net.minecraft.class_124.field_1063));
            g.method_51434(this.field_22793, tip, mx, my);
        }
        super.method_25394(g, mx, my, delta);
    }

    public boolean method_25402(double mx, double my, int button) {
        if (button == 0) {
            for (int[] r : this.hits) {
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) { this.send(r[4]); return true; }
            }
        }
        return super.method_25402(mx, my, button);
    }

    public boolean method_25401(double mx, double my, double horizontal, double vertical) {
        this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - vertical * 30));
        return true;
    }
}
