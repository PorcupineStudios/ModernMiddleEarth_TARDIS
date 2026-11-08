"""
The 16 small console controls (Aaron, 2026-11-08: "design a few different levers and controls based off of TARDIS
controls you find online ... each needs a (off/on) state or full animation", about 16, small). Each is built from
boxes in pixels (1/16 block) round its own base centre: x right, y up, the operator stands at +z.

Writes, for every control:
  assets/tardis_rb/console/controls/<id>.json   the game model: parts of quads in blocks (as console/capaldi.json),
                                                each part's pivot, and what clicking it does (toggle, steps, press,
                                                spin) with each moving part's off/on transform and its lit lamps
  tools/flight_controls/<id>.bbmodel            the same model for Blockbench, with its animations
and once:
  assets/tardis_rb/textures/entity/controls/controls.png (+ _glow)  the palette both use
  tools/flight_controls/preview.png             every control drawn off and on, for review

Run from the repo root: python3 tardis/tools/flight_controls.py
"""
import base64
import io
import json
import math
import os
import uuid

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "tardis_rb")
MODELS = os.path.join(ASSETS, "console", "controls")
TEXTURES = os.path.join(ASSETS, "textures", "entity", "controls")
OUT = os.path.join(HERE, "flight_controls")

# ---------------------------------------------------------------- palette: 4 x 4 swatches on a 32 x 32 texture

SIZE = 32
SW = 4
COLOURS = {
    "brass": (181, 140, 60), "brass_dark": (118, 86, 36), "steel": (152, 157, 163), "steel_dark": (78, 83, 90),
    "black": (32, 32, 35), "rubber": (54, 48, 42), "red": (184, 42, 36), "cream": (226, 214, 184),
    "wood": (112, 72, 40), "copper": (172, 96, 60), "white": (232, 232, 230), "gold": (222, 182, 72),
    "glass": (176, 214, 232),
    # glowing: drawn again at full brightness when lit
    "glow_green": (96, 255, 140), "glow_amber": (255, 184, 64), "glow_blue": (96, 204, 255),
    "glow_red": (255, 74, 62),
}
GLOWS = {k for k in COLOURS if k.startswith("glow_")}
SLOT = {name: i for i, name in enumerate(COLOURS)}


