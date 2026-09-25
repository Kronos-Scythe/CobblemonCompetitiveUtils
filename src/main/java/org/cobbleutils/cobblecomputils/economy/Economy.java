package org.cobbleutils.cobblecomputils.economy;

import java.math.BigInteger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import org.cobbleutils.cobblecomputils.Cobblecomputils;

/** The currency paid features charge in. CobbleDollars when installed, otherwise everything is free. */
public interface Economy {
    String COBBLEDOLLARS_MOD_ID = "cobbledollars";

    /** Short currency suffix for display, e.g. "CD". Empty when nothing is charged. */
    String symbol();

    boolean isFree();

    BigInteger balance(ServerPlayerEntity player);

    /** Takes {@code amount} if the player has it. Returns false and changes nothing otherwise. */
    boolean withdraw(ServerPlayerEntity player, BigInteger amount);

    void deposit(ServerPlayerEntity player, BigInteger amount);

    /** "1,250 CD", or just the number when nothing is charged. */
    default String format(BigInteger amount) {
        return String.format("%,d %s", amount, symbol()).trim();
    }

    static Economy detect() {
        if (FabricLoader.getInstance().isModLoaded(COBBLEDOLLARS_MOD_ID)) {
            Cobblecomputils.LOGGER.info("Using CobbleDollars as the economy");
            // Only loaded when the mod is present, so its classes are never touched otherwise.
            return new CobbleDollarsEconomy();
        }
        Cobblecomputils.LOGGER.warn("CobbleDollars not installed: paid features are free");
        return FreeEconomy.INSTANCE;
    }
}
