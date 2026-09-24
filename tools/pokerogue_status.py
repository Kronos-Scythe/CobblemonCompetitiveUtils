#!/usr/bin/env python3
"""
Find out which PokeRogue abilities and moves are flagged as not (fully) implemented.

PokeRogue's source marks them right in the definition, e.g.
    new AbBuilder(AbilityId.ILLUSION, 5)
      .unimplemented()
    new AttackMove(MoveId.SOME_MOVE, ...)
      .partial()

Source files (branch `beta` of pagefaultgames/pokerogue):
  src/data/abilities/init-abilities.ts
  src/data/moves/move.ts
(If those paths move, update SOURCES below.)

Output: pokerogue_status.json
  {"abilities": {"illusion": "unimplemented", "overgrow": "ok", ...},
   "moves":     {"bide": "unimplemented", ...}}
Keys are normalised IDs (lowercase letters/digits only), same as the extract script.

Usage:
  python pokerogue_status.py
  python pokerogue_status.py --local ./pokerogue      # clone with src/data checked out
"""
import argparse
import json
import re
import urllib.request
from pathlib import Path

BASE = "https://raw.githubusercontent.com/pagefaultgames/pokerogue/beta/"
SOURCES = {
    "abilities": ("src/data/abilities/init-abilities.ts", "AbilityId", r"new AbBuilder"),
    "moves": ("src/data/moves/move.ts", "MoveId", r"new \w+"),
}


def norm(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


def read(path: str, local: Path | None) -> str:
    if local:
        return (local / path).read_text(encoding="utf-8")
    req = urllib.request.Request(BASE + path, headers={"User-Agent": "cobblemon-port-script"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8")


def scan(src: str, enum: str, ctor: str) -> dict[str, str]:
    # Definitions sit at 4-space indent: `    new AttackMove(MoveId.POUND, ...)`
    pat = re.compile(rf"^    {ctor}\(\s*{enum}\.(\w+)", re.M)
    hits = list(pat.finditer(src))
    out = {}
    for i, m in enumerate(hits):
        end = hits[i + 1].start() if i + 1 < len(hits) else len(src)
        chunk = re.sub(r"//.*", "", src[m.start():end])  # ignore commented-out flags
        if ".unimplemented()" in chunk:
            status = "unimplemented"
        elif ".partial()" in chunk:
            status = "partial"
        else:
            status = "ok"
        out[norm(m.group(1))] = status
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--local", type=Path, help="local pokerogue checkout (needs src/data)")
    ap.add_argument("--out", type=Path, default=Path("pokerogue_status.json"))
    args = ap.parse_args()

    result = {}
    for kind, (path, enum, ctor) in SOURCES.items():
        result[kind] = scan(read(path, args.local), enum, ctor)
        counts = {s: sum(1 for v in result[kind].values() if v == s)
                  for s in ("ok", "partial", "unimplemented")}
        print(f"{kind}: {len(result[kind])} total  {counts}")
    args.out.write_text(json.dumps(result, indent=1), encoding="utf-8")
    print(f"Wrote {args.out}")


if __name__ == "__main__":
    main()
