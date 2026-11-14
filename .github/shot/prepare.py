"""THROWAWAY: readies tardis/run for a headless client screenshot of the time vortex (see tardis/docs/headless-client.md)."""
import json, os, sys
import nbtlib

run = sys.argv[1]
save = os.path.join(run, "saves", "shot")
with open(os.path.join(run, "options.txt"), "w") as f:
    f.write("onboardAccessibility:false\npauseOnLostFocus:false\nchatVisibility:2\nrenderDistance:4\n"
            "tutorialStep:none\nfov:0.0\n")
os.makedirs(os.path.join(run, "config"), exist_ok=True)
with open(os.path.join(run, "config", "immersive_portals.json"), "w") as f:
    json.dump({"initialScreenShown": True}, f)
lvl = nbtlib.load(os.path.join(save, "level.dat"))
lvl["Data"]["confirmedExperimentalSettings"] = nbtlib.Byte(1)
lvl.save()
pack = os.path.join(save, "datapacks", "shot")
os.makedirs(os.path.join(pack, "data", "minecraft", "tags", "function"), exist_ok=True)
os.makedirs(os.path.join(pack, "data", "shot", "function"), exist_ok=True)
json.dump({"pack": {"pack_format": 48, "description": "vortex shot"}}, open(os.path.join(pack, "pack.mcmeta"), "w"))
json.dump({"values": ["shot:start"]}, open(os.path.join(pack, "data", "minecraft", "tags", "function", "load.json"), "w"))
open(os.path.join(pack, "data", "shot", "function", "start.mcfunction"), "w").write("schedule function shot:go 100t\n")
# a spectator on the tunnel's axis (lane 0, LANE_Y 128), looking down it (+z); again every 5 s, so a late join is caught
open(os.path.join(pack, "data", "shot", "function", "go.mcfunction"), "w").write(
    "gamemode spectator @a\n"
    "execute in tardis_rb:time_vortex run tp @a 0.5 126.4 40 0 0\n"
    "execute as @a at @s if dimension tardis_rb:time_vortex run say SHOT_READY\n"
    "schedule function shot:go 100t\n")
print("ready")
