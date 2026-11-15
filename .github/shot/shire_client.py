"""THROWAWAY: readies lotr/run for aerial screenshots of the saved preview world (client/DevScript)."""
import os, sys
import nbtlib

run = sys.argv[1]
save = os.path.join(run, "saves", "misty")
with open(os.path.join(run, "options.txt"), "w") as f:
    f.write("onboardAccessibility:false\npauseOnLostFocus:false\nrenderDistance:16\ntutorialStep:none\n"
            "fov:0.25\n")
lvl = nbtlib.load(os.path.join(save, "level.dat"))
lvl["Data"]["confirmedExperimentalSettings"] = nbtlib.Byte(1)
lvl["Data"]["allowCommands"] = nbtlib.Byte(1)
lvl.save()
steps = """wait 100
/gamemode spectator
/time set 5000
/weather clear
/gamerule doDaylightCycle false
hud off
/tp @s 51000 630 6100 0 20
wait 700
shot a_north_high
/tp @s 50650 600 6500 -90 15
wait 700
shot b_west
/tp @s 51350 600 6850 135 15
wait 700
shot c_se
/tp @s 51000 630 6900 180 25
wait 700
shot d_south
/tp @s 51000 638 6500 0 90
wait 700
shot e_top
quit
"""
open(os.path.join(run, "aerial.txt"), "w").write(steps)
print("ready")
