package org.cobbleutils.cobblecomputils.showdown;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.types.tera.TeraType;
import com.cobblemon.mod.common.api.types.tera.TeraTypes;
import com.cobblemon.mod.common.pokemon.EVs;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.cobbleutils.cobblecomputils.Cobblecomputils;
import org.cobbleutils.cobblecomputils.evedit.EvEditConfig;
import org.cobbleutils.cobblecomputils.movetutor.LearnableMove;
import org.cobbleutils.cobblecomputils.movetutor.MoveTutorConfig;
import org.cobbleutils.cobblecomputils.showdown.ShowdownFormat.ShowdownSet;

/**
 * Applies Showdown sets to Pokémon the player already owns: EVs, nature (as a
 * mint), ability (only one the species can have), Tera type and moves (only
 * ones /movetutor could teach). Level, IVs, item, shininess and gender are
 * never changed. Priced with the /evedit and /movetutor prices plus
 * showdown.json.
 */
public final class ShowdownImport {
    /** What importing one set would do to one party Pokémon. */
    public static final class Change {
        public final ShowdownSet set;
        public final Pokemon pokemon;
        public final Map<Stat, Integer> evs = new LinkedHashMap<>();
        public Nature nature;
        public PotentialAbility ability;
        public TeraType teraType;
        /** The move list to end up with, or null to leave moves alone. */
        public List<MoveTemplate> moves;
        public long cost;
        public final List<String> summary = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();

        Change(ShowdownSet set, Pokemon pokemon) {
            this.set = set;
            this.pokemon = pokemon;
        }

        public boolean changesAnything() {
            return !evs.isEmpty() || nature != null || ability != null || teraType != null || moves != null;
        }
    }

    /** The whole import: one change per matched set, plus sets no party Pokémon matched. */
    public record Plan(List<Change> changes, List<String> unmatched, long totalCost) {
    }

    private ShowdownImport() {
    }

    public static Plan plan(PlayerPartyStore party, List<ShowdownSet> sets) {
        List<Change> changes = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        Set<Pokemon> used = new HashSet<>();
        long total = 0;
        for (ShowdownSet set : sets) {
            Pokemon target = null;
            for (int i = 0; i < party.size(); i++) {
                Pokemon candidate = party.get(i);
                if (candidate != null && !used.contains(candidate)
                    && ShowdownFormat.toId(candidate.getSpecies().getName()).equals(set.baseSpeciesId())) {
                    target = candidate;
                    break;
                }
            }
            if (target == null) {
                unmatched.add(set.species);
                continue;
            }
            used.add(target);
            Change change = planOne(set, target);
            changes.add(change);
            total += change.cost;
        }
        return new Plan(changes, unmatched, total);
    }

    private static Change planOne(ShowdownSet set, Pokemon pokemon) {
        Change change = new Change(set, pokemon);
        ShowdownConfig config = ShowdownConfig.get();
        boolean charging = !Cobblecomputils.economy().isFree();

        // EVs: Showdown omits zeros, so every stat not listed becomes 0.
        int total = 0;
        boolean valid = true;
        for (Stat stat : ShowdownFormat.STATS) {
            int ev = set.evs.getOrDefault(stat, 0);
            valid &= ev >= 0 && ev <= EVs.MAX_STAT_VALUE;
            total += ev;
        }
        if (!valid || total > EVs.MAX_TOTAL_VALUE) {
            change.skipped.add("EVs (over 252 in a stat or 510 total)");
        } else {
            EvEditConfig evConfig = EvEditConfig.get();
            for (Stat stat : ShowdownFormat.STATS) {
                int target = set.evs.getOrDefault(stat, 0);
                int current = pokemon.getEvs().getOrDefault(stat);
                if (target != current) {
                    change.evs.put(stat, target);
                    if (charging && evConfig.charge && target > current) {
                        change.cost += (long) (target - current) * evConfig.price(stat);
                    }
                }
            }
            if (!change.evs.isEmpty()) {
                change.summary.add("EVs → " + evSpread(set));
            }
        }

        if (set.nature != null) {
            Nature nature = Natures.INSTANCE.getNature(ShowdownFormat.toId(set.nature));
            if (nature == null) {
                change.skipped.add("nature \"" + set.nature + "\" (unknown)");
            } else if (nature != pokemon.getEffectiveNature()) {
                change.nature = nature;
                change.cost += charging ? config.naturePrice : 0;
                change.summary.add("Nature → " + ShowdownFormat.titleCase(nature.getName().getPath()));
            }
        }

        if (set.ability != null) {
            String id = ShowdownFormat.toId(set.ability);
            PotentialAbility match = null;
            for (PotentialAbility potential : pokemon.getForm().getAbilities()) {
                if (potential.getTemplate().getName().equals(id)) {
                    match = potential;
                    break;
                }
            }
            if (match == null) {
                change.skipped.add("ability \"" + set.ability + "\" (" + pokemon.getSpecies().getName() + " can't have it)");
            } else if (!pokemon.getAbility().getName().equals(id)) {
                change.ability = match;
                change.cost += charging ? config.abilityPrice : 0;
                change.summary.add("Ability → " + set.ability);
            }
        }

        if (set.teraType != null) {
            TeraType tera = TeraTypes.INSTANCE.get(ShowdownFormat.toId(set.teraType));
            if (tera == null) {
                change.skipped.add("Tera type \"" + set.teraType + "\" (unknown)");
            } else if (tera != pokemon.getTeraType()) {
                change.teraType = tera;
                change.cost += charging ? config.teraTypePrice : 0;
                change.summary.add("Tera type → " + set.teraType);
            }
        }

        planMoves(change, charging);

        if (set.item != null) {
            change.skipped.add("held item (never given)");
        }
        if (set.level != null && set.level != pokemon.getLevel()) {
            change.skipped.add("level (never changed)");
        }
        if (!set.ivs.isEmpty()) {
            change.skipped.add("IVs (use Hyper Training)");
        }
        return change;
    }

