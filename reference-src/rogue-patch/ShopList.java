package org.CobbleUtils.cobbleroguelike.ui;

import java.util.List;
import net.minecraft.class_1799;
import net.minecraft.class_3222;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.compat.ItemBridge;
import org.CobbleUtils.cobbleroguelike.run.Modifiers;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.CobbleUtils.cobbleroguelike.run.RunState;
import org.CobbleUtils.cobbleroguelike.shop.RewardService;
import org.CobbleUtils.cobbleroguelike.shop.ShopCatalog;
import org.CobbleUtils.cobbleroguelike.shop.ShopService;
import org.cobbleutils.cobblecomputils.capture.ListNet;

/** Potential Pack: the Rogue shop and token shop as one searchable list window (needs Cobblemon Competitive Utils on the client). */
public final class ShopList {
    private ShopList() {}

    /** @return true when the list window was opened and the old chest/card menu should be skipped. */
    public static boolean open(class_3222 pl, boolean tokenShop) {
        try {
            if (!ListNet.hasClient(pl)) return false;
            if (tokenShop) return tokens(pl);
            return coins(pl);
        } catch (Throwable t) {
            return false;
        }
    }

    private static class_1799 icon(String id) {
        class_1799 s = ItemBridge.stack(id);
        return s.method_7960() ? ItemBridge.stack("minecraft:paper") : s;
    }

    private static boolean coins(class_3222 pl) {
        if (!RunManager.isInRun(pl) || CobblemonBridge.isInBattle(pl)) return false;
        RunState st = RunManager.get().state(pl);
        if (st == null || Modifiers.has(st, "noshop")) return false;
        ListNet.open(pl, s -> {
            RunState state = RunManager.get().state(pl);
            s.title = "Rogue Shop";
            s.info = state.money + " coins";
            s.back = true;
            s.backAction = () -> RunManager.get().openCurrent(pl);
            s.hint = "Left-click: buy 1   |   Right-click: buy 5   |   Search by name or category";
            List<ShopCatalog.Category> cats = ShopCatalog.categories();
            for (ShopCatalog.Category c : cats) s.groups.add(c.name());
            for (int ci = 0; ci < cats.size(); ci++) {
                List<ShopCatalog.Entry> items = cats.get(ci).items();
                for (int ei = 0; ei < items.size(); ei++) {
                    ShopCatalog.Entry e = items.get(ei);
                    final int cIdx = ci, eIdx = ei;
                    boolean unlock = e.action().equals("unlock");
                    String gim = unlock ? e.item().substring("gimmick:".length()) : "";
                    boolean owned = unlock && state.gimmicks.contains(gim);
                    String name = ShopService.displayName(e);
                    String sub = unlock ? (owned ? "Unlocked for this run" : "Unlocks this gimmick for the whole run") : "In bag: " + state.bagCount(e.item()) + "  |  " + (e.action().equals("use") ? "Used on a Pokémon" : "Held by a Pokémon");
                    int rowState = owned ? 2 : (state.money < e.price() ? 3 : 0);
                    String tip = "Price: " + e.price() + " coins\n" + (unlock ? "No item needed" : "Left-click: 1   Right-click: 5");
                    s.add(ListNet.Row.of(icon(e.icon()), name, sub, owned ? "Owned" : e.price() + " coins", ci, rowState, tip), () -> {
                        ShopService.buy(pl, cIdx, eIdx, ListNet.button == 1 ? 5 : 1, 0);
                    });
                }
            }
            s.footer = "";
        });
        return true;
    }

    private static boolean tokens(class_3222 pl) {
        if (RunManager.isInRun(pl)) return false;
        ListNet.open(pl, s -> {
            int tok = RunManager.get().tokens(pl);
            s.title = "Rogue Shop";
            s.info = tok + " tokens";
            s.back = true;
            s.backAction = () -> RunManager.get().openCurrent(pl);
            s.hint = "Left-click: buy 1   |   Right-click: buy 5   |   Tokens come from finishing runs";
            List<RewardService.Category> cats = RewardService.catalog();
            for (RewardService.Category c : cats) s.groups.add(c.name());
            for (int ci = 0; ci < cats.size(); ci++) {
                List<RewardService.Entry> items = cats.get(ci).items();
                for (int ei = 0; ei < items.size(); ei++) {
                    RewardService.Entry e = items.get(ei);
                    final int cIdx = ci, eIdx = ei;
                    class_1799 st = icon(e.item());
                    String name = st.method_7964().getString();
                    int rowState = tok < e.price() ? 3 : 0;
                    s.add(ListNet.Row.of(st, name, cats.get(ci).name(), e.price() + " tokens", ci, rowState,
                        "Price: " + e.price() + " tokens\nLeft-click: 1   Right-click: 5\nGoes straight to your inventory"), () -> {
                        RewardService.buy(pl, cIdx, eIdx, ListNet.button == 1 ? 5 : 1, 0);
                    });
                }
            }
        });
        return true;
    }
}
