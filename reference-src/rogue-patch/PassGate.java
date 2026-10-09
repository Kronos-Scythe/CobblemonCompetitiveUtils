package raidqueue;

import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.class_3222;

/** Potential Pack: tier 7 raids require an active Tier 7 Raid Pass from Cobblemon Competitive Utils. */
public final class PassGate {
    private PassGate() {}
    private static Method deny;
    private static boolean tried;

    private static Method deny() {
        if (!tried) {
            tried = true;
            try { deny = Class.forName("org.cobbleutils.cobblecomputils.capture.RaidPass").getMethod("deny", class_3222.class); }
            catch (Throwable t) { deny = null; }
        }
        return deny;
    }

    /** true = block this player (message already sent). */
    public static boolean blocked(class_3222 player, int tier) {
        if (tier != 7) return false;
        Method m = deny();
        if (m == null) return false;
        try { return (Boolean) m.invoke(null, player); } catch (Throwable t) { return false; }
    }

    public static boolean blockedAll(List<class_3222> players, int tier) {
        if (tier != 7) return false;
        boolean b = false;
        for (class_3222 p : players) if (blocked(p, tier)) b = true;
        return b;
    }
}
