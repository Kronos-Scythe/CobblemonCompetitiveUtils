package org.cobbleutils.cobblecomputils.discord;

import com.google.gson.*;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

/**
 * Discord team board: one picture with every trainer's head, name, current gym and party (PMDCollab portraits,
 * tiny held item), posted/edited as a single Discord webhook message. Runs inside the server, no extra program.
 * Config: config/cobblecomputils/discord_board.properties.   Command: /teamboard refresh (op).
 */
public final class DiscordBoard implements net.fabricmc.api.ModInitializer {
    // ---------------------------------------------------------------- config
    static final File CFG_FILE = new File("config/cobblecomputils/discord_board.properties");
    static final File DIR = new File("config/cobblecomputils/discord_board");
    static final File CACHE = new File(DIR, "cache");
    static final File STATE = new File(DIR, "state.properties");
    static final String RAW = "https://raw.githubusercontent.com/PMDCollab/SpriteCollab/master/portrait";
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();

    static final class Cfg {
        boolean enabled = true, saveBefore = true, repost = true;
        String webhook = "", title = "Cobblemon Trainers", footer = "Sprites: PMDCollab SpriteCollab", columns = "auto";
        int intervalMin = 15, scale = 3, dummies = 0, cooldown = 30;
        Set<String> hide = new HashSet<>();
        String modsPath = "mods";
    }
    static Cfg cfg = new Cfg();

    static void loadConfig() {
        Properties p = new Properties();
        try {
            if (CFG_FILE.exists()) { try (InputStream in = new FileInputStream(CFG_FILE)) { p.load(in); } }
            else {
                CFG_FILE.getParentFile().mkdirs();
                p.setProperty("enabled", "true");
                p.setProperty("webhook_url", "");
                p.setProperty("interval_minutes", "15");
                p.setProperty("title", "Cobblemon Trainers");
                p.setProperty("footer", "Sprites: PMDCollab SpriteCollab");
                p.setProperty("columns", "auto");
                p.setProperty("sprite_scale", "3");
                p.setProperty("hide_players", "");
                p.setProperty("save_before_refresh", "true");
                p.setProperty("refresh_cooldown_seconds", "30");
                p.setProperty("dummy_players", "0");
                p.setProperty("repost_each_refresh", "true");
                try (OutputStream out = new FileOutputStream(CFG_FILE)) {
                    p.store(out, "Discord team board. Paste your webhook URL (Discord: channel Settings > Integrations > Webhooks > New Webhook > Copy URL) into webhook_url, then restart or run /teamboard refresh.\n"
                            + "columns: auto or a number. hide_players: comma separated names. dummy_players: fake players for layout testing.");
                }
            }
            Cfg c = new Cfg();
            c.enabled = Boolean.parseBoolean(p.getProperty("enabled", "true").trim());
            c.webhook = p.getProperty("webhook_url", "").trim();
            c.intervalMin = Math.max(1, Integer.parseInt(p.getProperty("interval_minutes", "15").trim()));
            c.title = p.getProperty("title", c.title);
            c.footer = p.getProperty("footer", c.footer);
            c.columns = p.getProperty("columns", "auto").trim();
            c.scale = Math.max(1, Math.min(5, Integer.parseInt(p.getProperty("sprite_scale", "3").trim())));
            c.saveBefore = Boolean.parseBoolean(p.getProperty("save_before_refresh", "true").trim());
            c.cooldown = Math.max(0, Integer.parseInt(p.getProperty("refresh_cooldown_seconds", "30").trim()));
            c.dummies = Math.max(0, Integer.parseInt(p.getProperty("dummy_players", "0").trim()));
            c.repost = Boolean.parseBoolean(p.getProperty("repost_each_refresh", "true").trim());
            for (String h : p.getProperty("hide_players", "").split(",")) if (!h.isBlank()) c.hide.add(h.trim().toLowerCase());
            cfg = c;
        } catch (Exception e) { System.out.println("[cobblecomputils] discord_board config problem, using defaults: " + e); }
    }

