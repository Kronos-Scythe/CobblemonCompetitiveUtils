package org.cobbleutils.cobblecomputils.capture;

import java.util.ArrayList;
import java.util.List;
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

/** Network layer for the restyled phone / Gym Challenge menu. The server keeps rendering the 54-slot menu; the client draws it as a window. */
public final class GymNet implements ModInitializer {
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(View.ID, View.CODEC);
        PayloadTypeRegistry.playC2S().register(Click.ID, Click.CODEC);
        PayloadTypeRegistry.playC2S().register(Close.ID, Close.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Click.ID, (payload, ctx) -> PpGui.virtClick(ctx.player(), payload.slot()));
        ServerPlayNetworking.registerGlobalReceiver(Close.ID, (payload, ctx) -> PpGui.virtClose(ctx.player()));
    }

    public static boolean hasClient(class_3222 pl) {
        return ServerPlayNetworking.canSend(pl, View.ID);
    }

    static void send(class_3222 pl, String title, List<class_1799> stacks) {
        ServerPlayNetworking.send(pl, new View(title, stacks));
    }

    /** An empty view tells the client to close the window. */
    static void sendClose(class_3222 pl) {
        ServerPlayNetworking.send(pl, new View("", new ArrayList<>()));
    }

    public static record View(String title, List<class_1799> stacks) implements class_8710 {
        public static final class_9154<View> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "gym_view"));
        public static final class_9139<class_9129, View> CODEC = class_9139.method_56438(
            (v, buf) -> {
                buf.method_10814(v.title);
                buf.method_10804(v.stacks.size());
                for (class_1799 s : v.stacks) class_1799.field_49268.encode(buf, s);
            },
            buf -> {
                String t = buf.method_19772();
                int n = buf.method_10816();
                List<class_1799> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(class_1799.field_49268.decode(buf));
                return new View(t, l);
            });
        public class_9154<? extends class_8710> method_56479() { return ID; }
    }

    public static record Click(int slot) implements class_8710 {
        public static final class_9154<Click> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "gym_click"));
        public static final class_9139<class_9129, Click> CODEC = class_9139.method_56438(
            (p, buf) -> buf.method_10804(p.slot), buf -> new Click(buf.method_10816()));
        public class_9154<? extends class_8710> method_56479() { return ID; }
    }

    public static record Close(int unused) implements class_8710 {
        public static final class_9154<Close> ID = new class_9154<>(class_2960.method_60655("cobblecomputils", "gym_close"));
        public static final class_9139<class_9129, Close> CODEC = class_9139.method_56438(
            (p, buf) -> buf.method_10804(0), buf -> new Close(buf.method_10816()));
        public class_9154<? extends class_8710> method_56479() { return ID; }
    }
}
