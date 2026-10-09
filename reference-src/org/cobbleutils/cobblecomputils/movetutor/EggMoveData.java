package org.cobbleutils.cobblecomputils.movetutor;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Complete Gen 9 egg-move lists read from Showdown's learnsets (Cobblemon's own species data only has some). */
public final class EggMoveData {
   private static final Pattern SPECIES = Pattern.compile("^  ([a-z0-9]+): \\{\\s*$");
   private static final Pattern MOVE = Pattern.compile("^      ([a-z0-9]+): \\[(.*)\\],?\\s*$");
   private static Map<String, Set<String>> data;

   private EggMoveData() {
   }

   private static synchronized Map<String, Set<String>> load() {
      if (data != null) {
         return data;
      }
      Map<String, Set<String>> map = new HashMap<>();
      try {
         Path p = Path.of("showdown", "data", "learnsets.js");
         if (Files.exists(p)) {
            String cur = null;
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
               Matcher sm = SPECIES.matcher(line);
               if (sm.matches()) {
                  cur = sm.group(1);
                  continue;
               }
               if (cur == null) {
                  continue;
               }
               Matcher mm = MOVE.matcher(line);
               if (mm.matches() && mm.group(2).contains("\"9E\"")) {
                  map.computeIfAbsent(cur, k -> new LinkedHashSet<>()).add(mm.group(1));
               }
            }
         }
      } catch (Exception e) {
         // fall back to Cobblemon's own data only
      }
      data = map;
      return map;
   }

   private static String id(String s) {
      return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
   }

   /** Egg moves of this Pokemon and every pre-evolution, as Cobblemon move templates. */
   public static void addTo(Pokemon pokemon, Set<MoveTemplate> out) {
      Map<String, Set<String>> map = load();
      if (map.isEmpty()) {
         return;
      }
      Set<String> ids = new LinkedHashSet<>();
      FormData form = pokemon.getForm();
      for (int i = 0; i < 6 && form != null; i++) {
         ids.add(id(form.showdownId()));
         ids.add(id(form.getSpecies().showdownId()));
         PreEvolution pre = form.getPreEvolution();
         if (pre == null) {
            pre = form.getSpecies().getPreEvolution();
         }
         if (pre == null) {
            break;
         }
         form = pre.getForm();
      }
      for (String sid : ids) {
         Set<String> moves = map.get(sid);
         if (moves == null) {
            continue;
         }
         for (String m : moves) {
            MoveTemplate t = Moves.getByName(m);
            if (t != null) {
               out.add(t);
            }
         }
      }
   }
}
