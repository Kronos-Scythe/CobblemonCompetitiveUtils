package org.cobbleutils.cobblecomputils.capture;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Tier 7 raid lives depend on the group size: 3 lives alone, 2 with two players, 1 with three or more.
 * The raid den config gives every player 3 lives when they join (tier_seven.json5 "lives": 3); this lowers each player's
 * lives to the allowance for the current group size. Lives are only ever lowered, never restored. Pure reflection, so
 * nothing happens when the raid den mod is missing.
 */
public final class RaidLives {
    private RaidLives() { }

    private static boolean failed;
    private static Map<?, ?> active;
    private static Field boss, players, lives;
    private static Class<?> playerCls;
    private static int tick;

    static int allowance(int players) { return players <= 1 ? 3 : players == 2 ? 2 : 1; }

    @SuppressWarnings("unchecked")
    public static void tick(MinecraftServer server) {
        if (failed || ++tick % 10 != 0) return;
        try {
            if (active == null) {
                Class<?> h = Class.forName("com.necro.raid.dens.common.raids.helpers.RaidHelper");
                active = (Map<?, ?>) h.getField("ACTIVE_RAIDS").get(null);
                Class<?> ri = Class.forName("com.necro.raid.dens.common.raids.RaidInstance");
                boss = ri.getDeclaredField("raidBoss");
                players = ri.getDeclaredField("playerMap");
                playerCls = Class.forName("com.necro.raid.dens.common.raids.RaidInstance$RaidPlayer");
                lives = playerCls.getDeclaredField("lives");
                boss.setAccessible(true);
                players.setAccessible(true);
                lives.setAccessible(true);
            }
            if (active.isEmpty()) return;
            for (Object raid : active.values().toArray()) {
                Object b = boss.get(raid);
                Object tier = b.getClass().getMethod("getTier").invoke(b);
                if (!"TIER_SEVEN".equals(String.valueOf(tier))) continue;
                Map<UUID, Object> pm = (Map<UUID, Object>) players.get(raid);
                int allowed = allowance(pm.size());
                for (Object rp : pm.values().toArray()) {
                    int cur = lives.getInt(rp);
                    if (cur > allowed) {
                        lives.setInt(rp, allowed);
                        System.out.println("[cobblecomputils] tier 7 raid: " + pm.size() + " player(s), lives " + cur + " -> " + allowed);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            failed = true;
        } catch (Throwable t) {
            failed = true;
            System.out.println("[cobblecomputils] RaidLives disabled: " + t);
        }
    }
}
