package org.cobbleutils.cobblecomputils.capture;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.*;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.minecraft.class_3222;

/**
 * Keeps moves taught by the Move Tutor. Remembers which moves each Pokemon was taught, snapshots the moveset when a battle starts,
 * and once the player is out of battle puts back any remembered move that vanished during the fight (and logs it).
 */
public class MoveGuard implements ModInitializer {
    static final File FILE = new File("config/cobblecomputils/tutored_moves.json");
    static final Map<UUID, Set<String>> TAUGHT = new HashMap<>();
    static final Map<UUID, Map<UUID, List<String>>> SNAPS = new HashMap<>();
    static boolean loaded = false;
    static int tick;

    @Override
    public void onInitialize() {
        try {
            Class<?> ev = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents");
            Object event = ev.getField("END_SERVER_TICK").get(null);
            Class<?> iface = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndTick");
            Object l = Proxy.newProxyInstance(MoveGuard.class.getClassLoader(), new Class<?>[]{iface}, (p, m, a) -> {
                if (m.getName().equals("onEndTick")) tick(a[0]);
                return null;
            });
            Class.forName("net.fabricmc.fabric.api.event.Event").getMethod("register", Object.class).invoke(event, l);
            System.out.println("[cobblecomputils] Tutor move guard active");
        } catch (Throwable t) { System.out.println("[cobblecomputils] Tutor move guard failed: " + t); }
    }

    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!FILE.exists()) return;
            JsonObject o = JsonParser.parseString(Files.readString(FILE.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                Set<String> s = new HashSet<>();
                e.getValue().getAsJsonArray().forEach(x -> s.add(x.getAsString()));
                TAUGHT.put(UUID.fromString(e.getKey()), s);
            }
        } catch (Throwable t) { System.out.println("[cobblecomputils] tutored_moves load failed: " + t); }
    }

    static synchronized void save() {
        try {
            JsonObject o = new JsonObject();
            for (Map.Entry<UUID, Set<String>> e : TAUGHT.entrySet()) {
                JsonArray a = new JsonArray();
                e.getValue().forEach(a::add);
                o.add(e.getKey().toString(), a);
            }
            FILE.getParentFile().mkdirs();
            Files.writeString(FILE.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
        } catch (Throwable t) { System.out.println("[cobblecomputils] tutored_moves save failed: " + t); }
    }

    /** Called by the Move Tutor right after a successful teach. */
    public static synchronized void record(Pokemon pokemon, MoveTemplate move) {
        try {
            load();
            TAUGHT.computeIfAbsent(pokemon.getUuid(), k -> new HashSet<>()).add(move.getName());
            save();
        } catch (Throwable ignored) { }
    }

    static List<String> names(Pokemon p) {
        List<String> l = new ArrayList<>();
        for (Move m : p.getMoveSet().getMoves()) l.add(m.getName());
        return l;
    }

    private static void tick(Object server) {
        if (++tick % 4 != 0) return;
        try {
            load();
            Object pm = server.getClass().getMethod("method_3760").invoke(server);
            List<?> players = (List<?>) pm.getClass().getMethod("method_14571").invoke(pm);
            for (Object o : new ArrayList<>(players)) {
                class_3222 pl = (class_3222) o;
                UUID id = pl.method_5667();
                boolean inBattle = BattleRegistry.getBattleByParticipatingPlayer(pl) != null;
                Map<UUID, List<String>> s = SNAPS.get(id);
                if (inBattle && s == null) {
                    Map<UUID, List<String>> m = new HashMap<>();
                    for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(pl)) m.put(p.getUuid(), names(p));
                    SNAPS.put(id, m);
                } else if (!inBattle && s != null) {
                    SNAPS.remove(id);
                    for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(pl)) restore(pl, p, s.get(p.getUuid()));
                }
            }
        } catch (Throwable t) { System.out.println("[cobblecomputils] tutor move guard tick error: " + t); }
    }

    static void restore(class_3222 pl, Pokemon p, List<String> before) {
        Set<String> taught = TAUGHT.get(p.getUuid());
        if (before == null || taught == null) return;
        for (String name : before) {
            if (!taught.contains(name)) continue;
            List<String> now = names(p);
            if (now.contains(name)) continue;
            MoveTemplate t = Moves.getByName(name);
            if (t == null) continue;
            boolean ok = false;
            if (p.getMoveSet().hasSpace()) {
                ok = p.getMoveSet().add(t.create());
            } else {
                for (String cur : now) {
                    if (before.contains(cur)) continue; // not the stray replacement
                    MoveTemplate old = Moves.getByName(cur);
                    if (old != null && p.exchangeMove(old, t)) { ok = true; break; }
                }
            }
            System.out.println("[cobblecomputils] Tutored move " + name + " vanished from " + p.getSpecies().getName() + " (" + pl.method_5477().getString() + ") during a battle; "
                + (ok ? "restored" : "could not restore") + ". Moves before: " + before + ", after: " + now);
        }
    }
}
