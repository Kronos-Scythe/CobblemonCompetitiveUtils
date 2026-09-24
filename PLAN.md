# Cobblemon Competitive Utils: Plan

Target: **Cobblemon 1.8.1**, **Minecraft 1.21.1**, **Fabric**, Java 21.
Mod id / datapack namespace: `cobblecomputils`.

Goal: bring PokeRogue's abilities and egg moves to Cobblemon species through a
separate mod. Cobblemon's jar is never modified.

## Data pipeline (build-time tools, `tools/`)

Run in this order:

```
python tools/pokerogue_extract.py --items pokedex_data.js --lang en.js
python tools/pokerogue_status.py
python tools/build_additions.py --cobblemon path/to/cobblemon-fabric-1.8.1.jar path/to/mega_showdown-fabric.jar --passive ability
```

1. `pokerogue_extract.py` reads the PokeRogue dex and language files and writes
   `pokerogue_data.json` with decoded names.
2. `pokerogue_status.py` reads PokeRogue's source (`.unimplemented()` /
   `.partial()` tags) and writes `pokerogue_status.json`.
3. `build_additions.py` **only reads** the Cobblemon jar. It writes one species
   addition per Pokémon to `generated/data/cobblecomputils/species_additions/<species>.json`
   (change with `--out` / `--namespace`),
   validates every ability and move against what Cobblemon knows, and writes
   everything dropped or skipped to `report.txt`.

Rules:
- Unimplemented PokeRogue abilities/moves are dropped. `--partial drop` also
  drops partial ones.
- Abilities or moves that no Cobblemon species has are dropped. This is
  checked against the species files, not Showdown's move list, so signature
  moves only gained through a form change or special mechanic (Behemoth
  Blade/Bash, the Starmobile torques, Let's Go partner moves, Nihil Light) are
  dropped even though Cobblemon's battle engine knows most of them. This is
  intentional: 19 moves on Cobblemon 1.8.1.
- If a species has no valid main ability left, it keeps its original abilities.
- An ability is never listed both as regular and hidden (Cobblemon's own
  `gastly.json` has `["levitate", "h:levitate"]`); the regular entry wins.
- Passives are ignored by default. `--passive ability` adds each passive to the
  regular ability pool as an extra option (Smeargle: Own Tempo / Technician /
  Prankster). That is not a true passive: the Pokémon rolls it *instead of*
  its other ability, not on top of it. A passive skips when it is already the
  hidden ability.
- The default replaces Cobblemon's `egg:` moves. `--keep-base-egg` adds
  PokeRogue's on top instead.
- Species additions rewrite whole lists, so the builder writes the full base
  move list with the `egg:` entries swapped.

Known gaps: true passives (a second ability active at the same time needs
battle-engine work, a candidate for after milestone 2), forms (Megas, regionals; skipped
and reported), abilities missing from Cobblemon (dropped, no custom Showdown
scripts).

## Milestone 1: data-only prototype (current)

- [x] Fabric project set up, Cobblemon 1.8.1 + Fabric Language Kotlin as dependencies
- [x] `src/main/resources/data/cobblecomputils/species_additions/` ready for generated files
- [x] Commit the three Python scripts to `tools/`
- [ ] Run the builder against the real Cobblemon 1.8.1 jar and review `generated/report.txt`
- [ ] Copy `generated/data/cobblecomputils/species_additions/*.json` into `src/main/resources/data/cobblecomputils/species_additions/`
- [ ] `runClient`, spawn a few species, confirm abilities and egg moves changed

Drawbacks this milestone accepts: the files copy Cobblemon's move lists, so
they go stale when Cobblemon changes moves, and they lose to another addon that
rewrites the same species first in load order.

## Findings from Cobblemon 1.8.1 source

- `species_additions` replaces every field it sets, except `forms` and
  `evolutions`, which it **appends to** (`SpeciesAdditions.reload`). So an
  addition cannot edit an existing form (Alolan Vulpix, Origin Giratina): it
  would add a second form with the same name. Form abilities/egg moves need
  the runtime merge (milestone 2).
- Regional and other forms live in the species file's `forms` list, each with
  its own `abilities` and `moves` (keyed by `name`, selected by `aspects`).
- Any number of regular abilities is fine: `AbilityPool.select` picks at random
  among the lowest-priority group, so `--passive ability` giving three regular
  abilities works.
- Mega and Gmax forms already exist in Cobblemon's species files as forms
  (Charizard: `Mega-X`, `Mega-Y`, `Gmax`); Cobblemon has the battle hooks
  (`MegaEvolutionEvent`, `MegaInstruction`) but not the gameplay.

## Mega Showdown (dependency)

https://github.com/yajatkaul/CobblemonMegaShowdown provides megas, Z-moves,
Tera, Dynamax and form changes (1.1.3, built on Cobblemon 1.8.0, needs
Architectury and Accessories).
- Its 114 species additions never set `abilities` or `moves`, so they don't
  clash with ours.
- It **replaces 55 Cobblemon species files** outright (Charizard, Gengar,
  Lucario, Zygarde...), sometimes with different moves (Gengar +2/-1). Our
  additions copy the base move list, so the builder must read Mega Showdown's
  jar after Cobblemon's:
  `--cobblemon cobblemon-fabric-1.8.1.jar mega_showdown-fabric.jar`.
- Its mega forms are `battleOnly` with a single fixed ability
  (`["toughclaws", "h:toughclaws"]`) and no own moves: they battle with the
  base form's moves. PokeRogue's mega entries therefore add nothing except
  possibly a different mega ability.
- Regional and other non-battle forms (Alolan Vulpix, Therian Landorus...)
  have their own abilities and moves, and additions can't edit an existing
  form (see above). Porting PokeRogue data to them is milestone 2 work and
  must run after Mega Showdown's data has loaded.

## Milestone 2: runtime merge

- Replace the generated files with a compact table (species → abilities, egg
  moves) built from the filtered `pokerogue_data.json`.
- When Cobblemon loads species, merge the table into the live data: replace the
  `egg:` entries and abilities, leave everything else alone.
- First, read Cobblemon 1.8.1's source to find how it loads species and applies
  additions (events or registry hooks) and pick the hook to use.
