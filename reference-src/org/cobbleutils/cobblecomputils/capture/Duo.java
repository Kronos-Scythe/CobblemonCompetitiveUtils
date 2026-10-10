package org.cobbleutils.cobblecomputils.capture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.class_2168;
import net.minecraft.class_3222;
import net.minecraft.server.MinecraftServer;

/**
 * Duo run: a separate co-op progression track for a fixed pair of players.
 * Each duo owns its own copy of the Radical Cobblemon Trainers progress (defeated trainers, current region, completed regions),
 * so both partners share one gym stage, one badge count and one level cap. A player's solo progress is saved when they switch
 * to the duo run and restored exactly when they switch back. Either partner can pick the region the duo plays.
 */
public final class Duo {
    private Duo() {}

    static final String[] REG = {"kanto", "johto", "hoenn", "sinnoh", "unova", "alola"};
    static final String[] REGN = {"Kanto", "Johto", "Hoenn", "Sinnoh", "Unova", "Alola"};

    // ------------------------------------------------------------------ data
    public static final class Rec {
        String id, a, b, an, bn;
        String series = "kanto", prev = "";
        boolean done;
        Set<String> defeated = new LinkedHashSet<>();
        Map<String, Integer> completed = new LinkedHashMap<>();
        long created;
        boolean has(String uuid) { return uuid.equals(a) || uuid.equals(b); }
        String other(String uuid) { return uuid.equals(a) ? b : a; }
        String otherName(String uuid) { return uuid.equals(a) ? bn : an; }
    }

    public static final class Snap {
        String series = "", prev = "";
        boolean done;
        Set<String> defeated = new LinkedHashSet<>();
        Map<String, Integer> completed = new LinkedHashMap<>();
        Snap copy() {
            Snap s = new Snap(); s.series = series; s.prev = prev; s.done = done;
            s.defeated.addAll(defeated); s.completed.putAll(completed); return s;
        }
        boolean same(Snap o) {
            return o != null && done == o.done && series.equals(o.series) && prev.equals(o.prev) && defeated.equals(o.defeated) && completed.equals(o.completed);
        }
    }

    static final class Store {
        Map<String, Rec> duos = new LinkedHashMap<>();
        Map<String, String> mode = new LinkedHashMap<>();   // player uuid -> duo id (players currently on the duo run)
        Map<String, Snap> solo = new LinkedHashMap<>();     // player uuid -> saved solo progress while on a duo run
    }

    static final class Invite { String from, fromName; long until; }

    static Store STORE = new Store();
    static final Map<String, Snap> LAST = new HashMap<>();
    static final Map<String, Invite> INVITES = new HashMap<>();   // target uuid -> invite
    static final Set<String> SEEN = new HashSet<>();
    static int tickN = 0;
    static final class Host { String partner; int left = 25; }
    static final Map<String, Host> HOSTING = new HashMap<>();   // host uuid -> pending "fight together"
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final File FILE = new File("config/cobblecomputils/duo.json");

    static {
        try {
            if (FILE.exists()) {
                Store s = GSON.fromJson(Files.readString(FILE.toPath()), Store.class);
                if (s != null) STORE = s;
                if (STORE.duos == null) STORE.duos = new LinkedHashMap<>();
                if (STORE.mode == null) STORE.mode = new LinkedHashMap<>();
                if (STORE.solo == null) STORE.solo = new LinkedHashMap<>();
            }
        } catch (Throwable t) { System.out.println("[cobblecomputils] duo data could not be read: " + t); }
    }

    static void save() {
        try {
            FILE.getParentFile().mkdirs();
            Files.writeString(FILE.toPath(), GSON.toJson(STORE));
        } catch (Throwable t) { System.out.println("[cobblecomputils] duo data could not be saved: " + t); }
    }

    // ------------------------------------------------------------------ lookups
    static String uid(class_3222 pl) { return pl.method_5667().toString(); }
    static String nameOf(class_3222 pl) { return pl.method_5477().getString(); }

    public static Rec recOf(class_3222 pl) {
        String u = uid(pl);
        for (Rec r : STORE.duos.values()) if (r.has(u)) return r;
        return null;
    }

    /** The duo run this player is currently on, or null when they are on their solo run. */
    public static Rec active(class_3222 pl) {
        String id = STORE.mode.get(uid(pl));
        return id == null ? null : STORE.duos.get(id);
    }

