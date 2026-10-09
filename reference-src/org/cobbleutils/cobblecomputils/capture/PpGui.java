package org.cobbleutils.cobblecomputils.capture;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.class_1277;
import net.minecraft.class_1661;
import net.minecraft.class_1657;
import net.minecraft.class_1703;
import net.minecraft.class_1707;
import net.minecraft.class_1713;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.class_3917;
import net.minecraft.class_747;
import net.minecraft.class_9015;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes;

/**
 * Server-side chest GUI for the Gym Challenge (main menu, rematches, Badge Point shop, badge case, modifiers).
 * Pure vanilla chest screen, so vanilla clients need nothing installed. Opened when a player carries the
 * "pp_gui" command tag (set by the datapack's gym/menu function). Every action runs the existing
 * "/trigger gym set N" datapack logic as the player, so all rules/costs stay in the datapack.
 */
public final class PpGui {
    private PpGui() {}

    static final String[] REG = {"kanto", "johto", "hoenn", "sinnoh", "unova", "alola"};
    static final String[] REGN = {"Kanto", "Johto", "Hoenn", "Sinnoh", "Unova", "Alola"};
    static final Set<H> OPEN = new HashSet<>();
    static final Map<UUID, H> VIRT = new HashMap<>();
    static int tickN = 0;

    // ------------------------------------------------------------------ init / tick
    static void init() throws Exception {
        Class<?> ev = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents");
        Object event = ev.getField("END_SERVER_TICK").get(null);
        Class<?> iface = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndTick");
        Object listener = Proxy.newProxyInstance(PpGui.class.getClassLoader(), new Class<?>[]{iface}, (p, m, a) -> {
            if (m.getName().equals("onEndTick")) {
                try { tick((net.minecraft.server.MinecraftServer) a[0]); } catch (Throwable t) { if (tickN % 200 == 0) System.out.println("[cobblecomputils] gym GUI tick error: " + t); }
            }
            return null;
        });
        Class<?> eventBase = Class.forName("net.fabricmc.fabric.api.event.Event");
        eventBase.getMethod("register", Object.class).invoke(event, listener);
        System.out.println("[cobblecomputils] Gym GUI active");
    }

    static void tick(net.minecraft.server.MinecraftServer server) {
        tickN++;
        try { RaidPass.tick(server); } catch (Throwable t) { if (tickN % 6000 == 0) System.out.println("[cobblecomputils] raid pass tick error: " + t); }
        if (!OPEN.isEmpty()) {
            for (H h : new ArrayList<>(OPEN)) if (h.virtual && h.pl.method_31481()) { OPEN.remove(h); VIRT.values().remove(h); }
            for (H h : new ArrayList<>(OPEN)) {
                if (h.pending > 0 && --h.pending == 0) h.render();
            }
        }
        if (tickN % 2 != 0) return;
        for (class_3222 pl : server.method_3760().method_14571()) {
            for (String[] tg : TAGS) {
                if (pl.method_5752().contains(tg[0])) {
                    pl.method_5738(tg[0]);
                    try { open(pl, tg[1]); }
                    catch (Throwable t) {
                        System.out.println("[cobblecomputils] could not open gym GUI: " + t);
                        run(pl, "function potentialpack:" + tg[2]);
                    }
                    break;
                }
            }
        }
    }

    /** command tag -> GUI screen -> chat fallback function */
    static final String[][] TAGS = {
        {"pp_gui", "main", "gym/menu_chat"},
        {"pp_gui_rm", "rm_hub", "gym/rm_hub"},
        {"pp_gui_shop", "shop", "shop/hub"},
        {"pp_gui_case", "case", "gym/badgecase"},
        {"pp_gui_mods", "mods", "gym/mods"},
        {"pp_gui_coop", "coop", "coop/menu_chat"},
        {"pp_gui_join", "coop_join", "coop/list_chat"},
        {"pp_gui_mart", "mart", "mart/hub_chat"},
        {"pp_gui_weather", "weather", "weather/menu_chat"},
        {"pp_gui_dex", "dex", "dex/menu_chat"},
    };

    static void virtClick(class_3222 pl, int slot) {
        H h = VIRT.get(pl.method_5667());
        if (h == null || slot < 0 || slot >= 54) return;
        Runnable r = h.act.get(slot);
        if (r != null) { try { r.run(); } catch (Throwable t) { System.out.println("[cobblecomputils] gym GUI click error: " + t); } }
    }

    static void virtClose(class_3222 pl) {
        H h = VIRT.remove(pl.method_5667());
        if (h != null) OPEN.remove(h);
    }

