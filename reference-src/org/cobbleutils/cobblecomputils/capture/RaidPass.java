package org.cobbleutils.cobblecomputils.capture;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.class_2561;
import net.minecraft.class_3222;

/**
 * Tier 7 raid pass for the /rqueue lobby.
 *  - Every region clear (Base, Hard and Elite) earns one voucher. Players who cleared regions before this feature
 *    existed get their vouchers on their next login (the check runs for every online player every few seconds).
 *  - A voucher is activated from the phone's Gym menu and gives one real-time hour of access to the tier 7 queue.
 * State lives in config/cobblecomputils/raidpass.json.
 */
public final class RaidPass {
    private RaidPass() {}

    static final File FILE = new File("config/cobblecomputils/raidpass.json");
    static final long DURATION_MS = 60L * 60L * 1000L;
    static final String[] SUFFIX = {"", "_hard", "_elite"};
    static final String[] SUFFIX_NAME = {"", " (Hard)", " (Elite)"};

    static final class Entry {
        int vouchers;
        long expiry;
        Set<String> granted = new LinkedHashSet<>();
    }

    static final Map<String, Entry> DATA = new HashMap<>();
    static boolean loaded = false;
    static boolean dirty = false;
    static int tickN = 0;

    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!FILE.exists()) return;
            JsonObject root = JsonParser.parseString(new String(Files.readAllBytes(FILE.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Entry en = new Entry();
                en.vouchers = o.has("vouchers") ? o.get("vouchers").getAsInt() : 0;
                en.expiry = o.has("expiry") ? o.get("expiry").getAsLong() : 0L;
                if (o.has("granted")) for (JsonElement g : o.getAsJsonArray("granted")) en.granted.add(g.getAsString());
                DATA.put(e.getKey(), en);
            }
        } catch (Throwable t) {
            System.out.println("[cobblecomputils] raid pass load failed: " + t);
        }
    }

    static synchronized void save() {
        try {
            FILE.getParentFile().mkdirs();
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Entry> e : DATA.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("vouchers", e.getValue().vouchers);
                o.addProperty("expiry", e.getValue().expiry);
                JsonArray arr = new JsonArray();
                for (String g : e.getValue().granted) arr.add(g);
                o.add("granted", arr);
                root.add(e.getKey(), o);
            }
            Files.write(FILE.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
            dirty = false;
        } catch (Throwable t) {
            System.out.println("[cobblecomputils] raid pass save failed: " + t);
        }
    }

    static synchronized Entry entry(class_3222 pl) {
        load();
        return DATA.computeIfAbsent(pl.method_5667().toString(), k -> new Entry());
    }

    // ---------------------------------------------------------------- public API (also used by the patched raid queue)
    public static synchronized boolean active(class_3222 pl) {
        return entry(pl).expiry > System.currentTimeMillis();
    }

    public static synchronized long remainingMs(class_3222 pl) {
        return Math.max(0L, entry(pl).expiry - System.currentTimeMillis());
    }

    public static synchronized int vouchers(class_3222 pl) {
        return entry(pl).vouchers;
    }

    /** Starts the one hour timer. Returns false if there is no voucher or a pass is already running. */
    public static synchronized boolean activate(class_3222 pl) {
        Entry e = entry(pl);
        if (e.expiry > System.currentTimeMillis()) {
            pl.method_7353(class_2561.method_43470("§eYour Tier 7 Raid Pass is already active (" + fmt(e.expiry - System.currentTimeMillis()) + " left)."), false);
            return false;
        }
        if (e.vouchers <= 0) {
            pl.method_7353(class_2561.method_43470("§cYou have no Tier 7 Raid Pass vouchers. Clear a region to earn one."), false);
            return false;
        }
        e.vouchers--;
        e.expiry = System.currentTimeMillis() + DURATION_MS;
        save();
        pl.method_7353(class_2561.method_43470("§6★ Tier 7 Raid Pass active for 1 hour! §7Open /rqueue and pick the 7★ queue."), false);
        return true;
    }

    public static String fmt(long ms) {
        long s = Math.max(0L, ms / 1000L);
        return String.format("%d:%02d", s / 60L, s % 60L);
    }

    /** Called by the patched raid queue before a tier 7 join/open/launch. Returns true when the player must be refused. */
    public static boolean deny(class_3222 pl) {
        if (active(pl)) return false;
        pl.method_7353(class_2561.method_43470("§c✗ The 7★ raid queue needs an active Tier 7 Raid Pass."), false);
        pl.method_7353(class_2561.method_43470("§7Clear a region to earn one, then activate it in your phone's Gym menu."), false);
        return true;
    }

    // ---------------------------------------------------------------- granting (new clears and retroactive)
    /** Called from PpGui's server tick. */
    static void tick(net.minecraft.server.MinecraftServer server) {
        tickN++;
        if (tickN % 100 != 0) return; // every 5 seconds
        for (class_3222 pl : server.method_3760().method_14571()) {
            try { grant(pl); } catch (Throwable t) { if (tickN % 6000 == 0) System.out.println("[cobblecomputils] raid pass grant error: " + t); }
        }
        if (dirty) save();
    }

    static synchronized void grant(class_3222 pl) {
        Entry e = entry(pl);
        for (int r = 0; r < PpGui.REG.length; r++) {
            for (int t = 0; t < SUFFIX.length; t++) {
                String key = PpGui.REG[r] + SUFFIX[t];
                if (e.granted.contains(key)) continue;
                if (!PpGui.adv(pl, "region/" + key)) continue;
                e.granted.add(key);
                e.vouchers++;
                dirty = true;
                pl.method_7353(class_2561.method_43470("§6★ Tier 7 Raid Pass earned: §e" + PpGui.REGN[r] + SUFFIX_NAME[t] + " §cleared!"), false);
                pl.method_7353(class_2561.method_43470("§7Activate it in your phone's Gym menu (1 hour of 7★ raid queue)."), false);
            }
        }
    }
}
