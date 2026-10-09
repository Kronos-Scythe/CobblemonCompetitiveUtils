#!/usr/bin/env python3
"""Builds mart_catalog.json (the all-in-one Poke Mart menu) from the datapack's CobbleDollars merchant shops.
usage: gen_mart_catalog.py <datapack>/data/potentialpack/cobbledollars/shop  out/mart_catalog.json"""
import glob, json, os, sys

GROUPS = ["Poké Balls", "Healing", "Training", "Battle", "Evolution", "Garden", "Food & Brew", "Tech & Tools", "Decor & Misc"]
BY_CAT = {
    "Pokéballs": 0, "Cobble Balls": 0, "Ancient Balls": 0,
    "Healing Items": 1, "Remedies": 1, "Revives": 1, "Roots": 1,
    "Vitamins": 2, "Exp": 2, "Supplements": 2, "Mint Leaf": 2, "Mochi": 2,
    "Battle Items": 3, "Held Items": 3, "X Items": 3, "Security": 3, "Gems": 3,
    "Evo Items": 4, "Evolution Items": 4, "Stones": 4,
    "Apricorn": 5, "Apricorns": 5, "Berries": 5, "Mulch": 5, "Mulches": 5, "Seeds": 5, "Mint Seeds": 5, "Grass": 5, "Herbs": 5, "Feathers": 5,
    "PokéFood": 6, "Delicious Tails": 6, "Food": 6, "Honey": 6, "Meats": 6, "Milk": 6, "Drinks": 6, "Ingredients": 6,
    "Pokédex": 7, "PokeFinder": 7, "PokéNav": 7, "Trainer Items": 7, "PokéRod": 7, "Baits": 7, "Pots": 7,
    "Dyes": 8, "Inks": 8, "Music Disc": 8, "Oddities": 8, "Bundles": 8, "Wild Loot": 8, "Mob Drops": 8, "Froglights": 8,
}

def group(merchant, cat):
    if cat == "Potions":
        return 6 if merchant == "potions_salesman" else 1
    return BY_CAT.get(cat, 8)

def main(src, out):
    best = {}
    for f in sorted(glob.glob(os.path.join(src, "mart_*.json"))):
        merchant = os.path.basename(f)[5:-5]
        for cat in json.load(open(f, encoding="utf-8")):
            for o in cat["offers"]:
                comps = o.get("components")
                key = (o["item"], json.dumps(comps, sort_keys=True))
                price = int(o["price"])
                e = {"item": o["item"], "price": price, "group": group(merchant, cat["name"]),
                     "merchant": merchant.replace("_", " ").title()}
                if comps: e["components"] = comps
                if key not in best or price < best[key]["price"]:
                    best[key] = e
    items = sorted(best.values(), key=lambda e: (e["group"], e["item"]))
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    json.dump({"groups": GROUPS, "offers": items}, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    print(len(items), "offers ->", out)

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
