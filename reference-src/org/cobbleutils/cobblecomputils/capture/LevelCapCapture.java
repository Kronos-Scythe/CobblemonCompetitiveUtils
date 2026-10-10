package org.cobbleutils.cobblecomputils.capture;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.class_124;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_3222;
import org.cobbleutils.cobblecomputils.integration.RctBridge;

/**
 * Wild Pokemon above the thrower's RCT level cap can't be caught: the capture roll is overridden with an immediate
 * break-free, and the ball is returned. The capture cap only applies while a player has not cleared a region yet:
 * once any region's Champion is beaten (including a Duo clear) wild Pokemon can be caught freely. Gym and trainer
 * fights keep their level cap.
 */
public final class LevelCapCapture {
    private LevelCapCapture() {
    }

    public static void register() {
        // LOWEST runs last, so no other mod's result overrides the block.
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(Priority.LOWEST,
            (Consumer<PokeBallCaptureCalculatedEvent>) LevelCapCapture::onCaptureCalculated);
    }

    private static boolean clearedARegion(class_3222 player) {
        for (String r : Duo.REG) if (PpGui.adv(player, "region/" + r)) return true;
        return false;
    }

    private static void onCaptureCalculated(PokeBallCaptureCalculatedEvent event) {
        CaptureConfig config = CaptureConfig.get();
        if (!config.blockAboveLevelCap || !(event.getThrower() instanceof class_3222 player)) {
            return;
        }
        Pokemon pokemon = event.getPokemonEntity().getPokemon();
        OptionalInt cap = RctBridge.levelCap(player);
        if (cap.isEmpty() || pokemon.getLevel() <= cap.getAsInt()) {
            return;
        }
        if (clearedARegion(player)) {
            return;
        }
        event.setCaptureResult(new CaptureContext(0, false, false));
        player.method_7353(class_2561.method_43473().method_10852(pokemon.getSpecies().getTranslatedName())
            .method_27693(" (Lv. " + pokemon.getLevel() + ") is above your level cap of " + cap.getAsInt()
                + " and can't be caught until you clear your first region.")
            .method_27692(class_124.field_1061), false);
        if (config.returnBall && !player.method_7337()) {
            player.method_31548().method_7398(new class_1799(event.getPokeBallEntity().getPokeBall().item()));
        }
    }
}
