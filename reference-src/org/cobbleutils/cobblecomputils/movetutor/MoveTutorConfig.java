package org.cobbleutils.cobblecomputils.movetutor;

import java.util.LinkedHashMap;
import java.util.Map;
import org.cobbleutils.cobblecomputils.config.JsonConfig;

public final class MoveTutorConfig {
   public boolean charge = true;
   public Map<String, Long> prices = defaultPrices();
   public boolean levelUpAboveCurrentLevel = false;
   /** Smeargle Sketch tab: which Pokedex progress a species needs before its moves can be sketched ("owned" = caught, "seen" = encountered). */
   public String sketchRequires = "owned";
   private static MoveTutorConfig current = new MoveTutorConfig();

   public static MoveTutorConfig get() {
      return current;
   }

   public long price(MoveSource source) {
      Long price = this.prices.get(source.key);
      Long fallback = defaultPrices().get(source.key);
      return Math.max(0L, price != null ? price : fallback);
   }

   public static void load() {
      current = (MoveTutorConfig)JsonConfig.load("movetutor.json", MoveTutorConfig.class, MoveTutorConfig::new, current);
      if (current.prices == null) {
         current.prices = defaultPrices();
      }
   }

   private static Map<String, Long> defaultPrices() {
      Map<String, Long> prices = new LinkedHashMap<>();
      prices.put(MoveSource.LEVEL_UP.key, 0L);
      prices.put(MoveSource.EVOLUTION.key, 0L);
      prices.put(MoveSource.EGG.key, 1000L);
      prices.put(MoveSource.TUTOR.key, 500L);
      prices.put(MoveSource.TM.key, 500L);
      prices.put(MoveSource.FORM_CHANGE.key, 500L);
      prices.put(MoveSource.SKETCH.key, 1000L);
      return prices;
   }
}
