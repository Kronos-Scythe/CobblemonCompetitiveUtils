package org.cobbleutils.cobblecomputils.capture;

import com.google.gson.*;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.minecraft.class_1799;
import net.minecraft.class_2168;
import net.minecraft.class_2561;
import net.minecraft.class_3222;

/**
 * Engagement systems: daily/weekly quest board, leaderboard, Rogue win streak bonuses, Rogue wins -> Tier 7 Raid Pass vouchers,
 * and the searchable phone. Data: config/cobblecomputils/engage.json. Commands: /quests, /leaderboard.
 */
public final class Engage implements ModInitializer {
    static final File DIR = new File("config/cobblecomputils");
    static final File FILE = new File(DIR, "engage.json");
    static final ZoneId ZONE = ZoneId.of("America/Denver");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final class PD {
        String name = "";
        String dayKey = "", weekKey = "";
        Map<String, Integer> dayBase = new HashMap<>(), weekBase = new HashMap<>();
        Set<String> claimed = new HashSet<>();
        Set<String> rwGiven = new HashSet<>();
        int dex, rogueWins, rogueRuns, bestFloor, streak, bestStreak, pendingTokens, pendingVouchers;
        String lastWinDay = "";
    }

    static final Map<String, PD> DATA = new HashMap<>();
    static int histDone = -1;
    static boolean loaded = false, dirty = false;
    static int tickN = 0;
    static final Set<String> notified = new HashSet<>();

    // ------------------------------------------------------------------ quests
    record Reward(int bp, int tokens, int vouchers, String item, int itemCount) {
        String text() {
            List<String> l = new ArrayList<>();
            if (bp > 0) l.add(bp + " Badge Points");
            if (tokens > 0) l.add(tokens + " Rogue Tokens");
            if (vouchers > 0) l.add(vouchers + " Raid Pass voucher" + (vouchers > 1 ? "s" : ""));
            if (item != null) l.add(itemCount + "x " + item.substring(item.indexOf(':') + 1).replace('_', ' '));
            return String.join(" + ", l);
        }
    }
    record Quest(String id, String title, String metric, int target, Reward reward, String icon) {}

    static final List<Quest> DAILY = List.of(
        new Quest("d_gym2", "Win 2 gym fights", "gym", 2, new Reward(6, 0, 0, null, 0), "cobblemon:poke_ball"),
        new Quest("d_gym4", "Win 4 gym fights", "gym", 4, new Reward(14, 0, 0, null, 0), "cobblemon:ultra_ball"),
        new Quest("d_dex3", "Register 3 new species", "dex", 3, new Reward(5, 0, 0, "cobblemon:rare_candy", 3), "minecraft:knowledge_book"),
        new Quest("d_coop1", "Win a co-op gym fight", "coop", 1, new Reward(8, 0, 0, null, 0), "minecraft:totem_of_undying"),
        new Quest("d_run1", "Finish a Rogue run", "runs", 1, new Reward(0, 40, 0, null, 0), "minecraft:compass"),
        new Quest("d_win1", "Win a Rogue run", "rwins", 1, new Reward(0, 90, 0, null, 0), "minecraft:nether_star"));
    static final List<Quest> WEEKLY = List.of(
        new Quest("w_gym10", "Win 10 gym fights", "gym", 10, new Reward(40, 0, 1, null, 0), "cobblemon:master_ball"),
        new Quest("w_dex15", "Register 15 new species", "dex", 15, new Reward(30, 0, 1, "cobblemon:ability_patch", 1), "minecraft:written_book"),
        new Quest("w_win2", "Win 2 Rogue runs", "rwins", 2, new Reward(0, 220, 1, null, 0), "minecraft:nether_star"),
        new Quest("w_coop3", "Win 3 co-op gym fights", "coop", 3, new Reward(30, 0, 1, null, 0), "minecraft:totem_of_undying"),
        new Quest("w_run4", "Finish 4 Rogue runs", "runs", 4, new Reward(0, 130, 0, "cobblemon:rare_candy", 5), "minecraft:compass"));

