package org.cobbleutils.cobblecomputils.capture;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.cobbleutils.cobblecomputils.integration.RctBridge;

/**
 * Wild Pokémon above the thrower's RCT level cap can't be caught: the capture
 * roll is overridden with an immediate break-free, and the ball is returned.
 */
public final class LevelCapCapture {
    private LevelCapCapture() {
    }

    public static void register() {
        // LOWEST runs last, so no other mod's result overrides the block.
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(Priority.LOWEST,
            (Consumer<PokeBallCaptureCalculatedEvent>) LevelCapCapture::onCaptureCalculated);
    }

    private static void onCaptureCalculated(PokeBallCaptureCalculatedEvent event) {
        CaptureConfig config = CaptureConfig.get();
        if (!config.blockAboveLevelCap || !(event.getThrower() instanceof ServerPlayerEntity player)) {
            return;
        }
        Pokemon pokemon = event.getPokemonEntity().getPokemon();
        OptionalInt cap = RctBridge.levelCap(player);
        if (cap.isEmpty() || pokemon.getLevel() <= cap.getAsInt()) {
            return;
        }
        event.setCaptureResult(new CaptureContext(0, false, false));
        player.sendMessage(Text.empty().append(pokemon.getSpecies().getTranslatedName())
            .append(" (Lv. " + pokemon.getLevel() + ") is above your level cap of " + cap.getAsInt()
                + " and can't be caught.")
            .formatted(Formatting.RED), false);
        if (config.returnBall && !player.isCreative()) {
            player.getInventory().offerOrDrop(new ItemStack(event.getPokeBallEntity().getPokeBall().item()));
        }
    }
}
