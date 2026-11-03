"""
LOTR's factions (factions.json from the exporter) as faction data for the port:

  lotr/src/generated/resources/data/lotr_port/faction/<id>.json   colour, region, types, ranks, pledge rank, map
                                                                    area, control zones, default relations
  lang: faction.lotr_port.<id>, .entity, .subtitle, .rank.<rank> (and every other key LOTR has under the faction)
"""
import os

from assets import NS, write_json


def faction_id(code):
    return code.lower()


class FactionData:
    def __init__(self, gen, export, data_dir, jar):
        self.gen = gen
        self.export = export
        self.data = data_dir
        self.jar = jar

    def faction(self, f, ids):
        fields = f.get("fields", {})
        ranks = [{"id": r["name"], "alignment": r["alignment"], **({"gendered": True} if r.get("isGendered") else {})}
                 for r in f.get("ranks", []) if r.get("name")]
        out = {
            "color": f.get("color", 0xFFFFFF) & 0xFFFFFF,
            "region": (f.get("region") or "none").lower(),
            "types": sorted(t.lower().removeprefix("type_") for t in f.get("types", [])),
            "playable": bool(fields.get("allowPlayer", True)),
        }
        if fields.get("hasFixedAlignment"):
            out["fixed_alignment"] = float(fields.get("fixedAlignment", 0))
        if fields.get("isolationist"):
            out["isolationist"] = True
        if fields.get("approvesWarCrimes"):
            out["approves_war_crimes"] = True
        if ranks:
            out["ranks"] = ranks
        pledge = (f.get("pledgeRank") or {}).get("name")
        if pledge and any(r["id"] == pledge for r in ranks):
            out["pledge_rank"] = pledge
        m = f.get("map")
        if m:
            out["map"] = {"x": m.get("mapX", 0), "z": m.get("mapY", 0), "radius": m.get("radius", 0)}
        zones = [{"x": z.get("mapX", 0), "z": z.get("mapY", 0), "radius": z.get("radius", 0)}
                 for z in f.get("controlZones", [])]
        if zones:
            out["control_zones"] = zones
        relations = {}
        for other, rel in sorted((f.get("relations") or {}).items()):
            if rel and rel != "NEUTRAL" and other in ids:
                relations[f"{NS}:{faction_id(other)}"] = rel.lower()
        if relations:
            out["relations"] = relations
        return out

    def lang(self, codes):
        for code_lang in self.jar.langs():
            src = self.jar.lang(code_lang)
            dst = self.gen.lang[code_lang.lower()]
            for code in codes:
                prefix = f"lotr.faction.{code}."
                new = f"faction.{NS}.{faction_id(code)}"
                for key, text in src.items():
                    if not key.startswith(prefix):
                        continue
                    rest = key[len(prefix):]
                    dst[new if rest == "name" else f"{new}.{rest}"] = text
            if code_lang == "en_US":
                for code in codes:
                    key = f"faction.{NS}.{faction_id(code)}"
                    if key not in dst:
                        dst[key] = code.replace("_", " ").title()
                        self.gen.report["untranslated_factions"].append(code)

    def run(self):
        factions = [f for f in self.export if f.get("code")]
        ids = {f["code"] for f in factions}
        for f in factions:
            write_json(os.path.join(self.data, NS, "faction", faction_id(f["code"]) + ".json"),
                       self.faction(f, ids))
        self.lang(sorted(ids))
        return {"factions": len(factions)}