def swatch_uv(colour):
    """The swatch's rectangle in texture pixels (u1, v1, u2, v2), inset half a pixel against bleeding."""
    i = SLOT[colour]
    u, v = (i % (SIZE // SW)) * SW, (i // (SIZE // SW)) * SW
    return [u + 0.5, v + 0.5, u + SW - 0.5, v + SW - 0.5]


def palette(glow):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for name, rgb in COLOURS.items():
        if glow and name not in GLOWS:
            continue
        i = SLOT[name]
        u, v = (i % (SIZE // SW)) * SW, (i // (SIZE // SW)) * SW
        for dx in range(SW):
            for dy in range(SW):
                # a little shading so faces don't read as flat plastic
                k = 1.08 - 0.05 * (dx + dy) / 2 if name not in GLOWS else 1.0
                img.putpixel((u + dx, v + dy), tuple(min(255, int(c * k)) for c in rgb) + (255,))
    return img


# ---------------------------------------------------------------- building blocks


def box(a, b, colour, rot=None):
    """A box from corner a to corner b in pixels; rot = (axis, degrees, origin) turns it in place (static)."""
    return {"from": list(a), "to": list(b), "colour": colour, "rot": rot}


class Control:
    def __init__(self, cid, name, real, does, group="flight"):
        self.group = group
        self.id = cid
        self.name = name
        self.real = real   # what it is on the show's consoles
        self.does = does   # what it does in the mod
        self.parts = {}    # part -> {"pivot": [x,y,z], "boxes": [...]}
        self.actions = []  # what a click on a part does

    def part(self, name, pivot, *boxes):
        self.parts[name] = {"pivot": list(pivot), "boxes": list(boxes)}
        return self

    def action(self, click, kind, ticks, moves, lit=None, count=None):
        """click: the part clicked; kind: toggle | steps | press | spin; moves: part -> transform; lit: lamp -> when."""
        a = {"click": click, "type": kind, "ticks": ticks, "moves": moves}
        if count:
            a["count"] = count
        if lit:
            a["lit"] = lit
        self.actions.append(a)
        return self


def lamp(x, z, colour, y=1.0, s=0.8):
    return box((x - s / 2, y, z - s / 2), (x + s / 2, y + 0.6, z + s / 2), colour)


# ---------------------------------------------------------------- the 16 controls

CONTROLS = []


def control(cid, name, real, does, group="flight"):
    c = Control(cid, name, real, does, group)
    CONTROLS.append(c)
    return c


# 1. Dematerialisation lever: the big take-off lever every console has
(control("demat_lever", "Dematerialisation Lever",
         "The main take-off lever (dematerialisation circuit); thrown to send the TARDIS into the vortex.",
         "Starts a jump: dematerialise here, land at the destination (with the handbrake off).")
 .part("base", (0, 0, 0),
       box((-2.5, 0, -3), (2.5, 1, 3), "brass_dark"), box((-1, 1, -2.5), (1, 2, 2.5), "black"),
       box((-2.5, 1, -3), (-2, 1.6, 3), "brass"), box((2, 1, -3), (2.5, 1.6, 3), "brass"))
 .part("arm", (0, 1.5, 0),
       box((-0.4, 1.5, -0.4), (0.4, 7, 0.4), "steel"), box((-1.6, 6.5, -0.7), (1.6, 7.6, 0.7), "red"),
       box((-1.8, 6.6, -0.5), (-1.6, 7.5, 0.5), "black"), box((1.6, 6.6, -0.5), (1.8, 7.5, 0.5), "black"))
 .part("lamp", (0, 0, 0), lamp(1.9, 2.4, "glow_green", y=1.6, s=0.6))
 .action("arm", "toggle", 8, {"arm": {"axis": "x", "off": 35, "on": -35}}, lit={"lamp": "on"}))

# 2. Handbrake: a car-style pull lever with a release button
(control("handbrake", "Handbrake",
         "The handbrake (parking brake), famously left on by the Doctor so the TARDIS groans on landing.",
         "On: no jump or flight can start. Off: the dematerialisation lever and thrusters work.")
 .part("base", (0, 0, 0),
       box((-2, 0, -3), (2, 1, 3), "steel_dark"), box((-0.6, 1, -3), (0.6, 2.4, -1), "black"),
       box((-1.4, 1, -3), (-0.8, 2.6, -1.2), "brass"), box((0.8, 1, -3), (1.4, 2.6, -1.2), "brass"))
 .part("arm", (0, 1.9, -2),
       box((-0.5, 1.5, -2.6), (0.5, 2.3, 2.5), "steel"), box((-0.8, 1.3, 2.2), (0.8, 2.6, 4.2), "rubber"),
       box((-0.3, 1.6, 4.2), (0.3, 2.3, 4.6), "red"))
 .part("lamp", (0, 0, 0), lamp(1.4, 2.2, "glow_red", s=0.6))
 .action("arm", "toggle", 6, {"arm": {"axis": "x", "off": 0, "on": -28}}, lit={"lamp": "on"}))

# 3. Exterior phase key: a key in a lock, turned to unlock the box from its spot
(control("phase_key", "Exterior Phase Key",
         "The TARDIS key in its console lock (the key that starts or locks the ship in several eras).",
         "Turned on, it frees the exterior: pull the Phase Lever to make the box an entity, or back to a block.")
 .part("base", (0, 0, 0),
       box((-2, 0, -2), (2, 1, 2), "brass_dark"), box((-1.25, 1, -1.25), (1.25, 2.4, 1.25), "brass"),
       box((-0.3, 2.4, -0.6), (0.3, 2.5, 0.6), "black"))
 .part("key", (0, 2.4, 0),
       box((-0.25, 2.4, -0.15), (0.25, 4.2, 0.15), "steel"),
       box((-1.2, 4.2, -0.2), (1.2, 6.2, 0.2), "gold"), box((-0.5, 4.8, -0.25), (0.5, 5.6, 0.25), "black"))
 .part("lamp", (0, 0, 0), lamp(1.5, 1.5, "glow_amber", s=0.5))
 .action("key", "toggle", 5, {"key": {"axis": "y", "off": 0, "on": 90}}, lit={"lamp": "on"}))

# 4. Phase lever: the short guarded lever that goes with the key
(control("phase_lever", "Phase Lever",
         "A guarded secondary lever, after the materialisation and stabiliser levers on the classic consoles.",
         "With the key turned: block -> entity (stays a materialised box) or entity -> block where it rests.")
 .part("base", (0, 0, 0),
       box((-2, 0, -2.5), (2, 1, 2.5), "steel_dark"),
       box((-2, 1, -2.5), (-1.4, 3.2, 2.5), "brass"), box((1.4, 1, -2.5), (2, 3.2, 2.5), "brass"),
       box((-0.6, 1, -2), (0.6, 1.4, 2), "black"))
 .part("arm", (0, 1.2, 0),
       box((-0.35, 1.2, -0.35), (0.35, 4.6, 0.35), "steel"), box((-0.9, 4.6, -0.9), (0.9, 5.8, 0.9), "cream"))
 .part("lamp", (0, 0, 0), lamp(0, 2.2, "glow_blue", y=1.4, s=0.6))
 .action("arm", "toggle", 6, {"arm": {"axis": "x", "off": 30, "on": -30}}, lit={"lamp": "on"}))

# 5. Thrusters: a rocker under a flip-up guard, like an aircraft master switch
(control("thrusters", "Thrusters",
         "The anti-grav / stabiliser engines; a guarded rocker like a cockpit master switch.",
         "On: anti-grav holds the box still in the air (hovering). Off: it is a physics object and falls.")
 .part("base", (0, 0, 0),
       box((-2.5, 0, -3), (2.5, 1, 3), "steel_dark"), box((-1.5, 1, -2), (1.5, 2, 2), "black"),
       box((-2.2, 1, -2.8), (2.2, 1.4, -2.3), "brass"))
 .part("rocker", (0, 2, 0),
       box((-1, 1.9, -1.8), (1, 2.6, 1.8), "red"), box((-1, 2.6, 0.6), (1, 2.9, 1.8), "red"))
 .part("guard", (0, 1.2, -2.5),
       box((-2, 1, -2.5), (-1.7, 3.4, 2.6), "brass"), box((1.7, 1, -2.5), (2, 3.4, 2.6), "brass"),
       box((-2, 3.1, -2.5), (2, 3.4, 2.6), "glass"), box((-2, 1, 2.3), (2, 3.4, 2.6), "brass"))
 .part("lamp", (0, 0, 0), box((-1.5, 1, 2.3), (1.5, 1.5, 2.9), "glow_amber"))
 .action("rocker", "toggle", 10, {"guard": {"axis": "x", "off": 0, "on": -110},
                                   "rocker": {"axis": "x", "off": 12, "on": -12}}, lit={"lamp": "on"}))

# 6. Flight yoke: the joystick the pilot takes to fly in third person
(control("flight_yoke", "Flight Yoke",
         "The flight / navigation stick (the 'space-time throttle' joystick of later consoles).",
         "Takes the pressing crew member into third-person piloting; again (or Shift) stops piloting.")
 .part("base", (0, 0, 0),
       box((-2.5, 0, -2.5), (2.5, 1, 2.5), "steel_dark"), box((-1.5, 1, -1.5), (1.5, 2, 1.5), "rubber"))
 .part("stick", (0, 1.6, 0),
       box((-0.45, 1.6, -0.45), (0.45, 5, 0.45), "steel"), box((-1, 5, -1), (1, 7, 1), "black"),
       box((-0.3, 5.4, 1), (0.3, 6.2, 1.4), "red"), box((-1.05, 6.6, -0.4), (-0.6, 6.95, 0.4), "glow_green"))
 .part("ring", (0, 0, 0),
       box((-2, 1, -2), (2, 1.3, -1.6), "glow_green"), box((-2, 1, 1.6), (2, 1.3, 2), "glow_green"),
       box((-2, 1, -1.6), (-1.6, 1.3, 1.6), "glow_green"), box((1.6, 1, -1.6), (2, 1.3, 1.6), "glow_green"))
 .action("stick", "toggle", 6, {"stick": {"axis": "x", "off": 0, "on": -18}}, lit={"ring": "on"}))

# 7. Helmic regulator: a notched rotary knob over a dial face
(control("helmic_regulator", "Helmic Regulator",
         "Sets the TARDIS's 'helmic' balance in flight; a big rotary regulator on many consoles.",
         "Four settings, one per click: flight speed (slow, cruise, fast, boost).")
 .part("base", (0, 0, 0),
       box((-2.5, 0, -2.5), (2.5, 1, 2.5), "brass_dark"), box((-2, 1, -2), (2, 1.3, 2), "cream"),
       box((-0.2, 1.3, -2), (0.2, 1.45, -1.4), "black"), box((1.4, 1.3, -0.2), (2, 1.45, 0.2), "black"),
       box((-0.2, 1.3, 1.4), (0.2, 1.45, 2), "black"), box((-2, 1.3, -0.2), (-1.4, 1.45, 0.2), "black"))
 .part("knob", (0, 1.3, 0),
       box((-1, 1.3, -1), (1, 2.8, 1), "black"),
       box((-1, 1.3, -1), (1, 2.8, 1), "black", rot=("y", 45, (0, 0, 0))),
       box((-0.25, 2.8, -1.1), (0.25, 3.0, 0), "brass"), box((-0.2, 1.4, -1.9), (0.2, 1.8, -1), "red"))
 .action("knob", "steps", 4, {"knob": {"axis": "y", "step": -90}}, count=4))

# 8. Fast return switch: a big guarded plunger
(control("fast_return", "Fast Return Switch",
         "The fast return switch: sends the TARDIS back to where it last came from.",
         "Pressed: the next jump goes back to the last landing spot.")
 .part("base", (0, 0, 0),
       box((-2, 0, -2), (2, 1, 2), "black"), box((-1.75, 1, -1.75), (1.75, 1.8, 1.75), "brass"),
       box((-1.5, 1, 2), (1.5, 1.6, 2.3), "white"))
 .part("button", (0, 1.8, 0), box((-1.2, 1.8, -1.2), (1.2, 3, 1.2), "red"),
       box((-0.8, 3, -0.8), (0.8, 3.2, 0.8), "red"))
 .part("glow", (0, 0, 0), box((-1.25, 2.9, -1.25), (1.25, 3.0, 1.25), "glow_red"))
 .action("button", "press", 8, {"button": {"move": [0, -0.9, 0]}, "glow": {"move": [0, -0.9, 0]}},
         lit={"glow": "active"}))

# 9. Vector tracker: a trackball in a brass socket
(control("vector_tracker", "Vector Tracker",
         "Tracks the flight vector through the vortex; drawn here as a glowing trackball.",
         "Spins when used: fine-tunes the landing spot by one block in the facing direction.")
 .part("base", (0, 0, 0),
       box((-2.5, 0, -2.5), (2.5, 1.5, 2.5), "brass_dark"), box((-2, 1.5, -2), (2, 2, 2), "brass"))
 .part("ball", (0, 2.6, 0),
       box((-1.5, 1.6, -1), (1.5, 3.6, 1), "glass"), box((-1, 1.6, -1.5), (1, 3.6, 1.5), "glass"),
       box((-1, 1.1, -1), (1, 4.1, 1), "glass"), box((-0.6, 2.2, -0.6), (0.6, 3.0, 0.6), "glow_blue"),
       box((-1.55, 2.4, -0.2), (1.55, 2.8, 0.2), "steel"))
 .action("ball", "spin", 16, {"ball": {"axis": [1, 0, 1], "turn": 360}}, lit={}))

# 10. Spatial coordinate wheels: three thumbwheels, X, Y, Z
(control("coordinate_wheels", "Coordinate Wheels",
         "The spatial co-ordinate controls; three thumbwheels as on the old navigation panels.",
         "Each wheel moves the course by the step size along X, Y or Z (8 detents per turn).")
 .part("base", (0, 0, 0),
       box((-3, 0, -2), (3, 2, 2), "steel_dark"),
       box((-2.7, 2, -1.6), (-1.3, 2.1, 1.6), "black"), box((-0.7, 2, -1.6), (0.7, 2.1, 1.6), "black"),
       box((1.3, 2, -1.6), (2.7, 2.1, 1.6), "black"),
       box((-2.3, 1.2, 2), (-1.7, 1.8, 2.2), "glow_red"), box((-0.3, 1.2, 2), (0.3, 1.8, 2.2), "glow_green"),
       box((1.7, 1.2, 2), (2.3, 1.8, 2.2), "glow_blue"))
 .part("wheel_x", (-2, 2.3, 0),
       box((-2.5, 1.1, -1.2), (-1.5, 3.5, 1.2), "brass"),
       box((-2.5, 1.1, -1.2), (-1.5, 3.5, 1.2), "brass", rot=("x", 45, (-2, 2.3, 0))))
 .part("wheel_y", (0, 2.3, 0),
       box((-0.5, 1.1, -1.2), (0.5, 3.5, 1.2), "brass"),
       box((-0.5, 1.1, -1.2), (0.5, 3.5, 1.2), "brass", rot=("x", 45, (0, 2.3, 0))))
 .part("wheel_z", (2, 2.3, 0),
       box((1.5, 1.1, -1.2), (2.5, 3.5, 1.2), "brass"),
       box((1.5, 1.1, -1.2), (2.5, 3.5, 1.2), "brass", rot=("x", 45, (2, 2.3, 0))))
 .action("wheel_x", "steps", 3, {"wheel_x": {"axis": "x", "step": 45}}, count=8)
 .action("wheel_y", "steps", 3, {"wheel_y": {"axis": "x", "step": 45}}, count=8)
 .action("wheel_z", "steps", 3, {"wheel_z": {"axis": "x", "step": 45}}, count=8))

# 11. Directional pointer: a compass rose with a turning needle
(control("directional_pointer", "Directional Pointer",
         "Sets which way the TARDIS faces when it lands (the directional unit).",
         "Each click turns the needle 45 degrees; the box lands facing that way (8 directions).")
 .part("base", (0, 0, 0),
       box((-2.5, 0, -2.5), (2.5, 1, 2.5), "brass_dark"),
       box((-2.2, 1, -0.3), (2.2, 1.2, 0.3), "cream"), box((-0.3, 1, -2.2), (0.3, 1.2, 2.2), "cream"),
       box((-0.25, 1, -2.4), (0.25, 1.3, -1.9), "glow_amber"))
 .part("needle", (0, 1.5, 0),
       box((-0.6, 1.2, -0.6), (0.6, 1.9, 0.6), "brass"),
       box((-0.3, 1.4, -2.1), (0.3, 1.7, -0.6), "red"), box((-0.3, 1.4, 0.6), (0.3, 1.7, 1.6), "white"))
 .action("needle", "steps", 4, {"needle": {"axis": "y", "step": -45}}, count=8))

# 12. Stabilisers: a pair of toggle switches
(control("stabilisers", "Stabilisers",
         "The blue stabilisers the Doctor leaves off for a bumpier, 'more fun' flight.",
         "Left: steadies flight (less shake, slower turns). Right: steadies landings (no drift on touchdown).")
 .part("base", (0, 0, 0),
       box((-3, 0, -2), (3, 1, 2), "steel_dark"),
       box((-2.2, 1, -1), (-0.8, 1.6, 1), "black"), box((0.8, 1, -1), (2.2, 1.6, 1), "black"))
 .part("left", (-1.5, 1.6, 0),
       box((-1.8, 1.6, -0.3), (-1.2, 4, 0.3), "steel"), box((-2, 3.8, -0.5), (-1, 4.5, 0.5), "glow_blue"))
 .part("right", (1.5, 1.6, 0),
       box((1.2, 1.6, -0.3), (1.8, 4, 0.3), "steel"), box((1, 3.8, -0.5), (2, 4.5, 0.5), "glow_blue"))
 .part("lamp_left", (0, 0, 0), lamp(-1.5, 1.6, "glow_green", s=0.5))
 .part("lamp_right", (0, 0, 0), lamp(1.5, 1.6, "glow_green", s=0.5))
 .action("left", "toggle", 4, {"left": {"axis": "x", "off": 25, "on": -25}}, lit={"lamp_left": "on"})
 .action("right", "toggle", 4, {"right": {"axis": "x", "off": 25, "on": -25}}, lit={"lamp_right": "on"}))

# 13. Throttle: a lever sliding along a notched rail
(control("throttle", "Space-Time Throttle",
         "The space-time throttle: how hard the TARDIS pushes through the vortex.",
         "Four notches: how much charge a jump spends to land closer to the aimed spot.")
 .part("base", (0, 0, 0),
       box((-1.5, 0, -3.5), (1.5, 1, 3.5), "steel_dark"), box((-0.5, 1, -3), (0.5, 1.2, 3), "black"),
       box((1, 1, -2.9), (1.5, 1.3, -2.5), "white"), box((1, 1, -1.3), (1.5, 1.3, -0.9), "white"),
       box((1, 1, 0.3), (1.5, 1.3, 0.7), "white"), box((1, 1, 1.9), (1.5, 1.3, 2.3), "white"))
 .part("handle", (0, 1, 2.1),
       box((-1, 1, 1.6), (1, 2, 2.6), "brass"), box((-0.4, 2, 1.8), (0.4, 4.5, 2.4), "steel"),
       box((-1.2, 4.2, 1.6), (1.2, 5.2, 2.6), "black"))
 .action("handle", "steps", 6, {"handle": {"move_step": [0, 0, -1.6]}}, count=4))

# 14. Telepathic circuits: a palm plate that lights when touched
(control("telepathic_circuits", "Telepathic Circuits",
         "The telepathic interface: put a hand on it and think of where to go.",
         "Hand on it (click): picks the destination you last looked at on the map; lights while set.")
 .part("base", (0, 0, 0), box((-3, 0, -3), (3, 1.5, 3), "brass_dark"))
 .part("plate", (0, 1.5, 0),
       box((-2.2, 1.4, -2.2), (2.2, 2.2, 2.2), "cream"),
       box((-1.4, 2.2, -1.8), (-1, 2.3, 0.4), "brass"), box((-0.6, 2.2, -2), (-0.2, 2.3, 0.4), "brass"),
       box((0.2, 2.2, -2), (0.6, 2.3, 0.4), "brass"), box((1, 2.2, -1.8), (1.4, 2.3, 0.4), "brass"))
 .part("ring", (0, 0, 0),
       box((-2.7, 1.5, -2.7), (2.7, 1.7, -2.3), "glow_blue"), box((-2.7, 1.5, 2.3), (2.7, 1.7, 2.7), "glow_blue"),
       box((-2.7, 1.5, -2.3), (-2.3, 1.7, 2.3), "glow_blue"), box((2.3, 1.5, -2.3), (2.7, 1.7, 2.3), "glow_blue"))
 .action("plate", "toggle", 5, {"plate": {"move_on": [0, -0.5, 0]}}, lit={"ring": "on"}))

# 15. Randomiser: a die on a spindle that tumbles
(control("randomiser", "Randomiser",
         "The randomiser, fitted to hide the TARDIS from the Black Guardian: it picks the next landing by chance.",
         "Spins the die: the next jump goes to a random open destination.")
 .part("base", (0, 0, 0),
       box((-2, 0, -2), (2, 1, 2), "black"), box((-0.4, 1, -0.4), (0.4, 2, 0.4), "steel"))
 .part("die", (0, 3.25, 0),
       box((-1.25, 2, -1.25), (1.25, 4.5, 1.25), "white"),
       box((-0.3, 4.5, -0.3), (0.3, 4.55, 0.3), "glow_red"),
       box((-0.9, 2.6, 1.25), (-0.4, 3.1, 1.3), "red"), box((0.4, 3.4, 1.25), (0.9, 3.9, 1.3), "red"),
       box((1.25, 2.4, -0.9), (1.3, 2.9, -0.4), "red"), box((1.25, 3.0, -0.25), (1.3, 3.5, 0.25), "red"),
       box((1.25, 3.6, 0.4), (1.3, 4.1, 0.9), "red"))
 .action("die", "spin", 14, {"die": {"axis": [1, 1, 0], "turn": 360}}))

# 16. Door control: a crank wheel
(control("door_control", "Door Control",
         "The door control (a crank or lever opening the doors from the console).",
         "Half a turn opens the doors, half a turn closes them (also locks them while flying).")
 .part("base", (0, 0, 0),
       box((-2, 0, -2), (2, 1, 2), "steel_dark"), box((-0.5, 1, -0.5), (0.5, 2.2, 0.5), "steel"))
 .part("wheel", (0, 2.4, 0),
       box((-2.2, 2.2, -2.2), (2.2, 2.7, -1.7), "brass"), box((-2.2, 2.2, 1.7), (2.2, 2.7, 2.2), "brass"),
       box((-2.2, 2.2, -1.7), (-1.7, 2.7, 1.7), "brass"), box((1.7, 2.2, -1.7), (2.2, 2.7, 1.7), "brass"),
       box((-1.7, 2.3, -0.25), (1.7, 2.6, 0.25), "steel"), box((-0.25, 2.3, -1.7), (0.25, 2.6, 1.7), "steel"),
       box((-0.6, 2.2, -0.6), (0.6, 2.9, 0.6), "brass_dark"),
       box((1.55, 2.7, 1.55), (2.1, 4, 2.1), "red"))
 .part("lamp", (0, 0, 0), lamp(-1.5, 1.5, "glow_green", s=0.5))
 .action("wheel", "toggle", 12, {"wheel": {"axis": "y", "off": 0, "on": 180}}, lit={"lamp": "on"}))


# ---------------------------------------------------------------- 16 more after the 1.7.10 TARDIS Mod's console
# (Aaron, 2026-11-08: "use the tardis mod 1.7.10 as reference"). The old console's control kinds and how they moved:
# the staple lever (swings 140 degrees), the valve wheel (a full turn), the take-off "special" lever, push switches
# (pushed in), flight buttons (rise and glow when they must be pressed in flight), gauges with sweeping needles,
# framed screens, the schematic chooser, the sonic screwdriver holder, lamps and the save/load button bank. Shapes and
# motions are redrawn in our own boxes; no code or textures are copied.


def classic(cid, name, real, does):
    return control(cid, name, real, does, group="classic")


# 17. Coordinate lever: the old console's staple lever (two posts and a handle bar)
(classic("coordinate_lever", "Coordinate Lever",
         "1.7.10: the X/Y/Z levers (ids 10-13, 20-23, 30-33): staple levers swinging 140 degrees; the later levers "
         "make bigger changes.",
         "Moves the course along one axis by its own amount (small to huge, set per lever in data).")
 .part("base", (0, 0, 0),
       box((-2, 0, -1.5), (2, 1, 1.5), "steel_dark"), box((-1.6, 1, -1), (-1, 2, 1), "brass_dark"),
       box((1, 1, -1), (1.6, 2, 1), "brass_dark"))
 .part("lever", (0, 1.5, 0),
       box((-1.6, 1.2, -0.3), (-1, 6, 0.3), "steel"), box((1, 1.2, -0.3), (1.6, 6, 0.3), "steel"),
       box((-1.6, 5.6, -0.3), (1.6, 6.2, 0.3), "steel"), box((-0.9, 5.4, -0.6), (0.9, 6.6, 0.6), "red"))
 .action("lever", "toggle", 8, {"lever": {"axis": "x", "off": 70, "on": -70}}))

# 18. Valve wheel: the old console's square valve wheel with spokes
(classic("valve_wheel", "Valve Wheel",
         "1.7.10: the X/Z coordinate wheels and the facing wheel (ids 3, 14, 15, 24, 25): a square valve wheel "
         "that turns a full circle.",
         "Each click turns it an eighth; eight make one full turn of the setting it drives.")
 .part("base", (0, 0, 0), box((-1, 0, -1), (1, 1, 1), "steel_dark"), box((-0.4, 1, -0.4), (0.4, 2.2, 0.4), "steel"))
 .part("wheel", (0, 2.4, 0),
       box((-3, 2.2, -3), (3, 2.7, -2.5), "copper"), box((-3, 2.2, 2.5), (3, 2.7, 3), "copper"),
       box((-3, 2.2, -2.5), (-2.5, 2.7, 2.5), "copper"), box((2.5, 2.2, -2.5), (3, 2.7, 2.5), "copper"),
       box((-2.5, 2.3, -0.25), (2.5, 2.6, 0.25), "steel"), box((-0.25, 2.3, -2.5), (0.25, 2.6, 2.5), "steel"),
       box((-0.7, 2.1, -0.7), (0.7, 2.9, 0.7), "brass"))
 .action("wheel", "steps", 8, {"wheel": {"axis": "y", "step": 45}}, count=8))

# 19. Take-off lever: the old "special lever" on its own base
(classic("takeoff_lever", "Flight Take-off Lever",
         "1.7.10: the flight take-off lever (id 42), the 'special lever' that swings sideways 140 degrees.",
         "The classic way to start a coordinated flight (kept beside the dematerialisation lever).")
 .part("base", (0, 0, 0),
       box((-1.5, 0, -2), (1.5, 1, 2), "steel_dark"), box((-1, 1, -1.5), (1, 2, 1.5), "brass_dark"))
 .part("lever", (0, 1.6, 0),
       box((-0.3, 1.4, -0.3), (0.3, 6, 0.3), "steel"), box((-0.3, 5.6, -0.3), (0.3, 6.2, 2), "steel"),
       box((-0.7, 5.2, 1.4), (0.7, 6.6, 3.2), "black"))
 .action("lever", "toggle", 10, {"lever": {"axis": "z", "off": 70, "on": -70}}))

# 20. Push switch: a rod with a cap that is pushed in
(classic("push_switch", "Push Switch",
         "1.7.10: push switches (stabilise 56, landing pad 55, save/load 900, ...): a rod pushed in, it stays in.",
         "On/off for whatever it is set to in data (stabilisers, landing pad, save/load mode).")
 .part("base", (0, 0, 0), box((-1.25, 0, -1.25), (1.25, 1, 1.25), "steel_dark"),
       box((-0.8, 1, -0.8), (0.8, 1.4, 0.8), "black"))
 .part("rod", (0, 1, 0),
       box((-0.35, 1.8, -0.35), (0.35, 4.2, 0.35), "steel"), box((-0.9, 4.2, -0.9), (0.9, 4.9, 0.9), "copper"))
 .part("lamp", (0, 0, 0), lamp(0.9, 0.9, "glow_green", s=0.5))
 .action("rod", "toggle", 4, {"rod": {"move_on": [0, -1.6, 0]}}, lit={"lamp": "on"}))

# 21. Flight button: rises and glows when it has to be pressed in flight
(classic("flight_button", "Flight Control Button",
         "1.7.10: flight controls (id 40 and the button bank): 'press these when they glow during flight'; the "
         "button rises while it waits.",
         "During a jump it lights and rises; press it in time or the flight drifts (stabilisers skip this).")
 .part("base", (0, 0, 0), box((-1.25, 0, -1.25), (1.25, 1, 1.25), "black"))
 .part("button", (0, 1, 0), box((-0.9, 0.6, -0.9), (0.9, 1.4, 0.9), "cream"))
 .part("glow", (0, 1, 0), box((-0.92, 1.38, -0.92), (0.92, 1.45, 0.92), "glow_amber"))
 .action("button", "toggle", 4, {"button": {"move_on": [0, 0.9, 0]}, "glow": {"move_on": [0, 0.9, 0]}},
         lit={"glow": "on"}))

# 22. Gauge: a display with a sweeping needle
(classic("gauge", "Gauge",
         "1.7.10: the energy, rooms, speed, XP and shields gauges (ids 0, 1, 2, 8, 9): a display with a needle "
         "sweeping 180 degrees.",
         "Shows one value from 0 to full (charge, rooms used, speed, shields), set in data; not clicked.")
 .part("base", (0, 0, 0),
       box((-2.5, 0, -0.75), (2.5, 4, 0.25), "brass_dark"), box((-2.1, 0.4, 0.25), (2.1, 3.6, 0.35), "cream"),
       box((-2.1, 0.4, 0.3), (-1.5, 0.9, 0.4), "glow_red"), box((1.5, 0.4, 0.3), (2.1, 0.9, 0.4), "glow_green"))
 .part("needle", (0, 0.8, 0.45),
       box((-0.15, 0.8, 0.38), (0.15, 3.3, 0.5), "black"), box((-0.35, 0.6, 0.35), (0.35, 1.0, 0.55), "brass"))
 .action(None, "gauge", 10, {"needle": {"axis": "z", "from": 65, "to": -65}}))

# 23. Twin gauge: two needles in one case (the old XP and shields pair)
(classic("twin_gauge", "Twin Gauge",
         "1.7.10: the XP and shields gauges sat as a pair below the other three.",
         "Two values side by side (e.g. shields and hull), each needle 0 to full.")
 .part("base", (0, 0, 0),
       box((-3.5, 0, -0.75), (3.5, 3, 0.25), "brass_dark"), box((-3.1, 0.4, 0.25), (-0.2, 2.6, 0.35), "cream"),
       box((0.2, 0.4, 0.25), (3.1, 2.6, 0.35), "cream"))
 .part("needle_left", (-1.65, 0.7, 0.45), box((-1.8, 0.7, 0.38), (-1.5, 2.4, 0.5), "red"))
 .part("needle_right", (1.65, 0.7, 0.45), box((1.5, 0.7, 0.38), (1.8, 2.4, 0.5), "black"))
 .action(None, "gauge", 10, {"needle_left": {"axis": "z", "from": 70, "to": -70},
                             "needle_right": {"axis": "z", "from": 70, "to": -70}}))

# 24. Scanner screen: a small framed screen that lights up
(classic("scanner_screen", "Scanner Screen",
         "1.7.10: the coordinate guesser (id 100) and scanner (id 54) screens: a framed screen set into the panel.",
         "Click to switch it on/off; shows the coordinate guess or the scanner view (text drawn by the renderer).")
 .part("base", (0, 0, 0),
       box((-3, 0, -3), (3, 0.8, 3), "black"),
       box((-3, 0.8, -3), (3, 1.4, -2.5), "copper"), box((-3, 0.8, 2.5), (3, 1.4, 3), "copper"),
       box((-3, 0.8, -2.5), (-2.5, 1.4, 2.5), "copper"), box((2.5, 0.8, -2.5), (3, 1.4, 2.5), "copper"))
 .part("screen", (0, 0, 0), box((-2.5, 0.8, -2.5), (2.5, 0.9, 2.5), "glow_green"))
 .action("screen", "toggle", 3, {}, lit={"screen": "on"}))

# 25. Schematic chooser: a long text display with four buttons
(classic("schema_chooser", "Schematic Chooser",
         "1.7.10: the schema chooser (ids 50, 51, 57, 58 and display 52): a long display with buttons to page "
         "categories and rooms.",
         "Pages through the rooms that can be added (category and room), as the old console did.")
 .part("base", (0, 0, 0),
       box((-3.5, 0, -2.5), (3.5, 0.8, 2.5), "steel_dark"),
       box((-3.3, 0.8, -2.2), (3.3, 1.2, -0.4), "copper"))
 .part("display", (0, 0, 0), box((-3, 1.2, -2), (3, 1.25, -0.6), "glow_blue"))
 .part("btn_1", (0, 0.8, 0), box((-3, 0.6, 0.6), (-1.8, 1.3, 1.8), "cream"))
 .part("btn_2", (0, 0.8, 0), box((-1.4, 0.6, 0.6), (-0.2, 1.3, 1.8), "cream"))
 .part("btn_3", (0, 0.8, 0), box((0.2, 0.6, 0.6), (1.4, 1.3, 1.8), "cream"))
 .part("btn_4", (0, 0.8, 0), box((1.8, 0.6, 0.6), (3, 1.3, 1.8), "cream"))
 .action("btn_1", "press", 4, {"btn_1": {"move": [0, -0.4, 0]}})
 .action("btn_2", "press", 4, {"btn_2": {"move": [0, -0.4, 0]}})
 .action("btn_3", "press", 4, {"btn_3": {"move": [0, -0.4, 0]}})
 .action("btn_4", "press", 4, {"btn_4": {"move": [0, -0.4, 0]}}))

# 26. Sonic screwdriver holder: a socket the screwdriver rises out of
(classic("screwdriver_holder", "Sonic Screwdriver Holder",
         "1.7.10: the screwdriver holder (id 6) and generator button (id 5): a socket holding the sonic.",
         "Click to take or put back the sonic screwdriver; it rises out of the socket when ready.")
 .part("base", (0, 0, 0),
       box((-1.5, 0, -1.5), (1.5, 2, -1), "steel_dark"), box((-1.5, 0, 1), (1.5, 2, 1.5), "steel_dark"),
       box((-1.5, 0, -1), (-1, 2, 1), "steel_dark"), box((1, 0, -1), (1.5, 2, 1), "steel_dark"),
       box((-1, 0, -1), (1, 0.4, 1), "black"))
 .part("sonic", (0, 0.4, 0),
       box((-0.45, 0.4, -0.45), (0.45, 4.2, 0.45), "steel"), box((-0.6, 2.2, -0.6), (0.6, 2.8, 0.6), "black"),
       box((-0.55, 4.2, -0.55), (0.55, 5, 0.55), "brass"), box((-0.35, 5, -0.35), (0.35, 5.3, 0.35), "glow_green"))
 .action("sonic", "toggle", 6, {"sonic": {"move_on": [0, 2, 0]}}))

# 27. Console lamp: a flat light that brightens with its state
(classic("console_lamp", "Console Lamp",
         "1.7.10: the console lights (LightModel / TardisLightModel): flat lamps that glow with a state.",
         "Lights for whatever it is tied to in data (in flight, low charge, door open...).")
 .part("base", (0, 0, 0), box((-2, 0, -2), (2, 0.6, 2), "brass_dark"))
 .part("light", (0, 0, 0), box((-1.6, 0.6, -1.6), (1.6, 1.0, 1.6), "glow_amber"))
 .action("light", "toggle", 2, {}, lit={"light": "on"}))

# 28. Memory bank: the 4 x 5 button grid of saved locations
_bank = (classic("memory_bank", "Memory Bank",
                 "1.7.10: the 20 save/load buttons on the left panel (ids 1000-1019) with the save/load switch.",
                 "Each button is a saved location: press to load it, or save to it in save mode.")
         .part("base", (0, 0, 0), box((-3.4, 0, -2.8), (3.4, 0.8, 2.8), "steel_dark")))
for _r in range(4):
    for _c in range(5):
        _x, _z = -2.6 + _c * 1.3, -2.0 + _r * 1.3
        _bank.part(f"slot_{_r * 5 + _c}", (_x, 0.8, _z), box((_x - 0.45, 0.6, _z - 0.45), (_x + 0.45, 1.3, _z + 0.45),
                                                            "cream" if _r % 2 == 0 else "copper"))
        _bank.action(f"slot_{_r * 5 + _c}", "press", 4, {f"slot_{_r * 5 + _c}": {"move": [0, -0.4, 0]}})

# 29. Drift lever: the coordinated / uncoordinated flight lever (in / out)
(classic("drift_lever", "Coordinated Flight Lever",
         "1.7.10: the uncoordinated flight lever: out = drifting mode, in = coordinated flight mode.",
         "Out: the TARDIS drifts (free roaming flight). In: coordinated flight to the set destination.")
 .part("base", (0, 0, 0),
       box((-1.5, 0, -3), (1.5, 1, 3), "steel_dark"), box((-0.5, 1, -2.6), (0.5, 1.6, 2.6), "black"),
       box((-1.5, 1, -0.5), (-1, 1.3, 0.5), "glow_amber"), box((1, 1, -2.6), (1.5, 1.3, -1.6), "glow_green"))
 .part("slide", (0, 1, 0),
       box((-0.4, 1.2, 0.4), (0.4, 2.2, 3.2), "steel"), box((-1, 1.4, 3), (1, 2.6, 4), "red"))
 .action("slide", "toggle", 6, {"slide": {"move_on": [0, 0, -2.4]}}))

# 30. Room deleter: a red button under a hazard frame
(classic("room_deleter", "Room Deleter",
         "1.7.10: the room deleter (id 901): right click then sneak right click deletes all rooms.",
         "Two-step press (arm, then sneak-press) removes every added room; the frame lights while armed.")
 .part("base", (0, 0, 0),
       box((-2, 0, -2), (2, 0.8, 2), "black"),
       box((-2, 0.8, -2), (2, 1.4, -1.5), "gold"), box((-2, 0.8, 1.5), (2, 1.4, 2), "black"),
       box((-2, 0.8, -1.5), (-1.5, 1.4, 1.5), "black"), box((1.5, 0.8, -1.5), (2, 1.4, 1.5), "gold"))
 .part("armed", (0, 0, 0), box((-1.5, 0.8, -1.5), (1.5, 0.95, 1.5), "glow_red"))
 .part("button", (0, 1, 0), box((-1, 0.9, -1), (1, 2.1, 1), "red"))
 .action("button", "press", 8, {"button": {"move": [0, -0.8, 0]}}, lit={"armed": "active"}))

# 31. Speed lever: a notched lever with five speeds
(classic("speed_lever", "Speed Lever",
         "1.7.10: the speed lever (id 4) with the speed gauge beside it.",
         "Five notches of flight speed; faster flights cost more charge.")
 .part("base", (0, 0, 0),
       box((-1.5, 0, -3), (1.5, 1, 3), "brass_dark"), box((-0.5, 1, -2.6), (0.5, 1.5, 2.6), "black"),
       box((1, 1, -2.4), (1.5, 1.2, -2), "white"), box((1, 1, -1.2), (1.5, 1.2, -0.8), "white"),
       box((1, 1, -0.2), (1.5, 1.2, 0.2), "white"), box((1, 1, 0.8), (1.5, 1.2, 1.2), "white"),
       box((1, 1, 2), (1.5, 1.2, 2.4), "white"))
 .part("lever", (0, 1.2, 0),
       box((-0.3, 1.2, -0.3), (0.3, 5, 0.3), "steel"), box((-0.8, 5, -0.8), (0.8, 6, 0.8), "black"))
 .action("lever", "steps", 5, {"lever": {"axis": "x", "step": -20}}, count=5))

# 32. Sub-lever bank: three tiny levers side by side
(classic("sub_levers", "Sub-Lever Bank",
         "1.7.10: the small levers round the panels (ids 1020-1029) for lesser functions.",
         "Three small on/off switches for lesser functions (lights, alarms, shields), set in data.")
 .part("base", (0, 0, 0), box((-2.5, 0, -1.2), (2.5, 0.8, 1.2), "steel_dark"))
 .part("lever_1", (-1.6, 0.8, 0), box((-1.85, 0.8, -0.2), (-1.35, 3, 0.2), "steel"),
       box((-2, 2.8, -0.35), (-1.2, 3.4, 0.35), "red"))
 .part("lever_2", (0, 0.8, 0), box((-0.25, 0.8, -0.2), (0.25, 3, 0.2), "steel"),
       box((-0.4, 2.8, -0.35), (0.4, 3.4, 0.35), "glow_amber"))
 .part("lever_3", (1.6, 0.8, 0), box((1.35, 0.8, -0.2), (1.85, 3, 0.2), "steel"),
       box((1.2, 2.8, -0.35), (2, 3.4, 0.35), "glow_blue"))
 .action("lever_1", "toggle", 4, {"lever_1": {"axis": "x", "off": 30, "on": -30}})
 .action("lever_2", "toggle", 4, {"lever_2": {"axis": "x", "off": 30, "on": -30}}, lit={"lever_2": "on"})
 .action("lever_3", "toggle", 4, {"lever_3": {"axis": "x", "off": 30, "on": -30}}, lit={"lever_3": "on"}))

# ---------------------------------------------------------------- geometry

FACES = {  # face -> (normal, corner indices on the box, for CCW quads seen from outside)
    "north": ((0, 0, -1), [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)]),
    "south": ((0, 0, 1), [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)]),
    "west": ((-1, 0, 0), [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)]),
    "east": ((1, 0, 0), [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)]),
    "up": ((0, 1, 0), [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)]),
    "down": ((0, -1, 0), [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)]),
}


