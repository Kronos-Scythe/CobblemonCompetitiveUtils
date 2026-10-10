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
        try { ListNet.tick(server); } catch (Throwable t) { }
        try { Duo.tick(server); } catch (Throwable t) { }
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

    // ------------------------------------------------------------------ list-window versions (clients with the mod)
    /** Screens that have a list-window version for clients with the mod. */
    static final Set<String> LISTABLE = new HashSet<>(Arrays.asList("main", "shop", "dex", "coop_join", "rm_hub", "rm_list", "case", "mods", "coop", "weather", "duo", "duo_region"));
    static final Map<UUID, int[]> RM = new HashMap<>();

    static void openList(class_3222 pl, String screen) {
        switch (screen) {
            case "main": ListNet.open(pl, s -> mainList(pl, s)); break;
            case "shop": ListNet.open(pl, s -> shopList(pl, s)); break;
            case "dex": ListNet.open(pl, s -> dexList(pl, s)); break;
            case "rm_hub": ListNet.open(pl, s -> rmHubList(pl, s)); break;
            case "rm_list": ListNet.open(pl, s -> rmList(pl, s)); break;
            case "case": ListNet.open(pl, s -> caseList(pl, s)); break;
            case "mods": ListNet.open(pl, s -> modsList(pl, s)); break;
            case "coop": ListNet.open(pl, s -> coopList(pl, s)); break;
            case "weather": ListNet.open(pl, s -> weatherList(pl, s)); break;
            case "duo": ListNet.open(pl, s -> duoList(pl, s)); break;
            case "duo_region": ListNet.open(pl, s -> duoRegionList(pl, s)); break;
            default: ListNet.open(pl, s -> laneList(pl, s)); break;
        }
    }

    static void shopList(class_3222 pl, ListNet.Spec s) {
        int bp = score(pl, "pp_bp");
        int el = 0;
        for (String r : REG) if (adv(pl, "region/" + r + "_elite")) el++;
        s.title = "Badge Point Shop"; s.back = true;
        s.info = "Badge Points: " + bp;
        Map<Integer, Integer> gi = new HashMap<>();
        for (String[] c : ShopData.CATS) { gi.put(Integer.parseInt(c[0]), s.groups.size()); s.groups.add(c[1]); }
        for (String[] it : ShopData.ITEMS) {
            int cat = Integer.parseInt(it[0]), cost = Integer.parseInt(it[2]), req = Integer.parseInt(it[3]), trig = Integer.parseInt(it[4]);
            boolean locked = req > el, afford = bp >= cost;
            String name = it[1];
            String sub = locked ? "Requires " + req + " Elite region" + (req > 1 ? "s" : "") + " (you have " + el + ")" : (req > 0 ? "Elite requirement met" : "");
            int state = locked ? 1 : (afford ? 0 : 3);
            String tip = (locked ? "Locked\n" : (afford ? "Click to buy\n" : "Not enough Badge Points\n")) + "Cost: " + cost + " BP";
            class_1799 st = stackOf(it[5], "");
            if (st.method_7960()) st = mk("minecraft:paper", 1, name, false);
            s.add(ListNet.Row.of(st, name, sub, cost + " BP", gi.getOrDefault(cat, 0), state, tip), () -> {
                if (locked) { ListNet.note(pl, name + " is locked: clear more regions on Elite.", true); return; }
                if (score(pl, "pp_bp") < cost) { ListNet.note(pl, "Not enough Badge Points for " + name + ".", true); return; }
                run(pl, "trigger gym set " + trig);
                ListNet.note(pl, "Bought " + name + " (-" + cost + " BP)", false);
            });
        }
    }

    static void dexList(class_3222 pl, ListNet.Spec s) {
        int caught = dexCaught(pl);
        runServer(pl, "scoreboard players set " + pl.method_5477().getString() + " pp_dexc " + caught);
        int ready = 0;
        for (int n : DEX_N) if (caught >= n && !tag(pl, "ppdex_" + n)) ready++;
        s.title = "Pokedex Milestones"; s.back = true;
        s.info = "Caught: " + caught;
        for (int i = 0; i < DEX_N.length; i++) {
            int n = DEX_N[i];
            boolean done = tag(pl, "ppdex_" + n), can = caught >= n && !done;
            String icon = done ? "minecraft:nether_star" : (can ? "minecraft:knowledge_book" : "minecraft:gray_dye");
            String right = done ? "Claimed" : (can ? "CLAIM" : (n - caught) + " to go");
            class_1799 st = stackOf(icon, "");
            s.add(ListNet.Row.of(st, n + " species", DEX_R[i], right, -1, done ? 2 : (can ? 4 : 0), done ? "Already claimed" : (can ? "Click to claim" : "Register " + (n - caught) + " more species")), () -> {
                if (can) {
                    runServer(pl, "scoreboard players set " + pl.method_5477().getString() + " pp_dexc " + dexCaught(pl));
                    run(pl, "trigger gym set 63");
                    ListNet.note(pl, "Claimed the " + n + " species reward", false);
                } else ListNet.note(pl, done ? "Already claimed." : "Not there yet: " + (n - caught) + " more species.", true);
            });
        }
        final int readyF = ready;
        if (ready > 1) {
            s.add(ListNet.Row.of(stackOf("minecraft:lime_dye", ""), "Claim all ready rewards", ready + " rewards ready", "CLAIM ALL", -1, 4, "Claims every milestone you qualify for"), () -> {
                runServer(pl, "scoreboard players set " + pl.method_5477().getString() + " pp_dexc " + dexCaught(pl));
                run(pl, "trigger gym set 63");
                ListNet.note(pl, "Claimed " + readyF + " rewards", false);
            });
        }
    }


    // ------------------------------------------------------------------ list versions of the phone / Gym Challenge screens
    private static class_1799 ic(String id) {
        class_1799 st = stackOf(id, "");
        return st.method_7960() ? mk("minecraft:paper", 1, " ", false) : st;
    }

    private static void nav(class_3222 pl, String screen) { open(pl, screen); }

    private static void closeAnd(class_3222 pl, String command) { ListNet.close(pl); run(pl, command); }

    static void mainList(class_3222 pl, ListNet.Spec s) {
        int bp = score(pl, "pp_bp"), heals = score(pl, "pp_heals"), wins = score(pl, "pp_wins"), gym = score(pl, "pp_gym");
        long day = pl.method_5682().method_30002().method_8532() / 24000L;
        String feat = REGN[(int) ((day / 7) % 6)];
        s.title = "Gym Challenge"; s.back = false; s.tiles = true;
        Duo.Rec dr = Duo.active(pl);
        s.info = (dr != null ? "DUO RUN with " + dr.otherName(pl.method_5667().toString()) + "   |   " : "") + bp + " BP   |   " + heals + " heals";
        s.hint = "Featured region: " + feat + " (x2 Badge Points)   |   Total wins: " + wins + "   |   Type to search";
        s.groups.addAll(Arrays.asList("Battle", "Pokemon", "Shops", "Progress", "Raids", "Social"));
        final int G_BATTLE = 0, G_MON = 1, G_SHOP = 2, G_PROG = 3, G_RAID = 4, G_SOC = 5;

        String[] info = GymBoard.infoByRank(gym);
        String next = info == null ? "Beat the Champion to unlock rematches" : info[1] + " " + info[2] + ": " + info[3];
        s.add(ListNet.Row.of(ic("cobblemon:poke_ball"), "Next: " + next, info == null ? "Pick a region to continue, or replay gyms from Rematches" : "Level cap " + capOf(pl) + (dr != null ? "   |   Duo run" : "   |   Solo run"), "FIGHT", G_BATTLE, 4, "Teleports you to an arena"),
                () -> closeAnd(pl, "trigger gym set 1"));
        s.add(ListNet.Row.of(ic("cobblemon:ultra_ball"), "Rematches (Hard / Elite)", "Replay any gym on harder teams for Badge Points", ">", G_BATTLE, 0, "Hard: +10 levels. Elite: level 100 with extra legends."),
                () -> nav(pl, "rm_hub"));
        if (dr != null) {
            s.add(ListNet.Row.of(ic("minecraft:map"), "Pick a region", "Duo run: choose where you and your partner play", ">", G_BATTLE, 0, "Any region, in any order"),
                    () -> nav(pl, "duo_region"));
        } else {
        int nx = 0; while (nx < 6 && adv(pl, "region/" + REGN[nx].toLowerCase())) nx++;
        if (nx == 0) s.add(ListNet.Row.of(ic("minecraft:compass"), "Start next region", "Beat the Kanto Champion first", "LOCKED", G_BATTLE, 1, "Clear Kanto to switch to Johto here"), null);
        else if (nx >= 6) s.add(ListNet.Row.of(ic("minecraft:compass"), "Start next region", "All six regions conquered. Use Rematches for Hard / Elite", "DONE", G_BATTLE, 2, "Every region cleared"), null);
        else {
            final String to = REGN[nx];
            s.add(ListNet.Row.of(ic("minecraft:compass"), "Start next region: " + to, REGN[nx - 1] + " conquered - switches your series to " + to, "START", G_BATTLE, 4, "Then use Fight my next Gym Leader"),
                    () -> closeAnd(pl, "trigger gym set 46"));
        }
        }
        s.add(ListNet.Row.of(ic("minecraft:comparator"), "Challenge Modifiers", "Iron Mode / Speedrun for x1.5 Badge Points", ">", G_BATTLE, 0, "Optional rules for extra Badge Points"),
                () -> nav(pl, "mods"));
        s.add(ListNet.Row.of(ic("minecraft:barrier"), "Leave the arena", "Return to where you were", "", G_BATTLE, 0, "Leaves the current gym arena"),
                () -> closeAnd(pl, "trigger gym set 2"));

        s.add(ListNet.Row.of(ic("cobblemon:full_restore"), "Heal my party", "Charges left today: " + heals + " (refills daily)", heals > 0 ? "x" + heals : "0 left", G_MON, heals > 0 ? 0 : 3, "Fully heals your party"),
                () -> run(pl, "trigger gym set 8"));
        s.add(ListNet.Row.of(ic("minecraft:enchanted_book"), "Move Tutor", "Teach your party any move it can learn, including egg moves", ">", G_MON, 0, "Opens the Move Tutor"),
                () -> closeAnd(pl, "movetutor"));
        s.add(ListNet.Row.of(ic("cobblemon:protein"), "EV Editor", "Set your party's EVs", ">", G_MON, 0, "Opens the EV Editor"),
                () -> closeAnd(pl, "evedit"));
        s.add(ListNet.Row.of(ic("minecraft:spyglass"), "Scout trainers", "Preview the next opponent's team and level cap", ">", G_MON, 0, "Opens the scouting list"),
                () -> closeAnd(pl, "scout"));

        s.add(ListNet.Row.of(ic("minecraft:emerald"), "Badge Point Shop", "You have " + bp + " BP: mints, held items, tera shards", ">", G_SHOP, 0, "Spend Badge Points"),
                () -> nav(pl, "shop"));
        s.add(ListNet.Row.of(ic("minecraft:villager_spawn_egg"), "Poke Mart", "Every village shopkeeper in one place (CobbleDollars)", ">", G_SHOP, 0, "Opens the Poke Mart"),
                () -> { ListNet.close(pl); openMartFor(pl); });
        s.add(ListNet.Row.of(ic("minecraft:lightning_rod"), "Weather Machine", "Clear skies, rain or a thunderstorm. Great for fishing legendaries", ">", G_SHOP, 0, "Changes the weather for the whole server"),
                () -> nav(pl, "weather"));

        s.add(ListNet.Row.of(ic("minecraft:writable_book"), "Badge Case", "Your progress in every region", ">", G_PROG, 0, "Base, Hard and Elite clears"),
                () -> nav(pl, "case"));
        int caught = dexCaught(pl), ready = 0;
        for (int n : DEX_N) if (caught >= n && !tag(pl, "ppdex_" + n)) ready++;
        s.add(ListNet.Row.of(ic("minecraft:knowledge_book"), "Pokedex Milestones", "Caught species: " + caught, ready > 0 ? ready + " READY" : ">", G_PROG, ready > 0 ? 4 : 0, "Rewards for registering Pokemon"),
                () -> nav(pl, "dex"));
        int qr = Engage.readyCount(pl);
        s.add(ListNet.Row.of(ic("minecraft:book"), "Daily & Weekly Quests", "Three daily and three weekly goals", qr > 0 ? qr + " READY" : ">", G_PROG, qr > 0 ? 4 : 0, "Ready to claim: " + qr),
                () -> { ListNet.close(pl); Engage.openQuests(pl); });
        s.add(ListNet.Row.of(ic("minecraft:gold_ingot"), "Leaderboard", "Top trainers: badges, dex, wins, Rogue clears", ">", G_PROG, 0, "Opens the leaderboard"),
                () -> { ListNet.close(pl); Engage.openBoard(pl); });

        boolean kanto = adv(pl, "region/kanto");
        s.add(ListNet.Row.of(ic("minecraft:tripwire_hook"), "Claim Raid Den Key", kanto ? "Unlocked: tier 6/7 raids. Claim a replacement key" : "Beat the Kanto Champion first", kanto ? "CLAIM" : "LOCKED", G_RAID, kanto ? 0 : 1, "Gives a replacement Raid Den key"),
                () -> run(pl, "trigger gym set 9"));
        int vch = RaidPass.vouchers(pl); boolean pact = RaidPass.active(pl);
        String psub = pact ? "ACTIVE: " + RaidPass.fmt(RaidPass.remainingMs(pl)) + " left   |   Vouchers: " + vch
                : (vch > 0 ? "1 hour of /rqueue on 7-star raids   |   Vouchers: " + vch : "Clear a region (Base, Hard or Elite) to earn a voucher");
        s.add(ListNet.Row.of(ic("minecraft:nether_star"), "Tier 7 Raid Pass", psub, pact ? "ACTIVE" : (vch > 0 ? "ACTIVATE" : ""), G_RAID, pact ? 2 : (vch > 0 ? 4 : 1), "Earned for every region clear"),
                () -> RaidPass.activate(pl));

        Duo.Rec dr2 = Duo.recOf(pl);
        Duo.Invite inv = Duo.inviteFor(pl);
        String dsub = inv != null ? inv.fromName + " invited you" : (dr2 == null ? "Play together on a shared run" : (dr != null ? "On the Duo run with " + dr2.otherName(pl.method_5667().toString()) : "Paired with " + dr2.otherName(pl.method_5667().toString())));
        s.add(ListNet.Row.of(ic("minecraft:totem_of_undying"), "Duo run", dsub, inv != null ? "INVITE" : (dr != null ? "ON" : ">"), G_SOC, inv != null || dr != null ? 4 : 0, "A separate co-op track: shared gym stage, badges and level cap"),
                () -> nav(pl, "duo"));
        s.add(ListNet.Row.of(ic("minecraft:lead"), "Trade Pokemon", "Swap Pokemon with another player", "/trade", G_SOC, 0, "Type /trade <player> to start"),
                () -> {
                    ListNet.close(pl);
                    pl.method_7353(class_2561.method_43470("§6Type §e/trade <player>§6 to start a trade. Both players see a confirm window."), false);
                });
    }

    static void rmHubList(class_3222 pl, ListNet.Spec s) {
        s.title = "Rematches"; s.back = true;
        s.info = "First clear pays x3 BP";
        s.hint = "Hard unlocks after a region's Champion. Elite unlocks after Hard.";
        s.groups.add("Hard"); s.groups.add("Elite");
        for (int tier = 1; tier <= 2; tier++) {
            for (int i = 0; i < 6; i++) {
                String r = REG[i];
                boolean base = adv(pl, "region/" + r), hard = adv(pl, "region/" + r + "_hard");
                boolean unlocked = tier == 1 ? base : hard;
                int cnt = 0;
                for (int k = 1; k <= 13; k++) if (tag(pl, "ppw" + tier + "_" + (i * 20 + k))) cnt++;
                final int ri = i, ti = tier;
                String name = REGN[i] + " - " + (tier == 1 ? "Hard" : "Elite");
                String icon = unlocked ? (tier == 1 ? "cobblemon:ultra_ball" : "cobblemon:master_ball") : "minecraft:gray_dye";
                String sub = unlocked ? "Cleared " + cnt + "/13   |   " + (tier == 1 ? "Gym teams +10 levels" : "All level 100 + extra legends")
                        : "Locked: clear " + REGN[i] + (tier == 1 ? "" : " Hard") + " first";
                s.add(ListNet.Row.of(ic(icon), name, sub, unlocked ? cnt + "/13" : "LOCKED", tier - 1, unlocked ? (cnt == 13 ? 2 : 0) : 1,
                        unlocked ? "Click to pick a gym" : "Clear " + REGN[i] + (tier == 1 ? " Champion" : " on Hard") + " to unlock"),
                        unlocked ? () -> { RM.put(pl.method_5667(), new int[]{ri, ti}); nav(pl, "rm_list"); } : () -> ListNet.note(pl, name + " is locked.", true));
            }
        }
    }

    static void rmList(class_3222 pl, ListNet.Spec s) {
        int[] rt = RM.getOrDefault(pl.method_5667(), new int[]{0, 1});
        final int region = rt[0], tier = rt[1];
        s.title = REGN[region] + " " + (tier == 1 ? "Hard" : "Elite") + " rematches"; s.back = true;
        s.backAction = () -> nav(pl, "rm_hub");
        s.hint = "Green = already cleared   |   First clear pays x3 Badge Points";
        s.groups.add("Gym Leaders"); s.groups.add("Elite Four"); s.groups.add("Champion");
        int done = 0;
        for (int k = 1; k <= 13; k++) if (tag(pl, "ppw" + tier + "_" + (region * 20 + k))) done++;
        s.info = done + "/13 cleared";
        for (int k = 1; k <= 13; k++) {
            int rank = region * 20 + k;
            String[] info = GymBoard.infoByRank(rank);
            if (info == null) continue;
            boolean cleared = tag(pl, "ppw" + tier + "_" + rank);
            String icon = k <= 8 ? "minecraft:iron_ingot" : (k <= 12 ? "minecraft:diamond" : "minecraft:netherite_ingot");
            final int val = (region + 1) * 1000 + tier * 100 + k;
            s.add(ListNet.Row.of(ic(icon), info[2] + ": " + info[3], cleared ? "Cleared" : "Not cleared yet", cleared ? "CLEARED" : "FIGHT", k <= 8 ? 0 : (k <= 12 ? 1 : 2), cleared ? 2 : 0,
                    "Click to fight"), () -> closeAnd(pl, "trigger gym set " + val));
        }
    }

    static void caseList(class_3222 pl, ListNet.Spec s) {
        s.title = "Badge Case"; s.back = true;
        s.hint = "Your Champion clears in every region";
        s.groups.addAll(Arrays.asList("Base", "Hard", "Elite", "Trophies"));
        String[] sf = {"", "_hard", "_elite"};
        String[] lab = {"Base", "Hard", "Elite"};
        String[] ball = {"cobblemon:poke_ball", "cobblemon:ultra_ball", "cobblemon:master_ball"};
        int total = 0;
        for (int t = 0; t < 3; t++) {
            for (int i = 0; i < 6; i++) {
                boolean done = adv(pl, "region/" + REG[i] + sf[t]);
                if (done) total++;
                String sub = done ? "Cleared" : "Not cleared";
                if (t == 2 && done) { int best = score(pl, "pp_best" + i); if (best > 0) sub += "   |   Best champion time: " + best + " s"; }
                s.add(ListNet.Row.of(ic(done ? ball[t] : "minecraft:gray_dye"), REGN[i] + " " + lab[t], sub, done ? "CLEARED" : "", t, done ? 2 : 0, ""), null);
            }
        }
        s.info = total + "/18 clears";
        String[][] tr = {{"grand_master", "World Champion", "All six regions"}, {"grand_hard", "Grand Hard Champion", "All regions on Hard"}, {"grand_elite", "Grand Elite Champion", "All regions on Elite"}};
        for (String[] t : tr) {
            boolean d = adv(pl, "region/" + t[0]);
            s.add(ListNet.Row.of(ic(d ? "minecraft:nether_star" : "minecraft:gray_dye"), t[1], t[2], d ? "EARNED" : "", 3, d ? 2 : 0, ""), null);
        }
    }

    static void modsList(class_3222 pl, ListNet.Spec s) {
        boolean iron = score(pl, "pp_modi") == 1, speed = score(pl, "pp_mods") == 1;
        s.title = "Challenge Modifiers"; s.back = true;
        s.info = "x1.5 Badge Points";
        s.hint = "Click to toggle a modifier before your next gym fight";
        s.add(ListNet.Row.of(ic(iron ? "minecraft:lime_dye" : "minecraft:gray_dye"), "Iron Mode", "No healing items in your inventory during the fight", iron ? "ON" : "off", -1, iron ? 4 : 0,
                "Satisfied: x1.5 Badge Points\nClick to toggle"), () -> run(pl, "trigger gym set 71"));
        s.add(ListNet.Row.of(ic(speed ? "minecraft:lime_dye" : "minecraft:gray_dye"), "Speedrun", "Win within 5 minutes of the leader appearing", speed ? "ON" : "off", -1, speed ? 4 : 0,
                "Satisfied: x1.5 Badge Points\nClick to toggle"), () -> run(pl, "trigger gym set 72"));
    }

    static void coopList(class_3222 pl, ListNet.Spec s) {
        int w = score(pl, "pp_coop");
        boolean open = tag(pl, "pp_coopopen"), inArena = score(pl, "pp_slot") >= 1;
        s.title = "Co-op Gyms"; s.back = true;
        s.info = "Team wins: " + w;
        s.hint = "A team win gives BOTH players the badge, +50% Badge Points and co-op rewards. Same gym stage required.";
        s.groups.add("How it works"); s.groups.add("Milestones");
        s.add(ListNet.Row.of(ic("cobblemon:poke_ball"), "1. Host: start a gym fight", "One of you starts a normal gym fight first", "FIGHT", 0, 4, "Fights your next gym leader"),
                () -> closeAnd(pl, "trigger gym set 1"));
        s.add(ListNet.Row.of(ic(open ? "minecraft:lime_dye" : "minecraft:gray_dye"), "2. Host: lane " + (open ? "OPEN" : "closed"),
                inArena ? "Click to " + (open ? "close" : "open") + " your lane for a partner" : "Start a gym fight first", open ? "OPEN" : (inArena ? "CLOSED" : ""), 0, open ? 4 : (inArena ? 0 : 1),
                "Partners can join while it is open"), () -> run(pl, "trigger gym set 42"));
        s.add(ListNet.Row.of(ic("minecraft:ender_pearl"), "3. Partner: join a host", "Pick a friend with an open lane", ">", 0, 0, "Opens the lane list"), () -> nav(pl, "coop_join"));
        s.add(ListNet.Row.of(ic("cobblemon:exp_candy_xl"), "4. Walk up to the trainer", "Right-click (or sneak + right-click) the trainer and send the invite", "", 0, 0, ""), null);
        int[] need = {5, 15, 30, 60};
        String[] rw = {"5 Rare Candy", "10 XL Exp Candy + 50,000 CobbleDollars", "Master Ball + 5% shiny odds", "+1 heart + Tag Team title"};
        for (int i = 0; i < need.length; i++) {
            boolean got = w >= need[i];
            s.add(ListNet.Row.of(ic(got ? "minecraft:nether_star" : "minecraft:gray_dye"), need[i] + " team wins", rw[i], got ? "DONE" : (need[i] - w) + " to go", 1, got ? 2 : 0, ""), null);
        }
    }

    static void weatherList(class_3222 pl, ListNet.Spec s) {
        org.cobbleutils.cobblecomputils.economy.Economy eco = org.cobbleutils.cobblecomputils.Cobblecomputils.economy();
        boolean dollars = !eco.isFree();
        int bp = score(pl, "pp_bp");
        java.math.BigInteger bal = dollars ? eco.balance(pl) : java.math.BigInteger.ZERO;
        long now = pl.method_5682().method_30002().method_8510();
        long left = Math.max(0, fakeScore(pl, "#next", "pp_wx") - now);
        s.title = "Weather Machine"; s.back = true;
        s.info = dollars ? eco.format(bal) : bp + " BP";
        s.hint = left > 0 ? "Recharging: " + ((left + 1199) / 1200) + " min left (shared cooldown)" : "Changes the weather for the whole server";
        String[] ic = {"minecraft:sunflower", "minecraft:water_bucket", "minecraft:lightning_rod"};
        String[] nm = {"Clear skies (20 min)", "Rain (10 min)", "Thunderstorm (7.5 min)"};
        long[] price = {20000L, 30000L, 50000L};
        int[] cost = {30, 60, 120};
        String[] ln = {"Ends rain and storms", "Needed for rain-only spawns", "x8 Kyogre-style fishing spawn weight"};
        for (int i = 0; i < 3; i++) {
            final int tr = 60 + i;
            final java.math.BigInteger p = java.math.BigInteger.valueOf(price[i]);
            boolean afford = dollars ? bal.compareTo(p) >= 0 : bp >= cost[i];
            boolean ok = afford && left == 0;
            String costTxt = dollars ? eco.format(p) : cost[i] + " BP";
            s.add(ListNet.Row.of(ic(ic[i]), nm[i], ln[i], left > 0 ? "WAIT" : costTxt, -1, ok ? 0 : (left > 0 ? 1 : 3),
                    ok ? "Click to use" : (left > 0 ? "Recharging" : "Not enough " + (dollars ? "CobbleDollars" : "Badge Points"))), () -> {
                if (!dollars) { run(pl, "trigger gym set " + tr); return; }
                long l2 = Math.max(0, fakeScore(pl, "#next", "pp_wx") - pl.method_5682().method_30002().method_8510());
                if (l2 > 0 || !eco.withdraw(pl, p)) { ListNet.note(pl, "Not available right now.", true); return; }
                runServer(pl, "scoreboard players set #paid pp_wx 1");
                run(pl, "trigger gym set " + tr);
            });
        }
    }

    static String capOf(class_3222 p) {
        try {
            java.util.OptionalInt c = org.cobbleutils.cobblecomputils.integration.RctBridge.levelCap(p);
            return c.isPresent() ? String.valueOf(c.getAsInt()) : "none";
        } catch (Throwable t) { return "?"; }
    }

    static void laneList(class_3222 pl, ListNet.Spec s) {
        s.title = "Open co-op lanes"; s.back = true; s.backAction = () -> nav(pl, "coop");
        s.info = "Pick a host to fight beside";
        if (score(pl, "pp_slot") >= 1) {
            s.add(ListNet.Row.of(stackOf("minecraft:barrier", ""), "You are in your own lane", "Leave the arena first, then join a partner", "", -1, 1, "Leave the arena first"), null);
            return;
        }
        final String myCap = capOf(pl);
        s.info = "Pick a host to fight beside   |   Your level cap: " + myCap;
        int n = 0;
        for (class_3222 p : pl.method_5682().method_3760().method_14571()) {
            if (p == pl || !tag(p, "pp_coopopen") || tag(p, "pp_pend")) continue;
            int slot = score(p, "pp_slot");
            if (slot < 1) continue;
            n++;
            String name = p.method_5477().getString();
            String hostCap = capOf(p);
            int hg = score(p, "pp_gym"), mg = score(pl, "pp_gym");
            String why = hg >= 1 ? (mg >= hg ? null : "You have not reached this gym yet (clear your own next gym first)") : (mg >= 1 ? "Rematch lane: finish your series first" : null);
            if (why != null) {
                s.add(ListNet.Row.of(stackOf("minecraft:player_head", ""), name, why, "LOCKED", -1, 1, why + "\nYour level cap: " + myCap + "\nHost level cap: " + hostCap), null);
                continue;
            }
            s.add(ListNet.Row.of(stackOf("minecraft:player_head", ""), name, "Gym stage " + slot + "  |  Level cap: you " + myCap + ", " + name + " " + hostCap, "JOIN", -1, 4,
                    "Click to teleport into " + name + "'s lane\nYour level cap: " + myCap + "\nHost level cap: " + hostCap + "\nPokemon above the cap are held back in gym fights"), () -> {
                pl.method_7353(class_2561.method_43470("\u00a76Co-op: \u00a7fyour level cap " + myCap + ", " + name + "'s cap " + hostCap + "."), false);
                run(pl, "trigger gym set " + (30000 + slot));
                ListNet.close(pl);
            });
        }
        if (n == 0) s.add(ListNet.Row.of(stackOf("minecraft:gray_dye", ""), "Nobody has an open lane", "Ask your partner to start a fight and open their lane first", "", -1, 1, "No open lanes right now"), null);
    }


    // ------------------------------------------------------------------ Duo run
    static final Set<UUID> DUO_CONFIRM = new HashSet<>();

    private static void duoDo(class_3222 pl, String err, String ok) {
        ListNet.note(pl, err != null ? err : ok, err != null);
    }

    static void duoList(class_3222 pl, ListNet.Spec s) {
        String me = pl.method_5667().toString();
        Duo.Rec r = Duo.recOf(pl);
        boolean on = Duo.onDuoRun(pl);
        s.title = "Duo Run"; s.back = true; s.backAction = () -> nav(pl, "main");
        s.groups.addAll(Arrays.asList("Run", "Together", "Partner"));
        final int G_RUN = 0, G_TOG = 1, G_PART = 2;
        if (r == null) {
            s.info = "No partner yet";
            s.hint = "A Duo run is its own track: you and your partner share one gym stage, one set of badges and one level cap. Your solo run is untouched.";
            Duo.Invite inv = Duo.inviteFor(pl);
            if (inv != null) {
                s.add(ListNet.Row.of(ic("minecraft:lime_dye"), "Accept the invite from " + inv.fromName, "Become a fixed duo with " + inv.fromName, "ACCEPT", G_RUN, 4, "You stay partners until one of you leaves"),
                        () -> duoDo(pl, Duo.accept(pl), "You are now a duo"));
                s.add(ListNet.Row.of(ic("minecraft:red_dye"), "Decline", "Ignore this invite", "", G_RUN, 0, ""),
                        () -> duoDo(pl, Duo.decline(pl), "Invite declined"));
            }
            int n = 0;
            for (class_3222 p : pl.method_5682().method_3760().method_14571()) {
                if (p == pl || Duo.recOf(p) != null) continue;
                n++;
                String name = p.method_5477().getString();
                s.add(ListNet.Row.of(stackOf("minecraft:player_head", ""), "Invite " + name, "Sends a duo invite (lasts 2 minutes)", "INVITE", G_RUN, 0, "They accept from their phone menu > Duo run"),
                        () -> duoDo(pl, Duo.invite(pl, name), "Invite sent to " + name));
            }
            if (n == 0 && inv == null) s.add(ListNet.Row.of(ic("minecraft:gray_dye"), "Nobody to invite right now", "Ask a friend to log in. You can also use /duo invite <player>", "", G_RUN, 1, ""), null);
            return;
        }
        String partnerName = r.otherName(me);
        class_3222 pp = Duo.partner(pl.method_5682(), pl);
        s.info = "Partner: " + partnerName + (pp == null ? " (offline)" : "");
        // ---- run switch
        if (!on) {
            s.add(ListNet.Row.of(ic("minecraft:totem_of_undying"), "Switch to the Duo run", "Your solo progress is saved and comes back exactly as it was", "START", G_RUN, 4, "Shared gym stage, badges and level cap with " + partnerName),
                    () -> duoDo(pl, Duo.enter(pl), "Duo run started"));
        } else {
            s.add(ListNet.Row.of(ic("minecraft:compass"), "Back to my solo run", "Your Duo progress stays saved for later", "SOLO", G_RUN, 0, "Restores your own gym stage and badges"),
                    () -> duoDo(pl, Duo.exit(pl), "Back on your solo run"));
            int idx = Arrays.asList(Duo.REG).indexOf(r.series);
            String regName = idx >= 0 ? Duo.REGN[idx] : r.series;
            String[] info = GymBoard.infoByRank(score(pl, "pp_gym"));
            String stage = info == null ? "All gyms cleared for now" : info[2] + ": " + info[3];
            s.add(ListNet.Row.of(ic("minecraft:filled_map"), regName + ": " + stage, "Badges " + Duo.badges(r, r.series) + "/8   |   Shared level cap " + capOf(pl), "", G_RUN, 2, "This is where the duo is right now"), null);
            s.add(ListNet.Row.of(ic("minecraft:map"), "Pick a region", "Either of you can choose where the duo plays next", ">", G_RUN, 0, "Any region, in any order"),
                    () -> nav(pl, "duo_region"));
            s.add(ListNet.Row.of(ic("cobblemon:poke_ball"), "Fight the next gym", "Starts a normal fight on the duo's stage", "FIGHT", G_TOG, 0, "Teleports you to an arena"),
                    () -> closeAnd(pl, "trigger gym set 1"));
            s.add(ListNet.Row.of(ic("minecraft:ender_pearl"), "Fight together (co-op lane)", "Host a lane or join " + partnerName + "'s lane for a team fight", ">", G_TOG, 0, "Team wins pay both of you"),
                    () -> nav(pl, "coop"));
            if (pp != null && tag(pp, "pp_coopopen") && !tag(pp, "pp_pend") && score(pp, "pp_slot") >= 1 && score(pl, "pp_slot") < 1) {
                final int slot = score(pp, "pp_slot");
                s.add(ListNet.Row.of(stackOf("minecraft:player_head", ""), "Join " + partnerName + "'s lane", partnerName + " has an open lane right now", "JOIN", G_TOG, 4, "Teleports you into their arena"),
                        () -> { run(pl, "trigger gym set " + (30000 + slot)); ListNet.close(pl); });
            }
        }
        // ---- partner / pair
        int pi = Arrays.asList(Duo.REG).indexOf(r.series);
        String ps = (pi >= 0 ? Duo.REGN[pi] : r.series) + ", " + Duo.badges(r, r.series) + "/8 badges, " + r.defeated.size() + " trainers beaten";
        s.add(ListNet.Row.of(stackOf("minecraft:player_head", ""), partnerName, (pp == null ? "Offline" : "Online") + "   |   Duo: " + ps, "", G_PART, 0, "Duo progress is shared and saved"), null);
        boolean sure = DUO_CONFIRM.contains(pl.method_5667());
        s.add(ListNet.Row.of(ic("minecraft:barrier"), sure ? "Click again to end the duo" : "End the duo", sure ? "Both of you go back to your solo runs" : "Leave this partner. Solo progress is not affected", sure ? "CONFIRM" : "", G_PART, sure ? 3 : 0, "Duo progress is deleted"),
                () -> {
                    if (!DUO_CONFIRM.remove(pl.method_5667())) { DUO_CONFIRM.add(pl.method_5667()); ListNet.note(pl, "Click again to end the duo.", true); return; }
                    duoDo(pl, Duo.leave(pl), "Duo ended");
                });
    }

    static void duoRegionList(class_3222 pl, ListNet.Spec s) {
        Duo.Rec r = Duo.active(pl);
        s.title = "Duo: pick a region"; s.back = true; s.backAction = () -> nav(pl, "duo");
        if (r == null) {
            s.add(ListNet.Row.of(ic("minecraft:barrier"), "Switch to the Duo run first", "Regions are picked while you are on the Duo run", "", -1, 1, ""), null);
            return;
        }
        s.info = "Both partners play the region you pick";
        s.hint = "Pick any region in any order. Progress in each region is kept.";
        String[] icons = {"cobblemon:poke_ball", "cobblemon:great_ball", "cobblemon:ultra_ball", "cobblemon:premier_ball", "cobblemon:luxury_ball", "cobblemon:master_ball"};
        for (int i = 0; i < Duo.REG.length; i++) {
            final String reg = Duo.REG[i];
            boolean cur = reg.equals(r.series);
            int done = r.completed.getOrDefault(reg, 0), b = Duo.badges(r, reg);
            String sub = (done > 0 ? "Cleared" : (b > 0 || r.defeated.stream().anyMatch(d -> d.startsWith(reg + "_")) ? "In progress" : "Not started")) + "   |   Badges " + b + "/8";
            s.add(ListNet.Row.of(ic(icons[i]), Duo.REGN[i], sub, cur ? "CURRENT" : "PICK", -1, cur ? 2 : (done > 0 ? 0 : 0), cur ? "You are here" : "Click to move the duo to " + Duo.REGN[i]),
                    cur ? null : () -> duoDo(pl, Duo.pickRegion(pl, reg), "Region set"));
        }
    }

    // ------------------------------------------------------------------ phone search
    static void openSearch(class_3222 pl) {
        if (ListNet.hasClient(pl)) { ListNet.open(pl, s -> searchList(pl, s)); return; }
        pl.method_7353(class_2561.method_43470("\u00a76Phone search needs the Cobblemon Competitive Utils mod. Use \u00a7e/quests\u00a76, \u00a7e/leaderboard\u00a76, \u00a7e/trade\u00a76, \u00a7e/movetutor\u00a76, \u00a7e/evedit\u00a76."), false);
    }

    private static void srow(ListNet.Spec s, String icon, String title, String keys, int group, Runnable act) {
        s.add(ListNet.Row.of(stackOf(icon, ""), title, keys, "OPEN", group, 0, "Click to open"), act);
    }

    static void searchList(class_3222 pl, ListNet.Spec s) {
        s.title = "Search the phone"; s.back = true;
        s.info = "Type any feature: heal, shop, raid, tutor, quests...";
        s.groups.add("Gym"); s.groups.add("Shops"); s.groups.add("Pokemon"); s.groups.add("Progress"); s.groups.add("Social");
        srow(s, "cobblemon:poke_ball", "Fight my next Gym Leader", "gym leader battle challenge fight arena badge", 0, () -> { ListNet.close(pl); run(pl, "trigger gym set 1"); });
        srow(s, "cobblemon:ultra_ball", "Rematches (Hard / Elite)", "rematch replay hard elite harder gym", 0, () -> { ListNet.close(pl); open(pl, "rm_hub"); });
        srow(s, "minecraft:compass", "Start next region", "region johto hoenn sinnoh unova kalos series", 0, () -> { ListNet.close(pl); run(pl, "trigger gym set 46"); });
        srow(s, "minecraft:barrier", "Leave the arena", "leave exit return arena quit", 0, () -> { ListNet.close(pl); run(pl, "trigger gym set 2"); });
        srow(s, "minecraft:comparator", "Challenge Modifiers", "iron mode speedrun modifier bonus bp", 0, () -> { ListNet.close(pl); open(pl, "mods"); });
        srow(s, "minecraft:totem_of_undying", "Duo run", "duo coop co-op partner team friend invite region", 4, () -> { ListNet.close(pl); open(pl, "duo"); });
        srow(s, "minecraft:totem_of_undying", "Co-op Gyms", "coop co-op partner team friend duo", 4, () -> { ListNet.close(pl); open(pl, "coop"); });
        srow(s, "minecraft:player_head", "Join an open co-op lane", "join coop lane partner level cap", 4, () -> { ListNet.close(pl); open(pl, "coop_join"); });
        srow(s, "minecraft:emerald", "Badge Point Shop", "bp shop buy mint held item tera shard", 1, () -> { ListNet.close(pl); open(pl, "shop"); });
        srow(s, "minecraft:villager_spawn_egg", "Poke Mart", "mart store villager cobbledollars buy items ball potion", 1, () -> { ListNet.close(pl); openMartFor(pl); });
        srow(s, "minecraft:lightning_rod", "Weather Machine", "weather rain storm clear thunder fishing", 1, () -> { ListNet.close(pl); open(pl, "weather"); });
        srow(s, "cobblemon:full_restore", "Heal my party", "heal restore revive charges pokemon center", 2, () -> { ListNet.close(pl); run(pl, "trigger gym set 8"); });
        srow(s, "minecraft:enchanted_book", "Move Tutor", "move tutor teach egg moves tm learn", 2, () -> { ListNet.close(pl); run(pl, "movetutor"); });
        srow(s, "cobblemon:protein", "EV Editor", "ev evs train stats vitamins", 2, () -> { ListNet.close(pl); run(pl, "evedit"); });
        srow(s, "minecraft:spyglass", "Scout trainers", "scout trainer team preview level cap next opponent", 2, () -> { ListNet.close(pl); run(pl, "scout"); });
        srow(s, "minecraft:writable_book", "Badge Case", "badges progress region case", 3, () -> { ListNet.close(pl); open(pl, "case"); });
        srow(s, "minecraft:knowledge_book", "Pokedex Milestones", "dex pokedex species caught milestone reward", 3, () -> { ListNet.close(pl); open(pl, "dex"); });
        srow(s, "minecraft:knowledge_book", "Raid Den Key", "raid key den claim replacement", 3, () -> { ListNet.close(pl); run(pl, "trigger gym set 9"); });
        srow(s, "minecraft:nether_star", "Tier 7 Raid Pass", "raid pass voucher tier 7 seven rqueue activate", 3, () -> { ListNet.close(pl); RaidPass.activate(pl); });
        srow(s, "minecraft:writable_book", "Daily & Weekly Quests", "quest daily weekly task goal reward", 3, () -> { ListNet.close(pl); Engage.openQuests(pl); });
        srow(s, "minecraft:gold_ingot", "Leaderboard", "leaderboard top ranking best players score", 4, () -> { ListNet.close(pl); Engage.openBoard(pl); });
        srow(s, "minecraft:lead", "Trade Pokemon", "trade swap exchange player pokemon", 4, () -> {
            ListNet.close(pl);
            pl.method_7353(class_2561.method_43470("\u00a76Type \u00a7e/trade <player>\u00a76 to start a trade. Both players see a confirm window."), false);
        });
    }

    static void openMartFor(class_3222 pl) {
        if (MartNet.hasClient(pl)) MartNet.open(pl); else open(pl, "mart");
    }

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
        if (LISTABLE.contains(screen) && ListNet.hasClient(pl)) { virtClose(pl); openList(pl, screen); return; }
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
        void go(String s) {
            if (LISTABLE.contains(s) && ListNet.hasClient(pl)) { close(); openList(pl, s); return; } screen = s; page = 0; render(); }

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
            put(29, mk("minecraft:writable_book", 1, "Daily & Weekly Quests", Engage.readyCount(pl) > 0, "s|Three daily + three weekly goals", "g|Ready to claim: " + Engage.readyCount(pl), "y|Click to open"), () -> { close(); Engage.openQuests(pl); });
            put(31, mk("minecraft:gold_ingot", 1, "Leaderboard", false, "s|Top trainers: badges, dex, wins,", "s|Rogue clears and more", "y|Click to open"), () -> { close(); Engage.openBoard(pl); });
            put(33, mk("minecraft:spyglass", 1, "Search the Phone", false, "s|Find any feature by name", "y|Type to search"), () -> { close(); openSearch(pl); });
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
