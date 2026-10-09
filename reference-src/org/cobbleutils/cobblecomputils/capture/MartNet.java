package org.cobbleutils.cobblecomputils.capture;

import com.google.gson.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3222;
import net.minecraft.class_8710;
import net.minecraft.class_9129;
import net.minecraft.class_9139;
import net.minecraft.class_8710.class_9154;
import org.cobbleutils.cobblecomputils.Cobblecomputils;
import org.cobbleutils.cobblecomputils.economy.Economy;

/**
 * Network layer + server logic of the all-in-one Poke Mart screen.
 * The catalog (data/cobblecomputils/mart_catalog.json, generated from the datapack's merchant shops by
 * tools/gen_mart_catalog.py) is sent once when the screen opens; search and category filtering run on the client.
 */
public final class MartNet implements ModInitializer {
    public static final int MAX_QTY = 64;

    record Offer(class_1799 stack, long price, int group, String merchant) {}

    static List<String> groups = new ArrayList<>();
    static List<Offer> offers = new ArrayList<>();
    static boolean loaded = false;

    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(Open.ID, Open.CODEC);
        PayloadTypeRegistry.playS2C().register(Result.ID, Result.CODEC);
        PayloadTypeRegistry.playC2S().register(Buy.ID, Buy.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Buy.ID, (payload, context) -> buy(context.player(), payload.index(), payload.qty()));
    }

    public static boolean hasClient(class_3222 p) {
        return ServerPlayNetworking.canSend(p, Open.ID);
    }

    // ------------------------------------------------------------------ payloads
    public static record Open(String balance, List<String> groups, List<class_1799> stacks, long[] prices, int[] group, List<String> merchants) implements class_8710 {
        public static final class_9154<Open> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "mart_open"));
        public static final class_9139<class_9129, Open> CODEC = class_9139.method_56438(Open::write, Open::read);

        static void write(Open o, class_9129 buf) {
            buf.method_10814(o.balance);
            buf.method_10804(o.groups.size());
            for (String g : o.groups) buf.method_10814(g);
            buf.method_10804(o.stacks.size());
            for (int i = 0; i < o.stacks.size(); i++) {
                class_1799.field_49268.encode(buf, o.stacks.get(i));
                buf.method_10791(o.prices[i]);
                buf.method_10804(o.group[i]);
                buf.method_10814(o.merchants.get(i));
            }
        }

        static Open read(class_9129 buf) {
            String balance = buf.method_19772();
            int gn = buf.method_10816();
            List<String> gl = new ArrayList<>();
            for (int i = 0; i < gn; i++) gl.add(buf.method_19772());
            int n = buf.method_10816();
            List<class_1799> st = new ArrayList<>();
            long[] pr = new long[n];
            int[] gr = new int[n];
            List<String> mer = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                st.add(class_1799.field_49268.decode(buf));
                pr[i] = buf.method_10792();
                gr[i] = buf.method_10816();
                mer.add(buf.method_19772());
            }
            return new Open(balance, gl, st, pr, gr, mer);
        }

        public class_9154<? extends class_8710> method_56479() { return ID; }
    }

    public static record Result(String balance, String msg, boolean err) implements class_8710 {
        public static final class_9154<Result> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "mart_result"));
        public static final class_9139<class_9129, Result> CODEC = class_9139.method_56438(
            (p, buf) -> { buf.method_10814(p.balance()); buf.method_10814(p.msg()); buf.writeBoolean(p.err()); },
            buf -> new Result(buf.method_19772(), buf.method_19772(), buf.readBoolean()));

        public class_9154<? extends class_8710> method_56479() { return ID; }
    }

    public static record Buy(int index, int qty) implements class_8710 {
        public static final class_9154<Buy> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "mart_buy"));
        public static final class_9139<class_9129, Buy> CODEC = class_9139.method_56438(
            (p, buf) -> { buf.method_10804(p.index()); buf.method_10804(p.qty()); },
            buf -> new Buy(buf.method_10816(), buf.method_10816()));

        public class_9154<? extends class_8710> method_56479() { return ID; }
    }

    // ------------------------------------------------------------------ catalog
    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = MartNet.class.getResourceAsStream("/data/cobblecomputils/mart_catalog.json")) {
            if (in == null) { System.out.println("[cobblecomputils] mart catalog missing"); return; }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement g : root.getAsJsonArray("groups")) groups.add(g.getAsString());
            for (JsonElement el : root.getAsJsonArray("offers")) {
                JsonObject o = el.getAsJsonObject();
                String comps = o.has("components") ? o.get("components").toString() : "";
                class_1799 st = PpGui.stackOf(o.get("item").getAsString(), comps);
                if (st.method_7960()) continue;
                offers.add(new Offer(st, o.get("price").getAsLong(), o.get("group").getAsInt(), o.get("merchant").getAsString()));
            }
            System.out.println("[cobblecomputils] Poke Mart catalog: " + offers.size() + " items");
        } catch (Throwable t) {
            System.out.println("[cobblecomputils] mart catalog load failed: " + t);
        }
    }

    static String money(Economy eco, BigInteger v) { return eco.format(v); }

    public static void open(class_3222 pl) {
        open(pl, "", false);
    }

    static void open(class_3222 pl, String msg, boolean err) {
        load();
        Economy eco = Cobblecomputils.economy();
        int n = offers.size();
        List<class_1799> st = new ArrayList<>(n);
        long[] pr = new long[n];
        int[] gr = new int[n];
        List<String> mer = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Offer o = offers.get(i);
            st.add(o.stack());
            pr[i] = o.price();
            gr[i] = o.group();
            mer.add(o.merchant());
        }
        String bal = eco.isFree() ? "" : money(eco, eco.balance(pl));
        ServerPlayNetworking.send(pl, new Open(bal, groups, st, pr, gr, mer));
        if (!msg.isEmpty()) ServerPlayNetworking.send(pl, new Result(bal, msg, err));
    }

    static void buy(class_3222 pl, int index, int qty) {
        load();
        Economy eco = Cobblecomputils.economy();
        String bal = eco.isFree() ? "" : money(eco, eco.balance(pl));
        if (index < 0 || index >= offers.size() || qty < 1 || qty > MAX_QTY) return;
        if (eco.isFree()) {
            ServerPlayNetworking.send(pl, new Result(bal, "The Poke Mart needs CobbleDollars installed.", true));
            return;
        }
        Offer o = offers.get(index);
        BigInteger cost = BigInteger.valueOf(o.price()).multiply(BigInteger.valueOf(qty));
        if (!eco.withdraw(pl, cost)) {
            ServerPlayNetworking.send(pl, new Result(bal, "Not enough CobbleDollars: need " + money(eco, cost) + ".", true));
            return;
        }
        int left = qty;
        int per = Math.max(1, o.stack().method_7914());
        while (left > 0) {
            int n = Math.min(per, left);
            class_1799 give = o.stack().method_7972();
            give.method_7939(n);
            if (!pl.method_31548().method_7394(give)) pl.method_7328(give, false);
            left -= n;
        }
        String name = o.stack().method_7964().getString();
        ServerPlayNetworking.send(pl, new Result(money(eco, eco.balance(pl)), "Bought " + qty + "x " + name + " for " + money(eco, cost), false));
    }
}
