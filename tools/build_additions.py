#!/usr/bin/env python3
"""
Build Cobblemon `species_additions` JSON from the extracted PokeRogue data,
validating everything on the way.

Inputs
  pokerogue_data.json    from pokerogue_extract.py
  pokerogue_status.json  from pokerogue_status.py (unimplemented / partial flags)
  --cobblemon PATH       Cobblemon jar/zip, or a folder holding its data files.
                         Needed for two reasons:
                           1. species additions REWRITE list fields, so the moves list we emit
                              must be the full base list with only the egg moves swapped;
                           2. it tells us which abilities/moves Cobblemon species actually use.

Rules applied
  - PokeRogue abilities/moves flagged `unimplemented` are dropped (`partial` kept unless --partial drop)
  - Abilities/moves that no Cobblemon species has (in any list, any form) are dropped,
    e.g. signature moves gained only through a form change such as Behemoth Blade
  - Species PokeRogue only lists under a form name are matched by dex number to their default form
  - Other entries that can't be matched to a Cobblemon species (forms, megas, gmax...) are skipped and reported
  - PokeRogue passives are ignored by default: a passive is active ON TOP of the chosen ability,
    which Cobblemon's one-ability battles can't express. `--passive ability` instead adds the
    passive to the regular ability pool as an extra option (Smeargle: Own Tempo / Technician /
    Prankster), which changes what it means: the Pokémon rolls it instead of having it as well
  - An ability is never listed twice: if the hidden ability is also a regular one (Cobblemon's own
    gastly.json has ["levitate", "h:levitate"]), only the regular entry is kept
  - If no main ability survives, the base abilities are left alone (nothing emitted for abilities)

Output
  <out>/data/<namespace>/species_additions/<species>.json
  <out>/report.txt

Usage
  python build_additions.py --cobblemon Cobblemon-fabric.jar
  python build_additions.py --cobblemon ./cobblemon_data --namespace myaddon --partial drop
"""
import argparse
import io
import json
import re
import sys
import unicodedata
import zipfile
from collections import defaultdict
from pathlib import Path

SPECIES_RE = re.compile(r"(?:^|/)data/[^/]+/species/(?:[^/]+/)*[^/]+\.json$")

# Display names in the SearchDex that don't normalise cleanly to a Showdown-style ID.
NAME_FIXES = {
    "embodyaspectattack": "embodyaspecthearthflame",
    "embodyaspectdefense": "embodyaspectcornerstone",
    "embodyaspectspeed": "embodyaspectteal",
    "embodyaspectspdef": "embodyaspectwellspring",
    "embodyaspectspnbspdef": "embodyaspectwellspring",  # 'Sp.&nbspDef' as scraped
    "nidoranf": "nidoranf", "nidoranm": "nidoranm",
}


def norm(name: str) -> str:
    name = name.replace("♀", "f").replace("♂", "m")
    name = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode()
    key = re.sub(r"[^a-z0-9]", "", name.lower())
    return NAME_FIXES.get(key, key)


# --------------------------------------------------------------------------
# Reading Cobblemon's base data
# --------------------------------------------------------------------------
def _from_zip(zf: zipfile.ZipFile, found: dict, depth=0):
    for info in zf.infolist():
        n = info.filename
        if SPECIES_RE.search(n):
            try:
                found[n] = json.loads(zf.read(info).decode("utf-8"))
            except (ValueError, UnicodeDecodeError) as e:
                print(f"  skipping unreadable {n}: {e}", file=sys.stderr)
        elif depth < 2 and n.endswith(".jar") and "META-INF/jars/" in n:
            with zipfile.ZipFile(io.BytesIO(zf.read(info))) as inner:
                _from_zip(inner, found, depth + 1)


def load_cobblemon(path: Path) -> dict[str, dict]:
    """Return {species_id: species_json} for base species files."""
    raw: dict[str, dict] = {}
    if path.is_dir():
        for f in path.rglob("*.json"):
            rel = f.as_posix()
            if SPECIES_RE.search(rel):
                raw[rel] = json.loads(f.read_text(encoding="utf-8"))
    else:
        with zipfile.ZipFile(path) as zf:
            _from_zip(zf, raw)
    species = {}
    for rel, data in raw.items():
        if not isinstance(data, dict):
            continue
        sid = norm(data.get("name") or Path(rel).stem)
        species[sid] = data
    return species


def known_ids(species: dict[str, dict]):
    abilities, moves = set(), set()

    def take(block):
        for a in block.get("abilities", []):
            abilities.add(norm(a.split(":", 1)[-1]))
        for m in block.get("moves", []):
            moves.add(norm(m.split(":", 1)[-1]))

    for data in species.values():
        take(data)
        for form in data.get("forms", []):
            take(form)
    return abilities, moves


# --------------------------------------------------------------------------
# Building
# --------------------------------------------------------------------------
# PokeRogue entries that are battle-only transformations, never a species' default form.
BATTLE_FORM_RE = re.compile(r"^(Mega|Gigantamax|Primal|Eternamax|Ultra) ")


