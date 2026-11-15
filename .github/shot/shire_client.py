"""THROWAWAY: readies lotr/run for aerial screenshots of the saved preview world (client/DevScript)."""
import os, sys
import nbtlib

run = sys.argv[1]
save = os.path.join(run, "saves", "shire")
with open(os.path.join(run, "options.txt"), "w") as f:
    f.write("onboardAccessibility:false\npauseOnLostFocus:false\nrenderDistance:10\ntutorialStep:none\n"
            "chatVisibility:2\nfov:0.25\n")
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
/tp @s 0 125 -260 0 18
wait 500
shot a_south
/tp @s -270 120 30 -90 16
wait 500
shot b_east
/tp @s 160 105 170 135 12
wait 500
shot c_low
/tp @s -120 100 -120 45 10
wait 500
shot d_low2
quit
"""
open(os.path.join(run, "aerial.txt"), "w").write(steps)
print("ready")
