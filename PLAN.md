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
python tools/build_additions.py --cobblemon path/to/cobblemon-1.8.1.jar
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
- The default replaces Cobblemon's `egg:` moves. `--keep-base-egg` adds
  PokeRogue's on top instead.
- Species additions rewrite whole lists, so the builder writes the full base
  move list with the `egg:` entries swapped.

Known gaps: passives (Cobblemon has no slot), forms (Megas, regionals; skipped
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

## Milestone 2: runtime merge

- Replace the generated files with a compact table (species → abilities, egg
  moves) built from the filtered `pokerogue_data.json`.
- When Cobblemon loads species, merge the table into the live data: replace the
  `egg:` entries and abilities, leave everything else alone.
- First, read Cobblemon 1.8.1's source to find how it loads species and applies
  additions (events or registry hooks) and pick the hook to use.
