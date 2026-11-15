"""THROWAWAY: readies lotr/run for aerial screenshots of the saved preview world (client/DevScript)."""
import os, sys
import nbtlib

run = sys.argv[1]
save = os.path.join(run, "saves", "shire")
with open(os.path.join(run, "options.txt"), "w") as f:
    f.write("onboardAccessibility:false\npauseOnLostFocus:false\nrenderDistance:10\ntutorialStep:none\n"
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
/tp @s 0 150 -300 0 22
wait 600
shot a_south_high
/tp @s -300 125 20 -90 14
wait 600
shot b_west
/tp @s 180 100 180 135 8
wait 600
shot c_low
/tp @s -150 95 -150 45 6
wait 600
shot d_low2
/tp @s 0 260 0 0 90
wait 600
shot e_top
quit
"""
open(os.path.join(run, "aerial.txt"), "w").write(steps)
print("ready")
