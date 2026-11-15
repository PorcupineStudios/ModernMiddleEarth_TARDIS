"""THROWAWAY: readies lotr/run for aerial screenshots of the saved preview world (client/DevScript)."""
import os, sys
import nbtlib

run = sys.argv[1]
save = os.path.join(run, "saves", "misty")
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
/tp @s 45632 300 13950 0 30
wait 700
shot a_north_high
/tp @s 45250 250 14400 -90 22
wait 700
shot b_west
/tp @s 45900 230 14700 135 18
wait 700
shot c_inside
/tp @s 45632 200 14000 160 8
wait 700
shot d_low
/tp @s 45632 330 14400 0 90
wait 700
shot e_top
quit
"""
open(os.path.join(run, "aerial.txt"), "w").write(steps)
print("ready")
