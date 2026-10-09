package org.cobbleutils.cobblecomputils.capture;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.Properties;

/**
 * Legendary capture lock: Pokemon whose species carries a locked label (default: "legendary",
 * which excludes "mythical" and "ultra_beast") cannot be caught until the player's RCT level
 * cap reaches minLevelCap (default 75). Loaded as a second "main" entrypoint (see fabric.mod.json).
 * Everything that touches Minecraft classes goes through reflection so it survives remapping.
 * Config: config/cobblecomputils/legendary_lock.properties
 */
public final class LegendaryLock implements net.fabricmc.api.ModInitializer {
    private static boolean enabled = true;
    private static int minLevelCap = 75;
    private static boolean returnBall = true;
    private static List<String> labels = new ArrayList<>(List.of("legendary"));
    private static List<String> exempt = new ArrayList<>();

    @Override
    public void onInitialize() {
        loadConfig();
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(Priority.LOWEST, LegendaryLock::onCalc);
        System.out.println("[cobblecomputils] Legendary lock: labels=" + labels + " until RCT level cap >= " + minLevelCap + " (enabled=" + enabled + ")");
    }

    private static void loadConfig() {
        File f = new File("config/cobblecomputils/legendary_lock.properties");
        Properties p = new Properties();
        try {
            if (f.exists()) {
                try (FileInputStream in = new FileInputStream(f)) { p.load(in); }
            } else {
                f.getParentFile().mkdirs();
                p.setProperty("enabled", "true");
                p.setProperty("minLevelCap", "75");
                p.setProperty("lockedLabels", "legendary");
                p.setProperty("exemptSpecies", "");
                p.setProperty("returnBall", "true");
                try (FileOutputStream out = new FileOutputStream(f)) {
                    p.store(out, "Legendary capture lock. lockedLabels: comma list of Cobblemon species labels (legendary, mythical, ultra_beast, paradox, restricted...). exemptSpecies: comma list of species ids that are never locked.");
                }
            }
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true").trim());
            minLevelCap = Integer.parseInt(p.getProperty("minLevelCap", "75").trim());
            returnBall = Boolean.parseBoolean(p.getProperty("returnBall", "true").trim());
            labels = split(p.getProperty("lockedLabels", "legendary"));
            exempt = split(p.getProperty("exemptSpecies", ""));
        } catch (Exception e) {
            System.out.println("[cobblecomputils] legendary_lock config problem, using defaults: " + e);
        }
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        for (String t : Arrays.asList(s.split(","))) if (!t.isBlank()) out.add(t.trim().toLowerCase());
        return out;
    }

    private static void onCalc(PokeBallCaptureCalculatedEvent ev) {
        if (!enabled) return;
        try {
            Object thrower = call(ev, "getThrower");
            Class<?> sp = Class.forName("net.minecraft.class_3222");
            if (thrower == null || !sp.isInstance(thrower)) return;
            Object pokemon = call(call(ev, "getPokemonEntity"), "getPokemon");
            Object species = call(pokemon, "getSpecies");
            Collection<?> lbl = (Collection<?>) call(species, "getLabels");
            boolean locked = false;
            for (Object o : lbl) if (labels.contains(String.valueOf(o).toLowerCase())) { locked = true; break; }
            if (!locked) return;
            String name = String.valueOf(call(species, "getName")).toLowerCase();
            if (exempt.contains(name)) return;

            OptionalInt cap = (OptionalInt) Class.forName("org.cobbleutils.cobblecomputils.integration.RctBridge")
                    .getMethod("levelCap", sp).invoke(null, thrower);
            if (cap.isEmpty()) return;                       // RCT missing / no data: don't lock
            int level = (Integer) call(pokemon, "getLevel");
            if (level > cap.getAsInt()) return;              // the level-cap check already blocks this one
            if (cap.getAsInt() >= minLevelCap) return;       // unlocked

            ev.setCaptureResult(new CaptureContext(0, false, false));

            Class<?> comp = Class.forName("net.minecraft.class_2561");
            Object msg = comp.getMethod("method_43470", String.class).invoke(null,
                    "§cThe legendary " + call(call(species, "getTranslatedName"), "getString")
                    + " resists capture until your level cap reaches " + minLevelCap + " (now " + cap.getAsInt() + ").");
            sp.getMethod("method_7353", comp, boolean.class).invoke(thrower, msg, false);

            boolean creative = (Boolean) sp.getMethod("method_7337").invoke(thrower);
            if (returnBall && !creative) {
                Object item = call(call(call(ev, "getPokeBallEntity"), "getPokeBall"), "item");
                Class<?> stackC = Class.forName("net.minecraft.class_1799");
                Object stack = stackC.getConstructor(Class.forName("net.minecraft.class_1935")).newInstance(item);
                Object inv = sp.getMethod("method_31548").invoke(thrower);
                inv.getClass().getMethod("method_7398", stackC).invoke(inv, stack);
            }
        } catch (Throwable t) {
            System.out.println("[cobblecomputils] legendary lock error: " + t);
        }
    }

    private static Object call(Object o, String m) throws Exception {
        Method me = null;
        for (Method x : o.getClass().getMethods()) if (x.getName().equals(m) && x.getParameterCount() == 0) { me = x; break; }
        if (me == null) throw new NoSuchMethodException(o.getClass() + "." + m);
        me.setAccessible(true);
        return me.invoke(o);
    }
}
