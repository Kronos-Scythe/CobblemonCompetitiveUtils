package org.cobbleutils.cobblecomputils.capture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * Gym progress board: shows every online player's current RCT gym ("Kanto Gym 3") as a suffix next to their
 * name in the tab list / above their head, on a right-hand sidebar scoreboard, and as a badge count in the tab list.
 * Second "main" entrypoint. Uses vanilla scoreboard/team commands through the server command dispatcher and
 * RctBridge (reflection) for progress data. Config: config/cobblecomputils/gym_board.properties
 */
public final class GymBoard implements net.fabricmc.api.ModInitializer {
    private static final class Key { String id, series, kind, name; int rank, badges; }
    private static final Map<String, Key> KEY = new HashMap<>();
    /** {id, series, kind, name} for a gym rank (used by the GUI), or null. */
    static String[] infoByRank(int rank) {
        for (Key k : KEY.values()) if (k.rank == rank) return new String[]{k.id, k.series, k.kind, k.name};
        return null;
    }
    /** Gym badges (the 8 gyms, not Elite Four / Champion) of a region found in a set of defeated trainer ids. */
    static int badgesOf(java.util.Collection<String> defeated, String series) {
        int n = 0;
        for (Key k : KEY.values()) if (k.series.equalsIgnoreCase(series) && k.kind.startsWith("Gym") && defeated.contains(k.id)) n++;
        return n;
    }
    /** Forget a player's last gym rank so a jump caused by switching Solo/Duo or region is not treated as progress (no Rogue tokens). */
    static void forget(String name) { lastGym.remove(name); }
    /** Refresh the gym scoreboard/sidebar on the next server tick instead of waiting for the interval. */
    static void kick() { tick = intervalTicks; }
    private static void put(String id, String series, String kind, String name, int rank, int badges) {
        Key k = new Key(); k.id = id; k.series = series; k.kind = kind; k.name = name; k.rank = rank; k.badges = badges; KEY.put(id, k);
    }
    static {
        put("kanto_brock","Kanto","Gym 1","Brock",1,0);
        put("kanto_misty","Kanto","Gym 2","Misty",2,1);
        put("kanto_ltsurge","Kanto","Gym 3","Lt. Surge",3,2);
        put("kanto_erika","Kanto","Gym 4","Erika",4,3);
        put("kanto_koga","Kanto","Gym 5","Koga",5,4);
        put("kanto_sabrina","Kanto","Gym 6","Sabrina",6,5);
        put("kanto_blaine","Kanto","Gym 7","Blaine",7,6);
        put("kanto_giovanni","Kanto","Gym 8","Giovanni",8,7);
        put("kanto_league_lorelei","Kanto","Elite Four 1","Lorelei",9,8);
        put("kanto_league_bruno","Kanto","Elite Four 2","Bruno",10,8);
        put("kanto_league_agatha","Kanto","Elite Four 3","Agatha",11,8);
        put("kanto_league_lance","Kanto","Elite Four 4","Lance",12,8);
        put("kanto_champion_blue","Kanto","Champion","Blue",13,8);
        put("johto_valerio","Johto","Gym 1","Valerio",21,8);
        put("johto_raffaello","Johto","Gym 2","Raffaello",22,9);
        put("johto_chiara","Johto","Gym 3","Chiara",23,10);
        put("johto_angelo","Johto","Gym 4","Angelo",24,11);
        put("johto_furio","Johto","Gym 5","Furio",25,12);
        put("johto_jasmine","Johto","Gym 6","Jasmine",26,13);
        put("johto_alfredo","Johto","Gym 7","Alfredo",27,14);
        put("johto_sandra","Johto","Gym 8","Sandra",28,15);
        put("johto_league_pino","Johto","Elite Four 1","Pino",29,16);
        put("johto_league_koga","Johto","Elite Four 2","Koga",30,16);
        put("johto_league_bruno","Johto","Elite Four 3","Bruno",31,16);
        put("johto_league_karen","Johto","Elite Four 4","Karen",32,16);
        put("johto_champion_lance","Johto","Champion","Lance",33,16);
        put("hoenn_petra","Hoenn","Gym 1","Petra",41,16);
        put("hoenn_rudi","Hoenn","Gym 2","Rudi",42,17);
        put("hoenn_walter","Hoenn","Gym 3","Walter",43,18);
        put("hoenn_fiammetta","Hoenn","Gym 4","Fiammetta",44,19);
        put("hoenn_norman","Hoenn","Gym 5","Norman",45,20);
        put("hoenn_alice","Hoenn","Gym 6","Alice",46,21);
        put("hoenn_tell","Hoenn","Gym 7","Tell",47,22);
        put("hoenn_adriano","Hoenn","Gym 8","Adriano",48,23);
        put("hoenn_league_fosco","Hoenn","Elite Four 1","Fosco",49,24);
        put("hoenn_league_ester","Hoenn","Elite Four 2","Ester",50,24);
        put("hoenn_league_frida","Hoenn","Elite Four 3","Frida",51,24);
        put("hoenn_league_drake","Hoenn","Elite Four 4","Drake",52,24);
        put("hoenn_champion_rocco","Hoenn","Champion","Rocco",53,24);
        put("sinnoh_pedro","Sinnoh","Gym 1","Pedro",61,24);
        put("sinnoh_gardenia","Sinnoh","Gym 2","Gardenia",62,25);
        put("sinnoh_marzia","Sinnoh","Gym 3","Marzia",63,26);
        put("sinnoh_omar","Sinnoh","Gym 4","Omar",64,27);
        put("sinnoh_fannie","Sinnoh","Gym 5","Fannie",65,28);
        put("sinnoh_ferruccio","Sinnoh","Gym 6","Ferruccio",66,29);
        put("sinnoh_bianca","Sinnoh","Gym 7","Bianca",67,30);
        put("sinnoh_corrado","Sinnoh","Gym 8","Corrado",68,31);
        put("sinnoh_league_aaron","Sinnoh","Elite Four 1","Aaron",69,32);
        put("sinnoh_league_terrie","Sinnoh","Elite Four 2","Terrie",70,32);
        put("sinnoh_league_vulcano","Sinnoh","Elite Four 3","Vulcano",71,32);
        put("sinnoh_league_luciano","Sinnoh","Elite Four 4","Luciano",72,32);
        put("sinnoh_champion_camilla","Sinnoh","Champion","Camilla",73,32);
        put("unova_cilan","Unova","Gym 1","Cilan",81,32);
        put("unova_lenora","Unova","Gym 2","Lenora",82,33);
        put("unova_burgh","Unova","Gym 3","Burgh",83,34);
        put("unova_elesa","Unova","Gym 4","Elesa",84,35);
        put("unova_clay","Unova","Gym 5","Clay",85,36);
        put("unova_skyla","Unova","Gym 6","Skyla",86,37);
        put("unova_brycen","Unova","Gym 7","Brycen",87,38);
        put("unova_drayden","Unova","Gym 8","Drayden",88,39);
        put("unova_league_shauntal","Unova","Elite Four 1","Shauntal",89,40);
        put("unova_league_grimsley","Unova","Elite Four 2","Grimsley",90,40);
        put("unova_league_caitlin","Unova","Elite Four 3","Caitlin",91,40);
        put("unova_league_marshal","Unova","Elite Four 4","Marshal",92,40);
        put("unova_champion_iris","Unova","Champion","Iris",93,40);
        put("alola_ilima","Alola","Gym 1","Ilima",101,40);
        put("alola_lana","Alola","Gym 2","Lana",102,41);
        put("alola_kiawe","Alola","Gym 3","Kiawe",103,42);
        put("alola_mallow","Alola","Gym 4","Mallow",104,43);
        put("alola_sophocles","Alola","Gym 5","Sophocles",105,44);
        put("alola_mina","Alola","Gym 6","Mina",106,45);
        put("alola_nanu","Alola","Gym 7","Nanu",107,46);
        put("alola_hapu","Alola","Gym 8","Hapu",108,47);
        put("alola_league_hala","Alola","Elite Four 1","Hala",109,48);
        put("alola_league_olivia","Alola","Elite Four 2","Olivia",110,48);
        put("alola_league_acerola","Alola","Elite Four 3","Acerola",111,48);
        put("alola_league_kahili","Alola","Elite Four 4","Kahili",112,48);
        put("alola_champion_kukui","Alola","Champion","Kukui",113,48);
    }

