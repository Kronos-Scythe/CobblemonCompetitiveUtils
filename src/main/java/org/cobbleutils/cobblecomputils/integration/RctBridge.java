package org.cobbleutils.cobblecomputils.integration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.cobbleutils.cobblecomputils.Cobblecomputils;

/**
 * Reads Radical Cobblemon Trainers (RCT) data through its public API, by
 * reflection: RCT isn't published to any Maven repository, and this way the
 * mod builds and runs without it. Every call returns empty when RCT is missing
 * or its API changed, and logs the problem once.
 */
public final class RctBridge {
    public static final String MOD_ID = "rctmod";
    private static final String API = "com.gitlab.srcmc.rctmod.api.";
    private static boolean warned;

    /** A trainer and its team, as scouting shows it. */
    public record Trainer(String id, Text name, List<TeamMember> team) {
    }

    public record TeamMember(String species, int level, Set<String> aspects, boolean shiny) {
    }

    private RctBridge() {
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    /** The player's current RCT level cap. */
    public static OptionalInt levelCap(ServerPlayerEntity player) {
        if (!isLoaded()) {
            return OptionalInt.empty();
        }
        try {
            Object playerData = call(call(rct(), "getTrainerManager"), "getData", player);
            return OptionalInt.of((Integer) call(playerData, "getLevelCap"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return OptionalInt.empty();
        }
    }

    /** The trainers the player has to beat next in their current series (the ones that raise the level cap). */
    public static List<Trainer> nextTrainers(ServerPlayerEntity player) {
        List<Trainer> trainers = new ArrayList<>();
        if (!isLoaded()) {
            return trainers;
        }
        try {
            Object rct = rct();
            Object state = Class.forName(API + "data.sync.PlayerState").getMethod("get", net.minecraft.entity.player.PlayerEntity.class)
                .invoke(null, player);
            String series = (String) call(state, "getCurrentSeries");
            Object defeated = call(state, "getDefeatedTrainerIds");
            Object graph = call(call(call(rct, "getSeriesManager"), "getGraph", series), "getNext", defeated);
            Object trainerManager = call(rct, "getTrainerManager");

            for (Object node : ((Stream<?>) call(graph, "stream")).toList()) {
                String id = (String) call(node, "id");
                Object trainerTeam = call(call(trainerManager, "getData", id), "getTrainerTeam");
                Text name = (Text) call(call(trainerTeam, "getName"), "getComponent", (Object) new Object[0]);
                List<TeamMember> team = new ArrayList<>();
                for (Object member : (Collection<?>) call(trainerTeam, "getTeam")) {
                    @SuppressWarnings("unchecked")
                    Set<String> aspects = (Set<String>) call(member, "getAspects");
                    team.add(new TeamMember((String) call(member, "getSpecies"), (Integer) call(member, "getLevel"),
                        aspects, (Boolean) call(member, "isShiny")));
                }
                trainers.add(new Trainer(id, name, team));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
        }
        return trainers;
    }

    private static Object rct() throws ReflectiveOperationException {
        return Class.forName(API + "RCTMod").getMethod("getInstance").invoke(null);
    }

    /** Calls the public method {@code name} whose parameters accept {@code args}. */
    private static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && accepts(method.getParameterTypes(), args)) {
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private static boolean accepts(Class<?>[] types, Object[] args) {
        if (types.length != args.length) {
            return false;
        }
        for (int i = 0; i < types.length; i++) {
            if (args[i] != null && !box(types[i]).isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> type) {
        if (type == int.class) {
            return Integer.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        return type;
    }

    private static void warn(Exception e) {
        if (!warned) {
            warned = true;
            Cobblecomputils.LOGGER.error("Couldn't read Radical Cobblemon Trainers data; level cap features are off", e);
        }
    }
}