def rotate(p, axis, deg, origin):
    """Turns point p by deg about an axis ('x'/'y'/'z' or a vector) through origin, right-handed."""
    if isinstance(axis, str):
        axis = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}[axis]
    n = math.sqrt(sum(a * a for a in axis))
    kx, ky, kz = (a / n for a in axis)
    x, y, z = (p[i] - origin[i] for i in range(3))
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    dot = kx * x + ky * y + kz * z
    cx, cy, cz = ky * z - kz * y, kz * x - kx * z, kx * y - ky * x
    r = [x * c + cx * s + kx * dot * (1 - c), y * c + cy * s + ky * dot * (1 - c), z * c + cz * s + kz * dot * (1 - c)]
    return [r[i] + origin[i] for i in range(3)]


def quads_of(b):
    """The six faces of a box as quads: four (x, y, z) corners in pixels, the normal, the colour."""
    out = []
    for face, (normal, corners) in FACES.items():
        pts = []
        for cx, cy, cz in corners:
            p = [(b["to"] if f else b["from"])[i] for i, f in enumerate((cx, cy, cz))]
            if b["rot"]:
                p = rotate(p, *b["rot"])
            pts.append(p)
        n = list(normal)
        if b["rot"]:
            n = rotate(n, b["rot"][0], b["rot"][1], (0, 0, 0))
        out.append((pts, n, b["colour"]))
    return out