    private static boolean enabled = true, sidebar = true, tabSuffix = true, tabBadges = true;
    private static int intervalTicks = 100;
    private static int tick = 0;
    private static Object lastServer = null;
    private static final Map<String, String[]> shown = new HashMap<>(); // player -> {suffix, entry, badges}
    private static final Map<String, Key> lastKey = new HashMap<>();
    private static final Map<String, Integer> lastGym = new HashMap<>();

    @Override
    public void onInitialize() {
        try { PpGui.init(); } catch (Throwable t) { System.out.println("[cobblecomputils] gym GUI failed to start: " + t); }
        loadConfig();
        if (!enabled) return;
        try {
            Class<?> ev = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents");
            Object event = ev.getField("END_SERVER_TICK").get(null);
            Class<?> iface = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndTick");
            Object listener = Proxy.newProxyInstance(GymBoard.class.getClassLoader(), new Class<?>[]{iface}, (p, m, a) -> {
                if (m.getName().equals("onEndTick")) onTick(a[0]);
                return null;
            });
            Class<?> eventBase = Class.forName("net.fabricmc.fabric.api.event.Event");   // public API class (the runtime class is package-private)
            eventBase.getMethod("register", Object.class).invoke(event, listener);
            System.out.println("[cobblecomputils] Gym board active (sidebar=" + sidebar + ", tabSuffix=" + tabSuffix + ", tabBadges=" + tabBadges + ")");
        } catch (Throwable t) {
            System.out.println("[cobblecomputils] Gym board could not start: " + t);
        }
    }

