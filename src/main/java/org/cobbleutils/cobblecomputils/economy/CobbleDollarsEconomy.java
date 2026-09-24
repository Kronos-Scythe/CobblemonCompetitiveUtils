package org.cobbleutils.cobblecomputils.economy;

import fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer;
import java.math.BigInteger;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * CobbleDollars stores each player's balance on the player entity itself,
 * exposed through the {@link CobbleDollarsPlayer} interface it mixes in.
 */
final class CobbleDollarsEconomy implements Economy {
    @Override
    public String symbol() {
        return "CD";
    }

    @Override
    public boolean isFree() {
        return false;
    }

    @Override
    public BigInteger balance(ServerPlayerEntity player) {
        return ((CobbleDollarsPlayer) player).cobbleDollars$getCobbleDollars();
    }

    @Override
    public boolean withdraw(ServerPlayerEntity player, BigInteger amount) {
        CobbleDollarsPlayer wallet = (CobbleDollarsPlayer) player;
        BigInteger balance = wallet.cobbleDollars$getCobbleDollars();
        if (balance.compareTo(amount) < 0) {
            return false;
        }
        wallet.cobbleDollars$setCobbleDollars(balance.subtract(amount));
        return true;
    }

    @Override
    public void deposit(ServerPlayerEntity player, BigInteger amount) {
        CobbleDollarsPlayer wallet = (CobbleDollarsPlayer) player;
        wallet.cobbleDollars$setCobbleDollars(wallet.cobbleDollars$getCobbleDollars().add(amount));
    }
}