def game_json(c):
    parts = {}
    for name, p in c.parts.items():
        quads = []
        for b in p["boxes"]:
            u1, v1, u2, v2 = swatch_uv(b["colour"])
            uv = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
            for pts, n, _ in quads_of(b):
                quads.append({"v": [[round(pt[0] / 16, 5), round(pt[1] / 16, 5), round(pt[2] / 16, 5),
                                     round(uv[i][0] / SIZE, 5), round(uv[i][1] / SIZE, 5)]
                                    for i, pt in enumerate(pts)],
                              "n": [round(x, 4) for x in n]})
        parts[name] = {"pivot": [round(x / 16, 5) for x in p["pivot"]], "quads": quads}
    actions = []
    for a in c.actions:
        a = json.loads(json.dumps(a))
        for m in a["moves"].values():
            for key in ("move", "move_on", "move_step"):
                if key in m:
                    m[key] = [round(x / 16, 5) for x in m[key]]
        actions.append(a)
    lo = [min(bb["from"][i] for p in c.parts.values() for bb in p["boxes"]) for i in range(3)]
    hi = [max(bb["to"][i] for p in c.parts.values() for bb in p["boxes"]) for i in range(3)]
    return {
        "_comment": "Made by tardis/tools/flight_controls.py; edit the script, not this file.",
        "name": c.name, "texture": "tardis_rb:textures/entity/controls/controls.png",
        "glow_texture": "tardis_rb:textures/entity/controls/controls_glow.png",
        "size": [round((hi[i] - lo[i]) / 16, 4) for i in range(3)],
        "parts": parts, "actions": actions,
    }