    private static void loadConfig() {
        File f = new File("config/cobblecomputils/gym_board.properties");
        Properties p = new Properties();
        try {
            if (f.exists()) { try (FileInputStream in = new FileInputStream(f)) { p.load(in); } }
            else {
                f.getParentFile().mkdirs();
                p.setProperty("enabled", "true"); p.setProperty("sidebar", "true");
                p.setProperty("tabSuffix", "true"); p.setProperty("tabBadges", "true"); p.setProperty("updateTicks", "100");
                try (FileOutputStream out = new FileOutputStream(f)) {
                    p.store(out, "Gym progress board. sidebar: right-hand scoreboard. tabSuffix: ' - Kanto Gym 3' after each name in the tab list. tabBadges: badge count in the tab list.");
                }
            }
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "true").trim());
            sidebar = Boolean.parseBoolean(p.getProperty("sidebar", "true").trim());
            tabSuffix = Boolean.parseBoolean(p.getProperty("tabSuffix", "true").trim());
            tabBadges = Boolean.parseBoolean(p.getProperty("tabBadges", "true").trim());
            intervalTicks = Math.max(20, Integer.parseInt(p.getProperty("updateTicks", "100").trim()));
        } catch (Exception e) { System.out.println("[cobblecomputils] gym_board config problem, using defaults: " + e); }
    }


    private static void cmd(Object server, String c) {
        try {
            Object cmds = server.getClass().getMethod("method_3734").invoke(server);
            Object src = server.getClass().getMethod("method_3739").invoke(server);
            cmds.getClass().getMethod("method_44252", Class.forName("net.minecraft.class_2168"), String.class).invoke(cmds, src, c);
        } catch (Throwable t) { System.out.println("[cobblecomputils] gym board command failed: " + c + " -> " + t); }
    }

    private static void init(Object server) {
        cmd(server, "scoreboard objectives add pp_gym dummy");
        cmd(server, "scoreboard objectives remove pp_board");
        cmd(server, "scoreboard objectives remove pp_badges");
        if (sidebar) {
            cmd(server, "scoreboard objectives add pp_board dummy {\"text\":\"Gym Progress\",\"color\":\"gold\",\"bold\":true}");
            cmd(server, "scoreboard objectives setdisplay sidebar pp_board");
        }
        if (tabBadges) {
            cmd(server, "scoreboard objectives add pp_badges dummy {\"text\":\"Badges\"}");
            cmd(server, "scoreboard objectives setdisplay list pp_badges");
        }
        shown.clear(); lastKey.clear(); lastGym.clear();
    }

    private static void onTick(Object server) {
        try {
            if (server != lastServer) { lastServer = server; tick = 0; init(server); }
            if (++tick % intervalTicks != 1 && tick != 5) return;
            Object plist = server.getClass().getMethod("method_3760").invoke(server);
            List<?> players = (List<?>) plist.getClass().getMethod("method_14571").invoke(plist);
            Class<?> sp = Class.forName("net.minecraft.class_3222");
            Class<?> bridge = Class.forName("org.cobbleutils.cobblecomputils.integration.RctBridge");
            Set<String> online = new HashSet<>();
            for (Object pl : players) {
                Object comp = sp.getMethod("method_5477").invoke(pl);
                String name = String.valueOf(comp.getClass().getMethod("getString").invoke(comp));
                online.add(name);
                Key best = null;
                List<?> next = (List<?>) bridge.getMethod("nextTrainers", sp).invoke(null, pl);
                for (Object t : next) {
                    String id = String.valueOf(t.getClass().getMethod("id").invoke(t));
                    Key k = KEY.get(id);
                    if (k != null && (best == null || k.rank < best.rank)) best = k;
                }
                if (best == null) best = lastKey.get(name);
                else lastKey.put(name, best);
                int gymVal = best == null ? 0 : best.rank;
                Integer sentGym = lastGym.get(name);
                if (sentGym != null && sentGym > 0 && gymVal > sentGym) { try { RogueLink.onGymProgress(server, pl, name, sentGym, gymVal); } catch (Throwable ignored) { } }
                if (sentGym == null || sentGym != gymVal) { cmd(server, "scoreboard players set " + name + " pp_gym " + gymVal); lastGym.put(name, gymVal); }
                String suffix, entry, badges;
                if (best == null) { suffix = " - No series"; entry = name + " -"; badges = "0"; }
                else {
                    String shortName = best.series + " " + best.kind;
                    suffix = " - " + shortName;
                    entry = trunc(name + ": " + best.series + " " + best.kind.replace("Elite Four ", "E4 ").replace("Champion", "Champ") + " " + best.name, 40);
                    badges = String.valueOf(best.badges);
                }
                // Score-holder arguments end at the first plain space (quotes do NOT group), so a name with spaces
                // broke the command ("Expected integer"). Non-breaking spaces look identical and parse as one token.
                entry = entry.replace(' ', '\u00a0');
                String[] old = shown.get(name);
                if (old != null && old[0].equals(suffix) && old[1].equals(entry) && old[2].equals(badges)) continue;
                String team = "pg" + Integer.toHexString(name.hashCode());
                if (tabSuffix) {
                    if (old == null) { cmd(server, "team add " + team); cmd(server, "team join " + team + " " + name); }
                    cmd(server, "team modify " + team + " suffix {\"text\":\"" + suffix + "\",\"color\":\"yellow\"}");
                }
                if (sidebar) {
                    if (old != null) cmd(server, "scoreboard players reset " + old[1] + " pp_board");
                    cmd(server, "scoreboard players set " + entry + " pp_board " + badges);
                }
                if (tabBadges) cmd(server, "scoreboard players set " + name + " pp_badges " + badges);
                shown.put(name, new String[]{suffix, entry, badges});
            }
            for (Iterator<Map.Entry<String, String[]>> it = shown.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, String[]> e = it.next();
                if (!online.contains(e.getKey())) {
                    if (sidebar) cmd(server, "scoreboard players reset " + e.getValue()[1] + " pp_board");
                    if (tabSuffix) cmd(server, "team remove pg" + Integer.toHexString(e.getKey().hashCode()));
                    it.remove();
                }
            }
        } catch (Throwable t) {
            if (tick % 600 == 1) System.out.println("[cobblecomputils] gym board error: " + t);
        }
    }

    private static String trunc(String s, int n) { return s.length() <= n ? s : s.substring(0, n); }
}