    public static boolean onDuoRun(class_3222 pl) { return active(pl) != null; }

    public static class_3222 partner(MinecraftServer s, class_3222 pl) {
        Rec r = recOf(pl);
        if (r == null) return null;
        try { return s.method_3760().method_14602(UUID.fromString(r.other(uid(pl)))); } catch (Throwable t) { return null; }
    }

    static class_3222 byName(MinecraftServer s, String name) {
        for (class_3222 p : s.method_3760().method_14571()) if (nameOf(p).equalsIgnoreCase(name)) return p;
        return null;
    }

    static boolean inArena(class_3222 pl) { return PpGui.score(pl, "pp_slot") >= 1; }

    public static Invite inviteFor(class_3222 pl) {
        Invite i = INVITES.get(uid(pl));
        if (i != null && i.until < System.currentTimeMillis()) { INVITES.remove(uid(pl)); return null; }
        return i;
    }

    // ------------------------------------------------------------------ RCT access (reflection, same approach as RctBridge)
    static Object rct() throws Exception { return Class.forName("com.gitlab.srcmc.rctmod.api.RCTMod").getMethod("getInstance").invoke(null); }

    static Object call(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            boolean ok = true;
            Class<?>[] pt = m.getParameterTypes();
            for (int i = 0; i < pt.length; i++) if (args[i] != null && !pt[i].isInstance(args[i])) { ok = false; break; }
            if (ok) return m.invoke(target, args);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    static Object data(class_3222 pl) throws Exception { return call(call(rct(), "getTrainerManager"), "getData", pl); }

    static Field field(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    @SuppressWarnings("unchecked")
    static Snap read(class_3222 pl) throws Exception {
        Object d = data(pl);
        Snap s = new Snap();
        s.defeated.addAll((Collection<String>) field(d, "defeatedTrainerIds").get(d));
        Map<String, Integer> cs = (Map<String, Integer>) field(d, "completedSeries").get(d);
        if (cs != null) s.completed.putAll(cs);
        String cur = (String) field(d, "currentSeries").get(d), prev = (String) field(d, "previousSeries").get(d);
        s.series = cur == null ? "" : cur;
        s.prev = prev == null ? "" : prev;
        s.done = (Boolean) field(d, "currentSeriesCompleted").get(d);
        return s;
    }

    /** Writes a progress snapshot straight into the player's RCT data (no completion side effects) and syncs it to the client. */
    static void write(class_3222 pl, Snap s) throws Exception {
        Object d = data(pl);
        try { call(d, "removeProgressDefeats"); } catch (Throwable ignored) { }
        field(d, "defeatedTrainerIds").set(d, new HashSet<>(s.defeated));
        field(d, "completedSeries").set(d, new HashMap<>(s.completed));
        field(d, "currentSeries").set(d, s.series);
        field(d, "previousSeries").set(d, s.prev);
        field(d, "currentSeriesCompleted").setBoolean(d, s.done);
        call(d, "method_80");   // markDirty
        call(d, "sync");
        GymBoard.forget(nameOf(pl));
        GymBoard.kick();
    }

    static Snap snapOf(Rec r) {
        Snap s = new Snap();
        s.series = r.series; s.prev = r.prev; s.done = r.done;
        s.defeated.addAll(r.defeated); s.completed.putAll(r.completed);
        return s;
    }

    static void backup(class_3222 pl, Snap s) {
        try {
            File dir = new File("config/cobblecomputils/duo_backups");
            dir.mkdirs();
            Files.writeString(new File(dir, uid(pl) + "_" + System.currentTimeMillis() + ".json").toPath(), GSON.toJson(s));
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ pair management
    static void say(class_3222 pl, String text) { TradeNet.say(pl, text); }

    /** Sends an invite. Returns an error text, or null on success. */
    public static String invite(class_3222 from, String targetName) {
        MinecraftServer s = from.method_5682();
        class_3222 to = byName(s, targetName);
        if (to == null) return targetName + " is not online.";
        if (to == from) return "You cannot invite yourself.";
        if (recOf(from) != null) return "You already have a duo partner. Leave your duo first.";
        if (recOf(to) != null) return nameOf(to) + " is already in a duo.";
        Invite i = new Invite();
        i.from = uid(from); i.fromName = nameOf(from); i.until = System.currentTimeMillis() + 120_000L;
        INVITES.put(uid(to), i);
        say(to, "§b[Duo] §f" + nameOf(from) + " invited you to a Duo run. Open the phone menu > Duo run, or type §e/duo accept§f. The invite lasts 2 minutes.");
        say(from, "§b[Duo] §7Invite sent to " + nameOf(to) + ".");
        return null;
    }

    public static String accept(class_3222 pl) {
        Invite i = inviteFor(pl);
        if (i == null) return "You have no pending duo invite.";
        MinecraftServer s = pl.method_5682();
        class_3222 from = null;
        try { from = s.method_3760().method_14602(UUID.fromString(i.from)); } catch (Throwable ignored) { }
        INVITES.remove(uid(pl));
        if (from == null) return i.fromName + " went offline.";
        if (recOf(pl) != null || recOf(from) != null) return "One of you is already in a duo.";
        Rec r = new Rec();
        r.id = UUID.randomUUID().toString().substring(0, 8);
        r.a = uid(from); r.an = nameOf(from); r.b = uid(pl); r.bn = nameOf(pl); r.created = System.currentTimeMillis();
        STORE.duos.put(r.id, r);
        save();
        say(from, "§b[Duo] §a" + nameOf(pl) + " accepted. You are now a duo! Open the phone menu > Duo run and switch to the Duo run.");
        say(pl, "§b[Duo] §aYou and " + nameOf(from) + " are now a duo! Open the phone menu > Duo run and switch to the Duo run.");
        return null;
    }

    public static String decline(class_3222 pl) {
        Invite i = INVITES.remove(uid(pl));
        if (i == null) return "You have no pending duo invite.";
        try {
            class_3222 from = pl.method_5682().method_3760().method_14602(UUID.fromString(i.from));
            if (from != null) say(from, "§b[Duo] §7" + nameOf(pl) + " declined your invite.");
        } catch (Throwable ignored) { }
        return null;
    }

    /** Switch this player onto the duo run. */
    public static String enter(class_3222 pl) {
        Rec r = recOf(pl);
        if (r == null) return "You need a duo partner first.";
        if (active(pl) != null) return "You are already on the Duo run.";
        if (inArena(pl)) return "Leave the arena first.";
        try {
            Snap solo = read(pl);
            backup(pl, solo);
            STORE.solo.put(uid(pl), solo);
            STORE.mode.put(uid(pl), r.id);
            save();
            write(pl, snapOf(r));
            LAST.put(uid(pl), read(pl));
        } catch (Throwable t) {
            // roll back: nothing may be lost
            Snap solo = STORE.solo.get(uid(pl));
            if (solo != null) { try { write(pl, solo); } catch (Throwable ignored) { } }
            STORE.mode.remove(uid(pl)); STORE.solo.remove(uid(pl)); LAST.remove(uid(pl)); save();
            System.out.println("[cobblecomputils] duo enter failed for " + nameOf(pl) + ": " + t);
            return "Could not switch to the Duo run (" + t.getClass().getSimpleName() + "). Your solo progress is untouched.";
        }
        say(pl, "§b[Duo] §aDuo run active with " + r.otherName(uid(pl)) + ". Shared gym stage, badges and level cap. Your solo run is saved.");
        return null;
    }

    /** Switch this player back to their solo run (restores the saved solo progress exactly). */
    public static String exit(class_3222 pl) {
        if (active(pl) == null && !STORE.mode.containsKey(uid(pl))) return "You are on your solo run.";
        if (inArena(pl)) return "Leave the arena first.";
        Snap solo = STORE.solo.get(uid(pl));
        if (solo == null) {
            // nothing saved (should not happen): leave the player as they are rather than risk their data
            STORE.mode.remove(uid(pl)); LAST.remove(uid(pl)); save();
            return "No solo progress was saved, so your current progress was kept.";
        }
        try {
            Rec r = active(pl);
            if (r != null) { try { syncRec(pl.method_5682(), r); } catch (Throwable ignored) { } }
            write(pl, solo);
        } catch (Throwable t) {
            System.out.println("[cobblecomputils] duo exit failed for " + nameOf(pl) + ": " + t);
            return "Could not restore your solo run (" + t.getClass().getSimpleName() + "). Try again.";
        }
        STORE.mode.remove(uid(pl)); STORE.solo.remove(uid(pl)); LAST.remove(uid(pl));
        save();
        say(pl, "§b[Duo] §aBack on your solo run. Your solo progress is exactly as you left it.");
        return null;
    }

    /** End the pair for both players (each goes back to solo). */
    public static String leave(class_3222 pl) {
        Rec r = recOf(pl);
        if (r == null) return "You are not in a duo.";
        if (STORE.mode.containsKey(uid(pl))) { String e = exit(pl); if (STORE.mode.containsKey(uid(pl))) return e; }
        class_3222 other = partner(pl.method_5682(), pl);
        String oid = r.other(uid(pl));
        if (other != null && STORE.mode.containsKey(oid)) {
            if (inArena(other)) { say(pl, "§b[Duo] §7" + nameOf(other) + " is in an arena; they go back to solo when they leave it."); }
            else exit(other);
        }
        STORE.duos.remove(r.id);
        save();   // a partner still marked as on the duo run is switched back on their next tick / login
        if (other != null) say(other, "§b[Duo] §7" + nameOf(pl) + " ended the duo. Your solo run is yours again.");
        say(pl, "§b[Duo] §7Duo ended.");
        return null;
    }

    /** Both partners on the duo run play this region. */
    public static String pickRegion(class_3222 pl, String series) {
        Rec r = active(pl);
        if (r == null) return "Switch to the Duo run first.";
        if (inArena(pl)) return "Leave the arena first.";
        try {
            Object d = data(pl);
            call(d, "setCurrentSeries", series);
            GymBoard.forget(nameOf(pl));
            GymBoard.kick();
            Snap cur = read(pl);
            if (!cur.series.equals(series)) return "That region is not available.";
            LAST.put(uid(pl), LAST.getOrDefault(uid(pl), cur));   // keep the old baseline so the change is picked up as a delta
            syncRec(pl.method_5682(), r);
        } catch (Throwable t) {
            System.out.println("[cobblecomputils] duo region pick failed: " + t);
            return "Could not change region (" + t.getClass().getSimpleName() + ").";
        }
        return null;
    }

    // ------------------------------------------------------------------ sync
    static List<class_3222> onlineMembers(MinecraftServer s, Rec r) {
        List<class_3222> l = new ArrayList<>();
        for (String u : new String[]{r.a, r.b}) {
            try {
                class_3222 p = s.method_3760().method_14602(UUID.fromString(u));
                if (p != null && r.id.equals(STORE.mode.get(u))) l.add(p);
            } catch (Throwable ignored) { }
        }
        return l;
    }

    /** Merge what each online partner changed into the duo's record, then give both the same state. */
    static void syncRec(MinecraftServer s, Rec r) throws Exception {
        List<class_3222> mem = onlineMembers(s, r);
        if (mem.isEmpty()) return;
        boolean changed = false;
        for (class_3222 m : mem) {
            Snap cur = read(m), last = LAST.get(uid(m));
            if (last == null) { LAST.put(uid(m), cur); continue; }
            if (cur.same(last)) continue;
            for (String id : cur.defeated) if (!last.defeated.contains(id) && r.defeated.add(id)) changed = true;
            for (String id : last.defeated) if (!cur.defeated.contains(id) && r.defeated.remove(id)) changed = true;
            for (Map.Entry<String, Integer> e : cur.completed.entrySet())
                if (e.getValue() > r.completed.getOrDefault(e.getKey(), 0)) { r.completed.put(e.getKey(), e.getValue()); changed = true; }
            if (!cur.series.equals(last.series) || cur.done != last.done || !cur.prev.equals(last.prev)) {
                r.series = cur.series; r.prev = cur.prev; r.done = cur.done; changed = true;
            }
            LAST.put(uid(m), cur);
        }
        Snap target = snapOf(r);
        for (class_3222 m : mem) {
            Snap cur = LAST.get(uid(m));
            if (cur == null || !cur.same(target)) write(m, target);
            LAST.put(uid(m), read(m));
        }
        // a cleared region counts for both partners (unlocks that region's rematches and pays its one-time reward)
        for (String reg : REG) {
            if (r.completed.getOrDefault(reg, 0) <= 0) continue;
            for (class_3222 m : mem) {
                if (!PpGui.adv(m, "region/" + reg)) {
                    PpGui.runServer(m, "advancement grant " + nameOf(m) + " only potentialpack:region/" + reg);
                    say(m, "§b[Duo] §6Your duo cleared " + reg.substring(0, 1).toUpperCase() + reg.substring(1) + "!");
                }
            }
        }
        if (changed) save();
    }

    static void onJoin(class_3222 pl) {
        Rec r = active(pl);
        if (r == null) return;
        try {
            write(pl, snapOf(r));
            LAST.put(uid(pl), read(pl));
            say(pl, "§b[Duo] §7Duo run active with " + r.otherName(uid(pl)) + ".");
        } catch (Throwable t) { System.out.println("[cobblecomputils] duo join apply failed for " + nameOf(pl) + ": " + t); }
    }

    /** Called every server tick from PpGui. */
    static void tick(MinecraftServer s) {
        if (++tickN % 20 != 0) return;
        try { hostTick(s); } catch (Throwable t) { }
        try {
            Set<String> online = new HashSet<>();
            for (class_3222 p : new ArrayList<>(s.method_3760().method_14571())) {
                String u = uid(p);
                online.add(u);
                if (SEEN.add(u)) onJoin(p);
                if (STORE.mode.containsKey(u) && STORE.duos.get(STORE.mode.get(u)) == null && !inArena(p)) exit(p);   // the duo was ended while they were away
            }
            SEEN.retainAll(online);
            if (tickN % 40 == 0) for (Rec r : new ArrayList<>(STORE.duos.values())) {
                try { syncRec(s, r); } catch (Throwable t) { if (tickN % 1200 == 0) System.out.println("[cobblecomputils] duo sync error: " + t); }
            }
        } catch (Throwable t) { if (tickN % 1200 == 0) System.out.println("[cobblecomputils] duo tick error: " + t); }
    }


    // ------------------------------------------------------------------ fighting together
    static boolean inArenaDim(class_3222 pl) {
        try { return "potentialpack:gym_arena".equals(pl.method_37908().method_27983().method_29177().toString()); } catch (Throwable t) { return false; }
    }

    /** Host a team fight: start the next gym fight, open the lane and send the partner a one-click join. */
    public static String fightTogether(class_3222 pl) {
        Rec r = active(pl);
        if (r == null) return "Switch to the Duo run first.";
        class_3222 pp = partner(pl.method_5682(), pl);
        if (pp == null) return r.otherName(uid(pl)) + " is offline.";
        if (!onDuoRun(pp)) return r.otherName(uid(pl)) + " is still on their solo run. Ask them to switch to the Duo run.";
        if (inArena(pp) && !PpGui.tag(pp, "pp_coopopen")) return r.otherName(uid(pl)) + " is in a fight already.";
        if (PpGui.score(pl, "pp_slot") < 1 || !inArenaDim(pl)) {
            if (inArena(pl)) return "Finish or leave your current fight first.";
            PpGui.run(pl, "trigger gym set 1");
        }
        Host h = new Host();
        h.partner = uid(pp);
        HOSTING.put(uid(pl), h);
        return null;
    }

    /** Join the partner's open lane. */
    public static String joinPartner(class_3222 pl) {
        Rec r = recOf(pl);
        if (r == null) return "You are not in a duo.";
        class_3222 pp = partner(pl.method_5682(), pl);
        if (pp == null) return r.otherName(uid(pl)) + " is offline.";
        if (inArena(pl)) return "Leave your own arena first.";
        int slot = PpGui.score(pp, "pp_slot");
        if (slot < 1 || !PpGui.tag(pp, "pp_coopopen") || PpGui.tag(pp, "pp_pend")) return r.otherName(uid(pl)) + " has no open lane right now.";
        PpGui.run(pl, "trigger gym set " + (30000 + slot));
        return null;
    }

    static void hostTick(MinecraftServer s) {
        for (Iterator<Map.Entry<String, Host>> it = HOSTING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Host> e = it.next();
            class_3222 pl = null, pp = null;
            try { pl = s.method_3760().method_14602(UUID.fromString(e.getKey())); pp = s.method_3760().method_14602(UUID.fromString(e.getValue().partner)); } catch (Throwable ignored) { }
            if (pl == null || pp == null || --e.getValue().left <= 0) {
                if (pl != null) say(pl, "§c[Duo] §7The team fight could not be set up. Start a gym fight and use Fight together again.");
                it.remove(); continue;
            }
            if (PpGui.score(pl, "pp_slot") >= 1 && inArenaDim(pl) && !PpGui.tag(pl, "pp_pend")) {
                if (!PpGui.tag(pl, "pp_coopopen")) PpGui.run(pl, "trigger gym set 42");
                String n = nameOf(pl);
                PpGui.runServer(pp, "tellraw " + nameOf(pp) + " [{\"text\":\"[Duo] \",\"color\":\"aqua\"},{\"text\":\"" + n + " is ready for a team fight. \",\"color\":\"green\"},{\"text\":\"[JOIN]\",\"color\":\"yellow\",\"bold\":true,\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/duo join\"}},{\"text\":\"  Then stay next to \" + n + \" and wait for the co-op invite.\",\"color\":\"gray\"}]");
                say(pl, "§b[Duo] §7Lane open. Once " + nameOf(pp) + " has joined, stand together and SNEAK + RIGHT-CLICK the trainer, then pick " + nameOf(pp) + " as your ally.");
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ display helpers
    /** Gym badges (not Elite Four / Champion) in a region that this duo has beaten. */
    public static int badges(Rec r, String series) { return GymBoard.badgesOf(r.defeated, series); }

    // ------------------------------------------------------------------ /duo command
    @SuppressWarnings("unchecked")
    static void registerCommands(Object dispatcherObj) {
        CommandDispatcher<class_2168> d = (CommandDispatcher<class_2168>) dispatcherObj;
        LiteralArgumentBuilder<class_2168> root = LiteralArgumentBuilder.<class_2168>literal("duo");
        root.then(LiteralArgumentBuilder.<class_2168>literal("invite").then(RequiredArgumentBuilder.<class_2168, String>argument("player", StringArgumentType.word())
            .suggests((c, b) -> {
                try { for (class_3222 p : c.getSource().method_9211().method_3760().method_14571()) b.suggest(nameOf(p)); } catch (Throwable t) { }
                return b.buildFuture();
            })
            .executes((Command<class_2168>) c -> { reply(c.getSource().method_9207(), invite(c.getSource().method_9207(), StringArgumentType.getString(c, "player"))); return 1; })));
        root.then(LiteralArgumentBuilder.<class_2168>literal("accept").executes((Command<class_2168>) c -> { reply(c.getSource().method_9207(), accept(c.getSource().method_9207())); return 1; }));
        root.then(LiteralArgumentBuilder.<class_2168>literal("decline").executes((Command<class_2168>) c -> { reply(c.getSource().method_9207(), decline(c.getSource().method_9207())); return 1; }));
        root.then(LiteralArgumentBuilder.<class_2168>literal("start").executes((Command<class_2168>) c -> { reply(c.getSource().method_9207(), enter(c.getSource().method_9207())); return 1; }));
        root.then(LiteralArgumentBuilder.<class_2168>literal("solo").executes((Command<class_2168>) c -> { reply(c.getSource().method_9207(), exit(c.getSource().method_9207())); return 1; }));
        root.then(LiteralArgumentBuilder.<class_2168>literal("join").executes((Command<class_2168>) c -> { reply(c.getSource().method_9207(), joinPartner(c.getSource().method_9207())); return 1; }));
        root.then(LiteralArgumentBuilder.<class_2168>literal("fight").executes((Command<class_2168>) c -> { reply(c.getSource().method_9207(), fightTogether(c.getSource().method_9207())); return 1; }));
        root.then(LiteralArgumentBuilder.<class_2168>literal("leave").executes((Command<class_2168>) c -> { reply(c.getSource().method_9207(), leave(c.getSource().method_9207())); return 1; }));
        LiteralArgumentBuilder<class_2168> region = LiteralArgumentBuilder.<class_2168>literal("region");
        for (String reg : REG) region.then(LiteralArgumentBuilder.<class_2168>literal(reg).executes((Command<class_2168>) c -> { reply(c.getSource().method_9207(), pickRegion(c.getSource().method_9207(), reg)); return 1; }));
        root.then(region);
        root.executes((Command<class_2168>) c -> {
            say(c.getSource().method_9207(), "§b[Duo] §f/duo invite <player>, accept, decline, start (join the Duo run), solo (back to solo), region <name>, leave. Or use the phone menu > Duo run.");
            return 1;
        });
        d.register(root);
    }

    static void reply(class_3222 pl, String err) { if (err != null) say(pl, "§c[Duo] " + err); }
}