# ---------------------------------------------------------------- poses (shared by the preview and Blockbench)


def pose(c, state):
    """Each part's transforms for a state: 'off', 'on' (toggles on, steps at the next notch, press/spin mid-way)."""
    out = {}
    lit = set()
    for name, p in c.parts.items():
        if any(b["colour"] in GLOWS for b in p["boxes"]) and not any(name in a.get("lit", {}) for a in c.actions):
            lit.add(name)  # lamps no action switches are always lit
    for a in c.actions:
        for part, m in a["moves"].items():
            t = []
            if a["type"] == "toggle":
                if "axis" in m:
                    t.append(("rot", m["axis"], m["on" if state == "on" else "off"]))
                if "move_on" in m and state == "on":
                    t.append(("move", m["move_on"]))
            elif a["type"] == "steps":
                k = 1 if state == "on" else 0
                if "step" in m:
                    t.append(("rot", m["axis"], m["step"] * k))
                if "move_step" in m:
                    t.append(("move", [x * k for x in m["move_step"]]))
            elif a["type"] == "press" and state == "on":
                t.append(("move", m["move"]))
            elif a["type"] == "spin" and state == "on":
                t.append(("rot", m["axis"], m["turn"] / 8))
            elif a["type"] == "gauge":
                t.append(("rot", m["axis"], m["from"] + (0.7 if state == "on" else 0) * (m["to"] - m["from"])))
            out.setdefault(part, []).extend(t)
        for l, when in a.get("lit", {}).items():
            if state == "on":
                lit.add(l)
    return out, lit


