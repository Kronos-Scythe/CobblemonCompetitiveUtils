#!/usr/bin/env python3
"""
Extract abilities + egg moves per species from Sandstormer's PokeRogue SearchDex.

Source repo: https://github.com/Sandstormer/PokeRogue-Dex
  - pokedex_data.js : one JS object per species/form, using numeric IDs
  - lang/en.js      : fidToName (ID -> ability/move/type name) and
                      speciesNames (same order as the items array)

Per-entry fields we care about:
  a1, a2  main abilities (a2 is missing when the species only has one)
  ha      hidden ability
  pa      passive ability (PokeRogue-only; Cobblemon has no equivalent)
  e1..e4  egg moves (e4 is the "rare" egg move)

Usage:
  python pokerogue_extract.py                  # writes pokerogue_data.json
  python pokerogue_extract.py --csv            # also writes pokerogue_data.csv
  python pokerogue_extract.py --local ./PokeRogue-Dex   # use a local clone
  python pokerogue_extract.py --items pokedex_data.js --lang en.js   # two loose files
"""
import argparse
import csv
import json
import re
import sys
import urllib.request
from pathlib import Path

RAW = "https://raw.githubusercontent.com/Sandstormer/PokeRogue-Dex/main/"
FILES = ["pokedex_data.js", "lang/en.js"]


# --------------------------------------------------------------------------
# Loading
# --------------------------------------------------------------------------
def load_text(name: str, local: Path | None, cache: Path) -> str:
    if local:
        return (local / name).read_text(encoding="utf-8")
    cached = cache / name.replace("/", "_")
    if cached.exists():
        return cached.read_text(encoding="utf-8")
    print(f"Downloading {name} ...", file=sys.stderr)
    req = urllib.request.Request(RAW + name, headers={"User-Agent": "cobblemon-port-script"})
    with urllib.request.urlopen(req, timeout=60) as r:
        text = r.read().decode("utf-8")
    cache.mkdir(parents=True, exist_ok=True)
    cached.write_text(text, encoding="utf-8")
    return text


# --------------------------------------------------------------------------
# Parsing the JS files
# --------------------------------------------------------------------------
def parse_string_array(js: str, var: str) -> list[str]:
    """Parse `var = [ 'a', 'b', ... ];` into a Python list."""
    start = js.index(f"{var} = [")
    end = js.index("\n];", start)
    body = js[start:end]
    out = []
    for m in re.finditer(r"'((?:[^'\\]|\\.)*)'", body):
        out.append(m.group(1).replace("\\'", "'").replace('\\"', '"'))
    return out


def parse_items(js: str) -> list[dict]:
    """Turn `const items=[{dex:1,img:"1",...},...];` into a list of dicts."""
    start = js.index("const items=[") + len("const items=")
    end = js.index("\n];", start) + 2
    body = js[start:end]
    body = re.sub(r",\s*\]$", "]", body)                # trailing comma
    body = re.sub(r"(?<=[{,])(\w+):", r'"\1":', body)   # quote bare keys
    return json.loads(body)


# --------------------------------------------------------------------------
# Building the output
# --------------------------------------------------------------------------
def cobblemon_id(name: str) -> str:
    """Rough Showdown/Cobblemon-style ID: lowercase, letters and digits only."""
    return re.sub(r"[^a-z0-9]", "", name.lower())


def build(items, species_names, fid_to_name):
    def nm(fid):
        if fid is None or fid < 0 or fid >= len(fid_to_name):
            return None
        return fid_to_name[fid]

    rows = []
    for idx, it in enumerate(items):
        egg = [nm(it.get(k)) for k in ("e1", "e2", "e3", "e4")]
        row = {
            "index": idx,
            "dex": it["dex"],
            "img": it.get("img"),
            "species": species_names[idx],
            "abilities": [a for a in (nm(it.get("a1")), nm(it.get("a2"))) if a],
            "hidden_ability": nm(it.get("ha")),
            "passive": nm(it.get("pa")),
            "egg_moves": egg,  # index 3 is the rare egg move
        }
        row["cobblemon"] = {
            "species_id": cobblemon_id(row["species"]),
            "abilities": [cobblemon_id(a) for a in row["abilities"]],
            "hidden_ability": cobblemon_id(row["hidden_ability"]) if row["hidden_ability"] else None,
            "egg_moves": [cobblemon_id(m) for m in egg if m],
        }
        rows.append(row)
    return rows


def write_csv(rows, path: Path):
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["dex", "species", "ability1", "ability2", "hidden", "passive",
                    "egg1", "egg2", "egg3", "egg4_rare"])
        for r in rows:
            a = r["abilities"] + [None] * (2 - len(r["abilities"]))
            w.writerow([r["dex"], r["species"], a[0], a[1], r["hidden_ability"],
                        r["passive"], *r["egg_moves"]])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--local", type=Path, help="path to a local clone of the repo")
    ap.add_argument("--items", type=Path, help="path to pokedex_data.js (overrides --local/download)")
    ap.add_argument("--lang", type=Path, help="path to en.js (overrides --local/download)")
    ap.add_argument("--out", type=Path, default=Path("pokerogue_data.json"))
    ap.add_argument("--csv", action="store_true", help="also write a CSV next to the JSON")
    ap.add_argument("--cache", type=Path, default=Path(".cache"))
    args = ap.parse_args()

    items_js = (args.items.read_text(encoding="utf-8") if args.items
                else load_text("pokedex_data.js", args.local, args.cache))
    lang_js = (args.lang.read_text(encoding="utf-8") if args.lang
               else load_text("lang/en.js", args.local, args.cache))

    items = parse_items(items_js)
    species = parse_string_array(lang_js, "speciesNames")
    fids = parse_string_array(lang_js, "fidToName")
    if len(items) != len(species):
        sys.exit(f"Mismatch: {len(items)} items vs {len(species)} species names")

    rows = build(items, species, fids)
    args.out.write_text(json.dumps(rows, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Wrote {len(rows)} entries to {args.out}")
    if args.csv:
        csv_path = args.out.with_suffix(".csv")
        write_csv(rows, csv_path)
        print(f"Wrote {csv_path}")


if __name__ == "__main__":
    main()
