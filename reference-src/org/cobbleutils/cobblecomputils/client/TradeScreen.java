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
import org.cobbleutils.cobblecomputils.capture.TradeNet;

/** Trade confirm window: your party on the left, both offers side by side, Accept / Withdraw / Cancel buttons. */
@Environment(EnvType.CLIENT)
public final class TradeScreen extends class_437 {
    private static final int BG = 0xF0121420, PANEL = 0xFF1B1E2E, BORDER = 0xFF4A5080, ACCENT = 0xFF5B8CFF;
    private static final int TEXT = 0xFFFFFFFF, MUTED = 0xFF9AA0B8, GOLD = 0xFFFFC85A, GREEN = 0xFF6CE08A, RED = 0xFFFF6B6B;

    private TradeNet.View v;
    private long received;
    private boolean serverClosed;
    private int x0, y0, w, h;
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();

    public TradeScreen(TradeNet.View v) {
        super(class_2561.method_43470("Trade"));
        this.update(v);
    }

    public void update(TradeNet.View nv) {
        this.v = nv;
        this.received = System.currentTimeMillis();
    }

    public void closeFromServer() {
        this.serverClosed = true;
        this.method_25419();
    }

    protected void method_25426() {
        this.w = Math.min(520, this.field_22789 - 12);
        this.h = Math.min(300, this.field_22790 - 12);
        this.x0 = (this.field_22789 - this.w) / 2;
        this.y0 = (this.field_22790 - this.h) / 2;
    }

    public void method_25420(class_332 g, int mx, int my, float delta) { }

    public boolean method_25421() { return false; }

    public void method_25419() {
        if (!this.serverClosed && ClientPlayNetworking.canSend(TradeNet.Act.ID)) ClientPlayNetworking.send(new TradeNet.Act(3, 0));
        super.method_25419();
    }

    private void box(class_332 g, int x, int y, int bw, int bh, int fill, int border) {
        g.method_25294(x, y, x + bw, y + bh, fill);
        g.method_49601(x, y, bw, bh, border);
    }

    private void hit(int x, int y, int bw, int bh, Runnable r) {
        this.hits.add(new int[]{x, y, bw, bh});
        this.actions.add(r);
    }