def apply(pt, transforms, pivot):
    for t in transforms:
        if t[0] == "rot":
            pt = rotate(pt, t[1], t[2], pivot)
        else:
            pt = [pt[i] + t[1][i] for i in range(3)]
    return pt


# ---------------------------------------------------------------- Blockbench

def uid():
    return str(uuid.uuid4())


def bbmodel(c, tex_png):
    elements, outliner, groups = [], [], {}
    for name, p in c.parts.items():
        children = []
        for i, b in enumerate(p["boxes"]):
            u = uid()
            e = {"name": f"{name}_{i}", "uuid": u, "type": "cube", "from": b["from"], "to": b["to"],
                 "origin": list(b["rot"][2]) if b["rot"] else p["pivot"], "color": SLOT[b["colour"]] % 8,
                 "faces": {f: {"uv": swatch_uv(b["colour"]), "texture": 0} for f in FACES}}
            if b["rot"]:
                r = [0, 0, 0]
                r["xyz".index(b["rot"][0])] = b["rot"][1]
                e["rotation"] = r
            elements.append(e)
            children.append(u)
        g = uid()
        groups[name] = g
        outliner.append({"name": name, "uuid": g, "origin": p["pivot"], "rotation": [0, 0, 0], "children": children,
                         "export": True, "visibility": True})
    animations = []

    def anim(name, length, loop, keys):
        animators = {}
        for part, frames in keys.items():
            kfs = []
            for time, channel, value in frames:
                kfs.append({"channel": channel, "data_points": [{"x": value[0], "y": value[1], "z": value[2]}],
                            "uuid": uid(), "time": time, "interpolation": "linear"})
            animators[groups[part]] = {"name": part, "type": "bone", "keyframes": kfs}
        animations.append({"uuid": uid(), "name": name, "loop": loop, "length": length, "snapping": 20,
                           "animators": animators})

    def vec(axis, deg):
        if isinstance(axis, str):
            v = [0, 0, 0]
            v["xyz".index(axis)] = deg
            return v
        # a tumble about a slanted axis: split the turn over the axis's components
        n = math.sqrt(sum(a * a for a in axis))
        return [deg * a / n for a in axis]

    for a in c.actions:
        secs = a["ticks"] / 20
        tag = a["click"] if len(c.actions) > 1 and a["click"] else ""
        keys_on, keys_off, keys_use = {}, {}, {}
        for part, m in a["moves"].items():
            if a["type"] == "toggle":
                if "axis" in m:
                    keys_on[part] = [(0, "rotation", vec(m["axis"], m["off"])), (secs, "rotation", vec(m["axis"], m["on"]))]
                    keys_off[part] = [(0, "rotation", vec(m["axis"], m["on"])), (secs, "rotation", vec(m["axis"], m["off"]))]
                if "move_on" in m:
                    keys_on[part] = [(0, "position", [0, 0, 0]), (secs, "position", m["move_on"])]
                    keys_off[part] = [(0, "position", m["move_on"]), (secs, "position", [0, 0, 0])]
            elif a["type"] == "steps":
                if "step" in m:
                    keys_use[part] = [(0, "rotation", [0, 0, 0]), (secs, "rotation", vec(m["axis"], m["step"]))]
                else:
                    keys_use[part] = [(0, "position", [0, 0, 0]), (secs, "position", m["move_step"])]
            elif a["type"] == "press":
                keys_use[part] = [(0, "position", [0, 0, 0]), (secs / 2, "position", m["move"]),
                                  (secs, "position", [0, 0, 0])]
            elif a["type"] == "spin":
                keys_use[part] = [(0, "rotation", [0, 0, 0]), (secs, "rotation", vec(m["axis"], m["turn"]))]
            elif a["type"] == "gauge":
                keys_use[part] = [(0, "rotation", vec(m["axis"], m["from"])), (secs, "rotation", vec(m["axis"], m["to"]))]
        sep = "_" if tag else ""
        if keys_on:
            anim(f"{tag}{sep}on", secs, "hold", keys_on)
            anim(f"{tag}{sep}off", secs, "hold", keys_off)
        if keys_use:
            anim(f"{tag}{sep}{'value' if a['type'] == 'gauge' else a['type']}", secs, "once", keys_use)
    buf = io.BytesIO()
    tex_png.save(buf, "PNG")
    return {
        "meta": {"format_version": "4.5", "model_format": "free", "box_uv": False},
        "name": c.id, "model_identifier": c.id, "visible_box": [1, 1, 0],
        "resolution": {"width": SIZE, "height": SIZE},
        "elements": elements, "outliner": outliner,
        "textures": [{"path": "", "name": "controls.png", "folder": "", "namespace": "", "id": "0",
                      "particle": False, "render_mode": "default", "visible": True, "mode": "bitmap",
                      "saved": True, "uuid": uid(), "relative_path": "../../src/main/resources/assets/tardis_rb/"
                      "textures/entity/controls/controls.png",
                      "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}],
        "animations": animations,
    }


# ---------------------------------------------------------------- preview

def render(c, state, scale=22, w=300, h=260, ss=2, posed=None):
    """A small z-buffered drawing from the operator's side, slightly above (supersampled ss times)."""
    import numpy as np
    W, H, sc = w * ss, h * ss, scale * ss
    rgb_buf = np.zeros((H, W, 3), dtype=np.float32)
    rgb_buf[:] = (24, 26, 32)
    zbuf = np.full((H, W), -1e9, dtype=np.float32)
    transforms, lit = posed if posed else pose(c, state)
    yaw, pitch = math.radians(-35), math.radians(30)
    light = (0.4, 0.8, 0.45)
    ys, xs = np.mgrid[0:H, 0:W].astype(np.float32) + 0.5
    for name, p in c.parts.items():
        t = transforms.get(name, [])
        for b in p["boxes"]:
            for pts, n, colour in quads_of(b):
                pts = [apply(pt, t, p["pivot"]) for pt in pts]
                n = apply(n, [x for x in t if x[0] == "rot"], (0, 0, 0))
                proj = []
                for x, y, z in pts:
                    x1 = x * math.cos(yaw) + z * math.sin(yaw)
                    z1 = -x * math.sin(yaw) + z * math.cos(yaw)
                    proj.append((W / 2 + x1 * sc, H * 0.78 - (y * math.cos(pitch) - z1 * math.sin(pitch)) * sc,
                                 y * math.sin(pitch) + z1 * math.cos(pitch)))
                rgb = COLOURS[colour]
                if colour in GLOWS:
                    k = 1.0 if name in lit else 0.35
                else:
                    k = 0.45 + 0.55 * max(0, n[0] * light[0] + n[1] * light[1] + n[2] * light[2])
                col = np.array([ch * k for ch in rgb], dtype=np.float32)
                for tri in ((proj[0], proj[1], proj[2]), (proj[0], proj[2], proj[3])):
                    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = tri
                    area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
                    if abs(area) < 1e-6:
                        continue
                    bx0, bx1 = max(0, int(min(x0, x1, x2))), min(W, int(max(x0, x1, x2)) + 1)
                    by0, by1 = max(0, int(min(y0, y1, y2))), min(H, int(max(y0, y1, y2)) + 1)
                    if bx0 >= bx1 or by0 >= by1:
                        continue
                    px, py = xs[by0:by1, bx0:bx1], ys[by0:by1, bx0:bx1]
                    w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area
                    w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area
                    w2 = 1 - w0 - w1
                    inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
                    depth = w0 * z0 + w1 * z1 + w2 * z2
                    zb = zbuf[by0:by1, bx0:bx1]
                    m = inside & (depth > zb + 1e-4)
                    zb[m] = depth[m]
                    rgb_buf[by0:by1, bx0:bx1][m] = col
    img = Image.fromarray(np.clip(rgb_buf, 0, 255).astype(np.uint8), "RGB")
    return img.resize((w, h), Image.LANCZOS)


def preview(controls, first=1):
    cell_w, cell_h, cols = 600, 300, 2
    rows = math.ceil(len(controls) / cols)
    sheet = Image.new("RGB", (cell_w * cols, cell_h * rows), (16, 17, 22))
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("DejaVuSans.ttf", 15)
        small = ImageFont.truetype("DejaVuSans.ttf", 12)
    except OSError:
        font = small = ImageFont.load_default()
    for i, c in enumerate(controls):
        x, y = (i % cols) * cell_w, (i // cols) * cell_h
        sheet.paste(render(c, "off"), (x, y + 30))
        sheet.paste(render(c, "on"), (x + 300, y + 30))
        kinds = ", ".join(sorted({a["type"] for a in c.actions}))
        d.text((x + 10, y + 6), f"{i + first}. {c.name}  ({kinds})", fill=(240, 230, 200), font=font)
        d.text((x + 10, y + cell_h - 22), "off / rest", fill=(150, 150, 160), font=small)
        d.text((x + 310, y + cell_h - 22), "on / mid-animation", fill=(150, 150, 160), font=small)
    return sheet


def pose_at(c, t):
    """The pose t (0..1) through each control's animation: toggles swing off -> on, steps turn one notch, presses go
    in and out, spins turn once, gauges sweep; lamps light for the second half (presses while pressed)."""
    out, lit = {}, set()
    for name, p in c.parts.items():
        if any(b["colour"] in GLOWS for b in p["boxes"]) and not any(name in a.get("lit", {}) for a in c.actions):
            lit.add(name)
    e = 0.5 - 0.5 * math.cos(math.pi * t)  # ease in and out
    for a in c.actions:
        for part, m in a["moves"].items():
            tr = []
            if a["type"] == "toggle":
                if "axis" in m:
                    tr.append(("rot", m["axis"], m["off"] + e * (m["on"] - m["off"])))
                if "move_on" in m:
                    tr.append(("move", [x * e for x in m["move_on"]]))
            elif a["type"] == "steps":
                if "step" in m:
                    tr.append(("rot", m["axis"], m["step"] * e))
                if "move_step" in m:
                    tr.append(("move", [x * e for x in m["move_step"]]))
            elif a["type"] == "press":
                tr.append(("move", [x * math.sin(math.pi * t) for x in m["move"]]))
            elif a["type"] == "spin":
                tr.append(("rot", m["axis"], m["turn"] * e))
            elif a["type"] == "gauge":
                tr.append(("rot", m["axis"], m["from"] + e * (m["to"] - m["from"])))
            out.setdefault(part, []).extend(tr)
        for l, when in a.get("lit", {}).items():
            if (when == "on" and t >= 0.5) or (when == "active" and 0.15 < t < 0.85):
                lit.add(l)
    return out, lit


def animated(controls, first, path, frames=12):
    """An animated GIF: every control playing its animation forward, holding, then back."""
    cw, ch, cols = 240, 200, 4
    rows = math.ceil(len(controls) / cols)
    try:
        font = ImageFont.truetype("DejaVuSans.ttf", 12)
    except OSError:
        font = ImageFont.load_default()
    ts = [i / (frames - 1) for i in range(frames)]
    seq = [0.0] * 3 + ts + [1.0] * 4 + ts[::-1] + [0.0] * 2
    cache = {}
    images = []
    for t in seq:
        sheet = Image.new("RGB", (cw * cols, ch * rows), (16, 17, 22))
        d = ImageDraw.Draw(sheet)
        for i, c in enumerate(controls):
            key = (c.id, round(t, 4))
            if key not in cache:
                cache[key] = render(c, None, scale=17, w=cw, h=ch - 18, ss=1, posed=pose_at(c, t))
            x, y = (i % cols) * cw, (i // cols) * ch
            sheet.paste(cache[key], (x, y + 18))
            d.text((x + 6, y + 3), f"{i + first}. {c.name}", fill=(240, 230, 200), font=font)
        images.append(sheet.convert("P", palette=Image.ADAPTIVE, colors=128))
    images[0].save(path, save_all=True, append_images=images[1:], duration=70, loop=0, optimize=True)


# ---------------------------------------------------------------- main

def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    tex = palette(False)
    tex.save(os.path.join(TEXTURES, "controls.png"))
    palette(True).save(os.path.join(TEXTURES, "controls_glow.png"))
    index = []
    for c in CONTROLS:
        with open(os.path.join(MODELS, c.id + ".json"), "w") as f:
            json.dump(game_json(c), f, separators=(",", ":"))
        with open(os.path.join(OUT, c.id + ".bbmodel"), "w") as f:
            json.dump(bbmodel(c, tex), f, indent=1)
        index.append({"id": c.id, "group": c.group, "name": c.name, "real": c.real, "does": c.does,
                      "actions": [{"click": a["click"], "type": a["type"]} for a in c.actions]})
    with open(os.path.join(OUT, "controls.json"), "w") as f:
        json.dump(index, f, indent=2)
    preview([c for c in CONTROLS if c.group == "flight"]).save(os.path.join(OUT, "preview.png"))
    preview([c for c in CONTROLS if c.group == "classic"], first=17).save(os.path.join(OUT, "preview_classic.png"))
    animated(CONTROLS, 1, os.path.join(OUT, "animations.gif"))
    print(f"{len(CONTROLS)} controls written")


if __name__ == "__main__":
    main()
