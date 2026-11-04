---
name: planet-space-design
description: How space/planets work in TARDIS-RingBearer (full-size planets, space as scale model, cloud-bank swap, seam portals) and Aaron's 2026-11-04 rules
metadata:
  type: project
  modified: 2026-11-04T08:00:00.000Z
---

Aaron 2026-11-04: Middle-earth is a planet and can NOT be scaled down; "cheat as long as it's immersive"; wants a
custom planet-scale portal system on Immersive Portals' foundations; performance is a must.

Built 2026-11-04 (branch claude/project-thread-8jgvmk, merged with the 6vl44n port branch; jars installed in the
TARDIS RingBearer mods folder): planets stay full size; each planet's space is an "orbit layer" in tardis_rb:space
(surface shrunk orbit.scale=16x, height = altitude growing exponentially to 3 planet radii), globe drawn under each
player from their own spot. Up/down swaps happen inside a cloud bank (fog + haze). Middle-earth planet = full map
exactly LOTR's map (no sea padding: Aaron called it out of bounds), painted from LOTR's map.png, via the shared API
PlanetProvider. Wrap default torus (Aaron: north of Forodwaith must loop back in at the south); globe and world loop
borders must agree on the edges (one surface, tested). Globe drawn unwrapped round the viewer (azimuthal) so it
doesn't stretch. SeamPortals = one-way IP portals only near players. Details:
tardis/docs/space.md. Not yet seen in a game client (as of 2026-11-04).

Gravity (2026-11-04, tardis/docs/gravity.md): orbits + /gravity scale built; Aaron picked Gravity Changer as the
reference; six-direction gravity ported from its 1.21.1 version (gravity/gc) with /gravity direction, seen working in a
headless client (tardis/docs/headless-client.md: the cloud sandbox CAN run the game client under Xvfb). Planets are
1/64 scale in space now. Aaron 06:52 UTC: "do slice 2 and 3 after" = next the solar-system zone (3D, every body
pulls, a sun) then free tilt while floating (six-way on blocks). Both built and installed 2026-11-04 08:00 UTC
(commits a03aafcb, bdf1380b; tardis/docs/solar-system.md): system data tardis/system/*.json (sol; LOTR's arda),
dimension tardis_rb:solar_system, sun + planet balls seen in the headless client. Aaron hasn't flown it yet.

**How to apply:** keep planets full size; next ideas: atmosphere rim, sharper local map patch, smoothing the eye jump
when the six-way direction flips mid-flight, WASD air control in the leaned frame. See [[ringbearer-progress]].