    static void open(class_3222 pl, String screen) {
        if (screen.equals("mart") && MartNet.hasClient(pl)) { virtClose(pl); MartNet.open(pl); return; }
        if (GymNet.hasClient(pl)) {
            virtClose(pl);
            H v = new H(0, pl.method_31548(), pl);
            v.virtual = true;
            v.screen = screen;
            VIRT.put(pl.method_5667(), v);
            v.render();
            return;
        }
        H[] ref = new H[1];
        pl.method_17355(new class_747((syncId, inv, p) -> {
            ref[0] = new H(syncId, inv, pl);
            ref[0].screen = screen;
            ref[0].render();
            return ref[0];
        }, class_2561.method_43470("Gym Challenge")));
    }

    static void run(class_3222 pl, String command) {
        try {
            net.minecraft.server.MinecraftServer s = pl.method_5682();
            s.method_3734().method_44252(pl.method_5671(), command);
        } catch (Throwable t) { System.out.println("[cobblecomputils] gym GUI command failed: " + command + " -> " + t); }
    }

    // ------------------------------------------------------------------ data access
    static int score(class_3222 pl, String obj) {
        try {
            net.minecraft.class_269 sb = pl.method_5682().method_3845();
            net.minecraft.class_266 o = sb.method_1170(obj);
            if (o == null) return 0;
            return sb.method_1180(class_9015.method_55422(pl.method_5477().getString()), o).method_55409();
        } catch (Throwable t) { return 0; }
    }

    static boolean adv(class_3222 pl, String path) {
        try {
            Object entry = pl.method_5682().method_3851().method_12896(class_2960.method_60655("potentialpack", path));
            if (entry == null) return false;
            return pl.method_14236().method_12882((net.minecraft.class_8779) entry).method_740();
        } catch (Throwable t) { return false; }
    }


    static final int[] DEX_N = {10, 25, 50, 100, 150, 250, 400, 600, 900};
    static final String[] DEX_R = {"5 Ultra Ball", "20 Ultra Ball + 10,000", "5 Rare Candy + 25,000",
            "Ability Patch + 10 XL Exp Candy + 50,000 + 5% shiny odds", "10 Rare Candy + 100,000 + 50 BP",
            "Master Ball + 150,000 + 5% shiny odds", "2 Master Ball + 300,000 + 100 BP + 5% shiny odds",
            "3 Master Ball + 500,000 + 5% shiny odds + 1 heart", "5 Master Ball + 1,000,000 + 10% shiny odds + title"};

    /** Number of species this player has registered as owned in the Cobblemon Pokedex. */
    static int dexCaught(class_3222 pl) {
        try {
            Object mgr = Cobblemon.INSTANCE.getPlayerDataManager().get((class_1657) pl, PlayerInstancedDataStoreTypes.INSTANCE.getPOKEDEX());
            AbstractPokedexManager dex = (AbstractPokedexManager) mgr;
            int n = 0;
            for (class_2960 id : new ArrayList<>(dex.getSpeciesRecords().keySet())) {
                if (dex.getKnowledgeForSpecies(id) == PokedexEntryProgress.OWNED) n++;
            }
            return n;
        } catch (Throwable t) { return 0; }
    }

    static void runServer(class_3222 pl, String command) {
        try {
            net.minecraft.server.MinecraftServer s = pl.method_5682();
            s.method_3734().method_44252(s.method_3739(), command);
        } catch (Throwable t) { System.out.println("[cobblecomputils] server command failed: " + command + " -> " + t); }
    }

    static int fakeScore(class_3222 pl, String holder, String obj) {
        try {
            net.minecraft.class_269 sb = pl.method_5682().method_3845();
            net.minecraft.class_266 o = sb.method_1170(obj);
            if (o == null) return 0;
            return sb.method_1180(class_9015.method_55422(holder), o).method_55409();
        } catch (Throwable t) { return 0; }
    }

    static boolean tag(class_3222 pl, String t) { return pl.method_5752().contains(t); }

    // ------------------------------------------------------------------ item building
    static Object codec, ops;
    static Method parse, result, snbt;
    static boolean itemInit = false;

    static void itemSetup() throws Exception {
        if (itemInit) return;
        codec = Class.forName("net.minecraft.class_1799").getField("field_24671").get(null);
        ops = Class.forName("net.minecraft.class_2509").getField("field_11560").get(null);
        parse = Class.forName("com.mojang.serialization.Codec").getMethod("parse", Class.forName("com.mojang.serialization.DynamicOps"), Object.class);
        result = Class.forName("com.mojang.serialization.DataResult").getMethod("result");
        snbt = Class.forName("net.minecraft.class_2522").getMethod("method_10718", String.class);
        itemInit = true;
    }

