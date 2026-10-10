package org.cobbleutils.cobblecomputils.client;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.trade.ModelWidget;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_332;

/** Draws a Pokemon's model (Cobblemon's own trade-screen widget) in a small clipped square. */
@Environment(EnvType.CLIENT)
public final class MonIcon {
    private static final Map<String, ModelWidget> CACHE = new HashMap<>();

    private MonIcon() { }

    /** Drop every cached model widget (called when a screen closes so nothing is held between uses). */
    public static void clear() { CACHE.clear(); }

    public static void draw(class_332 g, String sid, String aspects, int x, int y, int size, float rot, int mx, int my, float delta) {
        if (sid == null || sid.isEmpty()) return;
        try {
            String key = sid + "|" + aspects + "|" + size + "|" + rot;
            ModelWidget wd = CACHE.get(key);
            if (wd == null) {
                class_2960 id = class_2960.method_12829(sid);
                Species sp = id == null ? null : PokemonSpecies.INSTANCE.getByIdentifier(id);
                if (sp == null) return;
                Set<String> asp = new HashSet<>();
                for (String a : aspects.split(",")) if (!a.isEmpty()) asp.add(a);
                float k = size / 78.0F;
                wd = new ModelWidget(x, y, size, size, new RenderablePokemon(sp, asp, class_1799.field_8037), 2.0F * k, rot, -8.0 * k);
                if (CACHE.size() > 40) CACHE.clear();
                CACHE.put(key, wd);
            }
            wd.method_48229(x, y);
            g.method_44379(x, y, x + size, y + size);
            wd.method_25394(g, mx, my, delta);
            g.method_44380();
        } catch (Throwable t) {
            try { g.method_44380(); } catch (Throwable ignored) { }
        }
    }
}
