package org.cobbleutils.cobblecomputils.movetutor;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A move a Pokémon can be taught, with every way it learns it. */
public final class LearnableMove {
    public final MoveTemplate move;
    public final EnumSet<MoveSource> sources = EnumSet.noneOf(MoveSource.class);
    /** Level it's learned at by level-up, or -1. */
    public int level = -1;
    /** Already in the Pokémon's four moves. */
    public boolean known;
    /** Known before and forgotten: Cobblemon lets it be relearned for free. */
    public boolean benched;

    private LearnableMove(MoveTemplate move) {
        this.move = move;
    }

    public MoveSource primarySource() {
        return sources.iterator().next();
    }

    /** Cost to teach, or 0 when free. Only meaningful when {@link #known} is false. */
    public long price(MoveTutorConfig config) {
        if (benched) {
            return 0;
        }
        long price = Long.MAX_VALUE;
        for (MoveSource source : sources) {
            price = Math.min(price, config.price(source));
        }
        return price;
    }

    /** Everything this Pokémon's current form can learn, sorted by source, then level, then name. */
    public static List<LearnableMove> of(Pokemon pokemon, MoveTutorConfig config) {
        Map<MoveTemplate, LearnableMove> byMove = new LinkedHashMap<>();
        Learnset learnset = pokemon.getForm().getMoves();

        for (Map.Entry<Integer, List<MoveTemplate>> entry : new TreeMap<>(learnset.getLevelUpMoves()).entrySet()) {
            if (entry.getKey() > pokemon.getLevel() && !config.levelUpAboveCurrentLevel) {
                continue;
            }
            for (MoveTemplate move : entry.getValue()) {
                LearnableMove learnable = byMove.computeIfAbsent(move, LearnableMove::new);
                if (learnable.sources.add(MoveSource.LEVEL_UP)) {
                    learnable.level = entry.getKey();
                }
            }
        }
        add(byMove, learnset.getEvolutionMoves(), MoveSource.EVOLUTION);
        add(byMove, learnset.getEggMoves(), MoveSource.EGG);
        add(byMove, learnset.getTutorMoves(), MoveSource.TUTOR);
        add(byMove, learnset.getTmMoves(), MoveSource.TM);
        add(byMove, learnset.getFormChangeMoves(), MoveSource.FORM_CHANGE);

        for (Move move : pokemon.getMoveSet().getMoves()) {
            LearnableMove learnable = byMove.get(move.getTemplate());
            if (learnable != null) {
                learnable.known = true;
            }
        }
        for (BenchedMove benched : pokemon.getBenchedMoves()) {
            LearnableMove learnable = byMove.get(benched.getMoveTemplate());
            if (learnable != null) {
                learnable.benched = true;
            }
        }

        List<LearnableMove> result = new ArrayList<>(byMove.values());
        result.sort(Comparator.comparing(LearnableMove::primarySource)
            .thenComparingInt(m -> m.level)
            .thenComparing(m -> m.move.getName()));
        return result;
    }

    private static void add(Map<MoveTemplate, LearnableMove> byMove, Iterable<MoveTemplate> moves, MoveSource source) {
        for (MoveTemplate move : moves) {
            byMove.computeIfAbsent(move, LearnableMove::new).sources.add(source);
        }
    }
}