    private void send(int type, int arg) {
        if (ClientPlayNetworking.canSend(TradeNet.Act.ID)) {
            class_310.method_1551().method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1.0F));
            ClientPlayNetworking.send(new TradeNet.Act(type, arg));
        }
    }

    private String fit(String s, int max) {
        if (this.field_22793.method_1727(s) <= max) return s;
        while (s.length() > 3 && this.field_22793.method_1727(s + "...") > max) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String t = cur.length() == 0 ? word : cur + " " + word;
            if (this.field_22793.method_1727(t) > max && cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(word); }
            else cur = new StringBuilder(t);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private void card(class_332 g, int x, int y, int cw, int ch, String heading, TradeNet.Mon m, boolean accepted, int headColor) {
        this.box(g, x, y, cw, ch, PANEL, accepted ? GREEN : BORDER);
        g.method_51433(this.field_22793, heading, x + 6, y + 5, headColor, true);
        if (accepted) g.method_51433(this.field_22793, "ACCEPTED", x + cw - 6 - this.field_22793.method_1727("ACCEPTED"), y + 5, GREEN, true);
        if (m.empty()) {
            String t = "Nothing offered yet";
            g.method_51433(this.field_22793, t, x + (cw - this.field_22793.method_1727(t)) / 2, y + ch / 2, MUTED, false);
            return;
        }
        g.method_51433(this.field_22793, this.fit(m.name() + (m.shiny() ? " *" : ""), cw - 12), x + 6, y + 20, m.shiny() ? GOLD : TEXT, true);
        int ly = y + 34;
        for (String line : m.lines()) {
            for (String part : this.wrap(line, cw - 12)) {
                if (ly > y + ch - 10) return;
                g.method_51433(this.field_22793, part, x + 6, ly, MUTED, false);
                ly += 10;
            }
        }
    }

    private void button(class_332 g, int x, int y, int bw, String label, int mx, int my, boolean enabled, int color, Runnable r) {
        boolean over = enabled && mx >= x && mx < x + bw && my >= y && my < y + 18;
        this.box(g, x, y, bw, 18, enabled ? (over ? 0xFF2E4A8A : 0xFF23274A) : 0xFF16182A, enabled ? color : 0xFF30344C);
        g.method_51433(this.field_22793, label, x + (bw - this.field_22793.method_1727(label)) / 2, y + 5, enabled ? TEXT : 0xFF666B80, false);
        if (enabled) this.hit(x, y, bw, 18, r);
    }

    public void method_25394(class_332 g, int mx, int my, float delta) {
        g.method_25294(0, 0, this.field_22789, this.field_22790, 0x90000000);
        this.hits.clear();
        this.actions.clear();
        this.box(g, this.x0, this.y0, this.w, this.h, BG, BORDER);
        g.method_25294(this.x0 + 1, this.y0 + 1, this.x0 + this.w - 1, this.y0 + 20, 0xFF23274A);
        g.method_51433(this.field_22793, "Trade with " + this.v.partner(), this.x0 + 8, this.y0 + 6, TEXT, true);
        // party column
        int px = this.x0 + 8, py = this.y0 + 28, pw = 180;
        g.method_51433(this.field_22793, "Your party - click to offer", px, py, MUTED, false);
        py += 12;
        int hoverIdx = -1;
        for (int i = 0; i < this.v.party().size(); i++) {
            TradeNet.Mon m = this.v.party().get(i);
            int ry = py + i * 34;
            boolean offered = this.v.myOffer() == i;
            boolean over = !m.empty() && mx >= px && mx < px + pw && my >= ry && my < ry + 32;
            if (over) hoverIdx = i;
            this.box(g, px, ry, pw, 32, offered ? 0xFF1F3A2A : (over ? 0xFF262C48 : PANEL), offered ? GREEN : (over ? ACCENT : 0xFF2A2E48));
            if (m.empty()) {
                g.method_51433(this.field_22793, "(empty slot)", px + 8, ry + 12, 0xFF555A70, false);
            } else {
                g.method_51433(this.field_22793, this.fit(m.name() + (m.shiny() ? " *" : ""), pw - 16), px + 8, ry + 6, m.shiny() ? GOLD : TEXT, true);
                g.method_51433(this.field_22793, "Lv." + m.level() + (offered ? "   - offered" : ""), px + 8, ry + 18, offered ? GREEN : MUTED, false);
                final int idx = i;
                this.hit(px, ry, pw, 32, () -> this.send(0, idx));
            }
        }
        // offer cards
        int cx = this.x0 + 198, cw = (this.x0 + this.w - 8 - cx - 6) / 2, ch = 168, cy = this.y0 + 28;
        TradeNet.Mon mine = this.v.myOffer() >= 0 && this.v.myOffer() < this.v.party().size() ? this.v.party().get(this.v.myOffer()) : TradeNet.Mon.NONE;
        this.card(g, cx, cy, cw, ch, "You offer", mine, this.v.myAcc(), ACCENT);
        this.card(g, cx + cw + 6, cy, cw, ch, this.v.partner() + " offers", this.v.theirs(), this.v.theirAcc(), GOLD);
        // status + buttons
        int by = cy + ch + 8;
        long left = Math.max(0, this.v.lockMs() - (System.currentTimeMillis() - this.received));
        boolean ready = this.v.myOffer() >= 0 && !this.v.theirs().empty();
        String status;
        int sc = MUTED;
        if (!this.v.note().isEmpty()) { status = this.v.note(); sc = RED; }
        else if (this.v.myOffer() < 0) status = "Pick one of your Pokemon on the left.";
        else if (this.v.theirs().empty()) status = "Waiting for " + this.v.partner() + " to offer a Pokemon...";
        else if (this.v.myAcc() && !this.v.theirAcc()) { status = "You accepted. Waiting for " + this.v.partner() + "..."; sc = GREEN; }
        else if (!this.v.myAcc() && this.v.theirAcc()) { status = this.v.partner() + " accepted. Check the offers, then accept."; sc = GOLD; }
        else if (left > 0) status = "Check the offers carefully...";
        else status = "Review both Pokemon, then press Accept. Any change cancels acceptances.";
        g.method_51433(this.field_22793, this.fit(status, this.x0 + this.w - cx - 8), cx, by, sc, false);
        by += 14;
        boolean canAccept = ready && (left == 0 || this.v.myAcc());
        String lab = this.v.myAcc() ? "Accepted (click to undo)" : (ready && left > 0 ? "Accept (" + (left / 100 / 10.0) + "s)" : "Accept trade");
        this.button(g, cx, by, 150, lab, mx, my, canAccept, GREEN, () -> this.send(2, 0));
        this.button(g, cx + 156, by, 90, "Withdraw", mx, my, this.v.myOffer() >= 0, ACCENT, () -> this.send(1, 0));
        this.button(g, cx + 252, by, 70, "Cancel", mx, my, true, RED, () -> this.method_25419());
        g.method_51433(this.field_22793, "Trades are final. Both players must accept.", this.x0 + 8, this.y0 + this.h - 12, MUTED, false);
        if (hoverIdx >= 0) {
            List<class_2561> tip = new ArrayList<>();
            for (String l : this.v.party().get(hoverIdx).lines()) tip.add(class_2561.method_43470(l));
            g.method_51434(this.field_22793, tip, mx, my);
        }
        super.method_25394(g, mx, my, delta);
    }

    public boolean method_25402(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < this.hits.size(); i++) {
                int[] r = this.hits.get(i);
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    this.actions.get(i).run();
                    return true;
                }
            }
        }
        return super.method_25402(mx, my, button);
    }
}