    static String dayKey() { return LocalDate.now(ZONE).toString(); }
    static String weekKey() { LocalDate d = LocalDate.now(ZONE); return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR); }

    static List<Quest> pick(List<Quest> pool, String key, int n) {
        List<Quest> l = new ArrayList<>(pool);
        Collections.shuffle(l, new Random(key.hashCode() * 31L + 7));
        return new ArrayList<>(l.subList(0, Math.min(n, l.size())));
    }
    static List<Quest> todays() { return pick(DAILY, dayKey(), 3); }
    static List<Quest> thisWeeks() { return pick(WEEKLY, weekKey(), 3); }

    static int metric(class_3222 pl, PD d, String m) {
        switch (m) {
            case "gym": return PpGui.score(pl, "pp_wins");
            case "coop": return PpGui.score(pl, "pp_coop");
            case "dex": return d.dex;
            case "runs": return d.rogueRuns;
            case "rwins": return d.rogueWins;
            default: return 0;
        }
    }
    static final String[] METRICS = {"gym", "coop", "dex", "runs", "rwins"};

    static PD pd(String name) {
        load();
        PD d = DATA.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new PD());
        if (d.name.isEmpty()) d.name = name;
        return d;
    }

    /** Rolls the day/week over for this player and records baselines. */
    static void roll(class_3222 pl, PD d) {
        String dk = dayKey(), wk = weekKey();
        if (!dk.equals(d.dayKey)) {
            d.dayKey = dk; d.dayBase.clear();
            for (String m : METRICS) d.dayBase.put(m, metric(pl, d, m));
            d.claimed.removeIf(c -> c.startsWith("d_"));
            dirty = true;
        }
        if (!wk.equals(d.weekKey)) {
            d.weekKey = wk; d.weekBase.clear();
            for (String m : METRICS) d.weekBase.put(m, metric(pl, d, m));
            d.claimed.removeIf(c -> c.startsWith("w_"));
            dirty = true;
        }
    }

    static int progress(class_3222 pl, PD d, Quest q) {
        Map<String, Integer> base = q.id().startsWith("d_") ? d.dayBase : d.weekBase;
        int cur = metric(pl, d, q.metric()), b = base.getOrDefault(q.metric(), cur);
        return Math.max(0, Math.min(q.target(), cur - b));
    }

    static void give(class_3222 pl, Reward r) {
        String n = pl.method_5477().getString();
        if (r.bp() > 0) PpGui.runServer(pl, "scoreboard players add " + n + " pp_bp " + r.bp());
        if (r.item() != null) PpGui.runServer(pl, "give " + n + " " + r.item() + " " + r.itemCount());
        if (r.vouchers() > 0) RaidPass.addVouchers(pl, r.vouchers());
        if (r.tokens() > 0) addTokens(pl, r.tokens());
    }

    static boolean addTokens(class_3222 pl, int amount) {
        try {
            Object rm = Class.forName("org.CobbleUtils.cobbleroguelike.run.RunManager").getMethod("get").invoke(null);
            Object ok = rm.getClass().getMethod("changeTokens", class_3222.class, int.class).invoke(rm, pl, amount);
            return ok instanceof Boolean b && b;
        } catch (Throwable t) { return false; }
    }

    static void msg(class_3222 pl, String text) { pl.method_7353(class_2561.method_43470(text), false); }

    // ------------------------------------------------------------------ persistence
    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!FILE.exists()) return;
            JsonObject root = JsonParser.parseString(Files.readString(FILE.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("histDone")) histDone = root.get("histDone").getAsInt();
            JsonObject pl = root.getAsJsonObject("players");
            if (pl != null) for (Map.Entry<String, JsonElement> e : pl.entrySet()) DATA.put(e.getKey(), GSON.fromJson(e.getValue(), PD.class));
        } catch (Throwable t) { System.out.println("[cobblecomputils] engage load failed: " + t); }
    }

    static synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("histDone", histDone);
            JsonObject pl = new JsonObject();
            for (Map.Entry<String, PD> e : DATA.entrySet()) pl.add(e.getKey(), GSON.toJsonTree(e.getValue()));
            root.add("players", pl);
            DIR.mkdirs();
            Files.writeString(FILE.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
            dirty = false;
        } catch (Throwable t) { System.out.println("[cobblecomputils] engage save failed: " + t); }
    }

    // ------------------------------------------------------------------ init
    @Override
    public void onInitialize() {
        try {
            Class<?> ev = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents");
            Object event = ev.getField("END_SERVER_TICK").get(null);
            Class<?> iface = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndTick");
            Object listener = Proxy.newProxyInstance(Engage.class.getClassLoader(), new Class<?>[]{iface}, (p, m, a) -> {
                if (m.getName().equals("onEndTick")) {
                    try { tick((net.minecraft.server.MinecraftServer) a[0]); } catch (Throwable t) { if (tickN % 1200 == 0) System.out.println("[cobblecomputils] engage error: " + t); }
                }
                return null;
            });
            Class<?> eventBase = Class.forName("net.fabricmc.fabric.api.event.Event");
            eventBase.getMethod("register", Object.class).invoke(event, listener);
            Class<?> cr = Class.forName("net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback");
            Object cev = cr.getField("EVENT").get(null);
            Object cl = Proxy.newProxyInstance(Engage.class.getClassLoader(), new Class<?>[]{cr}, (p, m, a) -> {
                if (m.getName().equals("register")) registerCommands(a[0]);
                return null;
            });
            eventBase.getMethod("register", Object.class).invoke(cev, cl);
            System.out.println("[cobblecomputils] Engagement systems active (quests, leaderboard, rogue bonuses)");
        } catch (Throwable t) { System.out.println("[cobblecomputils] Engagement systems could not start: " + t); }
    }

    @SuppressWarnings("unchecked")
    static void registerCommands(Object dispatcherObj) {
        CommandDispatcher<class_2168> d = (CommandDispatcher<class_2168>) dispatcherObj;
        d.register(LiteralArgumentBuilder.<class_2168>literal("quests").executes((Command<class_2168>) ctx -> { openQuests(ctx.getSource().method_9207()); return 1; }));
        d.register(LiteralArgumentBuilder.<class_2168>literal("leaderboard").executes((Command<class_2168>) ctx -> { openBoard(ctx.getSource().method_9207()); return 1; }));
        d.register(LiteralArgumentBuilder.<class_2168>literal("top").executes((Command<class_2168>) ctx -> { openBoard(ctx.getSource().method_9207()); return 1; }));
    }

    // ------------------------------------------------------------------ tick
    static void tick(net.minecraft.server.MinecraftServer server) {
        tickN++;
        if (tickN % 100 != 0) return;
        processHistory(server);
        for (class_3222 pl : server.method_3760().method_14571()) {
            PD d = pd(pl.method_5477().getString());
            if (tickN % 400 == 0 || d.dex == 0) { int dx = PpGui.dexCaught(pl); if (dx != d.dex) { d.dex = dx; dirty = true; } }
            roll(pl, d);
            if (d.pendingTokens > 0 && addTokens(pl, d.pendingTokens)) {
                msg(pl, "§6[Rogue] §e+" + d.pendingTokens + " bonus Rogue Tokens §7(win bonus)");
                d.pendingTokens = 0; dirty = true;
            }
            if (d.pendingVouchers > 0) {
                RaidPass.addVouchers(pl, d.pendingVouchers);
                msg(pl, "§6★ Tier 7 Raid Pass earned: §eRogue wins§6! §7Activate it in your phone's Gym menu.");
                d.pendingVouchers = 0; dirty = true;
            }
            for (Quest q : todays()) notifyDone(pl, d, q);
            for (Quest q : thisWeeks()) notifyDone(pl, d, q);
        }
        if (dirty) save();
    }

    static void notifyDone(class_3222 pl, PD d, Quest q) {
        String key = pl.method_5667() + q.id() + (q.id().startsWith("d_") ? d.dayKey : d.weekKey);
        if (d.claimed.contains(q.id()) || progress(pl, d, q) < q.target() || !notified.add(key)) return;
        msg(pl, "§a✔ Quest complete: §f" + q.title() + " §7- claim it with §e/quests");
    }

    // ------------------------------------------------------------------ rogue history -> stats, streaks, bonuses
    static void processHistory(net.minecraft.server.MinecraftServer server) {
        if (!RogueLink.HIST.exists()) return;
        JsonArray arr;
        try { arr = JsonParser.parseString(Files.readString(RogueLink.HIST.toPath(), StandardCharsets.UTF_8)).getAsJsonArray(); }
        catch (Throwable t) { return; }
        load();
        int size = arr.size();
        boolean first = histDone < 0;
        int from = first ? 0 : Math.min(histDone, size);
        if (!first && from >= size) return;
        // history is trimmed to 'history_keep' entries; if it shrank, process only the tail we can't have seen
        for (int i = from; i < size; i++) {
            JsonObject r = arr.get(i).getAsJsonObject();
            boolean won = r.has("won") && r.get("won").getAsBoolean();
            int floor = r.has("floor") ? r.get("floor").getAsInt() : 0;
            String day = dayOf(r);
            for (JsonElement n : r.getAsJsonArray("players")) {
                PD d = pd(n.getAsString());
                d.rogueRuns++;
                if (floor > d.bestFloor) d.bestFloor = floor;
                if (won) {
                    d.rogueWins++;
                    d.streak++;
                    if (d.streak > d.bestStreak) d.bestStreak = d.streak;
                    if (!first) {
                        int tok = 50 + 25 * Math.min(d.streak - 1, 4) + (day.equals(d.lastWinDay) ? 0 : 50);
                        d.pendingTokens += tok;
                    }
                    d.lastWinDay = day;
                    for (int t : new int[]{1, 3, 5, 10, 15, 20, 25, 30, 40, 50}) {
                        if (d.rogueWins >= t && d.rwGiven.add("rw_" + t)) d.pendingVouchers++;
                    }
                } else d.streak = 0;
            }
        }
        histDone = size;
        dirty = true;
        save();
    }

    static String dayOf(JsonObject r) {
        try { return ZonedDateTime.parse(r.get("time").getAsString()).withZoneSameInstant(ZONE).toLocalDate().toString(); } catch (Throwable t) { return dayKey(); }
    }

    // ------------------------------------------------------------------ windows
    static String until(ZonedDateTime end) {
        long mins = Math.max(0, java.time.Duration.between(ZonedDateTime.now(ZONE), end).toMinutes());
        return mins >= 1440 ? (mins / 1440) + "d " + ((mins % 1440) / 60) + "h" : (mins / 60) + "h " + (mins % 60) + "m";
    }

    static int readyCount(class_3222 pl) {
        try {
            PD d = pd(pl.method_5477().getString());
            int n = 0;
            for (Quest q : todays()) if (!d.claimed.contains(q.id()) && progress(pl, d, q) >= q.target()) n++;
            for (Quest q : thisWeeks()) if (!d.claimed.contains(q.id()) && progress(pl, d, q) >= q.target()) n++;
            return n;
        } catch (Throwable t) { return 0; }
    }

    public static void openQuests(class_3222 pl) {
        if (!ListNet.hasClient(pl)) { chatQuests(pl); return; }
        ListNet.open(pl, s -> {
            PD d = pd(pl.method_5477().getString());
            roll(pl, d);
            s.title = "Quests"; s.back = true;
            ZonedDateTime now = ZonedDateTime.now(ZONE);
            s.info = "Daily resets in " + until(now.toLocalDate().plusDays(1).atStartOfDay(ZONE));
            s.groups.add("Daily"); s.groups.add("Weekly");
            s.hint = "Click a finished quest to claim it   |   Weekly resets Monday";
            for (int g = 0; g < 2; g++) {
                for (Quest q : g == 0 ? todays() : thisWeeks()) {
                    int prog = progress(pl, d, q);
                    boolean done = d.claimed.contains(q.id()), ready = !done && prog >= q.target();
                    class_1799 icon = PpGui.stackOf(q.icon(), "");
                    String sub = "Reward: " + q.reward().text();
                    String right = done ? "Claimed" : (ready ? "CLAIM" : prog + " / " + q.target());
                    String tip = q.title() + "\nProgress: " + prog + " / " + q.target() + "\nReward: " + q.reward().text();
                    s.add(ListNet.Row.of(icon, q.title(), sub, right, g, done ? 2 : (ready ? 4 : 0), tip), () -> {
                        PD dd = pd(pl.method_5477().getString());
                        roll(pl, dd);
                        if (dd.claimed.contains(q.id())) { ListNet.note(pl, "Already claimed.", true); return; }
                        if (progress(pl, dd, q) < q.target()) { ListNet.note(pl, "Not finished yet: " + progress(pl, dd, q) + " / " + q.target(), true); return; }
                        dd.claimed.add(q.id()); dirty = true; save();
                        give(pl, q.reward());
                        ListNet.note(pl, "Claimed: " + q.reward().text(), false);
                    });
                }
            }
        });
    }

    static void chatQuests(class_3222 pl) {
        PD d = pd(pl.method_5477().getString());
        roll(pl, d);
        msg(pl, "§6=== Quests ===");
        for (Quest q : todays()) msg(pl, (d.claimed.contains(q.id()) ? "§a✔ " : "§f• ") + q.title() + " §7(" + progress(pl, d, q) + "/" + q.target() + ") → " + q.reward().text());
        for (Quest q : thisWeeks()) msg(pl, (d.claimed.contains(q.id()) ? "§a✔ " : "§b• ") + q.title() + " §7(" + progress(pl, d, q) + "/" + q.target() + ") → " + q.reward().text());
        msg(pl, "§7Install the Cobblemon Competitive Utils mod on your client to claim rewards in a window.");
    }

    // ------------------------------------------------------------------ leaderboard
    static List<Map.Entry<String, Integer>> board(net.minecraft.server.MinecraftServer server, String cat) {
        Map<String, Integer> m = new HashMap<>();
        switch (cat) {
            case "Gym wins": scoreboardInto(server, "pp_wins", m); break;
            case "Co-op wins": scoreboardInto(server, "pp_coop", m); break;
            case "Pokédex": for (PD d : DATA.values()) if (d.dex > 0) m.put(d.name, d.dex); break;
            case "Rogue wins": for (PD d : DATA.values()) if (d.rogueWins > 0) m.put(d.name, d.rogueWins); break;
            default: for (PD d : DATA.values()) if (d.bestFloor > 0) m.put(d.name, d.bestFloor); break;
        }
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.removeIf(e -> e.getValue() <= 0 || e.getKey().startsWith("#"));
        l.sort((a, b) -> b.getValue() - a.getValue());
        return l;
    }

    static void scoreboardInto(net.minecraft.server.MinecraftServer server, String obj, Map<String, Integer> out) {
        try {
            net.minecraft.class_269 sb = server.method_3845();
            net.minecraft.class_266 o = sb.method_1170(obj);
            if (o == null) return;
            for (net.minecraft.class_9011 e : sb.method_1184(o)) out.merge(e.comp_2127(), e.comp_2128(), Math::max);
        } catch (Throwable ignored) { }
    }

    public static void openBoard(class_3222 pl) {
        if (!ListNet.hasClient(pl)) { msg(pl, "§cThe leaderboard window needs the Cobblemon Competitive Utils mod on your client."); return; }
        load();
        ListNet.open(pl, s -> {
            String[] cats = {"Gym wins", "Co-op wins", "Pokédex", "Rogue wins", "Best Rogue floor"};
            String[] unit = {" wins", " wins", " species", " wins", " floors"};
            String me = pl.method_5477().getString();
            s.title = "Leaderboard"; s.back = true;
            s.info = "Top players";
            s.hint = "Your rows are highlighted   |   Search a player's name to find them";
            for (String c : cats) s.groups.add(c);
            for (int g = 0; g < cats.length; g++) {
                List<Map.Entry<String, Integer>> l = board(pl.method_5682(), cats[g]);
                for (int i = 0; i < Math.min(25, l.size()); i++) {
                    Map.Entry<String, Integer> e = l.get(i);
                    String icon = i == 0 ? "minecraft:gold_ingot" : (i == 1 ? "minecraft:iron_ingot" : (i == 2 ? "minecraft:copper_ingot" : "minecraft:paper"));
                    boolean mine = e.getKey().equalsIgnoreCase(me);
                    s.add(ListNet.Row.of(PpGui.stackOf(icon, ""), "#" + (i + 1) + "  " + e.getKey(), cats[g], e.getValue() + unit[g], g, mine ? 4 : 0,
                        e.getKey() + "\n" + cats[g] + ": " + e.getValue()), null);
                }
                if (l.isEmpty()) s.add(ListNet.Row.of(PpGui.stackOf("minecraft:gray_dye", ""), "Nobody yet", cats[g], "", g, 1, "No entries yet"), null);
            }
        });
    }
}
