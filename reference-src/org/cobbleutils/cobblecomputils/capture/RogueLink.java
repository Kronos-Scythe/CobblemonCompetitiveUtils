package org.cobbleutils.cobblecomputils.capture;

import com.google.gson.*;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Links the CobbleRogueLike mod with the Gym Challenge and keeps a run history.
 *  - Finished rogue run  -> Badge Points (scoreboard pp_bp) for the gym shop.
 *  - Gym progress (rank up) -> Rogue Tokens (RunManager.changeTokens) for the rogue shop.
 *  - Every finished run is stored in config/cobblecomputils/run_history.json, posted to an optional second
 *    Discord webhook, and shown with /runhistory.
 * Everything is reflection based so the rogue mod stays an optional dependency.
 */
public final class RogueLink implements net.fabricmc.api.ModInitializer {
    static final File DIR = new File("config/cobblecomputils");
    static final File CFG = new File(DIR, "rogue_link.properties");
    static final File HIST = new File(DIR, "run_history.json");
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "run-history"); t.setDaemon(true); return t; });

    static boolean enabled = true, announce = true;
    static String webhook = "";
    static boolean rivalOn = true;
    static int rivalFrom = 4, rivalRunChance = 100, rivalTokens = 25, rivalBp = 4, rivalGymChance = 35, rivalCooldown = 15;
    static final Random RND = new Random();
    static final Map<UUID, Long> rivalCd = new HashMap<>();
    static final Map<UUID, JsonObject> rivalOffer = new HashMap<>();
    static int bpBadge = 3, bpElite = 5, bpWin = 40, bpFloors5 = 1, gymTokens = 10, keep = 200;

    static final class Snap {
        UUID owner; List<UUID> members = new ArrayList<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        int floor, badges, elite, money, wins0, runs0, tokens0;
        Set<String> mods = new LinkedHashSet<>();
        String biome = "";
        List<String> party = new ArrayList<>();
        List<String> sets = new ArrayList<>();
        boolean rivalDone, rivalPending; int rivalFloor; String rivalName = "", rivalRolled = "";
        long startMs = System.currentTimeMillis();
    }
    static final Map<UUID, Snap> snaps = new HashMap<>();
    static final List<Object[]> pending = new ArrayList<>(); // {Snap, Integer ticksLeft}
    static int tickN = 0;
    static Object lastServer;
    static final Object LOCK = new Object();

    // ------------------------------------------------------------------ init
    @Override
    public void onInitialize() {
        loadConfig();
        if (!enabled) return;
        try {
            Class<?> ev = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents");
            Object event = ev.getField("END_SERVER_TICK").get(null);
            Class<?> iface = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndTick");
            Object listener = Proxy.newProxyInstance(RogueLink.class.getClassLoader(), new Class<?>[]{iface}, (p, m, a) -> {
                if (m.getName().equals("onEndTick")) {
                    try { tick(a[0]); } catch (Throwable t) { if (tickN % 1200 == 0) System.out.println("[cobblecomputils] rogue link error: " + t); }
                }
                return null;
            });
            Class<?> eventBase = Class.forName("net.fabricmc.fabric.api.event.Event");
            eventBase.getMethod("register", Object.class).invoke(event, listener);
            Class<?> cr = Class.forName("net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback");
            Object cev = cr.getField("EVENT").get(null);
            Object cl = Proxy.newProxyInstance(RogueLink.class.getClassLoader(), new Class<?>[]{cr}, (p, m, a) -> {
                if (m.getName().equals("register")) registerCommand(a[0]);
                return null;
            });
            eventBase.getMethod("register", Object.class).invoke(cev, cl);
            System.out.println("[cobblecomputils] Rogue link active (run-history webhook " + (webhook.isEmpty() ? "not set" : "set") + ")");
        } catch (Throwable t) {
            System.out.println("[cobblecomputils] Rogue link could not start: " + t);
        }
    }

    static void loadConfig() {
        try {
            DIR.mkdirs();
            Properties p = new Properties();
            if (!CFG.exists()) {
                p.setProperty("enabled", "true");
                p.setProperty("webhook_url", "");
                p.setProperty("chat_announce", "true");
                p.setProperty("bp_per_badge", "3");
                p.setProperty("bp_per_elite_win", "5");
                p.setProperty("bp_win_bonus", "40");
                p.setProperty("bp_per_5_floors", "1");
                p.setProperty("gym_rank_tokens", "10");
                p.setProperty("history_keep", "200");
                try (OutputStream o = new FileOutputStream(CFG)) {
                    p.store(o, "Rogue <-> Gym link. webhook_url: Discord webhook for run history (separate from the team board). "
                            + "bp_*: Badge Points granted when a rogue run ends (badges*bp_per_badge + elite wins*bp_per_elite_win + floors/5*bp_per_5_floors + win bonus). "
                            + "gym_rank_tokens: Rogue Tokens granted each time a player advances one step in the gym ladder.");
                }
            }
            try (InputStream in = new FileInputStream(CFG)) { p.load(in); }
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true").trim());
            announce = Boolean.parseBoolean(p.getProperty("chat_announce", "true").trim());
            webhook = p.getProperty("webhook_url", "").trim().split("\\?")[0];
            bpBadge = num(p, "bp_per_badge", 3); bpElite = num(p, "bp_per_elite_win", 5); bpWin = num(p, "bp_win_bonus", 40);
            bpFloors5 = num(p, "bp_per_5_floors", 1); gymTokens = num(p, "gym_rank_tokens", 10); keep = Math.max(20, num(p, "history_keep", 200));
            rivalOn = Boolean.parseBoolean(p.getProperty("rival_enabled", "true").trim());
            rivalFrom = num(p, "rival_from_badge", 4); rivalRunChance = num(p, "rival_run_chance", 100); rivalTokens = num(p, "rival_win_tokens", 25);
            rivalBp = num(p, "rival_win_bp", 4); rivalGymChance = num(p, "rival_gym_chance", 35); rivalCooldown = num(p, "rival_cooldown_min", 15);
        } catch (Throwable t) { System.out.println("[cobblecomputils] rogue link config error: " + t); }
    }

    static int num(Properties p, String k, int d) { try { return Integer.parseInt(p.getProperty(k, "" + d).trim()); } catch (Exception e) { return d; } }

    // ------------------------------------------------------------------ reflection helpers
    static Object field(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    static int intField(Object o, String name) { try { return ((Number) field(o, name)).intValue(); } catch (Throwable t) { return 0; } }

    static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Method m = c.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(o, args); } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    /** True while this player is the owner or a member of an active CobbleRogueLike run. */
    @SuppressWarnings("unchecked")
    static boolean inRun(UUID id) {
        try {
            Object rm = runManager();
            if (rm == null) return false;
            Map<UUID, Object> active = (Map<UUID, Object>) field(rm, "active");
            for (Object st : new ArrayList<>(active.values())) {
                if (id.equals(field(st, "playerId"))) return true;
                try { if (((List<UUID>) st.getClass().getMethod("members").invoke(st)).contains(id)) return true; } catch (Throwable ignored) { }
            }
        } catch (Throwable t) { }
        return false;
    }

    static Object runManager() {
        try { return Class.forName("org.CobbleUtils.cobbleroguelike.run.RunManager").getMethod("get").invoke(null); } catch (Throwable t) { return null; }
    }

    static int profileInt(Object rm, UUID id, String key) {
        try {
            Object storage = field(rm, "storage");
            Object nbt = call(storage, "readProfile", new Class<?>[]{UUID.class}, id);
            return nbt == null ? 0 : (Integer) nbt.getClass().getMethod("method_10550", String.class).invoke(nbt, key);
        } catch (Throwable t) { return 0; }
    }

    static Object playerByUuid(Object server, UUID id) {
        try {
            Object pm = server.getClass().getMethod("method_3760").invoke(server);
            return pm.getClass().getMethod("method_14602", UUID.class).invoke(pm, id);
        } catch (Throwable t) { return null; }
    }

    static String playerName(Object pl) {
        try { Object c = pl.getClass().getMethod("method_5477").invoke(pl); return String.valueOf(c.getClass().getMethod("getString").invoke(c)); } catch (Throwable t) { return null; }
    }

    static void cmd(Object server, String c) {
        try {
            Object cmds = server.getClass().getMethod("method_3734").invoke(server);
            Object src = server.getClass().getMethod("method_3739").invoke(server);
            cmds.getClass().getMethod("method_44252", Class.forName("net.minecraft.class_2168"), String.class).invoke(cmds, src, c);
        } catch (Throwable t) { System.out.println("[cobblecomputils] rogue link command failed: " + c + " -> " + t); }
    }

    static void tell(Object server, String name, String text, String color) {
        cmd(server, "tellraw " + name + " " + jsonText(text, color));
    }

    static String jsonText(String text, String color) {
        JsonObject o = new JsonObject(); o.addProperty("text", text); o.addProperty("color", color);
        return o.toString();
    }

    @SuppressWarnings("unchecked")
    static List<String> partyOf(Object pl) {
        List<String> out = new ArrayList<>();
        try {
            Class<?> cob = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object inst = cob.getField("INSTANCE").get(null);
            Object storage = cob.getMethod("getStorage").invoke(inst);
            Method gp = null;
            for (Method m : storage.getClass().getMethods()) {
                if (m.getName().equals("getParty") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(pl)) { gp = m; break; }
            }
            if (gp == null) return out;
            Object party = gp.invoke(storage, pl);
            for (Object pk : (Iterable<Object>) party) {
                Object species = pk.getClass().getMethod("getSpecies").invoke(pk);
                String sp = String.valueOf(species.getClass().getMethod("getName").invoke(species));
                int lv = ((Number) pk.getClass().getMethod("getLevel").invoke(pk)).intValue();
                boolean shiny = false;
                try { shiny = (Boolean) pk.getClass().getMethod("getShiny").invoke(pk); } catch (Throwable ignored) { }
                out.add((shiny ? "⭐ " : "") + sp + " Lv." + lv);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    // ------------------------------------------------------------------ tick: watch active rogue runs
    @SuppressWarnings("unchecked")
    static void tick(Object server) throws Exception {
        lastServer = server;
        tickN++;
        if (tickN % 5 == 0) { try { rivalScan(server); } catch (Throwable t) { if (tickN % 1200 == 0) System.out.println("[cobblecomputils] rival scan error: " + t); } }
        if (tickN % 20 != 0) return;
        Object rm = runManager();
        if (rm == null) return;
        Map<UUID, Object> active = (Map<UUID, Object>) field(rm, "active");
        Set<UUID> liveOwners = new HashSet<>();
        for (Object st : new ArrayList<>(active.values())) {
            UUID owner = (UUID) field(st, "playerId");
            if (!liveOwners.add(owner)) continue;
            Snap s = snaps.get(owner);
            if (s == null) {
                s = new Snap(); s.owner = owner;
                s.runs0 = profileInt(rm, owner, "runs"); s.wins0 = profileInt(rm, owner, "wins"); s.tokens0 = profileInt(rm, owner, "tokens");
                snaps.put(owner, s);
            }
            s.floor = intField(st, "floor"); s.badges = intField(st, "badges"); s.elite = intField(st, "eliteWins"); s.money = intField(st, "money");
            try { s.biome = String.valueOf(field(st, "biome")); } catch (Throwable ignored) { }
            try { s.mods = new LinkedHashSet<>((Set<String>) field(st, "modifiers")); } catch (Throwable ignored) { }
            List<UUID> members = new ArrayList<>();
            try { members = (List<UUID>) st.getClass().getMethod("members").invoke(st); } catch (Throwable ignored) { members.add(owner); }
            s.members = new ArrayList<>(members);
            List<String> party = new ArrayList<>();
            for (UUID u : members) {
                Object pl = playerByUuid(server, u);
                if (pl == null) continue;
                String n = playerName(pl); if (n != null) s.names.put(u, n);
                if (u.equals(owner)) { List<String> ss = setsOf(pl); if (!ss.isEmpty()) s.sets = ss; }
                if (u.equals(owner) || party.isEmpty()) { List<String> pp = partyOf(pl); if (!pp.isEmpty() && u.equals(owner)) party = pp; else if (party.isEmpty()) party = pp; }
            }
            if (!party.isEmpty()) s.party = party;
        }
        // runs that disappeared -> resolve after a short delay (profile is written during the end of the run)
        for (Iterator<Map.Entry<UUID, Snap>> it = snaps.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Snap> e = it.next();
            if (!liveOwners.contains(e.getKey())) { pending.add(new Object[]{e.getValue(), 3}); it.remove(); }
        }
        for (Iterator<Object[]> it = pending.iterator(); it.hasNext(); ) {
            Object[] p = it.next();
            int left = (Integer) p[1] - 1;
            if (left > 0) { p[1] = left; continue; }
            it.remove();
            try { resolve(server, rm, (Snap) p[0]); } catch (Throwable t) { System.out.println("[cobblecomputils] run resolve error: " + t); }
        }
    }

    static void resolve(Object server, Object rm, Snap s) {
        int runsNow = profileInt(rm, s.owner, "runs"), winsNow = profileInt(rm, s.owner, "wins"), tokNow = profileInt(rm, s.owner, "tokens");
        if (runsNow <= s.runs0) return; // paused/saved or abandoned without payout: nothing to record
        boolean won = winsNow > s.wins0;
        int tokens = Math.max(0, tokNow - s.tokens0);
        int bp = s.badges * bpBadge + s.elite * bpElite + (s.floor / 5) * bpFloors5 + (won ? bpWin : 0);
        List<String> names = new ArrayList<>();
        for (UUID u : s.members.isEmpty() ? List.of(s.owner) : s.members) {
            String n = s.names.get(u);
            if (n == null) { Object pl = playerByUuid(server, u); if (pl != null) n = playerName(pl); }
            if (n == null) n = "Unknown";
            names.add(n);
        }
        // Badge Points for the gym shop
        if (bp > 0) {
            for (String n : names) {
                if (n.equals("Unknown")) continue;
                cmd(server, "scoreboard players add " + n + " pp_bp " + bp);
                tell(server, n, "[Gym link] +" + bp + " Badge Points for your rogue run (" + s.badges + " badges, floor " + s.floor + (won ? ", CLEARED" : "") + ").", "gold");
            }
        }
        if (won && announce) {
            cmd(server, "tellraw @a " + jsonText(String.join(" & ", names) + " cleared a rogue run (" + s.badges + " badges, floor " + s.floor + ")!", "green"));
        }
        JsonObject rec = new JsonObject();
        rec.addProperty("time", Instant.now().toString());
        JsonArray pa = new JsonArray(); names.forEach(pa::add); rec.add("players", pa);
        rec.addProperty("won", won); rec.addProperty("floor", s.floor); rec.addProperty("badges", s.badges); rec.addProperty("elite", s.elite);
        rec.addProperty("tokens", tokens); rec.addProperty("bp", bp); rec.addProperty("biome", s.biome);
        rec.addProperty("minutes", Math.max(1, (System.currentTimeMillis() - s.startMs) / 60000));
        JsonArray ma = new JsonArray(); s.mods.forEach(ma::add); rec.add("modifiers", ma);
        JsonArray pt = new JsonArray(); s.party.forEach(pt::add); rec.add("party", pt);
        if (won && !s.sets.isEmpty()) { JsonArray sa = new JsonArray(); s.sets.forEach(sa::add); rec.add("sets", sa); }
        POOL.submit(() -> { store(rec); post(rec); });
    }

    // ------------------------------------------------------------------ gym -> rogue
    /** Called by GymBoard when a player's gym ladder rank goes up. */
    static void onGymProgress(Object server, Object player, String name, int oldRank, int newRank) {
        if (!enabled || gymTokens <= 0 || oldRank <= 0 || newRank <= oldRank) return;
        try {
            Object rm = runManager();
            if (rm == null) return;
            Boolean ok = (Boolean) rm.getClass().getMethod("changeTokens", Class.forName("net.minecraft.class_3222"), int.class).invoke(rm, player, gymTokens);
            if (ok != null && ok) tell(server, name, "[Gym link] +" + gymTokens + " Rogue Tokens for advancing in the gym challenge.", "aqua");
            offerRival(server, player, name);
        } catch (Throwable t) { System.out.println("[cobblecomputils] gym->rogue tokens failed: " + t); }
    }

    // ------------------------------------------------------------------ history store / webhook
    static void store(JsonObject rec) {
        synchronized (LOCK) {
            try {
                JsonArray arr = new JsonArray();
                if (HIST.exists()) arr = JsonParser.parseString(Files.readString(HIST.toPath(), StandardCharsets.UTF_8)).getAsJsonArray();
                arr.add(rec);
                while (arr.size() > keep) arr.remove(0);
                DIR.mkdirs();
                Files.writeString(HIST.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(arr), StandardCharsets.UTF_8);
            } catch (Throwable t) { System.out.println("[cobblecomputils] run history save failed: " + t); }
        }
    }

    static void post(JsonObject rec) {
        if (webhook.isEmpty()) return;
        try {
            boolean won = rec.get("won").getAsBoolean();
            List<String> names = new ArrayList<>(); rec.getAsJsonArray("players").forEach(e -> names.add(e.getAsString()));
            JsonObject embed = new JsonObject();
            embed.addProperty("title", won ? "🏆 Rogue run cleared!" : "💀 Rogue run ended");
            embed.addProperty("description", "**" + String.join("** & **", names) + "**");
            embed.addProperty("color", won ? 0x57F287 : 0xED4245);
            JsonArray fields = new JsonArray();
            fields.add(field("Floor", String.valueOf(rec.get("floor").getAsInt()), true));
            fields.add(field("Badges", String.valueOf(rec.get("badges").getAsInt()), true));
            fields.add(field("Elite wins", String.valueOf(rec.get("elite").getAsInt()), true));
            fields.add(field("Rogue Tokens", "+" + rec.get("tokens").getAsInt(), true));
            fields.add(field("Gym Badge Points", "+" + rec.get("bp").getAsInt(), true));
            fields.add(field("Time", rec.get("minutes").getAsLong() + " min", true));
            List<String> party = new ArrayList<>(); rec.getAsJsonArray("party").forEach(e -> party.add(e.getAsString()));
            if (!party.isEmpty()) fields.add(field("Final team", String.join("\n", party), false));
            List<String> mods = new ArrayList<>(); rec.getAsJsonArray("modifiers").forEach(e -> mods.add(e.getAsString()));
            if (!mods.isEmpty()) fields.add(field("Modifiers", String.join(", ", mods), false));
            embed.add("fields", fields);
            if (!names.isEmpty() && names.get(0).matches("[A-Za-z0-9_]{1,16}")) {
                JsonObject th = new JsonObject(); th.addProperty("url", "https://mc-heads.net/avatar/" + names.get(0) + "/64"); embed.add("thumbnail", th);
            }
            embed.addProperty("timestamp", rec.get("time").getAsString());
            JsonObject footer = new JsonObject(); footer.addProperty("text", "Potential Pack • Run History"); embed.add("footer", footer);
            JsonArray embeds = new JsonArray(); embeds.add(embed);
            JsonObject payload = new JsonObject(); payload.add("embeds", embeds);
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(webhook)).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json").header("User-Agent", "cobblemon-run-history/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) System.out.println("[cobblecomputils] run history webhook HTTP " + r.statusCode() + ": " + r.body());
        } catch (Throwable t) { System.out.println("[cobblecomputils] run history post failed: " + t); }
    }

    static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject(); f.addProperty("name", name); f.addProperty("value", value.isEmpty() ? "-" : value); f.addProperty("inline", inline); return f;
    }

    // ------------------------------------------------------------------ /runhistory
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void registerCommand(Object dispatcherObj) {
        CommandDispatcher<Object> d = (CommandDispatcher<Object>) dispatcherObj;
        LiteralArgumentBuilder<Object> all = LiteralArgumentBuilder.<Object>literal("all");
        all.executes((Command<Object>) ctx -> { show(ctx.getSource(), null); return 1; });
        LiteralArgumentBuilder<Object> root = LiteralArgumentBuilder.<Object>literal("runhistory");
        root.executes((Command<Object>) ctx -> {
            String me = null;
            try { Object pl = ctx.getSource().getClass().getMethod("method_9207").invoke(ctx.getSource()); if (pl != null) me = playerName(pl); } catch (Throwable ignored) { }
            show(ctx.getSource(), me);
            return 1;
        });
        root.then((LiteralArgumentBuilder<Object>) all);
        d.register(root);
        LiteralArgumentBuilder<Object> rv = LiteralArgumentBuilder.<Object>literal("rival");
        rv.executes((Command<Object>) ctx -> { rivalCommand(ctx.getSource()); return 1; });
        d.register(rv);
    }

    static void feedback(Object src, String msg) {
        try {
            Object t = Class.forName("net.minecraft.class_2561").getMethod("method_43470", String.class).invoke(null, msg);
            java.util.function.Supplier<Object> sup = () -> t;
            src.getClass().getMethod("method_9226", java.util.function.Supplier.class, boolean.class).invoke(src, sup, false);
        } catch (Throwable ignored) { System.out.println("[cobblecomputils] " + msg); }
    }

    static void show(Object src, String who) {
        List<String> lines = new ArrayList<>();
        synchronized (LOCK) {
            try {
                if (HIST.exists()) {
                    JsonArray arr = JsonParser.parseString(Files.readString(HIST.toPath(), StandardCharsets.UTF_8)).getAsJsonArray();
                    for (int i = arr.size() - 1; i >= 0 && lines.size() < 6; i--) {
                        JsonObject o = arr.get(i).getAsJsonObject();
                        List<String> names = new ArrayList<>(); o.getAsJsonArray("players").forEach(e -> names.add(e.getAsString()));
                        if (who != null && names.stream().noneMatch(n -> n.equalsIgnoreCase(who))) continue;
                        lines.add((o.get("won").getAsBoolean() ? "[WIN] " : "[END] ") + String.join(" & ", names) + " - floor " + o.get("floor").getAsInt()
                                + ", " + o.get("badges").getAsInt() + " badges, +" + o.get("tokens").getAsInt() + " tokens, +" + o.get("bp").getAsInt() + " BP ("
                                + o.get("time").getAsString().substring(0, 10) + ")");
                    }
                }
            } catch (Throwable t) { lines.add("Could not read run history: " + t); }
        }
        if (lines.isEmpty()) { feedback(src, who == null ? "No finished rogue runs recorded yet." : "No finished rogue runs recorded for you yet."); return; }
        feedback(src, who == null ? "Recent rogue runs:" : "Your recent rogue runs:");
        for (String l : lines) feedback(src, l);
    }

    // ------------------------------------------------------------------ rival: full sets of winning teams
    static final String BR = "org.CobbleUtils.cobbleroguelike.compat.";

    @SuppressWarnings("unchecked")
    static Iterable<Object> partyIterable(Object pl) {
        try {
            Class<?> cob = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object inst = cob.getField("INSTANCE").get(null);
            Object storage = cob.getMethod("getStorage").invoke(inst);
            for (Method m : storage.getClass().getMethods()) {
                if (m.getName().equals("getParty") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(pl)) return (Iterable<Object>) m.invoke(storage, pl);
            }
        } catch (Throwable ignored) { }
        return List.of();
    }

    static String afterColon(Object o) { String t = String.valueOf(o); int i = t.lastIndexOf(':'); return (i >= 0 ? t.substring(i + 1) : t).trim().toLowerCase(Locale.ROOT); }

    /** Full property strings (species, level, nature, ability, moves, held item) for every Pokemon in the party. */
    static List<String> setsOf(Object pl) {
        List<String> out = new ArrayList<>();
        try {
            for (Object pk : partyIterable(pl)) {
                try {
                    Object species = pk.getClass().getMethod("getSpecies").invoke(pk);
                    String sp;
                    try {
                        Class<?> br = Class.forName(BR + "CobblemonBridge");
                        Method pid = null;
                        for (Method m : br.getMethods()) if (m.getName().equals("propertyId") && m.getParameterCount() == 1) pid = m;
                        sp = String.valueOf(pid.invoke(null, species));
                    } catch (Throwable t) { sp = String.valueOf(species.getClass().getMethod("getName").invoke(species)).toLowerCase(Locale.ROOT); }
                    int lv = ((Number) pk.getClass().getMethod("getLevel").invoke(pk)).intValue();
                    StringBuilder sb = new StringBuilder(sp).append(" level=").append(lv);
                    try { if ((Boolean) pk.getClass().getMethod("getShiny").invoke(pk)) sb.append(" shiny=yes"); } catch (Throwable ignored) { }
                    try { Object n = pk.getClass().getMethod("getNature").invoke(pk); sb.append(" nature=").append(afterColon(n.getClass().getMethod("getName").invoke(n))); } catch (Throwable ignored) { }
                    try { Object a = pk.getClass().getMethod("getAbility").invoke(pk); sb.append(" ability=").append(afterColon(a.getClass().getMethod("getName").invoke(a))); } catch (Throwable ignored) { }
                    try {
                        Object ms = pk.getClass().getMethod("getMoveSet").invoke(pk);
                        List<String> mv = new ArrayList<>();
                        for (Object m : (Iterable<Object>) ms.getClass().getMethod("getMoves").invoke(ms)) mv.add(afterColon(m.getClass().getMethod("getName").invoke(m)));
                        if (!mv.isEmpty()) sb.append(" moves=").append(String.join(",", mv));
                    } catch (Throwable ignored) { }
                    try {
                        Object stack = pk.getClass().getMethod("heldItem").invoke(pk);
                        boolean empty = (Boolean) stack.getClass().getMethod("method_7960").invoke(stack);
                        if (!empty) {
                            String t = String.valueOf(stack).trim(); int i = t.lastIndexOf(' ');
                            if (i >= 0) t = t.substring(i + 1);
                            if (t.contains(":")) sb.append(" held_item=").append(t);
                        }
                    } catch (Throwable ignored) { }
                    try {
                        Object f = pk.getClass().getMethod("getForm").invoke(pk);
                        String fn = String.valueOf(f.getClass().getMethod("getName").invoke(f)).toLowerCase(Locale.ROOT);
                        if (!fn.isEmpty() && !fn.equals("normal") && !fn.contains("mega") && !fn.contains("gmax") && !fn.contains("tera") && fn.matches("[a-z0-9_-]+")) sb.append(" form=").append(fn);
                    } catch (Throwable ignored) { }
                    out.add(sb.toString());
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        return out;
    }

    static String withLevel(String set, int level) {
        StringBuilder r = new StringBuilder();
        for (String part : set.split(" ")) {
            if (part.startsWith("level=")) part = "level=" + level;
            r.append(r.length() == 0 ? "" : " ").append(part);
        }
        return r.toString();
    }

    static int levelIn(String set) {
        for (String part : set.split(" ")) if (part.startsWith("level=")) { try { return Integer.parseInt(part.substring(6)); } catch (Exception e) { return 50; } }
        return 50;
    }

    /** Random winning run with stored sets, not owned by `exclude` (case-insensitive names). */
    static JsonObject pickRival(Collection<String> exclude) {
        List<JsonObject> ok = new ArrayList<>();
        synchronized (LOCK) {
            try {
                if (!HIST.exists()) return null;
                JsonArray arr = JsonParser.parseString(Files.readString(HIST.toPath(), StandardCharsets.UTF_8)).getAsJsonArray();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    if (!o.has("won") || !o.get("won").getAsBoolean() || !o.has("sets") || o.getAsJsonArray("sets").size() < 2) continue;
                    boolean mine = false;
                    for (JsonElement n : o.getAsJsonArray("players")) for (String x : exclude) if (n.getAsString().equalsIgnoreCase(x)) mine = true;
                    if (!mine) ok.add(o);
                }
            } catch (Throwable t) { System.out.println("[cobblecomputils] rival pick failed: " + t); }
        }
        return ok.isEmpty() ? null : ok.get(RND.nextInt(ok.size()));
    }

    static String rivalLabel(JsonObject rec) {
        List<String> names = new ArrayList<>(); rec.getAsJsonArray("players").forEach(e -> names.add(e.getAsString()));
        return String.join(" & ", names);
    }

    static List<String> rivalSets(JsonObject rec) { List<String> l = new ArrayList<>(); rec.getAsJsonArray("sets").forEach(e -> l.add(e.getAsString())); return l; }

    // ---- inside rogue runs: swap one trainer after the halfway point for a past winner's team
    @SuppressWarnings("unchecked")
    static void rivalScan(Object server) throws Exception {
        if (!rivalOn) return;
        Object rm = runManager();
        if (rm == null) return;
        Map<UUID, Object> active = (Map<UUID, Object>) field(rm, "active");
        for (Object st : new ArrayList<>(active.values())) {
            UUID owner = (UUID) field(st, "playerId");
            Snap s = snaps.get(owner);
            if (s == null) continue;
            String ph = String.valueOf(field(st, "phase"));
            Object pl = playerByUuid(server, owner);
            if (pl == null) continue;
            if (s.rivalPending && !ph.equals("BATTLE")) {
                s.rivalPending = false;
                if (intField(st, "floor") > s.rivalFloor) {
                    String name = playerName(pl);
                    try { rm.getClass().getMethod("changeTokens", Class.forName("net.minecraft.class_3222"), int.class).invoke(rm, pl, rivalTokens); } catch (Throwable ignored) { }
                    if (name != null) {
                        if (rivalBp > 0) cmd(server, "scoreboard players add " + name + " pp_bp " + rivalBp);
                        tell(server, name, "[Rival] You beat " + s.rivalName + "'s champion team! +" + rivalTokens + " Rogue Tokens, +" + rivalBp + " Badge Points.", "gold");
                    }
                }
                continue;
            }
            if (s.rivalDone || !ph.equals("BATTLE")) continue;
            if ((Boolean) st.getClass().getMethod("isCoop").invoke(st)) continue;
            if (!String.valueOf(field(st, "battleKind")).equals("TRAINER")) continue;
            if (intField(st, "badges") < rivalFrom) continue;
            try {
                Class<?> br = Class.forName(BR + "CobblemonBridge");
                Method inb = br.getMethod("isInBattle", Class.forName("net.minecraft.class_3222"));
                if ((Boolean) inb.invoke(null, pl)) continue;
            } catch (Throwable ignored) { }
            String key = intField(st, "floor") + ":" + field(st, "battleName");
            if (key.equals(s.rivalRolled)) continue;
            s.rivalRolled = key;
            if (RND.nextInt(100) >= rivalRunChance) continue;
            String me = playerName(pl);
            JsonObject rec = pickRival(me == null ? List.of() : List.of(me));
            if (rec == null) { s.rivalDone = true; continue; }
            List<String> sets = rivalSets(rec);
            List<String> existing = (List<String>) field(st, "battleTeam");
            int top = 5; for (String e : existing) top = Math.max(top, levelIn(e));
            int n = Math.max(2, Math.min(Math.min(6, sets.size()), existing.size() + 1));
            Collections.shuffle(sets, RND);
            List<String> team = new ArrayList<>();
            for (int i = 0; i < n; i++) { String set = sets.get(i); team.add(withLevel(set, Math.max(5, Math.min(levelIn(set), top + 1)))); }
            setField(st, "battleTeam", team);
            setField(st, "battleName", "Rival " + rivalLabel(rec));
            setField(st, "battleSkill", 5);
            setField(st, "battleType", "");
            if (!(Boolean) field(st, "battleDoubles") || n < 2) setField(st, "battleDoubles", false);
            s.rivalDone = true; s.rivalPending = true; s.rivalFloor = intField(st, "floor"); s.rivalName = rivalLabel(rec);
            try { rm.getClass().getMethod("openCurrent", Class.forName("net.minecraft.class_3222")).invoke(rm, pl); } catch (Throwable ignored) { }
            if (me != null) tell(server, me, "[Rival] " + s.rivalName + "'s champion team blocks the road! Beat them for bonus tokens.", "red");
        }
    }

    static void setField(Object o, String name, Object value) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); f.set(o, value); return; } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    // ---- gyms / anywhere: /rival fights a past winner's team (scaled to the challenger), offered after gym progress
    static void offerRival(Object server, Object player, String name) {
        if (!rivalOn || rivalGymChance <= 0 || RND.nextInt(100) >= rivalGymChance) return;
        try {
            JsonObject rec = pickRival(List.of(name));
            if (rec == null) return;
            UUID id = (UUID) player.getClass().getMethod("method_5667").invoke(player);
            rivalOffer.put(id, rec);
            cmd(server, "tellraw " + name + " [{\"text\":\"[Rival] \",\"color\":\"gold\"},{\"text\":\"" + rivalLabel(rec).replace("\"", "") + "'s champion team wants a battle! \",\"color\":\"yellow\"},"
                    + "{\"text\":\"[Fight]\",\"color\":\"red\",\"bold\":true,\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/rival\"}}]");
        } catch (Throwable t) { System.out.println("[cobblecomputils] rival offer failed: " + t); }
    }

    @SuppressWarnings("unchecked")
    static void rivalCommand(Object src) {
        try {
            Object pl = src.getClass().getMethod("method_9207").invoke(src);
            if (pl == null) { feedback(src, "Only players can fight a rival."); return; }
            if (!rivalOn) { feedback(src, "Rival battles are disabled."); return; }
            Object server = lastServer;
            String name = playerName(pl);
            UUID id = (UUID) pl.getClass().getMethod("method_5667").invoke(pl);
            Object rm = runManager();
            if (rm != null && ((Map<UUID, Object>) field(rm, "active")).containsKey(id)) { feedback(src, "Finish or leave your rogue run first - rivals also appear inside runs after badge " + rivalFrom + "."); return; }
            long now = System.currentTimeMillis(); Long cd = rivalCd.get(id);
            if (cd != null && now < cd) { feedback(src, "Your rival is still recovering. Try again in " + Math.max(1, (cd - now) / 60000 + 1) + " min."); return; }
            JsonObject rec = rivalOffer.remove(id);
            if (rec == null) rec = pickRival(List.of(name == null ? "" : name));
            if (rec == null) { feedback(src, "No winning rogue run is on record yet (finish and WIN a run to create the first rival)."); return; }
            Class<?> bridge = Class.forName(BR + "CobblemonBridge"), battles = Class.forName(BR + "CobblemonBattles");
            Class<?> sp = Class.forName("net.minecraft.class_3222");
            int top = 5;
            try { for (Object pk : (Iterable<Object>) bridge.getMethod("partyMembers", sp).invoke(null, pl)) top = Math.max(top, ((Number) pk.getClass().getMethod("getLevel").invoke(pk)).intValue()); } catch (Throwable ignored) { }
            List<Object> team = new ArrayList<>();
            Method create = bridge.getMethod("create", String.class);
            List<String> sets = rivalSets(rec); Collections.shuffle(sets, RND);
            int n = Math.min(6, sets.size());
            for (int i = 0; i < n; i++) {
                String set = sets.get(i);
                String scaled = withLevel(set, Math.max(5, Math.min(levelIn(set), top + 2)));
                try { team.add(create.invoke(null, scaled)); }
                catch (Throwable t) { try { team.add(create.invoke(null, scaled.split(" ")[0] + " level=" + levelIn(scaled))); } catch (Throwable ignored) { } }
            }
            if (team.size() < 2) { feedback(src, "That rival's team could not be loaded."); return; }
            Method start = null;
            for (Method m : battles.getMethods()) if (m.getName().equals("startTrainerBattle") && m.getParameterCount() == 6) start = m;
            String label = rivalLabel(rec);
            Object battle = start.invoke(null, pl, "Rival " + label, team, 5, team.size() >= 2 && RND.nextInt(100) < 25, "");
            if (battle == null) { feedback(src, "The battle couldn't start. Is your lead Pokemon able to fight?"); return; }
            rivalCd.put(id, now + rivalCooldown * 60000L);
            Method onEnd = null, won = null;
            for (Method m : battles.getMethods()) { if (m.getName().equals("onEnd")) onEnd = m; if (m.getName().equals("playerWon")) won = m; }
            final Method fwon = won; final Object fserver = server; final String fname = name;
            java.util.function.Consumer<Object> handler = ended -> {
                try {
                    Boolean w = (Boolean) fwon.invoke(null, ended, id);
                    if (w != null && w && fname != null) {
                        Object r2 = runManager();
                        if (r2 != null) { try { r2.getClass().getMethod("changeTokens", sp, int.class).invoke(r2, pl, rivalTokens); } catch (Throwable ignored) { } }
                        if (rivalBp > 0) cmd(fserver, "scoreboard players add " + fname + " pp_bp " + rivalBp);
                        tell(fserver, fname, "[Rival] You beat " + label + "'s champion team! +" + rivalTokens + " Rogue Tokens, +" + rivalBp + " Badge Points.", "gold");
                    }
                } catch (Throwable t) { System.out.println("[cobblecomputils] rival result failed: " + t); }
            };
            onEnd.invoke(null, battle, handler);
            feedback(src, "Rival battle: " + label + "'s champion team!");
        } catch (Throwable t) {
            feedback(src, "Rival battle unavailable: " + t);
            System.out.println("[cobblecomputils] rival command failed: " + t);
        }
    }
}