    // ---------------------------------------------------------------- server hooks
    static Object lastServer;
    static int tick;
    static final AtomicBoolean running = new AtomicBoolean(false);
    static volatile long lastRefresh = 0;
    static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "discord-board"); t.setDaemon(true); return t; });

    @Override
    public void onInitialize() {
        loadConfig();
        if (!cfg.enabled) return;
        try {
            Class<?> loader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object inst = loader.getMethod("getInstance").invoke(null);
            Object env = loader.getMethod("getEnvironmentType").invoke(inst);
            if (String.valueOf(env).equals("SERVER")) System.setProperty("java.awt.headless", "true");
        } catch (Throwable ignored) { }
        try {
            Class<?> ev = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents");
            Object event = ev.getField("END_SERVER_TICK").get(null);
            Class<?> iface = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndTick");
            Object listener = Proxy.newProxyInstance(DiscordBoard.class.getClassLoader(), new Class<?>[]{iface}, (p, m, a) -> {
                if (m.getName().equals("onEndTick")) onTick(a[0]);
                return null;
            });
            Class<?> eventBase = Class.forName("net.fabricmc.fabric.api.event.Event");
            eventBase.getMethod("register", Object.class).invoke(event, listener);

            Class<?> cr = Class.forName("net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback");
            Object cev = cr.getField("EVENT").get(null);
            Object cl = Proxy.newProxyInstance(DiscordBoard.class.getClassLoader(), new Class<?>[]{cr}, (p, m, a) -> {
                if (m.getName().equals("register")) registerCommand(a[0]);
                return null;
            });
            eventBase.getMethod("register", Object.class).invoke(cev, cl);
            System.out.println("[cobblecomputils] Discord team board loaded (webhook " + (cfg.webhook.isEmpty() ? "NOT set - edit config/cobblecomputils/discord_board.properties" : "set") + ")");
        } catch (Throwable t) {
            System.out.println("[cobblecomputils] Discord team board could not start: " + t);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void registerCommand(Object dispatcherObj) {
        CommandDispatcher<Object> d = (CommandDispatcher<Object>) dispatcherObj;
        LiteralArgumentBuilder<Object> refresh = LiteralArgumentBuilder.<Object>literal("refresh");
        refresh.executes((Command<Object>) ctx -> {
            Object src = ctx.getSource();
            if (!(Boolean) call(src, "method_9259", new Class[]{int.class}, 2)) { feedback(src, "You need permission level 2 for this."); return 0; }
            long wait = cfg.cooldown * 1000L - (System.currentTimeMillis() - lastRefresh);
            if (wait > 0) { feedback(src, "Board was refreshed a moment ago, try again in " + (wait / 1000 + 1) + "s."); return 0; }
            if (cfg.webhook.isEmpty()) { feedback(src, "No webhook_url set in config/cobblecomputils/discord_board.properties"); return 0; }
            if (!startRefresh(lastServer)) { feedback(src, "A refresh is already running."); return 0; }
            feedback(src, "Team board refresh started - it will update in a few seconds.");
            return 1;
        });
        LiteralArgumentBuilder<Object> root = LiteralArgumentBuilder.<Object>literal("teamboard");
        root.requires(s -> (Boolean) call(s, "method_9259", new Class[]{int.class}, 2));
        root.then((LiteralArgumentBuilder<Object>) refresh);
        d.register(root);
    }

    static Object call(Object o, String m, Class<?>[] types, Object... args) {
        try { return o.getClass().getMethod(m, types).invoke(o, args); } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void feedback(Object src, String msg) {
        try {
            Class<?> text = Class.forName("net.minecraft.class_2561");
            Object t = text.getMethod("method_43470", String.class).invoke(null, msg);
            java.util.function.Supplier<Object> sup = () -> t;
            src.getClass().getMethod("method_9226", java.util.function.Supplier.class, boolean.class).invoke(src, sup, false);
        } catch (Throwable ignored) { System.out.println("[cobblecomputils] " + msg); }
    }

    static void cmd(Object server, String c) {
        try {
            Object cmds = server.getClass().getMethod("method_3734").invoke(server);
            Object src = server.getClass().getMethod("method_3739").invoke(server);
            cmds.getClass().getMethod("method_44252", Class.forName("net.minecraft.class_2168"), String.class).invoke(cmds, src, c);
        } catch (Throwable t) { System.out.println("[cobblecomputils] discord board command failed: " + c + " -> " + t); }
    }

    static void onTick(Object server) {
        lastServer = server;
        tick++;
        int interval = cfg.intervalMin * 1200;
        if (tick == 1200 || (tick > 1200 && tick % interval == 0)) {
            if (!cfg.webhook.isEmpty()) startRefresh(server);
        }
    }

    /** Runs on the server thread (saves the world so files are current), then draws + uploads on a worker thread. */
    static boolean startRefresh(Object server) {
        if (!running.compareAndSet(false, true)) return false;
        try {
            if (cfg.saveBefore && server != null) cmd(server, "save-all");
        } catch (Throwable ignored) { }
        lastRefresh = System.currentTimeMillis();
        POOL.submit(() -> {
            try { refreshNow(); } catch (Throwable t) { System.out.println("[cobblecomputils] discord board refresh failed: " + t); }
            finally { running.set(false); }
        });
        return true;
    }

    static String lastSig = "";

    static void refreshNow() throws Exception {
        File world = findWorld();
        if (world == null) { System.out.println("[cobblecomputils] discord board: no world found"); return; }
        byte[] png = buildPng(world);
        if (png == null) return;
        String id = webhookSend(png);
        Properties st = new Properties(); st.setProperty("message_id", id);
        DIR.mkdirs();
        try (OutputStream o = new FileOutputStream(STATE)) { st.store(o, null); }
        System.out.println("[cobblecomputils] discord board updated");
    }

    // ---------------------------------------------------------------- world / data
    static File findWorld() {
        File props = new File("server.properties");
        if (props.exists()) {
            String level = "world";
            try {
                for (String l : Files.readAllLines(props.toPath(), StandardCharsets.UTF_8)) if (l.startsWith("level-name=")) { String v = l.substring(11).trim(); if (!v.isEmpty()) level = v; }
            } catch (Exception ignored) { }
            return new File(level);
        }
        File[] saves = new File("saves").listFiles(f -> new File(f, "pokemon").isDirectory());
        if (saves == null || saves.length == 0) return null;
        return Arrays.stream(saves).max(Comparator.comparingLong(File::lastModified)).orElse(null);
    }

    static final class Mon { String species, form, held, gender; boolean shiny, fainted; int level; }
    static final class Player { String uuid, name; List<Mon> mons = new ArrayList<>(); String[] gym; }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readNbt(File f) throws IOException {
        byte[] raw = Files.readAllBytes(f.toPath());
        InputStream in = new ByteArrayInputStream(raw);
        if (raw.length > 2 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b) in = new GZIPInputStream(in);
        DataInputStream d = new DataInputStream(new BufferedInputStream(in));
        int t = d.readByte(); d.readUTF();
        return (Map<String, Object>) payload(d, t);
    }

    static Object payload(DataInputStream d, int t) throws IOException {
        switch (t) {
            case 1: return (int) d.readByte();
            case 2: return (int) d.readShort();
            case 3: return d.readInt();
            case 4: return d.readLong();
            case 5: return d.readFloat();
            case 6: return d.readDouble();
            case 7: { int n = d.readInt(); d.skipBytes(n); return null; }
            case 8: return d.readUTF();
            case 9: { int it = d.readByte(), n = d.readInt(); List<Object> l = new ArrayList<>(); for (int i = 0; i < n; i++) l.add(payload(d, it)); return l; }
            case 10: {
                Map<String, Object> m = new LinkedHashMap<>();
                while (true) { int tt = d.readByte(); if (tt == 0) return m; String k = d.readUTF(); m.put(k, payload(d, tt)); }
            }
            case 11: { int n = d.readInt(); for (int i = 0; i < n; i++) d.readInt(); return null; }
            case 12: { int n = d.readInt(); for (int i = 0; i < n; i++) d.readLong(); return null; }
            default: throw new IOException("bad nbt tag " + t);
        }
    }

    static int num(Object o, int def) { return o instanceof Number ? ((Number) o).intValue() : def; }

    @SuppressWarnings("unchecked")
    static List<Player> readParties(File world) {
        List<Player> out = new ArrayList<>();
        File[] dirs = new File(world, "pokemon/playerpartystore").listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File dir : dirs) {
            File[] files = dir.listFiles((x, n) -> n.endsWith(".dat"));
            if (files == null) continue;
            for (File fp : files) {
                String uid;
                try { uid = UUID.fromString(fp.getName().substring(0, fp.getName().length() - 4)).toString(); } catch (Exception e) { continue; }
                try {
                    Map<String, Object> nbt = readNbt(fp);
                    Player p = new Player(); p.uuid = uid;
                    int slots = Math.max(6, num(nbt.get("SlotCount"), 6));
                    for (int i = 0; i < slots; i++) {
                        Object mo = nbt.get("Slot" + i);
                        if (!(mo instanceof Map) || ((Map<?, ?>) mo).isEmpty()) continue;
                        Map<String, Object> m = (Map<String, Object>) mo;
                        Mon mon = new Mon();
                        mon.species = String.valueOf(m.getOrDefault("Species", "cobblemon:unknown"));
                        mon.form = String.valueOf(m.getOrDefault("FormId", "normal"));
                        mon.shiny = num(m.get("Shiny"), 0) != 0;
                        mon.level = num(m.get("Level"), 1);
                        mon.fainted = num(m.get("Health"), 1) <= 0;
                        Object h = m.get("HeldItem");
                        String hid = null;
                        if (h instanceof Map) { Object id = ((Map<?, ?>) h).get("id"); if (id != null) hid = id.toString(); }
                        else if (h instanceof String) hid = (String) h;
                        if ("minecraft:air".equals(hid) || (hid != null && hid.isEmpty())) hid = null;
                        mon.held = hid;
                        p.mons.add(mon);
                    }
                    p.gym = gymProgress(uid, world);
                    out.add(p);
                } catch (Exception e) { System.out.println("[cobblecomputils] discord board: skip " + fp + ": " + e); }
            }
        }
        return out;
    }

    static Map<String, String> names;
    static String playerName(String uid) {
        if (names == null || System.currentTimeMillis() - namesAt > 60000) {
            names = new HashMap<>(); namesAt = System.currentTimeMillis();
            try {
                JsonArray a = JsonParser.parseString(Files.readString(Path.of("usercache.json"), StandardCharsets.UTF_8)).getAsJsonArray();
                for (JsonElement e : a) { JsonObject o = e.getAsJsonObject(); names.put(o.get("uuid").getAsString(), o.get("name").getAsString()); }
            } catch (Exception ignored) { }
        }
        String n = names.get(uid);
        if (n != null) return n;
        byte[] b = cached("name_" + uid + ".json", "https://sessionserver.mojang.com/session/minecraft/profile/" + uid.replace("-", ""));
        if (b != null) { try { return JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject().get("name").getAsString(); } catch (Exception ignored) { } }
        return uid.substring(0, 8);
    }
    static long namesAt = 0;

    // ---------------------------------------------------------------- gym progress (same order as the in-game gym board)
    static final Map<String, Object[]> PROGRESSION = new LinkedHashMap<>();
    static {
        String[][] kanto = {{"brock","Gym 1","Brock"},{"misty","Gym 2","Misty"},{"ltsurge","Gym 3","Lt. Surge"},{"erika","Gym 4","Erika"},{"koga","Gym 5","Koga"},{"sabrina","Gym 6","Sabrina"},{"blaine","Gym 7","Blaine"},{"giovanni","Gym 8","Giovanni"},
                {"league_lorelei","Elite Four 1","Lorelei"},{"league_bruno","Elite Four 2","Bruno"},{"league_agatha","Elite Four 3","Agatha"},{"league_lance","Elite Four 4","Lance"},{"champion_blue","Champion","Blue"}};
        String[][] johto = {{"valerio","Gym 1","Valerio"},{"raffaello","Gym 2","Raffaello"},{"chiara","Gym 3","Chiara"},{"angelo","Gym 4","Angelo"},{"furio","Gym 5","Furio"},{"jasmine","Gym 6","Jasmine"},{"alfredo","Gym 7","Alfredo"},{"sandra","Gym 8","Sandra"},
                {"league_pino","Elite Four 1","Pino"},{"league_koga","Elite Four 2","Koga"},{"league_bruno","Elite Four 3","Bruno"},{"league_karen","Elite Four 4","Karen"},{"champion_lance","Champion","Lance"}};
        String[][] hoenn = {{"petra","Gym 1","Petra"},{"rudi","Gym 2","Rudi"},{"walter","Gym 3","Walter"},{"fiammetta","Gym 4","Fiammetta"},{"norman","Gym 5","Norman"},{"alice","Gym 6","Alice"},{"tell","Gym 7","Tell"},{"adriano","Gym 8","Adriano"},
                {"league_fosco","Elite Four 1","Fosco"},{"league_ester","Elite Four 2","Ester"},{"league_frida","Elite Four 3","Frida"},{"league_drake","Elite Four 4","Drake"},{"champion_rocco","Champion","Rocco"}};
        String[][] sinnoh = {{"pedro","Gym 1","Pedro"},{"gardenia","Gym 2","Gardenia"},{"marzia","Gym 3","Marzia"},{"omar","Gym 4","Omar"},{"fannie","Gym 5","Fannie"},{"ferruccio","Gym 6","Ferruccio"},{"bianca","Gym 7","Bianca"},{"corrado","Gym 8","Corrado"},
                {"league_aaron","Elite Four 1","Aaron"},{"league_terrie","Elite Four 2","Terrie"},{"league_vulcano","Elite Four 3","Vulcano"},{"league_luciano","Elite Four 4","Luciano"},{"champion_camilla","Champion","Camilla"}};
        String[][] unova = {{"cilan","Gym 1","Cilan"},{"lenora","Gym 2","Lenora"},{"burgh","Gym 3","Burgh"},{"elesa","Gym 4","Elesa"},{"clay","Gym 5","Clay"},{"skyla","Gym 6","Skyla"},{"brycen","Gym 7","Brycen"},{"drayden","Gym 8","Drayden"},
                {"league_shauntal","Elite Four 1","Shauntal"},{"league_grimsley","Elite Four 2","Grimsley"},{"league_caitlin","Elite Four 3","Caitlin"},{"league_marshal","Elite Four 4","Marshal"},{"champion_iris","Champion","Iris"}};
        String[][] alola = {{"ilima","Gym 1","Ilima"},{"lana","Gym 2","Lana"},{"kiawe","Gym 3","Kiawe"},{"mallow","Gym 4","Mallow"},{"sophocles","Gym 5","Sophocles"},{"mina","Gym 6","Mina"},{"nanu","Gym 7","Nanu"},{"hapu","Gym 8","Hapu"},
                {"league_hala","Elite Four 1","Hala"},{"league_olivia","Elite Four 2","Olivia"},{"league_acerola","Elite Four 3","Acerola"},{"league_kahili","Elite Four 4","Kahili"},{"champion_kukui","Champion","Kukui"}};
        PROGRESSION.put("kanto", new Object[]{"Kanto", kanto});
        PROGRESSION.put("johto", new Object[]{"Johto", johto});
        PROGRESSION.put("hoenn", new Object[]{"Hoenn", hoenn});
        PROGRESSION.put("sinnoh", new Object[]{"Sinnoh", sinnoh});
        PROGRESSION.put("unova", new Object[]{"Unova", unova});
        PROGRESSION.put("alola", new Object[]{"Alola", alola});
    }

    /** Players currently on a Duo run (config/cobblecomputils/duo.json, written by the Duo system). */
    static boolean onDuoRun(String uid) {
        try {
            File f = new File("config/cobblecomputils/duo.json");
            if (!f.exists()) return false;
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f.toPath())).getAsJsonObject();
            return root.has("mode") && root.getAsJsonObject("mode").has(uid);
        } catch (Throwable t) { return false; }
    }

    static String[] gymProgress(String uid, File world) {
        String[] g = gymProgressRaw(uid, world);
        if (g != null && onDuoRun(uid) && !g[0].startsWith("No series")) g[0] = "Duo - " + g[0];
        return g;
    }

    @SuppressWarnings("unchecked")
    static String[] gymProgressRaw(String uid, File world) {
        File fp = new File(world, "data/rctmod.player." + uid + ".stat.dat");
        if (!fp.exists()) return null;
        try {
            Object dat = readNbt(fp).get("data");
            if (!(dat instanceof Map)) return null;
            Map<String, Object> data = (Map<String, Object>) dat;
            String cur = lastPart(String.valueOf(data.getOrDefault("currentSeries", "empty"))).toLowerCase();
            Set<String> done = new HashSet<>();
            Object pd = data.get("progressDefeats");
            if (pd instanceof Map) for (Object k : ((Map<?, ?>) pd).keySet()) done.add(lastPart(String.valueOf(k)).toLowerCase());
            Object[] series = PROGRESSION.get(cur);
            if (series == null) return new String[]{"No series yet", ""};
            for (String[] g : (String[][]) series[1]) if (!done.contains(cur + "_" + g[0])) return new String[]{series[0] + " " + g[1], g[2]};
            return new String[]{series[0] + " complete", "Champion defeated"};
        } catch (Exception e) { System.out.println("[cobblecomputils] discord board: gym read failed " + fp + ": " + e); return null; }
    }
    static String lastPart(String s) { int i = s.lastIndexOf(':'); return i >= 0 ? s.substring(i + 1) : s; }

    // ---------------------------------------------------------------- downloads / cache
    static byte[] cached(String name, String url) {
        CACHE.mkdirs();
        File f = new File(CACHE, name);
        try {
            if (f.exists()) return f.length() == 0 ? null : Files.readAllBytes(f.toPath());
            HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "cobblemon-team-board/1.0").timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() == 404) { f.createNewFile(); return null; }
            if (r.statusCode() != 200) return null;
            Files.write(f.toPath(), r.body());
            return r.body();
        } catch (Exception e) { return null; }
    }

    static BufferedImage img(byte[] b) { try { return b == null ? null : ImageIO.read(new ByteArrayInputStream(b)); } catch (Exception e) { return null; } }

    static JsonObject DEX, FORMS;
    static void loadTables() throws IOException {
        if (DEX != null) return;
        try (InputStream a = DiscordBoard.class.getResourceAsStream("/assets/cobblecomputils/discordboard/species_dex.json");
             InputStream b = DiscordBoard.class.getResourceAsStream("/assets/cobblecomputils/discordboard/forms.json")) {
            DEX = JsonParser.parseString(new String(a.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            FORMS = JsonParser.parseString(new String(b.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    static final Map<String, String> ALIAS = Map.of("alolan", "alola", "galarian", "galar", "hisuian", "hisui", "paldean", "paldea");

    static BufferedImage spriteFor(String speciesId, String formId, boolean shiny) {
        String sp = lastPart(speciesId);
        if (!DEX.has(sp)) return null;
        String key = String.format("%04d", DEX.get(sp).getAsInt());
        String formIdx = "0000";
        String fid = (formId == null ? "normal" : formId).toLowerCase().replace("-", "_");
        if (!(fid.equals("normal") || fid.isEmpty() || fid.equals("standard"))) {
            String want = ALIAS.getOrDefault(fid, fid);
            JsonObject e = FORMS.has(key) ? FORMS.getAsJsonObject(key) : null;
            if (e != null && e.has("f")) {
                for (Map.Entry<String, JsonElement> fe : e.getAsJsonObject("f").entrySet()) {
                    String n = fe.getValue().getAsJsonObject().get("n").getAsString();
                    if (n.equals(want) || (!n.isEmpty() && n.contains(want))) { formIdx = fe.getKey(); break; }
                }
            }
        }
        List<String[]> tries = new ArrayList<>();
        if (shiny) tries.add(new String[]{key + "_" + formIdx + "_s", RAW + "/" + key + "/" + formIdx + "/0001/Normal.png"});
        if (!formIdx.equals("0000")) tries.add(new String[]{key + "_" + formIdx, RAW + "/" + key + "/" + formIdx + "/Normal.png"});
        if (shiny) tries.add(new String[]{key + "_0000_s", RAW + "/" + key + "/0000/0001/Normal.png"});
        tries.add(new String[]{key + "_0000", RAW + "/" + key + "/Normal.png"});
        for (String[] t : tries) { BufferedImage i = img(cached(t[0] + ".png", t[1])); if (i != null) return i; }
        return null;
    }

    // Offline-mode servers give players offline UUIDs that Mojang does not know, so a UUID lookup returns Steve.
    // Look the skin up by the player NAME instead (their real account name), resolving the real UUID as a fallback.
    static BufferedImage head(String uid, String name) {
        String n = name == null ? "" : name.trim();
        if (n.matches("[A-Za-z0-9_]{1,16}")) {
            String key = n.toLowerCase();
            BufferedImage im = img(cached("headn_0_" + key + ".png", "https://mc-heads.net/avatar/" + n + "/64"));
            if (im != null) return im;
            byte[] prof = cached("realuuid_" + key + ".json", "https://api.mojang.com/users/profiles/minecraft/" + n);
            if (prof != null) {
                try {
                    String real = JsonParser.parseString(new String(prof, StandardCharsets.UTF_8)).getAsJsonObject().get("id").getAsString();
                    im = img(cached("headn_1_" + real + ".png", "https://crafatar.com/avatars/" + real + "?size=64&overlay"));
                    if (im != null) return im;
                } catch (Exception ignored) { }
            }
        }
        String[] urls = {"https://mc-heads.net/avatar/%s/64", "https://crafatar.com/avatars/%s?size=64&overlay"};
        for (int i = 0; i < urls.length; i++) {
            BufferedImage im = img(cached("head_" + i + "_" + uid + ".png", String.format(urls[i], uid.replace("-", ""))));
            if (im != null) return im;
        }
        return null;
    }

    static Map<String, String[]> itemIndex; // item name -> {jar, entry}
    static BufferedImage itemIcon(String itemId) {
        String name = lastPart(itemId);
        if (itemIndex == null) {
            itemIndex = new HashMap<>();
            File[] jars = new File(cfg.modsPath).listFiles((d, n) -> n.endsWith(".jar"));
            if (jars != null) for (File jar : jars) {
                String b = jar.getName().toLowerCase();
                if (!(b.startsWith("cobblemon") || b.contains("mega_showdown"))) continue;
                try (ZipFile z = new ZipFile(jar)) {
                    java.util.regex.Pattern pat = java.util.regex.Pattern.compile("assets/([^/]+)/textures/item/(?:.*/)?([^/]+)\\.png$");
                    for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
                        String n = en.nextElement().getName();
                        java.util.regex.Matcher m = pat.matcher(n);
                        if (m.find()) itemIndex.putIfAbsent(m.group(2), new String[]{jar.getPath(), n});
                    }
                } catch (Exception ignored) { }
            }
        }
        String[] hit = itemIndex.get(name);
        if (hit == null) return null;
        try (ZipFile z = new ZipFile(hit[0])) { return ImageIO.read(z.getInputStream(z.getEntry(hit[1]))); } catch (Exception e) { return null; }
    }

    // ---------------------------------------------------------------- rendering
    static final Color BG = new Color(24, 26, 33), CARD = new Color(35, 38, 48), EDGE = new Color(58, 63, 80), TXT = new Color(240, 242, 250), DIM = new Color(150, 156, 175), GOLD = new Color(255, 205, 80);
    static Font fReg, fBold;
    static Font font(float size, boolean bold) {
        try {
            if (fReg == null) {
                fReg = Font.createFont(Font.TRUETYPE_FONT, DiscordBoard.class.getResourceAsStream("/assets/cobblecomputils/discordboard/DejaVuSans.ttf"));
                fBold = Font.createFont(Font.TRUETYPE_FONT, DiscordBoard.class.getResourceAsStream("/assets/cobblecomputils/discordboard/DejaVuSans-Bold.ttf"));
            }
        } catch (Exception e) { return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, (int) size); }
        return (bold ? fBold : fReg).deriveFont(size);
    }

    static void text(Graphics2D g, String s, Font f, Color c, int x, int y) { // (x, y) = top-left like PIL
        g.setFont(f); g.setColor(c);
        g.drawString(s, x, y + g.getFontMetrics().getAscent());
    }
    static int tw(Graphics2D g, String s, Font f) { return g.getFontMetrics(f).stringWidth(s); }

    static BufferedImage scaled(BufferedImage src, int w, int h) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, w, h, null); g.dispose(); return o;
    }

    static BufferedImage render(List<Player> players) {
        int S = cfg.scale, B = 40 * S, cell = B + 10, pad = 14, head = 56;
        String c = cfg.columns;
        int cols = c.equalsIgnoreCase("auto") ? (players.size() <= 3 ? 1 : 2) : Math.max(1, Integer.parseInt(c));
        int cardW = pad * 2 + cell * 6 - 10, cardH = pad * 2 + head + 10 + B + 4;
        int rows = Math.max(1, (players.size() + cols - 1) / cols);
        int W = 12 + cols * (cardW + 12), titleH = 54, H = titleH + rows * (cardH + 12) + 8;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(BG); g.fillRect(0, 0, W, H);
        Font fTitle = font(26, true), fStamp = font(14, false), fName = font(22, true), fLv = font(14, true), fSmall = font(12, false), fGym = font(18, true);
        text(g, cfg.title, fTitle, TXT, 18, 14);
        String stamp = "updated " + new java.text.SimpleDateFormat("HH:mm").format(new Date());
        text(g, stamp, fStamp, DIM, W - 18 - tw(g, stamp, fStamp), 22);
        for (int n = 0; n < players.size(); n++) {
            Player p = players.get(n);
            int cx = 12 + (n % cols) * (cardW + 12), cy = titleH + (n / cols) * (cardH + 12);
            g.setColor(CARD); g.fill(new RoundRectangle2D.Float(cx, cy, cardW, cardH, 28, 28));
            g.setColor(EDGE); g.setStroke(new BasicStroke(1)); g.draw(new RoundRectangle2D.Float(cx, cy, cardW, cardH, 28, 28));
            BufferedImage hd = head(p.uuid, p.name);
            int hx = cx + pad, hy = cy + pad;
            if (hd != null) g.drawImage(scaled(hd, head, head), hx, hy, null);
            else { g.setColor(EDGE); g.fillRect(hx, hy, head, head); }
            text(g, p.name, fName, TXT, hx + head + 12, hy + 6);
            text(g, p.mons.size() + " Pokémon in party", fSmall, DIM, hx + head + 12, hy + 34);
            if (p.gym != null) {
                int gx = cx + cardW - pad;
                text(g, p.gym[0], fGym, GOLD, gx - tw(g, p.gym[0], fGym), hy + 4);
                if (!p.gym[1].isEmpty()) text(g, p.gym[1], fSmall, DIM, gx - tw(g, p.gym[1], fSmall), hy + 30);
            }
            int sy = hy + head + 10;
            for (int i = 0; i < 6; i++) {
                int sx = cx + pad + i * cell;
                Mon m = i < p.mons.size() ? p.mons.get(i) : null;
                Shape clip = new RoundRectangle2D.Float(sx, sy, B, B, 20, 20);
                g.setColor(new Color(28, 30, 38)); g.fill(clip);
                if (m != null) {
                    BufferedImage sp = spriteFor(m.species, m.form, m.shiny);
                    Shape old = g.getClip(); g.setClip(clip);
                    if (sp == null) { text(g, "?", font(18, true), DIM, sx + 14 * S, sy + 10); }
                    else {
                        sp = scaled(sp, B, B);
                        if (m.fainted) sp = fade(sp);
                        g.drawImage(sp, sx, sy, null);
                    }
                    g.setClip(old);
                }
                g.setColor(EDGE); g.setStroke(new BasicStroke(2)); g.draw(new RoundRectangle2D.Float(sx + 1, sy + 1, B - 2, B - 2, 18, 18));
                if (m == null) continue;
                outlined(g, "Lv." + m.level, fLv, sx + 8, sy + B - 24);
                if (m.shiny) {
                    double cx0 = sx + B - 17, cy0 = sy + 17, ro = 11, ri = 4.6;
                    Path2D star = new Path2D.Double();
                    for (int k = 0; k < 10; k++) {
                        double r = k % 2 == 0 ? ro : ri;
                        double px = cx0 + r * Math.sin(k * Math.PI / 5), py = cy0 - r * Math.cos(k * Math.PI / 5);
                        if (k == 0) star.moveTo(px, py); else star.lineTo(px, py);
                    }
                    star.closePath();
                    g.setColor(new Color(255, 214, 64)); g.fill(star);
                    g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1)); g.draw(star);
                }
                if (m.held != null) {
                    BufferedImage ic = itemIcon(m.held);
                    if (ic != null) {
                        int bx = sx + B - 36, by = sy + B - 36;
                        g.setColor(new Color(20, 22, 28)); g.fill(new Ellipse2D.Float(bx - 3, by - 3, 34, 34));
                        g.setColor(EDGE); g.setStroke(new BasicStroke(1)); g.draw(new Ellipse2D.Float(bx - 3, by - 3, 34, 34));
                        g.drawImage(scaled(ic, 28, 28), bx, by, null);
                    }
                }
            }
        }
        g.dispose();
        return img;
    }

    static void outlined(Graphics2D g, String s, Font f, int x, int y) {
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++)
            if (dx * dx + dy * dy <= 5 && (dx != 0 || dy != 0)) text(g, s, f, Color.BLACK, x + dx, y + dy);
        text(g, s, f, TXT, x, y);
    }

    static BufferedImage fade(BufferedImage s) {
        BufferedImage o = new BufferedImage(s.getWidth(), s.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < s.getHeight(); y++) for (int x = 0; x < s.getWidth(); x++) {
            int p = s.getRGB(x, y), a = (p >>> 24) & 0xff, r = (p >> 16) & 0xff, gg = (p >> 8) & 0xff, b = p & 0xff;
            int l = (int) (0.299 * r + 0.587 * gg + 0.114 * b);
            o.setRGB(x, y, ((int) (a * 0.45) << 24) | (l << 16) | (l << 8) | l);
        }
        return o;
    }

    // ---------------------------------------------------------------- build + upload
    static final String[] DUMMY_NAMES = {"AshKetchum", "MistyWaters", "BrockSolid", "GaryOak", "LanceDragon", "CynthiaS", "SteveTheTrainer", "RedFromPallet", "LeafGreen", "HilbertBW"};
    static final String[] DUMMY_POOL = {"pikachu", "charizard", "blastoise", "venusaur", "gengar", "dragonite", "garchomp", "lucario", "umbreon", "espeon", "gyarados", "snorlax", "tyranitar", "salamence", "metagross", "gardevoir", "scizor", "heracross", "starmie", "alakazam", "machamp", "arcanine", "jolteon", "vaporeon", "flareon", "sylveon", "greninja", "blaziken", "swampert", "sceptile", "infernape", "empoleon", "torterra", "togekiss", "roserade", "weavile", "mamoswine", "aegislash", "hydreigon", "volcarona"};
    static final String[] DUMMY_ITEMS = {"cobblemon:leftovers", "cobblemon:choice_band", "cobblemon:oran_berry", null, null, null};

    static List<Player> dummies(int n) {
        List<Player> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String name = DUMMY_NAMES[i % DUMMY_NAMES.length] + (i < DUMMY_NAMES.length ? "" : String.valueOf(i));
            Random r = new Random(name.hashCode());
            Player p = new Player(); p.name = name; p.uuid = new UUID(r.nextLong(), r.nextLong()).toString();
            int cnt = 2 + r.nextInt(5);
            for (int k = 0; k < cnt; k++) {
                Mon m = new Mon(); m.species = "cobblemon:" + DUMMY_POOL[r.nextInt(DUMMY_POOL.length)]; m.form = "normal";
                m.shiny = r.nextDouble() < 0.12; m.level = 10 + r.nextInt(71); m.fainted = r.nextDouble() < 0.1; m.held = DUMMY_ITEMS[r.nextInt(DUMMY_ITEMS.length)];
                p.mons.add(m);
            }
            Object[] s = (Object[]) PROGRESSION.values().toArray()[r.nextInt(PROGRESSION.size())];
            String[] g = ((String[][]) s[1])[r.nextInt(13)];
            p.gym = new String[]{s[0] + " " + g[1], g[2]};
            out.add(p);
        }
        return out;
    }

    static byte[] buildPng(File world) throws Exception {
        loadTables();
        List<Player> players = world == null ? new ArrayList<>() : readParties(world);
        for (Player p : players) p.name = playerName(p.uuid);
        players.addAll(dummies(cfg.dummies));
        players.removeIf(p -> p.mons.isEmpty() || cfg.hide.contains(p.name.toLowerCase()));
        players.sort(Comparator.comparing(p -> p.name.toLowerCase()));
        if (players.isEmpty()) { System.out.println("[cobblecomputils] discord board: no players with a party yet"); return null; }
        BufferedImage im = render(players);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        BufferedImage rgb = new BufferedImage(im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics(); g.drawImage(im, 0, 0, null); g.dispose();
        ImageIO.write(rgb, "png", bo);
        return bo.toByteArray();
    }

    static String webhookSend(byte[] png) throws Exception {
        String url = cfg.webhook.split("\\?")[0];
        String boundary = "----tb" + UUID.randomUUID().toString().replace("-", "");
        JsonObject embed = new JsonObject();
        JsonObject image = new JsonObject(); image.addProperty("url", "attachment://teams.png"); embed.add("image", image);
        embed.addProperty("color", 0x5865F2);
        JsonObject footer = new JsonObject(); footer.addProperty("text", cfg.footer); embed.add("footer", footer);
        JsonArray embeds = new JsonArray(); embeds.add(embed);
        JsonObject att = new JsonObject(); att.addProperty("id", 0); att.addProperty("filename", "teams.png");
        JsonArray atts = new JsonArray(); atts.add(att);
        JsonObject payload = new JsonObject(); payload.add("embeds", embeds); payload.add("attachments", atts);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json\r\n\r\n" + payload + "\r\n").getBytes(StandardCharsets.UTF_8));
        bo.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\"teams.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bo.write(png); bo.write("\r\n".getBytes());
        bo.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] body = bo.toByteArray();
        String mid = null;
        if (STATE.exists()) { Properties st = new Properties(); try (InputStream in = new FileInputStream(STATE)) { st.load(in); } mid = st.getProperty("message_id"); }
        if (mid != null && !mid.isEmpty() && cfg.repost) {
            try {
                HTTP.send(HttpRequest.newBuilder(URI.create(url + "/messages/" + mid)).timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "cobblemon-team-board/1.0").DELETE().build(), HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) { }
            mid = null;
        }
        if (mid != null && !mid.isEmpty()) {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url + "/messages/" + mid)).timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "cobblemon-team-board/1.0").header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 == 2) return mid;
            if (r.statusCode() != 404) throw new IOException("Discord PATCH " + r.statusCode() + ": " + r.body());
            System.out.println("[cobblecomputils] discord board: message was deleted, posting a new one");
        }
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url + "?wait=true")).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "cobblemon-team-board/1.0").header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) throw new IOException("Discord POST " + r.statusCode() + ": " + r.body());
        return JsonParser.parseString(r.body()).getAsJsonObject().get("id").getAsString();
    }

    // ---------------------------------------------------------------- standalone preview: java -cp ... DiscordBoard <world|none> <out.png> [dummies]
    public static void main(String[] a) throws Exception {
        System.setProperty("java.awt.headless", "true");
        loadConfig();
        cfg.dummies = a.length > 2 ? Integer.parseInt(a[2]) : 4;
        cfg.columns = "2";
        File w = a[0].equals("none") ? null : new File(a[0]);
        byte[] png = buildPng(w);
        Files.write(Path.of(a[1]), png);
        System.out.println("wrote " + a[1]);
    }
}
