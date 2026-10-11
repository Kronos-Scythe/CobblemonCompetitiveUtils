package org.cobbleutils.cobblecomputils.capture;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.class_1799;
import net.minecraft.class_2168;
import net.minecraft.class_3222;
import net.minecraft.class_7923;

/**
 * /pokeexport: writes a player's whole team and PC as real Gen 9 .pk9 files (PKHeX layout, decrypted, checksummed) plus a
 * PKForge-style manifest.json and a Showdown text file, ready to import in PKForge (Bank > import folder) or PKHeX.
 * Cobblemon does not store a caught date, a PID, a tid/sid or a met location, so those are filled in deterministically
 * (same trainer always gets the same TID/SID, same Pokemon always the same PID/EC, shininess is kept).
 */
public final class PokeExport {
    private PokeExport() { }

    static final Path ROOT = Path.of("config", "cobblecomputils", "pokeexport");

    private static String[] MOVES, ABILITIES, ITEMS, SPECIES;
    private static Map<String, Integer> MOVE_ID, ABILITY_ID, ITEM_ID;
    private static int[] N2I9;

    static final String[] NATURES = {"hardy", "lonely", "brave", "adamant", "naughty", "bold", "docile", "relaxed", "impish", "lax", "timid",
        "hasty", "serious", "jolly", "naive", "modest", "mild", "quiet", "bashful", "rash", "calm", "gentle", "sassy", "careful", "quirky"};
    static final String[] TYPES = {"normal", "fighting", "flying", "poison", "ground", "rock", "bug", "ghost", "steel", "fire", "water",
        "grass", "electric", "psychic", "ice", "dragon", "dark", "fairy"};
    static final String[] BALLS = {"", "masterball", "ultraball", "greatball", "pokeball", "safariball", "netball", "diveball", "nestball",
        "repeatball", "timerball", "luxuryball", "premierball", "duskball", "healball", "quickball", "cherishball", "fastball", "levelball",
        "lureball", "heavyball", "loveball", "friendball", "moonball", "sportball", "parkball", "dreamball", "beastball"};