    private static void planMoves(Change change, boolean charging) {
        if (change.set.moves.isEmpty()) {
            return;
        }
        Pokemon pokemon = change.pokemon;
        MoveTutorConfig tutorConfig = MoveTutorConfig.get();
        List<LearnableMove> learnable = LearnableMove.of(pokemon, tutorConfig);
        List<MoveTemplate> wanted = new ArrayList<>();
        long cost = 0;
        for (String name : change.set.moves) {
            MoveTemplate move = Moves.INSTANCE.getByName(ShowdownFormat.toId(name));
            LearnableMove entry = move == null ? null
                : learnable.stream().filter(m -> m.move == move).findFirst().orElse(null);
            if (entry == null) {
                change.skipped.add("move \"" + name + "\" (" + pokemon.getSpecies().getName() + " can't learn it here)");
            } else if (!wanted.contains(move)) {
                wanted.add(move);
                if (!entry.known && charging && tutorConfig.charge) {
                    cost += entry.price(tutorConfig);
                }
            }
        }
        List<MoveTemplate> current = pokemon.getMoveSet().getMoveTemplates();
        if (wanted.isEmpty() || (wanted.size() == current.size() && current.containsAll(wanted))) {
            return;
        }
        change.moves = wanted;
        change.cost += cost;
        change.summary.add("Moves → " + String.join(", ", change.set.moves.stream()
            .filter(n -> wanted.stream().anyMatch(m -> m.getName().equals(ShowdownFormat.toId(n)))).toList()));
    }

    private static String evSpread(ShowdownSet set) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < ShowdownFormat.STATS.length; i++) {
            int ev = set.evs.getOrDefault(ShowdownFormat.STATS[i], 0);
            if (ev > 0) {
                parts.add(ev + " " + ShowdownFormat.STAT_NAMES[i]);
            }
        }
        return parts.isEmpty() ? "none" : String.join(" / ", parts);
    }

    /** Applies a planned change. Payment is handled by the caller. */
    public static void apply(Change change) {
        Pokemon pokemon = change.pokemon;
        if (!change.evs.isEmpty()) {
            EVs evs = pokemon.getEvs();
            // Lower first so raising never trips the 510 total.
            for (Map.Entry<Stat, Integer> entry : change.evs.entrySet()) {
                if (entry.getValue() < evs.getOrDefault(entry.getKey())) {
                    evs.set(entry.getKey(), entry.getValue());
                }
            }
            for (Map.Entry<Stat, Integer> entry : change.evs.entrySet()) {
                evs.set(entry.getKey(), entry.getValue());
            }
            if (pokemon.getCurrentHealth() > pokemon.getMaxHealth()) {
                pokemon.setCurrentHealth(pokemon.getMaxHealth());
            }
        }
        if (change.nature != null) {
            pokemon.setMintedNature(change.nature == pokemon.getNature() ? null : change.nature);
        }
        if (change.ability != null) {
            pokemon.updateAbility(change.ability.getTemplate().create(false, change.ability.getPriority()));
        }
        if (change.teraType != null) {
            pokemon.setTeraType(change.teraType);
        }
        if (change.moves != null) {
            applyMoves(pokemon, change.moves);
        }
    }

    /** Swaps moves in and out through Cobblemon's own relearn logic (removed moves are benched). */
    private static void applyMoves(Pokemon pokemon, List<MoveTemplate> wanted) {
        List<MoveTemplate> toRemove = new ArrayList<>(pokemon.getMoveSet().getMoveTemplates());
        toRemove.removeAll(wanted);
        List<MoveTemplate> toAdd = new ArrayList<>(wanted);
        toAdd.removeAll(pokemon.getMoveSet().getMoveTemplates());

        for (MoveTemplate move : toAdd) {
            if (pokemon.getMoveSet().hasSpace()) {
                if (!pokemon.exchangeMove(null, move)) {
                    pokemon.getMoveSet().add(move.create());
                }
            } else if (!toRemove.isEmpty()) {
                pokemon.exchangeMove(toRemove.remove(0), move);
            }
        }
        for (MoveTemplate move : toRemove) {
            pokemon.exchangeMove(move, null);
        }
        // New moves start with full PP.
        for (Move move : pokemon.getMoveSet().getMoves()) {
            if (toAdd.contains(move.getTemplate())) {
                move.setCurrentPp(move.getMaxPp());
            }
        }
    }

    public static PlayerPartyStore party(net.minecraft.server.network.ServerPlayerEntity player) {
        return Cobblemon.INSTANCE.getStorage().getParty(player);
    }
}
