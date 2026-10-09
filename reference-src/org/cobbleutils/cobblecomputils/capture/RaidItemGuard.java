package org.cobbleutils.cobblecomputils.capture;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.api.ModInitializer;
import net.minecraft.class_1799;
import net.minecraft.class_3222;

import java.lang.reflect.Proxy;
import java.util.*;

/** Safety net for HeldItemSaver on Raid Dens: snapshot held items when any battle starts, restore once the player is out of battle (win, loss, timeout, leave, rejoin). */
public class RaidItemGuard implements ModInitializer {
    private static final Map<UUID, Map<UUID, class_1799>> snaps = new HashMap<>();
    private static int tick;

    @Override
    public void onInitialize() {
        try {
            Class<?> ev = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents");
            Object event = ev.getField("END_SERVER_TICK").get(null);
            Class<?> iface = Class.forName("net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents$EndTick");
            Object l = Proxy.newProxyInstance(RaidItemGuard.class.getClassLoader(), new Class<?>[]{iface}, (p, m, a) -> {
                if (m.getName().equals("onEndTick")) tick(a[0]);
                return null;
            });
            Class.forName("net.fabricmc.fabric.api.event.Event").getMethod("register", Object.class).invoke(event, l);
            System.out.println("[cobblecomputils] Raid held-item guard active");
        } catch (Throwable t) { System.out.println("[cobblecomputils] Raid held-item guard failed: " + t); }
    }

    private static void tick(Object server) {
        if (++tick % 4 != 0) return;
        try {
            Object pm = server.getClass().getMethod("method_3760").invoke(server);
            List<?> players = (List<?>) pm.getClass().getMethod("method_14571").invoke(pm);
            for (Object o : new ArrayList<>(players)) {
                class_3222 pl = (class_3222) o;
                UUID id = pl.method_5667();
                boolean inBattle = BattleRegistry.getBattleByParticipatingPlayer(pl) != null;
                Map<UUID, class_1799> s = snaps.get(id);
                if (inBattle && s == null) {
                    Map<UUID, class_1799> m = new HashMap<>();
                    for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(pl)) m.put(p.getUuid(), p.heldItem().method_7972());
                    snaps.put(id, m);
                } else if (!inBattle && s != null) {
                    int n = 0;
                    for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(pl)) {
                        class_1799 want = s.get(p.getUuid());
                        if (want != null && !class_1799.method_7973(want, p.heldItem())) { p.swapHeldItem(want, false, false); n++; }
                    }
                    snaps.remove(id);
                    if (n > 0) System.out.println("[cobblecomputils] Restored held items on " + n + " Pokémon for " + pl.method_5477().getString());
                }
            }
        } catch (Throwable t) { System.out.println("[cobblecomputils] raid guard tick error: " + t); }
    }
}