    static String esc(String s) {
        return s.replace("\\", "").replace("\"", "”").replace("'", "’");
    }

    static final Map<Character, String> COLORS = new HashMap<>();
    static {
        COLORS.put('g', "green"); COLORS.put('r', "red"); COLORS.put('y', "yellow"); COLORS.put('a', "aqua");
        COLORS.put('w', "white"); COLORS.put('s', "gray"); COLORS.put('o', "gold"); COLORS.put('p', "light_purple");
        COLORS.put('d', "dark_gray"); COLORS.put('b', "blue");
    }

    /** Text line: first 2 chars "c|" choose the colour (g,r,y,a,w,s,o,p,d,b). */
    static String line(String t, String defColor, boolean bold) {
        String color = defColor;
        if (t.length() > 2 && t.charAt(1) == '|' && COLORS.containsKey(t.charAt(0))) { color = COLORS.get(t.charAt(0)); t = t.substring(2); }
        return "{\"text\":\"" + esc(t) + "\",\"color\":\"" + color + "\",\"italic\":false" + (bold ? ",\"bold\":true" : "") + "}";
    }

    static class_1799 mk(String id, int count, String name, boolean glint, String... lore) {
        try {
            itemSetup();
            String comp = "\"minecraft:custom_name\":'" + line(name, "white", false) + "'";
            if (lore.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (String l : lore) { if (sb.length() > 0) sb.append(','); sb.append('\'').append(line(l, "gray", false)).append('\''); }
                comp += ",\"minecraft:lore\":[" + sb + "]";
            }
            comp += ",\"minecraft:hide_additional_tooltip\":{}";
            if (glint) comp += ",\"minecraft:enchantment_glint_override\":1b";
            for (String itemId : new String[]{id, "minecraft:paper"}) {
                for (String c : new String[]{comp, comp.replace(",\"minecraft:hide_additional_tooltip\":{}", ""), ""}) {
                    try {
                        String s = "{id:\"" + itemId + "\",count:" + Math.max(1, count) + (c.isEmpty() ? "" : ",components:{" + c + "}") + "}";
                        Object nbt = snbt.invoke(null, s);
                        Object dr = parse.invoke(codec, ops, nbt);
                        Optional<?> r = (Optional<?>) result.invoke(dr);
                        if (r.isPresent()) return (class_1799) r.get();
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) { if (!warned) { warned = true; System.out.println("[cobblecomputils] gym GUI item build failed: " + t); } }
        return class_1799.field_8037;
    }
    static boolean warned = false;

    /** Build an item stack from an item id and an optional components JSON object (SNBT-compatible). */
    static class_1799 stackOf(String id, String comps) {
        try {
            itemSetup();
            boolean has = comps != null && !comps.isEmpty() && !comps.equals("{}");
            for (boolean withComps : has ? new boolean[]{true, false} : new boolean[]{false}) {
                try {
                    String s = "{id:\"" + id + "\",count:1" + (withComps ? ",components:" + comps : "") + "}";
                    Object dr = parse.invoke(codec, ops, snbt.invoke(null, s));
                    Optional<?> r = (Optional<?>) result.invoke(dr);
                    if (r.isPresent()) return (class_1799) r.get();
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return class_1799.field_8037;
    }

    static class_1799 pane() { return mk("minecraft:gray_stained_glass_pane", 1, " ", false); }

    // ------------------------------------------------------------------ the screen handler
    static final class H extends class_1707 {
        final class_3222 pl;
        final class_1277 box;
        String screen = "main";
        int page = 0, cat = 51, region = 0, tier = 1;
        int pending = 0;
        boolean virtual = false;
        final Map<Integer, Runnable> act = new HashMap<>();

        H(int syncId, class_1661 inv, class_3222 pl) { this(syncId, inv, pl, new class_1277(54)); }
        private H(int syncId, class_1661 inv, class_3222 pl, class_1277 box) {
            super(class_3917.field_17327, syncId, inv, box, 6);
            this.pl = pl; this.box = box;
            OPEN.add(this);
        }

        @Override public void method_7593(int slot, int button, class_1713 type, class_1657 player) {
            if (player == pl && slot >= 0 && slot < 54) {
                Runnable r = act.get(slot);
                if (r != null) { try { r.run(); } catch (Throwable t) { System.out.println("[cobblecomputils] gym GUI click error: " + t); } }
            }
            method_34252();
        }

        @Override public class_1799 method_7601(class_1657 player, int slot) { return class_1799.field_8037; }

        @Override public void method_7595(class_1657 player) { OPEN.remove(this); super.method_7595(player); }

        // --- helpers
        void put(int slot, class_1799 st, Runnable r) { box.method_5447(slot, st); if (r != null) act.put(slot, r); }
        void trig(int n, boolean close) {
            run(pl, "trigger gym set " + n);
            if (close) close(); else pending = 4;
        }
        void close() {
            if (virtual) { VIRT.remove(pl.method_5667()); OPEN.remove(this); GymNet.sendClose(pl); }
            else pl.method_7346();
        }
        void openMart() {
            if (MartNet.hasClient(pl)) { close(); MartNet.open(pl); } else go("mart");
        }
        void cmd(String c) { close(); run(pl, c); }
        void go(String s) { screen = s; page = 0; render(); }

        void render() {
            act.clear();
            for (int i = 0; i < 54; i++) box.method_5447(i, pane());
            switch (screen) {
                case "rm_hub": rematchHub(); break;
                case "rm_list": rematchList(); break;
                case "shop": shop(); break;
                case "case": badgeCase(); break;
                case "mods": mods(); break;
                case "coop": coop(); break;
                case "coop_join": coopJoin(); break;
                case "mart": mart(); break;
                case "weather": weather(); break;
                case "dex": dexScreen(); break;
                default: main();
            }
            if (virtual) {
                List<class_1799> l = new ArrayList<>(54);
                for (int i = 0; i < 54; i++) l.add(box.method_5438(i));
                String t = switch (screen) {
                    case "rm_hub" -> "Rematches"; case "rm_list" -> "Rematches - " + REGN[region] + (tier == 1 ? " Hard" : " Elite");
                    case "shop" -> "Badge Point Shop"; case "case" -> "Badge Case"; case "mods" -> "Challenge Modifiers";
                    case "coop" -> "Co-op Gyms"; case "coop_join" -> "Open co-op lanes"; case "weather" -> "Weather Machine";
                    case "dex" -> "Pokedex Milestones"; default -> "Gym Challenge";
                };
                GymNet.send(pl, t, l);
            } else method_7623();
        }

        // --- screens
        void back(int slot) { put(slot, mk("minecraft:arrow", 1, "Back", false, "s|Return to the main menu"), () -> go("main")); }

        void main() {
            int bp = score(pl, "pp_bp"), heals = score(pl, "pp_heals"), wins = score(pl, "pp_wins"), gym = score(pl, "pp_gym");
            long day = pl.method_5682().method_30002().method_8532() / 24000L;
            String feat = REGN[(int) ((day / 7) % 6)];
            put(4, mk("minecraft:book", 1, "Gym Challenge", true,
                    "o|Badge Points: " + bp, "y|Heal charges: " + heals, "w|Total wins: " + wins, "a|Featured region: " + feat + " (x2 BP)"), null);
            String[] info = GymBoard.infoByRank(gym);
            String next = info == null ? "Beat the Champion to unlock rematches!" : info[1] + " " + info[2] + ": " + info[3];
            put(20, mk("cobblemon:poke_ball", 1, "Fight my next Gym Leader", true, "s|Next opponent:", "y|" + next, "d|Teleports you to an arena"), () -> trig(1, true));
            put(22, mk("cobblemon:ultra_ball", 1, "Rematches (Hard / Elite)", false, "s|Replay any gym on harder teams", "s|for Badge Points"), () -> go("rm_hub"));
            put(24, mk("minecraft:emerald", 1, "Badge Point Shop", false, "o|You have " + bp + " BP", "s|Mints, held items, tera shards,", "s|and region exclusives"), () -> { go("shop"); });
            int nx = 0; while (nx < 6 && adv(pl, "region/" + REGN[nx].toLowerCase())) nx++;
            if (nx == 0) put(28, mk("minecraft:compass", 1, "Start next region", false, "r|Beat the Kanto Champion first", "s|Then switch to Johto here"), null);
            else if (nx >= 6) put(28, mk("minecraft:compass", 1, "Start next region", false, "g|All six regions conquered!", "s|Use Rematches for Hard / Elite"), null);
            else put(28, mk("minecraft:compass", 1, "Start next region: " + REGN[nx], true, "g|" + REGN[nx - 1] + " conquered!", "y|Switches your series to " + REGN[nx], "d|Then use Fight my next Gym Leader"), () -> trig(46, true));
            put(30, mk("minecraft:writable_book", 1, "Badge Case", false, "s|Your progress in every region"), () -> go("case"));
            put(32, mk("minecraft:comparator", 1, "Challenge Modifiers", false, "s|Iron Mode / Speedrun for", "s|x1.5 Badge Points"), () -> go("mods"));
            put(38, mk("cobblemon:full_restore", 1, "Heal my party", false, "y|Charges left today: " + heals, "d|Refills daily"), () -> trig(8, false));
            boolean kanto = adv(pl, "region/kanto");
            put(40, mk("minecraft:knowledge_book", 1, "Claim Raid Den Key", kanto, kanto ? "g|Unlocked: tier 6/7 raids" : "r|Beat the Kanto Champion first", "s|Click to claim a replacement key"), () -> trig(9, false));
            put(42, mk("minecraft:barrier", 1, "Leave the arena", false, "s|Return to where you were"), () -> trig(2, true));
            put(46, mk("minecraft:totem_of_undying", 1, "Co-op Gyms", score(pl, "pp_coop") > 0, "s|Fight a gym leader with a partner", "a|Team wins: " + score(pl, "pp_coop")), () -> go("coop"));
            put(52, mk("minecraft:villager_spawn_egg", 1, "Poke Mart", false, "s|Every village shopkeeper", "s|(CobbleDollars)"), () -> openMart());
            put(34, mk("minecraft:lightning_rod", 1, "Weather Machine", false, "s|Summon clear skies, rain or a", "s|thunderstorm for CobbleDollars.", "y|Great for fishing legendaries"), () -> go("weather"));
            put(44, mk("minecraft:knowledge_book", 1, "Pokedex Milestones", false, "s|Rewards for registering Pokemon", "a|Caught species: " + dexCaught(pl)), () -> go("dex"));
            put(47, mk("minecraft:enchanted_book", 1, "Move Tutor", false, "s|Teach your party any move it can learn,", "s|including egg moves", "y|Click to open"), () -> cmd("movetutor"));
            put(48, mk("cobblemon:protein", 1, "EV Editor", false, "s|Set your party's EVs", "y|Click to open"), () -> cmd("evedit"));
            int vch = RaidPass.vouchers(pl); boolean pact = RaidPass.active(pl);
            put(50, mk("minecraft:nether_star", 1, "Tier 7 Raid Pass", pact || vch > 0,
                    pact ? "g|ACTIVE: " + RaidPass.fmt(RaidPass.remainingMs(pl)) + " left" : "s|1 hour of /rqueue on 7-star raids",
                    "y|Vouchers: " + vch, "d|Earned for every Base, Hard and Elite region clear",
                    pact ? "s|Pass running" : (vch > 0 ? "y|Click to activate a voucher" : "r|Clear a region to earn one")), () -> { RaidPass.activate(pl); render(); });
            put(49, mk("minecraft:oak_door", 1, "Close", false), () -> close());
        }

        void weather() {
            org.cobbleutils.cobblecomputils.economy.Economy eco = org.cobbleutils.cobblecomputils.Cobblecomputils.economy();
            boolean dollars = !eco.isFree();
            int bp = score(pl, "pp_bp");
            java.math.BigInteger bal = dollars ? eco.balance(pl) : java.math.BigInteger.ZERO;
            long now = pl.method_5682().method_30002().method_8510();
            long left = Math.max(0, fakeScore(pl, "#next", "pp_wx") - now);
            put(4, mk("minecraft:book", 1, "Weather Machine", true, "s|Changes the weather for the whole server.",
                    dollars ? "y|Balance: " + eco.format(bal) : "y|Badge Points: " + bp,
                    left > 0 ? "r|Recharging: " + ((left + 1199) / 1200) + " min left (shared cooldown)" : "g|Ready"), null);
            String[] ic = {"minecraft:sunflower", "minecraft:water_bucket", "minecraft:lightning_rod"};
            String[] nm = {"Clear skies (20 min)", "Rain (10 min)", "Thunderstorm (7.5 min)"};
            long[] price = {20000L, 30000L, 50000L};
            int[] cost = {30, 60, 120};
            String[] ln = {"s|Ends rain and storms", "s|Needed for rain-only spawns", "s|x8 Kyogre-style fishing spawn weight"};
            for (int i = 0; i < 3; i++) {
                final int tr = 60 + i;
                final java.math.BigInteger p = java.math.BigInteger.valueOf(price[i]);
                boolean afford = dollars ? bal.compareTo(p) >= 0 : bp >= cost[i];
                boolean ok = afford && left == 0;
                String costLine = dollars ? "o|Cost: " + eco.format(p) : "o|Cost: " + cost[i] + " Badge Points";
                put(20 + 2 * i, mk(ic[i], 1, nm[i], ok, costLine, ln[i], ok ? "y|Click to use" : (left > 0 ? "r|Recharging" : "r|Not enough " + (dollars ? "CobbleDollars" : "Badge Points"))), () -> {
                    if (!dollars) { trig(tr, false); return; }
                    long l2 = Math.max(0, fakeScore(pl, "#next", "pp_wx") - pl.method_5682().method_30002().method_8510());
                    if (l2 > 0 || !eco.withdraw(pl, p)) { render(); return; }
                    runServer(pl, "scoreboard players set #paid pp_wx 1");
                    trig(tr, false);
                });
            }
            back(49);
        }

        void dexScreen() {
            int caught = dexCaught(pl);
            runServer(pl, "scoreboard players set " + pl.method_5477().getString() + " pp_dexc " + caught);
            int claimable = 0;
            for (int n : DEX_N) if (caught >= n && !tag(pl, "ppdex_" + n)) claimable++;
            put(4, mk("minecraft:book", 1, "Pokedex Milestones", true, "a|Caught species: " + caught, "s|Own a Pokemon to register it.", "y|Rewards ready to claim: " + claimable), null);
            int[] slots = {19, 20, 21, 22, 23, 24, 25, 31, 40};
            for (int i = 0; i < DEX_N.length; i++) {
                int n = DEX_N[i];
                boolean done = tag(pl, "ppdex_" + n), can = caught >= n && !done;
                put(slots[i], mk(done ? "minecraft:nether_star" : (can ? "minecraft:knowledge_book" : "minecraft:gray_dye"), 1, n + " species", can,
                        (done ? "g|Claimed" : (can ? "y|Ready - click to claim" : "r|" + (n - caught) + " to go")), "s|" + DEX_R[i]), null);
            }
            put(49, mk("minecraft:lime_dye", 1, "Claim all ready rewards", claimable > 0, claimable > 0 ? "y|" + claimable + " reward(s) ready" : "s|Nothing to claim yet"), () -> { runServer(pl, "scoreboard players set " + pl.method_5477().getString() + " pp_dexc " + dexCaught(pl)); trig(63, false); });
            put(45, mk("minecraft:arrow", 1, "Back", false), () -> go("main"));
        }

        void coop() {
            int w = score(pl, "pp_coop");
            boolean open = tag(pl, "pp_coopopen"), inArena = score(pl, "pp_slot") >= 1;
            put(4, mk("minecraft:book", 1, "Co-op Gyms", true,
                    "s|Fight a gym leader together.", "g|A team win gives BOTH players the badge,", "g|+50% Badge Points and co-op rewards.",
                    "a|Your team wins: " + w, "d|Both players must be on the same gym stage"), null);
            put(19, mk("cobblemon:poke_ball", 1, "1. Host: start a gym fight", false, "s|One of you starts a normal", "s|gym fight first.", "y|Click to fight your next gym leader"), () -> trig(1, true));
            put(21, mk(open ? "minecraft:lime_dye" : "minecraft:gray_dye", 1, "2. Host: lane " + (open ? "OPEN" : "closed"), open,
                    inArena ? "s|Click to " + (open ? "close" : "open") + " your lane for a partner" : "r|Start a gym fight first", "d|Partners can join while it is open"), () -> trig(42, false));
            put(23, mk("minecraft:ender_pearl", 1, "3. Partner: join a host", false, "s|Pick a friend with an open lane", "y|Click to see open lanes"), () -> go("coop_join"));
            put(25, mk("cobblemon:exp_candy_xl", 1, "4. Walk up to the trainer", false, "s|Right-click (or sneak + right-click)", "s|the trainer and send the co-op invite."), null);
            String nxt = w < 5 ? "5 wins: 5 Rare Candy" : w < 15 ? "15 wins: 10 XL Exp Candy + 50,000 CobbleDollars" : w < 30 ? "30 wins: Master Ball + 5% shiny odds" : w < 60 ? "60 wins: +1 heart + Tag Team title" : "All milestones claimed!";
            put(31, mk("minecraft:nether_star", 1, "Co-op milestones", w >= 60, "a|Team wins: " + w, (w >= 5 ? "g|" : "s|") + "5: 5 Rare Candy", (w >= 15 ? "g|" : "s|") + "15: 10 XL Exp Candy + 50k CobbleDollars",
                    (w >= 30 ? "g|" : "s|") + "30: Master Ball + 5% shiny odds", (w >= 60 ? "g|" : "s|") + "60: +1 heart + Tag Team title", "y|Next: " + nxt), null);
            back(49);
        }

        void coopJoin() {
            put(4, mk("minecraft:book", 1, "Open co-op lanes", true, "s|Click a host to teleport into", "s|their lane and fight together."), null);
            int n = 0;
            int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            if (score(pl, "pp_slot") >= 1) {
                put(22, mk("minecraft:barrier", 1, "You are in your own lane", false, "s|Leave the arena first, then", "s|join a partner."), null);
            } else {
                for (class_3222 p : pl.method_5682().method_3760().method_14571()) {
                    if (p == pl || !tag(p, "pp_coopopen") || tag(p, "pp_pend")) continue;
                    int slot = score(p, "pp_slot");
                    if (slot < 1 || n >= slots.length) continue;
                    put(slots[n++], mk("minecraft:player_head", 1, p.method_5477().getString(), true, "g|Open lane", "y|Click to join and fight together"), () -> trig(30000 + slot, true));
                }
                if (n == 0) put(22, mk("minecraft:gray_dye", 1, "Nobody has an open lane", false, "s|Ask your partner to start a fight", "s|and click \"open lane\" first."), null);
            }
            put(49, mk("minecraft:arrow", 1, "Back", false, "s|Co-op menu"), () -> go("coop"));
        }

        void mart() {
            put(4, mk("minecraft:book", 1, "Poke Mart", true, "s|Every village shopkeeper in one phone.", "y|Pays in CobbleDollars.", "d|Click a merchant to open their shop"), null);
            int per = 36, total = MartData.SHOPS.length, pages = Math.max(1, (total + per - 1) / per);
            if (page >= pages) page = pages - 1;
            for (int n = 0; n < per; n++) {
                int idx = page * per + n;
                if (idx >= total) break;
                String[] s = MartData.SHOPS[idx];
                int trig = Integer.parseInt(s[1]);
                put(9 + n, mk(s[3], 1, s[0], false, "s|" + s[2] + " items", "y|Click to open the shop"), () -> trig(trig, true));
            }
            if (page > 0) put(48, mk("minecraft:spectral_arrow", 1, "Previous page", false), () -> { page--; render(); });
            if (page + 1 < pages) put(50, mk("minecraft:spectral_arrow", 1, "Next page", false), () -> { page++; render(); });
            back(49);
        }

        void mods() {
            boolean iron = score(pl, "pp_modi") == 1, speed = score(pl, "pp_mods") == 1;
            put(20, mk(iron ? "minecraft:lime_dye" : "minecraft:gray_dye", 1, "Iron Mode: " + (iron ? "ON" : "off"), iron,
                    "s|No healing items in your inventory", "s|during the fight.", "a|Satisfied: x1.5 Badge Points", "y|Click to toggle"), () -> trig(71, false));
            put(24, mk(speed ? "minecraft:lime_dye" : "minecraft:gray_dye", 1, "Speedrun: " + (speed ? "ON" : "off"), speed,
                    "s|Win within 5 minutes of the", "s|leader appearing.", "a|Satisfied: x1.5 Badge Points", "y|Click to toggle"), () -> trig(72, false));
            back(49);
        }

        void badgeCase() {
            for (int i = 0; i < 6; i++) {
                String r = REG[i];
                put(9 * i, mk("minecraft:map", 1, REGN[i], false, "s|Champion clears"), null);
                String[] sf = {"", "_hard", "_elite"};
                String[] lab = {"Base", "Hard", "Elite"};
                String[] ball = {"cobblemon:poke_ball", "cobblemon:ultra_ball", "cobblemon:master_ball"};
                for (int t = 0; t < 3; t++) {
                    boolean done = adv(pl, "region/" + r + sf[t]);
                    List<String> lore = new ArrayList<>();
                    lore.add(done ? "g|Cleared" : "r|Not cleared");
                    if (t == 2) { int best = score(pl, "pp_best" + i); if (best > 0) lore.add("y|Best champion time: " + best + " s"); }
                    put(9 * i + 2 + 2 * t, done ? mk(ball[t], 1, REGN[i] + " " + lab[t], true, lore.toArray(new String[0])) : mk("minecraft:gray_dye", 1, REGN[i] + " " + lab[t], false, lore.toArray(new String[0])), null);
                }
            }
            put(8, trophy("grand_master", "World Champion", "All six regions"), null);
            put(17, trophy("grand_hard", "Grand Hard Champion", "All regions on Hard"), null);
            put(26, trophy("grand_elite", "Grand Elite Champion", "All regions on Elite"), null);
            back(53);
        }

        class_1799 trophy(String adv, String name, String desc) {
            boolean d = adv(pl, "region/" + adv);
            return d ? mk("minecraft:nether_star", 1, name, true, "g|Earned", "s|" + desc) : mk("minecraft:gray_dye", 1, name, false, "r|Not yet", "s|" + desc);
        }

        void rematchHub() {
            for (int i = 0; i < 6; i++) {
                String r = REG[i];
                boolean base = adv(pl, "region/" + r), hard = adv(pl, "region/" + r + "_hard");
                put(9 * i, mk("minecraft:map", 1, REGN[i] + " rematches", false), null);
                int hc = 0, ec = 0;
                for (int k = 1; k <= 13; k++) { if (tag(pl, "ppw1_" + (i * 20 + k))) hc++; if (tag(pl, "ppw2_" + (i * 20 + k))) ec++; }
                final int ri = i;
                put(9 * i + 2, base ? mk("cobblemon:ultra_ball", 1, REGN[i] + " - Hard", hc == 13, "g|Unlocked", "y|Cleared " + hc + "/13", "s|Gym teams +10 levels") : mk("minecraft:gray_dye", 1, REGN[i] + " - Hard", false, "r|Locked", "s|Beat the " + REGN[i] + " Champion first"),
                        base ? () -> { region = ri; tier = 1; go("rm_list"); } : null);
                put(9 * i + 4, hard ? mk("cobblemon:master_ball", 1, REGN[i] + " - Elite", ec == 13, "g|Unlocked", "y|Cleared " + ec + "/13", "s|All level 100 + extra legends") : mk("minecraft:gray_dye", 1, REGN[i] + " - Elite", false, "r|Locked", "s|Clear " + REGN[i] + " on Hard first"),
                        hard ? () -> { region = ri; tier = 2; go("rm_list"); } : null);
            }
            back(53);
        }

        void rematchList() {
            put(4, mk("minecraft:book", 1, REGN[region] + " - " + (tier == 1 ? "Hard" : "Elite") + " rematches", true, "s|Green = already cleared", "s|First clear pays x3 Badge Points"), null);
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 17, 29, 30, 32, 33, 40};
            for (int k = 1; k <= 13; k++) {
                int rank = region * 20 + k;
                String[] info = GymBoard.infoByRank(rank);
                if (info == null) continue;
                boolean cleared = tag(pl, "ppw" + tier + "_" + rank);
                String icon = k <= 8 ? "minecraft:iron_ingot" : (k <= 12 ? "minecraft:diamond" : "minecraft:netherite_ingot");
                final int val = (region + 1) * 1000 + tier * 100 + k;
                put(slots[k - 1], mk(icon, 1, info[2] + ": " + info[3], cleared,
                        cleared ? "g|Cleared" : "s|Not cleared yet", "y|Click to fight"), () -> trig(val, true));
            }
            put(49, mk("minecraft:arrow", 1, "Back", false), () -> go("rm_hub"));
        }

        void shop() {
            int bp = score(pl, "pp_bp");
            int el = 0;
            for (String r : REG) if (adv(pl, "region/" + r + "_elite")) el++;
            // category tabs
            int ti = 0;
            for (String[] c : ShopData.CATS) {
                final int cid = Integer.parseInt(c[0]);
                boolean on = cid == cat;
                put(1 + ti, mk("minecraft:oak_sign", 1, c[1], on, on ? "g|Showing" : "y|Click to open"), () -> { cat = cid; page = 0; render(); });
                ti++;
            }
            put(8, mk("minecraft:emerald", 1, "Badge Points: " + bp, true, "s|Elite regions cleared: " + el), null);
            List<String[]> items = new ArrayList<>();
            for (String[] it : ShopData.ITEMS) if (Integer.parseInt(it[0]) == cat) items.add(it);
            int per = 36, pages = Math.max(1, (items.size() + per - 1) / per);
            if (page >= pages) page = pages - 1;
            for (int n = 0; n < per; n++) {
                int idx = page * per + n;
                if (idx >= items.size()) break;
                String[] it = items.get(idx);
                int cost = Integer.parseInt(it[2]), req = Integer.parseInt(it[3]), trig = Integer.parseInt(it[4]);
                boolean locked = req > el, afford = bp >= cost;
                List<String> lore = new ArrayList<>();
                lore.add((afford ? "g|" : "r|") + "Cost: " + cost + " Badge Points");
                if (req > 0) lore.add((locked ? "r|" : "g|") + "Requires " + req + " Elite region" + (req > 1 ? "s" : "") + " (you have " + el + ")");
                lore.add(locked ? "r|Locked" : (afford ? "y|Click to buy" : "r|Not enough Badge Points"));
                String name = it[1];
                put(9 + n, mk(locked ? "minecraft:barrier" : it[5], 1, name, !locked && afford, lore.toArray(new String[0])), () -> trig(trig, false));
            }
            if (page > 0) put(48, mk("minecraft:spectral_arrow", 1, "Previous page", false), () -> { page--; render(); });
            put(49, mk("minecraft:paper", 1, "Page " + (page + 1) + "/" + pages, false), null);
            if (page + 1 < pages) put(50, mk("minecraft:spectral_arrow", 1, "Next page", false), () -> { page++; render(); });
            put(45, mk("minecraft:arrow", 1, "Back", false), () -> go("main"));
        }
    }
}
