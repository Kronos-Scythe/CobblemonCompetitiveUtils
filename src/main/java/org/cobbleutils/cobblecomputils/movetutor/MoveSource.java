package org.cobbleutils.cobblecomputils.movetutor;

/** Where a Pokémon can learn a move from, in display order. */
public enum MoveSource {
    LEVEL_UP("level_up", "Level-up"),
    EVOLUTION("evolution", "Evolution"),
    EGG("egg", "Egg move"),
    TUTOR("tutor", "Tutor"),
    TM("tm", "TM"),
    FORM_CHANGE("form_change", "Form change");

    /** Key in the price config. */
    public final String key;
    public final String label;

    MoveSource(String key, String label) {
        this.key = key;
        this.label = label;
    }
}