    static String norm(String s) {
        if (s == null) return "";
        String d = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return d.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String[] lines(String name) throws IOException {
        try (InputStream in = PokeExport.class.getResourceAsStream("/data/cobblecomputils/pk/" + name)) {
            if (in == null) throw new IOException("missing resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n", -1);
        }
    }

    private static Map<String, Integer> index(String[] names) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 1; i < names.length; i++) {
            String k = norm(names[i]);
            if (!k.isEmpty()) m.putIfAbsent(k, i);
        }
        return m;
    }

    private static synchronized void load() throws IOException {
        if (MOVES != null) return;
        String[] mv = lines("text_Moves_en.txt"), ab = lines("text_Abilities_en.txt"), it = lines("text_Items_en.txt"), sp = lines("text_Species_en.txt");
        String[] t = lines("n2i9.txt");
        List<Integer> nums = new ArrayList<>();
        for (String tok : String.join(" ", t).trim().split("\\s+")) if (!tok.isEmpty()) nums.add(Integer.parseInt(tok));
        int[] n = new int[nums.size()];
        for (int i = 0; i < n.length; i++) n[i] = nums.get(i);
        N2I9 = n;
        MOVE_ID = index(mv);
        ABILITY_ID = index(ab);
        ITEM_ID = index(it);
        ITEMS = it;
        ABILITIES = ab;
        SPECIES = sp;
        MOVES = mv;
    }

    static int internalSpecies(int national) {
        int shift = national - 917;
        if (shift < 0 || shift >= N2I9.length) return national;
        return national + N2I9[shift];
    }

    static String speciesName(int national) { return national > 0 && national < SPECIES.length ? SPECIES[national] : "Unknown"; }

    // ---- form mapping (only the forms that are unambiguous; the rest export as the base form and are reported) ----
    static int formIndex(int dex, String formName, List<String> notes) {
        String f = norm(formName);
        if (f.isEmpty() || f.equals("normal") || f.equals("standard") || f.equals("base")) return 0;
        if (f.contains("alola")) return 1;
        if (f.contains("galar")) return dex == 52 ? 2 : dex == 555 ? 2 : 1;
        if (f.contains("hisui")) return 1;
        if (f.contains("paldea")) {
            if (dex == 128) return f.contains("blaze") ? 2 : f.contains("aqua") ? 3 : 1;
            return 1;
        }
        notes.add("form '" + formName + "' is not mapped, exported as the base form");
        return 0;
    }

    static int hash32(String s) {
        int h = 0x811C9DC5;
        for (int i = 0; i < s.length(); i++) { h ^= s.charAt(i); h *= 0x01000193; }
        return h;
    }

    static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static void str(ByteBuffer b, int off, String s) {
        for (int i = 0; i < 26; i++) b.put(off + i, (byte) 0);
        if (s == null) return;
        int n = Math.min(12, s.length());
        for (int i = 0; i < n; i++) b.putShort(off + i * 2, (short) s.charAt(i));
    }

    private static int stat(Pokemon p, boolean ev, Stats s) {
        try { return ev ? p.getEvs().getOrDefault((Stat) s) : p.getIvs().getOrDefault((Stat) s); } catch (Throwable t) { return 0; }
    }

    static String itemPath(Pokemon p) {
        try {
            class_1799 st = p.heldItem();
            if (st == null || st.method_7960()) return null;
            return class_7923.field_41178.method_10221(st.method_7909()).method_12832();
        } catch (Throwable t) { return null; }
    }

    /** One exported Pokemon. */
    static final class Out {
        byte[] pk9;
        String showdown, name, file, sha;
        int dex, box, slot;
        boolean shiny;
        List<String> notes = new ArrayList<>();
    }

    static Out build(Pokemon p, String exporter, int box, int slot) {
        Out o = new Out();
        List<String> notes = o.notes;
        int dex = p.getSpecies().getNationalPokedexNumber();
        int form = formIndex(dex, p.getForm().getName(), notes);
        o.dex = dex;
        o.box = box;
        o.slot = slot;
        o.shiny = p.getShiny();

        ByteBuffer b = ByteBuffer.allocate(0x148).order(ByteOrder.LITTLE_ENDIAN);
        String otName = null, otKey = null;
        try {
            if (p.getOriginalTrainerType() != null && p.getOriginalTrainerType().name().equals("NONE")) otName = null;
            else { otName = p.getOriginalTrainerName(); otKey = p.getOriginalTrainer(); }
        } catch (Throwable t) { }
        if (otName == null || otName.isBlank()) { otName = exporter; otKey = exporter; }
        if (otKey == null || otKey.isBlank()) otKey = otName;
        int idh = hash32("tid:" + otKey);
        int tid = idh & 0xFFFF, sid = (idh >>> 16) & 0xFFFF;

        long m1 = mix(p.getUuid().getMostSignificantBits() ^ p.getUuid().getLeastSignificantBits() * 31);
        long m2 = mix(m1 ^ 0x5851F42D4C957F2DL);
        int ec = (int) m1;
        int pid = (int) m2;
        int xor = (sid ^ tid ^ (pid >>> 16) ^ (pid & 0xFFFF)) & 0xFFFF;
        if (p.getShiny()) { if (xor >= 16) pid = (pid & 0xFFFF) | (((pid & 0xFFFF) ^ tid ^ sid) << 16); }
        else if (xor < 16) pid ^= 0x01000000;

        b.putInt(0x00, ec);
        b.putShort(0x08, (short) internalSpecies(dex));
        String item = itemPath(p);
        int itemId = 0;
        if (item != null) {
            Integer id = ITEM_ID.get(norm(item));
            if (id != null) itemId = id; else notes.add("held item '" + item + "' has no Gen 9 id, left empty");
        }
        b.putShort(0x0A, (short) itemId);
        b.putShort(0x0C, (short) tid);
        b.putShort(0x0E, (short) sid);
        b.putInt(0x10, p.getExperience());
        String abName = p.getAbility().getName();
        Integer abId = ABILITY_ID.get(norm(abName));
        if (abId == null) { notes.add("ability '" + abName + "' has no Gen 9 id"); abId = 1; }
        b.putShort(0x14, (short) abId.intValue());
        b.put(0x16, (byte) 1);
        b.putInt(0x1C, pid);
        int nat = Arrays.asList(NATURES).indexOf(p.getNature().getName().method_12832());
        int nat2 = Arrays.asList(NATURES).indexOf(p.getMintedNature().getName().method_12832());
        if (nat < 0) nat = 0;
        if (nat2 < 0) nat2 = nat;
        b.put(0x20, (byte) nat);
        b.put(0x21, (byte) nat2);
        int g = p.getGender().name().equals("MALE") ? 0 : p.getGender().name().equals("FEMALE") ? 1 : 2;
        b.put(0x22, (byte) (g << 1));
        b.put(0x24, (byte) form);
        b.put(0x26, (byte) stat(p, true, Stats.HP));
        b.put(0x27, (byte) stat(p, true, Stats.ATTACK));
        b.put(0x28, (byte) stat(p, true, Stats.DEFENCE));
        b.put(0x29, (byte) stat(p, true, Stats.SPEED));
        b.put(0x2A, (byte) stat(p, true, Stats.SPECIAL_ATTACK));
        b.put(0x2B, (byte) stat(p, true, Stats.SPECIAL_DEFENCE));
        b.put(0x48, (byte) 128);
        b.put(0x49, (byte) 128);
        b.put(0x4A, (byte) 128);

        String nick = null;
        try { if (p.getNickname() != null) nick = p.getNickname().getString(); } catch (Throwable t) { }
        String spName = speciesName(dex);
        boolean nicknamed = nick != null && !nick.isBlank() && !norm(nick).equals(norm(spName)) && !norm(nick).equals(norm(p.getSpecies().getName()));
        str(b, 0x58, nicknamed ? nick : spName);
        o.name = nicknamed ? nick : spName;

        List<com.cobblemon.mod.common.api.moves.Move> mv = p.getMoveSet().getMoves();
        for (int i = 0; i < 4; i++) {
            if (i >= mv.size()) break;
            com.cobblemon.mod.common.api.moves.Move m = mv.get(i);
            Integer id = MOVE_ID.get(norm(m.getName()));
            if (id == null) { notes.add("move '" + m.getName() + "' has no Gen 9 id, left empty"); continue; }
            b.putShort(0x72 + i * 2, (short) id.intValue());
            b.put(0x7A + i, (byte) Math.min(255, m.getCurrentPp()));
            b.put(0x7E + i, (byte) Math.min(3, m.getRaisedPpStages()));
        }
        b.putShort(0x8A, (short) Math.min(65535, Math.max(1, p.getCurrentHealth())));
        int ivs = stat(p, false, Stats.HP) | (stat(p, false, Stats.ATTACK) << 5) | (stat(p, false, Stats.DEFENCE) << 10)
            | (stat(p, false, Stats.SPEED) << 15) | (stat(p, false, Stats.SPECIAL_ATTACK) << 20) | (stat(p, false, Stats.SPECIAL_DEFENCE) << 25);
        if (nicknamed) ivs |= 0x80000000;
        b.putInt(0x8C, ivs);
        int tera = 0;
        try { tera = Arrays.asList(TYPES).indexOf(p.getTeraType().getName().toLowerCase(Locale.ROOT)); } catch (Throwable t) { }
        if (tera < 0) tera = 99;
        b.put(0x94, (byte) tera);
        b.put(0x95, (byte) 19);
        b.put(0xCE, (byte) 50);
        b.put(0xD5, (byte) 2);

        str(b, 0xF8, otName);
        int fr = 70;
        try { fr = Math.max(0, Math.min(255, p.getFriendship())); } catch (Throwable t) { }
        b.put(0x112, (byte) fr);
        LocalDate d = LocalDate.now();
        b.put(0x11C, (byte) (d.getYear() - 2000));
        b.put(0x11D, (byte) d.getMonthValue());
        b.put(0x11E, (byte) d.getDayOfMonth());
        int lvl = Math.max(1, Math.min(100, p.getLevel()));
        b.put(0x11F, (byte) lvl);
        int ball = 4;
        try {
            int idx = Arrays.asList(BALLS).indexOf(norm(p.getCaughtBall().getName().method_12832()));
            if (idx > 0) ball = idx; else notes.add("ball '" + p.getCaughtBall().getName().method_12832() + "' does not exist in Gen 9, used Poké Ball");
        } catch (Throwable t) { }
        b.put(0x124, (byte) ball);
        b.put(0x125, (byte) (lvl & 0x7F));
        int ht = 0;
        try {
            com.cobblemon.mod.common.pokemon.IVs iv = p.getIvs();
            if (iv.isHyperTrained(Stats.HP)) ht |= 1;
            if (iv.isHyperTrained(Stats.ATTACK)) ht |= 2;
            if (iv.isHyperTrained(Stats.DEFENCE)) ht |= 4;
            if (iv.isHyperTrained(Stats.SPECIAL_ATTACK)) ht |= 8;
            if (iv.isHyperTrained(Stats.SPECIAL_DEFENCE)) ht |= 16;
            if (iv.isHyperTrained(Stats.SPEED)) ht |= 32;
        } catch (Throwable t) { }
        b.put(0x126, (byte) ht);

        int sum = 0;
        for (int i = 8; i < 0x148; i += 2) sum += b.getShort(i) & 0xFFFF;
        b.putShort(0x06, (short) (sum & 0xFFFF));
        o.pk9 = b.array();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest(o.pk9)) sb.append(String.format("%02X", x));
            o.sha = sb.toString();
        } catch (Throwable t) { o.sha = ""; }
        String clean = (nicknamed ? nick : spName).replaceAll("[\\\\/:*?\"<>|]", "").trim();
        if (clean.length() > 20) clean = clean.substring(0, 20).trim();
        String sid8 = p.getUuid().toString().replace("-", "").substring(0, 8);
        o.file = String.format("%03d", dex) + (clean.isEmpty() ? " " : " - " + clean + " ") + sid8 + ".pk9";
        o.showdown = showdown(p, o.name, spName, form, nicknamed, itemId, abId, nat2, tera, lvl);
        return o;
    }

    static String showdown(Pokemon p, String name, String spName, int form, boolean nicknamed, int itemId, int abId, int nat, int tera, int lvl) {
        StringBuilder s = new StringBuilder();
        String formTag = "";
        String fn = norm(p.getForm().getName());
        if (fn.contains("alola")) formTag = "-Alola";
        else if (fn.contains("galar")) formTag = "-Galar";
        else if (fn.contains("hisui")) formTag = "-Hisui";
        else if (fn.contains("paldea")) formTag = "-Paldea";
        String species = spName + formTag;
        s.append(nicknamed ? name + " (" + species + ")" : species);
        String gs = p.getGender().name().equals("MALE") ? " (M)" : p.getGender().name().equals("FEMALE") ? " (F)" : "";
        s.append(gs);
        if (itemId > 0 && itemId < ITEMS.length) s.append(" @ ").append(ITEMS[itemId]);
        s.append('\n');
        s.append("Ability: ").append(ABILITIES[abId]).append('\n');
        s.append("Level: ").append(lvl).append('\n');
        if (p.getShiny()) s.append("Shiny: Yes\n");
        if (tera >= 0 && tera < TYPES.length) s.append("Tera Type: ").append(Character.toUpperCase(TYPES[tera].charAt(0))).append(TYPES[tera].substring(1)).append('\n');
        String[] lab = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
        Stats[] st = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};
        StringBuilder ev = new StringBuilder(), iv = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int e = stat(p, true, st[i]), v = stat(p, false, st[i]);
            if (e > 0) ev.append(ev.length() > 0 ? " / " : "").append(e).append(' ').append(lab[i]);
            if (v != 31) iv.append(iv.length() > 0 ? " / " : "").append(v).append(' ').append(lab[i]);
        }
        if (ev.length() > 0) s.append("EVs: ").append(ev).append('\n');
        s.append(Character.toUpperCase(NATURES[nat].charAt(0))).append(NATURES[nat].substring(1)).append(" Nature\n");
        if (iv.length() > 0) s.append("IVs: ").append(iv).append('\n');
        for (com.cobblemon.mod.common.api.moves.Move m : p.getMoveSet().getMoves()) {
            Integer id = MOVE_ID.get(norm(m.getName()));
            s.append("- ").append(id == null ? m.getName() : MOVES[id]).append('\n');
        }
        return s.toString();
    }

    /** Exports everything; returns the user-facing result text. */
    public static String export(class_3222 target, String by) {
        try {
            load();
            String who = target.method_5477().getString();
            List<Out> all = new ArrayList<>();
            int slot = 0;
            for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(target)) {
                if (p != null) all.add(build(p, who, 0, slot));
                slot++;
            }
            PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(target);
            int bn = 1;
            for (PCBox box : pc.getBoxes()) {
                for (Map.Entry<Integer, Pokemon> e : box.getNonEmptySlots().entrySet()) all.add(build(e.getValue(), who, bn, e.getKey()));
                bn++;
            }
            if (all.isEmpty()) return "§c[Export] " + who + " has no Pokémon to export.";
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String base = who.replaceAll("[^A-Za-z0-9_-]", "_") + "-" + stamp;
            Path dir = ROOT.resolve(base);
            Files.createDirectories(dir);
            JsonObject man = new JsonObject();
            man.addProperty("app", "PKForge");
            man.addProperty("schemaVersion", 1);
            man.addProperty("exportedUtc", java.time.Instant.now().toString());
            JsonArray arr = new JsonArray();
            StringBuilder sd = new StringBuilder(), rep = new StringBuilder();
            Set<String> used = new HashSet<>();
            for (Out o : all) {
                String f = o.file;
                int n = 2;
                while (!used.add(f)) f = o.file.replace(".pk9", " (" + (n++) + ").pk9");
                o.file = f;
                Files.write(dir.resolve(f), o.pk9);
                JsonObject e = new JsonObject();
                e.addProperty("file", f);
                e.addProperty("species", o.dex);
                e.addProperty("nickname", o.name);
                e.addProperty("shiny", o.shiny);
                e.addProperty("generation", 9);
                e.addProperty("box", o.box);
                e.addProperty("slot", o.slot);
                e.addProperty("sha256", o.sha);
                arr.add(e);
                sd.append(o.showdown).append('\n');
                for (String note : o.notes) rep.append(f).append(": ").append(note).append('\n');
            }
            man.add("entries", arr);
            Files.writeString(dir.resolve("manifest.json"), new GsonBuilder().setPrettyPrinting().create().toJson(man), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("showdown.txt"), sd.toString(), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("README.txt"), "Exported from Cobblemon by Cobblemon Competitive Utils (" + by + ").\n"
                + "Import in PKForge: Bank > import archive folder (pick this folder), or select the .pk9 files.\n"
                + "Showdown sets for every Pokémon are in showdown.txt.\n"
                + "Cobblemon does not store PID, TID/SID, caught date or met location, so those are generated (same trainer = same TID/SID).\n"
                + "Expect legality warnings in PKForge: these Pokémon are not from a real Scarlet/Violet encounter.\n"
                + (rep.length() > 0 ? "\nThings that could not be mapped:\n" + rep : ""), StandardCharsets.UTF_8);
            Path zip = ROOT.resolve(base + ".zip");
            try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
                try (var st = Files.list(dir)) {
                    for (Path f : (Iterable<Path>) st.sorted()::iterator) {
                        z.putNextEntry(new ZipEntry(f.getFileName().toString()));
                        z.write(Files.readAllBytes(f));
                        z.closeEntry();
                    }
                }
            }
            int warn = 0;
            for (Out o : all) warn += o.notes.size();
            System.out.println("[cobblecomputils] pokeexport: " + all.size() + " Pokemon of " + who + " -> " + zip + " (" + warn + " notes)");
            return "§a[Export] " + all.size() + " Pokémon exported as .pk9 for " + who + ". Files are on the server in §fconfig/cobblecomputils/pokeexport/" + base
                + "§a (and the same as a .zip). Import the folder in PKForge's Bank." + (warn > 0 ? " §e" + warn + " small field(s) could not be mapped, see README.txt in the folder." : "");
        } catch (Throwable t) {
            t.printStackTrace();
            return "§c[Export] failed: " + t;
        }
    }

    @SuppressWarnings("unchecked")
    static void registerCommands(Object dispatcherObj) {
        CommandDispatcher<class_2168> d = (CommandDispatcher<class_2168>) dispatcherObj;
        LiteralArgumentBuilder<class_2168> root = LiteralArgumentBuilder.<class_2168>literal("pokeexport");
        root.executes((Command<class_2168>) c -> {
            class_3222 me = c.getSource().method_9207();
            TradeNet.say(me, export(me, me.method_5477().getString()));
            return 1;
        });
        root.then(RequiredArgumentBuilder.<class_2168, String>argument("player", StringArgumentType.word())
            .requires(s -> s.method_9259(2))
            .executes((Command<class_2168>) c -> {
                class_3222 me = c.getSource().method_9207();
                class_3222 t = c.getSource().method_9211().method_3760().method_14566(StringArgumentType.getString(c, "player"));
                if (t == null) { TradeNet.say(me, "§c[Export] That player is not online."); return 0; }
                TradeNet.say(me, export(t, me.method_5477().getString()));
                return 1;
            }));
        d.register(root);
    }
}
