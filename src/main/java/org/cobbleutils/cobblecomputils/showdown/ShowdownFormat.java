package org.cobbleutils.cobblecomputils.showdown;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Language;

/** Converts Pokémon to and from Showdown's team text format. */
public final class ShowdownFormat {
    static final Stat[] STATS = {
        Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    };
    static final String[] STAT_NAMES = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};
    private static final Pattern NICKNAMED = Pattern.compile("^(.*) \\((.+)\\)$");

    /** One set parsed from Showdown text. Missing EVs are 0, missing IVs 31, as in Showdown. */
    public static final class ShowdownSet {
        public String nickname;
        public String species;
        public String item;
        public String ability;
        public String nature;
        public String teraType;
        public Integer level;
        public final Map<Stat, Integer> evs = new LinkedHashMap<>();
        public final Map<Stat, Integer> ivs = new LinkedHashMap<>();
        public final List<String> moves = new ArrayList<>();

        /** "Ninetales-Alola" → "ninetales". */
        public String baseSpeciesId() {
            int dash = species.indexOf('-');
            return toId(dash > 0 ? species.substring(0, dash) : species);
        }
    }

    private ShowdownFormat() {
    }

    /** Showdown's toID: lowercase letters and digits only. */
    public static String toId(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    public static String export(Pokemon pokemon) {
        StringBuilder out = new StringBuilder();
        String species = pokemon.getSpecies().getName();
        String form = pokemon.getForm().getName();
        if (!form.equalsIgnoreCase("Normal") && !form.equalsIgnoreCase(species)) {
            species += "-" + form;
        }
        String nickname = pokemon.getNickname() == null ? "" : pokemon.getNickname().getString();
        out.append(nickname.isEmpty() || nickname.equals(species) ? species : nickname + " (" + species + ")");
        if (pokemon.getGender() == Gender.MALE) {
            out.append(" (M)");
        } else if (pokemon.getGender() == Gender.FEMALE) {
            out.append(" (F)");
        }
        ItemStack item = pokemon.heldItem();
        if (!item.isEmpty()) {
            out.append(" @ ").append(titleCase(Registries.ITEM.getId(item.getItem()).getPath()));
        }
        out.append('\n');

        out.append("Ability: ").append(translate(pokemon.getAbility().getDisplayName(), pokemon.getAbility().getName())).append('\n');
        out.append("Level: ").append(pokemon.getLevel()).append('\n');
        if (pokemon.getShiny()) {
            out.append("Shiny: Yes\n");
        }
        out.append("Tera Type: ").append(titleCase(pokemon.getTeraType().getName())).append('\n');

        StringJoiner evs = new StringJoiner(" / ");
        StringJoiner ivs = new StringJoiner(" / ");
        for (int i = 0; i < STATS.length; i++) {
            int ev = pokemon.getEvs().getOrDefault(STATS[i]);
            int iv = pokemon.getIvs().getOrDefault(STATS[i]);
            if (ev > 0) {
                evs.add(ev + " " + STAT_NAMES[i]);
            }
            if (iv != 31) {
                ivs.add(iv + " " + STAT_NAMES[i]);
            }
        }
        if (evs.length() > 0) {
            out.append("EVs: ").append(evs).append('\n');
        }
        out.append(titleCase(pokemon.getEffectiveNature().getName().getPath())).append(" Nature\n");
        if (ivs.length() > 0) {
            out.append("IVs: ").append(ivs).append('\n');
        }
        for (Move move : pokemon.getMoveSet().getMoves()) {
            String name = move.getTemplate().getDisplayName().getString();
            out.append("- ").append(name.startsWith("cobblemon.") ? move.getTemplate().getName() : name).append('\n');
        }
        return out.toString();
    }

    private static String translate(String key, String fallbackId) {
        String translated = Language.getInstance().get(key);
        return translated.equals(key) ? titleCase(fallbackId) : translated;
    }

    /** "choice_band" → "Choice Band". Showdown matches names by {@link #toId}, so this only needs to be readable. */
    static String titleCase(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("[_ ]")) {
            if (!word.isEmpty()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    /** Parses Showdown team text: sets separated by blank lines. Unknown lines are ignored. */
    public static List<ShowdownSet> parse(String text) {
        List<ShowdownSet> sets = new ArrayList<>();
        ShowdownSet current = null;
        for (String raw : text.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                current = null;
                continue;
            }
            if (line.startsWith("===")) {
                continue; // team header from Showdown's teambuilder export
            }
            if (current == null) {
                current = parseHeader(line);
                sets.add(current);
            } else if (line.startsWith("- ")) {
                if (current.moves.size() < 4) {
                    current.moves.add(line.substring(2).trim());
                }
            } else if (line.startsWith("Ability:")) {
                current.ability = line.substring(8).trim();
            } else if (line.startsWith("Level:")) {
                try {
                    current.level = Integer.parseInt(line.substring(6).trim());
                } catch (NumberFormatException ignored) {
                    // leave unset
                }
            } else if (line.startsWith("Tera Type:")) {
                current.teraType = line.substring(10).trim();
            } else if (line.startsWith("EVs:")) {
                parseStats(line.substring(4), current.evs);
            } else if (line.startsWith("IVs:")) {
                parseStats(line.substring(4), current.ivs);
            } else if (line.endsWith(" Nature")) {
                current.nature = line.substring(0, line.length() - 7).trim();
            }
        }
        return sets;
    }

    private static ShowdownSet parseHeader(String line) {
        ShowdownSet set = new ShowdownSet();
        String left = line;
        int at = line.lastIndexOf(" @ ");
        if (at >= 0) {
            set.item = line.substring(at + 3).trim();
            left = line.substring(0, at).trim();
        }
        if (left.endsWith(" (M)") || left.endsWith(" (F)")) {
            left = left.substring(0, left.length() - 4).trim();
        }
        Matcher matcher = NICKNAMED.matcher(left);
        if (matcher.matches()) {
            set.nickname = matcher.group(1).trim();
            set.species = matcher.group(2).trim();
        } else {
            set.species = left;
        }
        return set;
    }

    /** "252 Atk / 4 SpD / 252 Spe" */
    private static void parseStats(String text, Map<Stat, Integer> into) {
        for (String part : text.split("/")) {
            String[] pieces = part.trim().split("\\s+");
            if (pieces.length != 2) {
                continue;
            }
            for (int i = 0; i < STAT_NAMES.length; i++) {
                if (STAT_NAMES[i].equalsIgnoreCase(pieces[1])) {
                    try {
                        into.put(STATS[i], Integer.parseInt(pieces[0]));
                    } catch (NumberFormatException ignored) {
                        // skip this stat
                    }
                }
            }
        }
    }
}
