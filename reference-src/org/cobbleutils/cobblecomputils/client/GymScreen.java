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
import org.cobbleutils.cobblecomputils.capture.GymNet;

/** The phone / Gym Challenge menu drawn in the same style as the EV editor and Move Tutor. Mirrors the server's 54-slot menu. */
@Environment(EnvType.CLIENT)
public final class GymScreen extends class_437 {
    private static final int BG = 0xF0121420;
    private static final int PANEL = 0xFF1B1E2E;
    private static final int BORDER = 0xFF4A5080;
    private static final int ACCENT = 0xFF5B8CFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0B8;

    private GymNet.View view;
    private boolean closedByServer = false;

    public GymScreen(GymNet.View view) {
        super(class_2561.method_43470("Gym Challenge"));
        this.view = view;
    }

    public void update(GymNet.View v) {
        this.view = v;
    }

    public boolean method_25421() {
        return false;
    }

    public void method_25420(class_332 g, int mx, int my, float delta) {
    }

    private boolean filler(class_1799 s) {
        return s.method_7960() || s.method_7964().getString().isBlank();
    }

    private int cell() {
        return Math.max(22, Math.min(40, (this.field_22790 - 70) / 6));
    }

    public void method_25394(class_332 g, int mx, int my, float delta) {
        g.method_25294(0, 0, this.field_22789, this.field_22790, 0x90000000);
        int c = this.cell();
        int gap = 2;
        int gw = 9 * (c + gap) - gap + 16;
        int gh = 6 * (c + gap) - gap + 40;
        int x0 = (this.field_22789 - gw) / 2, y0 = (this.field_22790 - gh) / 2;
        g.method_25294(x0, y0, x0 + gw, y0 + gh, BG);
        g.method_49601(x0, y0, gw, gh, BORDER);
        g.method_25294(x0 + 1, y0 + 1, x0 + gw - 1, y0 + 20, 0xFF23274A);
        g.method_51433(this.field_22793, this.view.title(), x0 + 8, y0 + 6, TEXT, true);
        String hint = "Click a tile  |  Esc to close";
        g.method_51433(this.field_22793, hint, x0 + gw - 8 - this.field_22793.method_1727(hint), y0 + 6, MUTED, false);
        int hover = -1;
        List<class_1799> st = this.view.stacks();
        for (int i = 0; i < 54 && i < st.size(); i++) {
            int cx = x0 + 8 + (i % 9) * (c + gap), cy = y0 + 28 + (i / 9) * (c + gap);
            class_1799 s = st.get(i);
            if (filler(s)) {
                g.method_25294(cx, cy, cx + c, cy + c, 0xFF14162A);
                continue;
            }
            boolean over = mx >= cx && mx < cx + c && my >= cy && my < cy + c;
            if (over) hover = i;
            g.method_25294(cx, cy, cx + c, cy + c, over ? 0xFF2A3050 : PANEL);
            g.method_49601(cx, cy, c, c, over ? ACCENT : BORDER);
            g.method_51427(s, cx + (c - 16) / 2, cy + (c - 16) / 2);
        }
        if (hover >= 0) {
            class_1799 s = st.get(hover);
            List<class_2561> tip = new ArrayList<>(s.method_7950(net.minecraft.class_1792.class_9635.field_51353, class_310.method_1551().field_1724, net.minecraft.class_1836.field_41070));
            g.method_51434(this.field_22793, tip, mx, my);
        }
        super.method_25394(g, mx, my, delta);
    }

    public boolean method_25402(double mx, double my, int button) {
        if (button == 0) {
            int c = this.cell();
            int gap = 2;
            int gw = 9 * (c + gap) - gap + 16;
            int gh = 6 * (c + gap) - gap + 40;
            int x0 = (this.field_22789 - gw) / 2, y0 = (this.field_22790 - gh) / 2;
            List<class_1799> st = this.view.stacks();
            for (int i = 0; i < 54 && i < st.size(); i++) {
                int cx = x0 + 8 + (i % 9) * (c + gap), cy = y0 + 28 + (i / 9) * (c + gap);
                if (mx >= cx && mx < cx + c && my >= cy && my < cy + c && !this.filler(st.get(i))) {
                    class_310.method_1551().method_1483().method_4873(class_1109.method_47978(class_3417.field_15015, 1.0F));
                    ClientPlayNetworking.send(new GymNet.Click(i));
                    return true;
                }
            }
        }
        return super.method_25402(mx, my, button);
    }

    public void closeFromServer() {
        this.closedByServer = true;
        this.method_25419();
    }

    public void method_25432() {
        if (!this.closedByServer && ClientPlayNetworking.canSend(GymNet.Close.ID)) {
            ClientPlayNetworking.send(new GymNet.Close(0));
        }
        super.method_25432();
    }
}