def match_rows(rows, species, report):
    """Pair each Cobblemon species with one PokeRogue entry.

    Exact name first. Species PokeRogue only lists under a form name
    ("Altered Giratina", "Female Nidoran", "Shield Aegislash") fall back to the
    first non-battle-form entry with the same national dex number, which is the
    default form in PokeRogue's ordering.
    """
    matched, by_dex = {}, defaultdict(list)
    for row in rows:
        sid = norm(row["species"])
        if sid in species:
            matched.setdefault(sid, row)
        elif not BATTLE_FORM_RE.match(row["species"]):
            by_dex[row["dex"]].append(row)
    for sid, data in species.items():
        if sid in matched:
            continue
        candidates = by_dex.get(data.get("nationalPokedexNumber"))
        if candidates:
            matched[sid] = candidates[0]
            report["base species matched via their default-form entry"].append(
                f"{data.get('name', sid)} <- {candidates[0]['species']}")
        else:
            report["Cobblemon species with no PokeRogue entry"].append(data.get("name", sid))
    used = {id(r) for r in matched.values()}
    for row in rows:
        if id(row) not in used and norm(row["species"]) not in species:
            report["skipped entries (no matching Cobblemon species - forms, megas, etc.)"].append(row["species"])
    return matched


def build(rows, status, species, k_abilities, k_moves, args):
    report = defaultdict(list)
    additions = {}

    def check(kind, display, cobble_known):
        """Return the normalised ID if usable, else None (and log why)."""
        key = norm(display)
        st = status[kind].get(key)
        if st == "unimplemented":
            report[f"dropped {kind} (unimplemented in PokeRogue)"].append(display)
            return None
        if st == "partial":
            report[f"{kind} flagged partial in PokeRogue"].append(display)
            if args.partial == "drop":
                return None
        if st is None:
            report[f"{kind} not found in PokeRogue source (kept)"].append(display)
        if key not in cobble_known:
            report[f"dropped {kind} (no Cobblemon species has them)"].append(display)
            return None
        return key

    for sid, row in match_rows(rows, species, report).items():
        base = species[sid]
        add = {"target": f"cobblemon:{sid}"}

        # ---- abilities -------------------------------------------------
        main = [check("abilities", a, k_abilities) for a in row["abilities"]]
        main = list(dict.fromkeys(a for a in main if a))
        if main:
            hidden = check("abilities", row["hidden_ability"], k_abilities) if row["hidden_ability"] else None
            if hidden:
                hidden_entries = [f"h:{hidden}"]
            else:  # keep whatever hidden ability Cobblemon already had
                hidden_entries = [a for a in base.get("abilities", []) if a.startswith("h:")]
            if args.passive == "ability" and row.get("passive"):
                passive = check("abilities", row["passive"], k_abilities)
                if passive and passive not in main and f"h:{passive}" not in hidden_entries:
                    main.append(passive)
                    report["passives added as a regular ability"].append(f"{row['species']}: {row['passive']}")
            abilities = main + [h for h in hidden_entries if h.split(":", 1)[-1] not in main]
            if len(abilities) < len(main) + len(hidden_entries):
                report["hidden ability dropped (same as a regular ability)"].append(row["species"])
            add["abilities"] = abilities
        else:
            report["species left with base abilities (no valid main ability)"].append(row["species"])

        # ---- moves (list is rewritten, so start from the full base list) -
        egg = [check("moves", m, k_moves) for m in row["egg_moves"]]
        egg = list(dict.fromkeys(m for m in egg if m))
        base_moves = base.get("moves", [])
        if egg:
            kept = [m for m in base_moves if args.keep_base_egg or not m.startswith("egg:")]
            new = [f"egg:{m}" for m in egg if f"egg:{m}" not in kept]
            add["moves"] = kept + new
        else:
            report["species left with base egg moves (none valid)"].append(row["species"])

        if len(add) > 1:
            additions[sid] = add
    return additions, report


def write_output(additions, report, out: Path, ns: str, stats: str):
    d = out / "data" / ns / "species_additions"
    d.mkdir(parents=True, exist_ok=True)
    for sid, add in additions.items():
        (d / f"{sid}.json").write_text(json.dumps(add, indent=2), encoding="utf-8")
    lines = [stats, ""]
    for title, items in sorted(report.items()):
        uniq = sorted(set(items))
        lines.append(f"## {title} ({len(uniq)})")
        lines.append(", ".join(uniq))
        lines.append("")
    (out / "report.txt").write_text("\n".join(lines), encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", type=Path, default=Path("pokerogue_data.json"))
    ap.add_argument("--status", type=Path, default=Path("pokerogue_status.json"))
    ap.add_argument("--cobblemon", type=Path, required=True)
    ap.add_argument("--out", type=Path, default=Path("generated"))
    ap.add_argument("--namespace", default="cobblecomputils")
    ap.add_argument("--partial", choices=["keep", "drop"], default="keep",
                    help="what to do with abilities/moves PokeRogue flags as partially implemented")
    ap.add_argument("--passive", choices=["ignore", "ability"], default="ignore",
                    help="'ability' adds each PokeRogue passive to the regular ability pool")
    ap.add_argument("--keep-base-egg", action="store_true",
                    help="keep Cobblemon's own egg moves and add PokeRogue's on top")
    args = ap.parse_args()

    rows = json.loads(args.data.read_text(encoding="utf-8"))
    status = json.loads(args.status.read_text(encoding="utf-8"))
    species = load_cobblemon(args.cobblemon)
    if not species:
        sys.exit("No Cobblemon species files found. Point --cobblemon at the jar or an extracted data folder.")
    k_ab, k_mv = known_ids(species)
    print(f"Cobblemon: {len(species)} species, {len(k_ab)} abilities, {len(k_mv)} moves known")

    additions, report = build(rows, status, species, k_ab, k_mv, args)
    stats = (f"{len(additions)} species additions written "
             f"(of {len(rows)} PokeRogue entries, {len(species)} Cobblemon species)")
    write_output(additions, report, args.out, args.namespace, stats)
    print(stats)
    print(f"See {args.out / 'report.txt'} for everything that was dropped or skipped.")


if __name__ == "__main__":
    main()
