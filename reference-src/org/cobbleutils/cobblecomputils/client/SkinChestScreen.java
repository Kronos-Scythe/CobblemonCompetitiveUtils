package org.cobbleutils.cobblecomputils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_1661;
import net.minecraft.class_1707;
import net.minecraft.class_1735;
import net.minecraft.class_2561;
import net.minecraft.class_2588;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;
import net.minecraft.class_476;

/**
 * Re-skins server-driven chest menus (wiki, hunts, trade views, shops, ...) with the pack's dark window style.
 * Slots, clicks and tooltips are vanilla, so every menu keeps working; only the background changes.
 * Real chests/barrels use a translatable title and are left alone.
 */
@Environment(EnvType.CLIENT)
public final class SkinChestScreen extends class_476 {
    private static final int BG = 0xF0121420;
    private static final int PANEL = 0xFF1B1E2E;
    private static final int BORDER = 0xFF4A5080;
    private static final int HEAD = 0xFF23274A;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF9AA0B8;

    private final class_1707 menu;
    private final int rows;

    public SkinChestScreen(class_1707 menu, class_1661 inv, class_2561 title) {
        super(menu, inv, title);
        this.menu = menu;
        this.rows = menu.method_17388();
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            class_437 s = mc.field_1755;
            if (s != null && s.getClass() == class_476.class && mc.field_1724 != null) {
                class_476 c = (class_476) s;
                class_2561 title = c.method_25440();
                if (title.method_10851() instanceof class_2588) return;
                if (!(mc.field_1724.field_7512 instanceof class_1707 m)) return;
                mc.method_1507(new SkinChestScreen(m, mc.field_1724.method_31548(), title));
            }
        });
    }

    protected void method_2389(class_332 g, float delta, int mx, int my) {
        int x = this.field_2776, y = this.field_2800, w = this.field_2792, h = this.field_2779;
        g.method_25294(x - 4, y - 4, x + w + 4, y + h + 4, BG);
        g.method_49601(x - 4, y - 4, w + 8, h + 8, BORDER);
        g.method_25294(x - 3, y - 3, x + w + 3, y + 15, HEAD);
        int n = this.menu.field_7761.size();
        for (int i = 0; i < n; i++) {
            class_1735 s = this.menu.field_7761.get(i);
            int sx = x + s.field_7873 - 1, sy = y + s.field_7872 - 1;
            boolean menuSlot = i < this.rows * 9;
            var st = s.method_7677();
            boolean filler = menuSlot && !st.method_7960() && st.method_7964().getString().isBlank();
            int fill = filler ? 0xFF14162A : (st.method_7960() ? 0xFF181A28 : PANEL);
            g.method_25294(sx, sy, sx + 18, sy + 18, fill);
            if (!filler) g.method_49601(sx, sy, 18, 18, st.method_7960() ? 0xFF2A2E48 : BORDER);
        }
    }

    protected void method_2388(class_332 g, int mx, int my) {
        g.method_51433(this.field_22793, this.field_22785.getString(), 8, 4, TEXT, true);
        g.method_51433(this.field_22793, this.field_29347.getString(), 8, this.field_2779 - 94, MUTED, false);
    }
}
