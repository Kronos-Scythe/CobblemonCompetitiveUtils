package org.cobbleutils.cobblecomputils.movetutor;

public enum MoveSource {
   LEVEL_UP("level_up", "Level-up"),
   EVOLUTION("evolution", "Evolution"),
   EGG("egg", "Egg move"),
   TUTOR("tutor", "Tutor"),
   TM("tm", "TM"),
   FORM_CHANGE("form_change", "Form change"),
   SKETCH("sketch", "Sketch");

   public final String key;
   public final String label;

   private MoveSource(String key, String label) {
      this.key = key;
      this.label = label;
   }
}
