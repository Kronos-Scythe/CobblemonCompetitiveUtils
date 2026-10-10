package org.cobbleutils.cobblecomputils.capture;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;

/** Generic searchable list window (search bar, category chips, rows with icons) used by the Badge Point shop, /scout, Pokedex milestones and co-op lanes. */
public final class ListNet implements ModInitializer {
    public static final class Spec {
        public String title = "", info = "", footer = "", hint = "";
        public Runnable backAction = null;
        public boolean err = false, back = false;
        public final List<String> groups = new ArrayList<>();
        public final List<Row> rows = new ArrayList<>();
        final Map<Integer, Runnable> actions = new HashMap<>();
        public void add(Row r, Runnable action) { if (action != null) actions.put(rows.size(), action); rows.add(r); }
    }

    public interface Builder { void build(Spec s); }

    static final class Session { Builder b; Spec cur; int pending = 0; String note = ""; boolean noteErr; }
    static final Map<UUID, Session> SESS = new HashMap<>();
    /** 0 = left click, 1 = right click, for the click being handled. */
    public static int button = 0;

    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(View.ID, View.CODEC);
        PayloadTypeRegistry.playC2S().register(Click.ID, Click.CODEC);
        PayloadTypeRegistry.playC2S().register(Close.ID, Close.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Click.ID, (p, ctx) -> { button = p.button(); click(ctx.player(), p.index()); });
        ServerPlayNetworking.registerGlobalReceiver(Close.ID, (p, ctx) -> SESS.remove(ctx.player().method_5667()));
    }

    public static boolean hasClient(class_3222 pl) { return ServerPlayNetworking.canSend(pl, View.ID); }

    public static void open(class_3222 pl, Builder b) {
        Session s = new Session();
        s.b = b;
        SESS.put(pl.method_5667(), s);
        push(pl, s);
    }

    static void push(class_3222 pl, Session s) {
        Spec sp = new Spec();
        s.b.build(sp);
        if (!s.note.isEmpty()) { sp.footer = s.note; sp.err = s.noteErr; }
        s.cur = sp;
        ServerPlayNetworking.send(pl, new View(sp.title, sp.info, sp.footer, sp.hint, sp.err, sp.back, sp.groups, sp.rows));
    }

    public static void note(class_3222 pl, String msg, boolean err) {
        Session s = SESS.get(pl.method_5667());
        if (s == null) return;
        s.note = msg; s.noteErr = err; s.pending = 1;
    }

    public static void close(class_3222 pl) {
        if (SESS.remove(pl.method_5667()) != null) ServerPlayNetworking.send(pl, new View("", "", "", "", false, false, new ArrayList<>(), new ArrayList<>()));
    }

    static void click(class_3222 pl, int index) {
        Session s = SESS.get(pl.method_5667());
        if (s == null) return;
        if (index == -1) {
            Runnable back = s.cur == null ? null : s.cur.backAction;
            SESS.remove(pl.method_5667());
            // navigate in place (the replacement list reuses the open window); close it only if nothing took over
            try { if (back != null) back.run(); else PpGui.open(pl, "main"); }
            catch (Throwable t) { System.out.println("[cobblecomputils] list back error: " + t); }
            if (!SESS.containsKey(pl.method_5667())) ServerPlayNetworking.send(pl, new View("", "", "", "", false, false, new ArrayList<>(), new ArrayList<>()));
            return;
        }
        if (s.cur == null) return;
        Runnable r = s.cur.actions.get(index);
        s.note = "";
        if (r != null) {
            try { r.run(); } catch (Throwable t) { System.out.println("[cobblecomputils] list click error: " + t); }
        }
        if (SESS.get(pl.method_5667()) == s && s.pending == 0) s.pending = 4;
    }

    static void tick(net.minecraft.server.MinecraftServer server) {
        if (SESS.isEmpty()) return;
        for (Map.Entry<UUID, Session> e : new ArrayList<>(SESS.entrySet())) {
            Session s = e.getValue();
            class_3222 pl = server.method_3760().method_14602(e.getKey());
            if (pl == null || pl.method_31481()) { SESS.remove(e.getKey()); continue; }
            if (s.pending > 0 && --s.pending == 0) {
                try { push(pl, s); } catch (Throwable t) { System.out.println("[cobblecomputils] list refresh error: " + t); }
            }
        }
    }

    // ------------------------------------------------------------------ payloads
    public static record Row(class_1799 stack, String title, String sub, String right, int group, int state, String tip, List<class_1799> icons) {
        public static Row of(class_1799 stack, String title, String sub, String right, int group, int state, String tip) {
            return new Row(stack, title, sub, right, group, state, tip, new ArrayList<>());
        }
    }

    public static record View(String title, String info, String footer, String hint, boolean err, boolean back, List<String> groups, List<Row> rows) implements class_8710 {
        public static final class_9154<View> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "list_view"));
        public static final class_9139<class_9129, View> CODEC = class_9139.method_56438(View::write, View::read);

        static void write(View v, class_9129 buf) {
            buf.method_10814(v.title); buf.method_10814(v.info); buf.method_10814(v.footer); buf.method_10814(v.hint);
            buf.writeBoolean(v.err); buf.writeBoolean(v.back);
            buf.method_10804(v.groups.size());
            for (String g : v.groups) buf.method_10814(g);
            buf.method_10804(v.rows.size());
            for (Row r : v.rows) {
                class_1799.field_49268.encode(buf, r.stack);
                buf.method_10814(r.title); buf.method_10814(r.sub); buf.method_10814(r.right);
                buf.method_10804(r.group + 1); buf.method_10804(r.state);
                buf.method_10814(r.tip);
                buf.method_10804(r.icons.size());
                for (class_1799 i : r.icons) class_1799.field_49268.encode(buf, i);
            }
        }

        static View read(class_9129 buf) {
            String t = buf.method_19772(), i = buf.method_19772(), f = buf.method_19772(), hint = buf.method_19772();
            boolean err = buf.readBoolean(), back = buf.readBoolean();
            int gn = buf.method_10816();
            List<String> groups = new ArrayList<>();
            for (int k = 0; k < gn; k++) groups.add(buf.method_19772());
            int rn = buf.method_10816();
            List<Row> rows = new ArrayList<>(rn);
            for (int k = 0; k < rn; k++) {
                class_1799 st = class_1799.field_49268.decode(buf);
                String title = buf.method_19772(), sub = buf.method_19772(), right = buf.method_19772();
                int group = buf.method_10816() - 1, state = buf.method_10816();
                String tip = buf.method_19772();
                int n = buf.method_10816();
                List<class_1799> icons = new ArrayList<>();
                for (int j = 0; j < n; j++) icons.add(class_1799.field_49268.decode(buf));
                rows.add(new Row(st, title, sub, right, group, state, tip, icons));
            }
            return new View(t, i, f, hint, err, back, groups, rows);
        }

        public class_9154<? extends class_8710> method_56479() { return ID; }
    }

    public static record Click(int index, int button) implements class_8710 {
        public static final class_9154<Click> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "list_click"));
        public static final class_9139<class_9129, Click> CODEC = class_9139.method_56438((p, buf) -> { buf.method_10804(p.index + 1); buf.method_10804(p.button); }, buf -> new Click(buf.method_10816() - 1, buf.method_10816()));
        public class_9154<? extends class_8710> method_56479() { return ID; }
    }

    public static record Close(int unused) implements class_8710 {
        public static final class_9154<Close> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "list_close"));
        public static final class_9139<class_9129, Close> CODEC = class_9139.method_56438((p, buf) -> buf.method_10804(0), buf -> new Close(buf.method_10816()));
        public class_9154<? extends class_8710> method_56479() { return ID; }
    }
}
