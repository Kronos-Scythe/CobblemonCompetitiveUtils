package org.cobbleutils.cobblecomputils.economy;

import java.math.BigInteger;
import net.minecraft.server.network.ServerPlayerEntity;

/** Used when no economy mod is installed: every price is waived. */
public final class FreeEconomy implements Economy {
    public static final FreeEconomy INSTANCE = new FreeEconomy();

    private FreeEconomy() {
    }

    @Override
    public String symbol() {
        return "";
    }

    @Override
    public boolean isFree() {
        return true;
    }

    @Override
    public BigInteger balance(ServerPlayerEntity player) {
        return BigInteger.ZERO;
    }

    @Override
    public boolean withdraw(ServerPlayerEntity player, BigInteger amount) {
        return true;
    }

    @Override
    public void deposit(ServerPlayerEntity player, BigInteger amount) {
    }
}
