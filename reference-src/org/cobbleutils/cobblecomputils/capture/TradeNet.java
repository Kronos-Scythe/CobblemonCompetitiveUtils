package org.cobbleutils.cobblecomputils.capture;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.lang.reflect.Proxy;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.class_2168;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;
import net.minecraft.server.MinecraftServer;

/**
 * Player to player Pokemon trading with a confirm screen. /trade <player> sends a request; when it is accepted both players see
 * their own party plus the partner's offer side by side. Both must press Accept (and the offer must be unchanged for a short
 * moment) before the swap happens. The swap is done server side in one step and re-validated first.
 */
public final class TradeNet implements ModInitializer {
    static final long REQ_MS = 60_000, SESSION_MS = 5 * 60_000, LOCK_MS = 1500;

    public record Mon(boolean empty, String name, int level, boolean shiny, List<String> lines) {
        public static final Mon NONE = new Mon(true, "", 0, false, List.of());
    }

    static final class Req { UUID from, to; long exp; }
    static final class Sess {
        UUID a, b, offA, offB; boolean accA, accB; long changed, born = System.currentTimeMillis(); String note = "";
    }

    static final Map<UUID, Req> reqs = new HashMap<>();   // keyed by target
    static final Map<UUID, Sess> sessions = new HashMap<>(); // keyed by each player
    static long tickN = 0;

