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
import java.util.Map.Entry;

public final class LearnableMove {
   public final MoveTemplate move;
   public final EnumSet<MoveSource> sources = EnumSet.noneOf(MoveSource.class);
   public int level = -1;
   public boolean known;
   public boolean benched;

   private LearnableMove(MoveTemplate move) {
      this.move = move;
   }

   public MoveSource primarySource() {
      return (MoveSource)this.sources.iterator().next();
   }

   public long price(MoveTutorConfig config) {
      if (this.benched) {
         return 0L;
      } else {
         long price = Long.MAX_VALUE;

         for (MoveSource source : this.sources) {
            price = Math.min(price, config.price(source));
         }

         return price;
      }
   }

   public static List<LearnableMove> of(Pokemon pokemon, MoveTutorConfig config) {
      Map<MoveTemplate, LearnableMove> byMove = new LinkedHashMap<>();
      Learnset learnset = pokemon.getForm().getMoves();

      for (Entry<Integer, List<MoveTemplate>> entry : new TreeMap<Integer, List<MoveTemplate>>(learnset.getLevelUpMoves()).entrySet()) {
         if (entry.getKey() <= pokemon.getLevel() || config.levelUpAboveCurrentLevel) {
            for (MoveTemplate move : entry.getValue()) {
               LearnableMove learnable = byMove.computeIfAbsent(move, LearnableMove::new);
               if (learnable.sources.add(MoveSource.LEVEL_UP)) {
                  learnable.level = entry.getKey();
               }
            }
         }
      }

      add(byMove, learnset.getEvolutionMoves(), MoveSource.EVOLUTION);
      add(byMove, learnset.getEggMoves(), MoveSource.EGG);
      java.util.Set<MoveTemplate> eggExtra = new java.util.LinkedHashSet<>();
      EggMoveData.addTo(pokemon, eggExtra);
      add(byMove, eggExtra, MoveSource.EGG);
      add(byMove, learnset.getTutorMoves(), MoveSource.TUTOR);
      add(byMove, learnset.getTmMoves(), MoveSource.TM);
      add(byMove, learnset.getFormChangeMoves(), MoveSource.FORM_CHANGE);
      addSketch(pokemon, config, byMove);

      for (Move movex : pokemon.getMoveSet().getMoves()) {
         LearnableMove learnable = byMove.get(movex.getTemplate());
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
      result.sort(Comparator.comparing(LearnableMove::primarySource).thenComparingInt(m -> m.level).thenComparing(m -> m.move.getName()));
      return result;
   }

   /** Smeargle only: every move of every species the owner has registered in the Pokedex. */
   private static void addSketch(Pokemon pokemon, MoveTutorConfig config, Map<MoveTemplate, LearnableMove> byMove) {
      if (!pokemon.getSpecies().getName().equalsIgnoreCase("smeargle")) {
         return;
      }
      net.minecraft.class_3222 owner = pokemon.getOwnerPlayer();
      if (owner == null) {
         return;
      }
      try {
         com.cobblemon.mod.common.api.pokedex.PokedexManager dex = com.cobblemon.mod.common.Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(owner);
         boolean seenOk = "seen".equalsIgnoreCase(config.sketchRequires);
         java.util.Set<MoveTemplate> pool = new java.util.LinkedHashSet<>();
         for (com.cobblemon.mod.common.pokemon.Species species : com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getImplemented()) {
            com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress progress = dex.getKnowledgeForSpecies(species.getResourceIdentifier());
            boolean ok = progress == com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress.OWNED
               || (seenOk && progress == com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress.SEEN);
            if (!ok) {
               continue;
            }
            pool.addAll(species.getMoves().getAllLegalMoves());
            for (com.cobblemon.mod.common.pokemon.FormData form : species.getForms()) {
               pool.addAll(form.getMoves().getAllLegalMoves());
            }
         }
         for (MoveTemplate move : pool) {
            String id = move.getName();
            if (id.equals("sketch") || id.equals("chatter") || id.equals("struggle")) {
               continue;
            }
            byMove.computeIfAbsent(move, LearnableMove::new).sources.add(MoveSource.SKETCH);
         }
      } catch (Throwable t) {
         System.out.println("[cobblecomputils] Sketch pool failed: " + t);
      }
   }

   private static void add(Map<MoveTemplate, LearnableMove> byMove, Iterable<MoveTemplate> moves, MoveSource source) {
      for (MoveTemplate move : moves) {
         byMove.computeIfAbsent(move, LearnableMove::new).sources.add(source);
      }
   }
}