    // ------------------------------------------------------------------ payloads
    public static record View(String partner, List<Mon> party, Mon theirs, int myOffer, boolean myAcc, boolean theirAcc, int lockMs, String note) implements class_8710 {
        public static final class_9154<View> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "trade_view"));
        public static final class_9139<class_9129, View> CODEC = class_9139.method_56438(View::write, View::read);

        static void wMon(class_9129 b, Mon m) {
            b.writeBoolean(m.empty());
            if (m.empty()) return;
            b.method_10814(m.name()); b.method_10804(m.level()); b.writeBoolean(m.shiny());
            b.method_10804(m.lines().size());
            for (String l : m.lines()) b.method_10814(l);
        }
        static Mon rMon(class_9129 b) {
            if (b.readBoolean()) return Mon.NONE;
            String n = b.method_19772(); int lv = b.method_10816(); boolean sh = b.readBoolean();
            int c = b.method_10816(); List<String> ls = new ArrayList<>();
            for (int i = 0; i < c; i++) ls.add(b.method_19772());
            return new Mon(false, n, lv, sh, ls);
        }
        static void write(View v, class_9129 b) {
            b.method_10814(v.partner());
            b.method_10804(v.party().size());
            for (Mon m : v.party()) wMon(b, m);
            wMon(b, v.theirs());
            b.method_10804(v.myOffer() + 1); b.writeBoolean(v.myAcc()); b.writeBoolean(v.theirAcc()); b.method_10804(v.lockMs());
            b.method_10814(v.note());
        }
        static View read(class_9129 b) {
            String p = b.method_19772();
            int n = b.method_10816(); List<Mon> party = new ArrayList<>();
            for (int i = 0; i < n; i++) party.add(rMon(b));
            Mon th = rMon(b);
            return new View(p, party, th, b.method_10816() - 1, b.readBoolean(), b.readBoolean(), b.method_10816(), b.method_19772());
        }
        public class_9154<? extends class_8710> method_56479() { return ID; }
    }

    public static record Act(int type, int arg) implements class_8710 {
        public static final class_9154<Act> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "trade_act"));
        public static final class_9139<class_9129, Act> CODEC = class_9139.method_56438(
            (p, b) -> { b.method_10804(p.type()); b.method_10804(p.arg()); },
            b -> new Act(b.method_10816(), b.method_10816()));
        public class_9154<? extends class_8710> method_56479() { return ID; }
    }

    // ------------------------------------------------------------------ init
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(View.ID, View.CODEC);
        PayloadTypeRegistry.playC2S().register(Act.ID, Act.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Act.ID, (payload, ctx) -> {
            try { act(ctx.player(), payload.type(), payload.arg()); } catch (Throwable t) { System.out.println("[cobblecomputils] trade action error: " + t); }
        });
        openExtrasPerms();
        try {
            Class<?> ev = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents");
            Object event = ev.getField("END_SERVER_TICK").get(null);
            Class<?> iface = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndTick");
            Object listener = Proxy.newProxyInstance(TradeNet.class.getClassLoader(), new Class<?>[]{iface}, (p, m, a) -> {
                if (m.getName().equals("onEndTick")) {
                    try { tick((MinecraftServer) a[0]); } catch (Throwable t) { if (tickN % 1200 == 0) System.out.println("[cobblecomputils] trade tick error: " + t); }
                }
                return null;
            });
            Class<?> eventBase = Class.forName("net.fabricmc.fabric.api.event.Event");
            eventBase.getMethod("register", Object.class).invoke(event, listener);
            Class<?> cr = Class.forName("net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback");
            Object cev = cr.getField("EVENT").get(null);
            Object cl = Proxy.newProxyInstance(TradeNet.class.getClassLoader(), new Class<?>[]{cr}, (p, m, a) -> {
                if (m.getName().equals("register")) registerCommands(a[0]);
                return null;
            });
            eventBase.getMethod("register", Object.class).invoke(cev, cl);
            System.out.println("[cobblecomputils] Trading active: /trade <player> (open to everyone)");
        } catch (Throwable t) { System.out.println("[cobblecomputils] Trading could not start: " + t); }
    }

    /** CobblemonExtras' /poketrade defaults to op level 2. Merge level 0 into its existing config (keeps every other setting). */
    static void openExtrasPerms() {
        try {
            java.io.File dir = new java.io.File("config/cobblemonextras");
            java.io.File[] fs = dir.listFiles((d, n) -> n.endsWith(".json"));
            if (fs == null) return;
            for (java.io.File f : fs) {
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f.toPath())).getAsJsonObject();
                for (String k : new ArrayList<>(root.keySet())) {
                    if (!k.equalsIgnoreCase("permissionlevels") || !root.get(k).isJsonObject()) continue;
                    com.google.gson.JsonObject lv = root.getAsJsonObject(k);
                    boolean changed = false;
                    for (String key : new String[]{"command.poketrade"}) {
                        if (lv.has(key) && lv.get(key).getAsInt() != 0) { lv.addProperty(key, 0); changed = true; }
                    }
                    if (changed) {
                        java.nio.file.Files.writeString(f.toPath(), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
                        System.out.println("[cobblecomputils] CobblemonExtras: /poketrade opened to all players in " + f.getName());
                    }
                }
            }
        } catch (Throwable t) { System.out.println("[cobblecomputils] could not adjust CobblemonExtras permissions: " + t); }
    }

    @SuppressWarnings("unchecked")
    static void registerCommands(Object dispatcherObj) {
        CommandDispatcher<class_2168> d = (CommandDispatcher<class_2168>) dispatcherObj;
        LiteralArgumentBuilder<class_2168> root = LiteralArgumentBuilder.<class_2168>literal("trade");
        root.then(LiteralArgumentBuilder.<class_2168>literal("accept").executes((Command<class_2168>) c -> { accept(c.getSource().method_9207(), null); return 1; })
            .then(RequiredArgumentBuilder.<class_2168, String>argument("player", StringArgumentType.word())
                .executes((Command<class_2168>) c -> { accept(c.getSource().method_9207(), StringArgumentType.getString(c, "player")); return 1; })));
        root.then(LiteralArgumentBuilder.<class_2168>literal("deny").executes((Command<class_2168>) c -> { deny(c.getSource().method_9207()); return 1; }));
        root.then(LiteralArgumentBuilder.<class_2168>literal("cancel").executes((Command<class_2168>) c -> { cancel(c.getSource().method_9207(), "You cancelled the trade."); return 1; }));
        root.then(RequiredArgumentBuilder.<class_2168, String>argument("player", StringArgumentType.word())
            .suggests((c, b) -> {
                try { for (class_3222 p : c.getSource().method_9211().method_3760().method_14571()) b.suggest(p.method_5477().getString()); } catch (Throwable t) { }
                return b.buildFuture();
            })
            .executes((Command<class_2168>) c -> { request(c.getSource().method_9207(), StringArgumentType.getString(c, "player")); return 1; }));
        root.executes((Command<class_2168>) c -> {
            say(c.getSource().method_9207(), "§6Trade: §f/trade <player> §7sends a request. Both players pick one Pokemon and press Accept.");
            return 1;
        });
        d.register(root);
    }

    // ------------------------------------------------------------------ helpers
    static void say(class_3222 pl, String text) { pl.method_7353(class_2561.method_43470(text), false); }

    static class_3222 find(MinecraftServer s, UUID id) { return id == null ? null : s.method_3760().method_14602(id); }

    static String name(class_3222 p) { return p.method_5477().getString(); }

    static boolean busy(class_3222 p) {
        try { return BattleRegistry.getBattleByParticipatingPlayer(p) != null; } catch (Throwable t) { return false; }
    }

    static boolean inArena(class_3222 p) { return PpGui.score(p, "pp_slot") >= 1; }

    static PartyStore party(class_3222 p) { return Cobblemon.INSTANCE.getStorage().getParty(p); }

    static Mon mon(Pokemon p) {
        if (p == null) return Mon.NONE;
        List<String> l = new ArrayList<>();
        String species = p.getSpecies().getName();
        String shown = p.getDisplayName(false).getString();
        l.add(species + "  Lv." + p.getLevel() + (p.getShiny() ? "  *Shiny*" : ""));
        try { l.add("Gender: " + p.getGender().name().toLowerCase(Locale.ROOT)); } catch (Throwable t) { }
        try { l.add("Nature: " + p.getNature().getName().method_12832()); } catch (Throwable t) { }
        try { l.add("Ability: " + p.getAbility().getName()); } catch (Throwable t) { }
        try {
            if (!p.heldItem().method_7960()) l.add("Holding: " + p.heldItem().method_7964().getString());
        } catch (Throwable t) { }
        try {
            List<String> mv = new ArrayList<>();
            for (com.cobblemon.mod.common.api.moves.Move m : p.getMoveSet().getMoves()) mv.add(m.getName());
            if (!mv.isEmpty()) l.add("Moves: " + String.join(", ", mv));
        } catch (Throwable t) { }
        return new Mon(false, shown, p.getLevel(), p.getShiny(), l);
    }

    static int indexOf(PartyStore ps, UUID id) {
        if (id == null) return -1;
        for (int i = 0; i < 6; i++) { Pokemon p = ps.get(i); if (p != null && p.getUuid().equals(id)) return i; }
        return -1;
    }

    static Sess sessionOf(class_3222 p) { return sessions.get(p.method_5667()); }

    // ------------------------------------------------------------------ requests
    static String blocker(class_3222 p) {
        if (sessions.containsKey(p.method_5667())) return name(p) + " is already trading.";
        if (busy(p)) return name(p) + " is in a battle.";
        if (inArena(p)) return name(p) + " is inside a gym arena.";
        if (!ServerPlayNetworking.canSend(p, View.ID)) return name(p) + " does not have the Cobblemon Competitive Utils mod.";
        return null;
    }

    static void request(class_3222 from, String targetName) {
        MinecraftServer s = from.method_5682();
        class_3222 to = s.method_3760().method_14566(targetName);
        if (to == null) { say(from, "§cPlayer not found: " + targetName); return; }
        if (to == from) { say(from, "§cYou cannot trade with yourself."); return; }
        String b = blocker(from); if (b != null) { say(from, "§c" + b.replace(name(from) + " is", "You are").replace(name(from) + " does", "You do")); return; }
        b = blocker(to); if (b != null) { say(from, "§c" + b); return; }
        // the other player already asked us: accept that one instead
        Req back = reqs.get(from.method_5667());
        if (back != null && back.from.equals(to.method_5667())) { accept(from, name(to)); return; }
        Req r = new Req(); r.from = from.method_5667(); r.to = to.method_5667(); r.exp = System.currentTimeMillis() + REQ_MS;
        reqs.put(to.method_5667(), r);
        say(from, "§6Trade request sent to §f" + name(to) + "§6. It expires in 60 seconds.");
        String json = "[{\"text\":\"" + name(from) + " wants to trade Pokemon with you! \",\"color\":\"gold\"},"
            + "{\"text\":\"[Accept]\",\"color\":\"green\",\"bold\":true,\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/trade accept " + name(from) + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"Open the trade window\"}},"
            + "{\"text\":\" \"},{\"text\":\"[Decline]\",\"color\":\"red\",\"bold\":true,\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/trade deny\"}}]";
        PpGui.runServer(to, "tellraw " + name(to) + " " + json);
    }

    static void deny(class_3222 pl) {
        Req r = reqs.remove(pl.method_5667());
        if (r == null) { say(pl, "§7You have no pending trade request."); return; }
        class_3222 from = find(pl.method_5682(), r.from);
        say(pl, "§7Trade request declined.");
        if (from != null) say(from, "§c" + name(pl) + " declined your trade request.");
    }

    static void accept(class_3222 pl, String fromName) {
        MinecraftServer s = pl.method_5682();
        Req r = reqs.get(pl.method_5667());
        if (r == null || r.exp < System.currentTimeMillis()) { reqs.remove(pl.method_5667()); say(pl, "§cNo active trade request. Ask the other player to send it again."); return; }
        class_3222 from = find(s, r.from);
        if (from == null) { reqs.remove(pl.method_5667()); say(pl, "§cThat player went offline."); return; }
        if (fromName != null && !name(from).equalsIgnoreCase(fromName)) { say(pl, "§cYour pending request is from " + name(from) + "."); return; }
        String b = blocker(pl); if (b != null) { say(pl, "§c" + b); return; }
        b = blocker(from); if (b != null) { say(pl, "§c" + b); return; }
        reqs.remove(pl.method_5667());
        Sess ss = new Sess(); ss.a = from.method_5667(); ss.b = pl.method_5667(); ss.changed = System.currentTimeMillis();
        sessions.put(ss.a, ss); sessions.put(ss.b, ss);
        push(s, ss);
    }

    static void cancel(class_3222 pl, String why) {
        Sess ss = sessionOf(pl);
        if (ss == null) { Req r = reqs.remove(pl.method_5667()); if (r == null) say(pl, "§7You are not trading."); return; }
        end(pl.method_5682(), ss, why, pl.method_5667());
    }

    /** Ends a session; `except` is told nothing extra (they already know), the other side gets `why`. */
    static void end(MinecraftServer s, Sess ss, String why, UUID actor) {
        sessions.remove(ss.a); sessions.remove(ss.b);
        for (UUID id : new UUID[]{ss.a, ss.b}) {
            class_3222 p = find(s, id);
            if (p == null) continue;
            closeView(p);
            if (actor == null || !actor.equals(id)) say(p, "§c" + why); else say(p, "§7Trade closed.");
        }
    }

    static void closeView(class_3222 p) {
        try { ServerPlayNetworking.send(p, new View("", List.of(), Mon.NONE, -1, false, false, 0, "")); } catch (Throwable t) { }
    }

    // ------------------------------------------------------------------ session
    static void act(class_3222 pl, int type, int arg) {
        Sess ss = sessionOf(pl);
        if (ss == null) return;
        MinecraftServer s = pl.method_5682();
        boolean isA = ss.a.equals(pl.method_5667());
        class_3222 other = find(s, isA ? ss.b : ss.a);
        if (other == null) { end(s, ss, "The other player left.", null); return; }
        if (type == 3) { end(s, ss, name(pl) + " cancelled the trade.", pl.method_5667()); return; }
        if (type == 0) { // offer party slot
            Pokemon p = arg >= 0 && arg < 6 ? party(pl).get(arg) : null;
            if (p == null) return;
            if (isA) ss.offA = p.getUuid(); else ss.offB = p.getUuid();
            ss.accA = ss.accB = false; ss.changed = System.currentTimeMillis(); ss.note = "";
        } else if (type == 1) { // withdraw
            if (isA) ss.offA = null; else ss.offB = null;
            ss.accA = ss.accB = false; ss.changed = System.currentTimeMillis(); ss.note = "";
        } else if (type == 2) { // accept toggle
            if (ss.offA == null || ss.offB == null) { ss.note = "Both players must offer a Pokemon first."; }
            else if (System.currentTimeMillis() - ss.changed < LOCK_MS) { ss.note = "Offer just changed - check it, then accept."; }
            else {
                boolean now = isA ? !ss.accA : !ss.accB;
                if (isA) ss.accA = now; else ss.accB = now;
                ss.note = "";
                if (ss.accA && ss.accB) { finish(s, ss); return; }
            }
        }
        push(s, ss);
    }

    static void push(MinecraftServer s, Sess ss) {
        class_3222 a = find(s, ss.a), b = find(s, ss.b);
        if (a == null || b == null) { end(s, ss, "The other player left.", null); return; }
        sendView(a, b, ss, true);
        sendView(b, a, ss, false);
    }

    static void sendView(class_3222 me, class_3222 other, Sess ss, boolean isA) {
        PartyStore mine = party(me), theirs = party(other);
        List<Mon> list = new ArrayList<>();
        for (int i = 0; i < 6; i++) list.add(mon(mine.get(i)));
        UUID myOff = isA ? ss.offA : ss.offB, thOff = isA ? ss.offB : ss.offA;
        Mon th = Mon.NONE;
        if (thOff != null) { int i = indexOf(theirs, thOff); if (i >= 0) th = mon(theirs.get(i)); }
        int lock = (int) Math.max(0, LOCK_MS - (System.currentTimeMillis() - ss.changed));
        ServerPlayNetworking.send(me, new View(name(other), list, th, indexOf(mine, myOff), isA ? ss.accA : ss.accB, isA ? ss.accB : ss.accA, lock, ss.note));
    }

    static void finish(MinecraftServer s, Sess ss) {
        class_3222 a = find(s, ss.a), b = find(s, ss.b);
        if (a == null || b == null) { end(s, ss, "The other player left.", null); return; }
        String why = null;
        if (busy(a) || busy(b)) why = "A player entered a battle - trade cancelled.";
        PartyStore pa = party(a), pb = party(b);
        int ia = indexOf(pa, ss.offA), ib = indexOf(pb, ss.offB);
        if (why == null && (ia < 0 || ib < 0)) why = "An offered Pokemon is no longer in the party - trade cancelled.";
        if (why != null) { end(s, ss, why, null); return; }
        Pokemon ma = pa.get(ia), mb = pb.get(ib);
        try {
            try { if (ma.getEntity() != null) ma.recall(); } catch (Throwable t) { }
            try { if (mb.getEntity() != null) mb.recall(); } catch (Throwable t) { }
            pa.remove(ma);
            pb.remove(mb);
            pa.set(ia, mb);
            pb.set(ib, ma);
        } catch (Throwable t) {
            // put things back as best we can so nothing is lost
            System.out.println("[cobblecomputils] TRADE FAILED midway: " + t);
            try { if (indexOf(pa, ss.offA) < 0 && indexOf(pb, ss.offA) < 0) pa.add(ma); } catch (Throwable u) { }
            try { if (indexOf(pb, ss.offB) < 0 && indexOf(pa, ss.offB) < 0) pb.add(mb); } catch (Throwable u) { }
            end(s, ss, "The trade failed; no Pokemon were lost. Tell an admin.", null);
            return;
        }
        System.out.println("[cobblecomputils] TRADE " + name(a) + " gave " + ma.getSpecies().getName() + " Lv" + ma.getLevel() + " <-> " + name(b) + " gave " + mb.getSpecies().getName() + " Lv" + mb.getLevel());
        sessions.remove(ss.a); sessions.remove(ss.b);
        closeView(a); closeView(b);
        say(a, "§a✔ Trade complete! §7You received §f" + mb.getSpecies().getName() + " Lv." + mb.getLevel() + "§7 from " + name(b) + ".");
        say(b, "§a✔ Trade complete! §7You received §f" + ma.getSpecies().getName() + " Lv." + ma.getLevel() + "§7 from " + name(a) + ".");
    }

    // ------------------------------------------------------------------ tick
    static void tick(MinecraftServer s) {
        tickN++;
        if (tickN % 20 != 0) return;
        long now = System.currentTimeMillis();
        for (Iterator<Req> it = reqs.values().iterator(); it.hasNext(); ) {
            Req r = it.next();
            if (r.exp < now || find(s, r.from) == null) it.remove();
        }
        for (Sess ss : new HashSet<>(sessions.values())) {
            class_3222 a = find(s, ss.a), b = find(s, ss.b);
            if (a == null || b == null) { end(s, ss, "The other player left.", null); continue; }
            if (now - ss.born > SESSION_MS) { end(s, ss, "The trade timed out.", null); continue; }
            if (busy(a) || busy(b)) { end(s, ss, "A player entered a battle - trade cancelled.", null); continue; }
            // refresh so the accept lock countdown and party changes show up
            if (now - ss.changed < LOCK_MS + 1000 || tickN % 100 == 0) push(s, ss);
        }
    }
}
