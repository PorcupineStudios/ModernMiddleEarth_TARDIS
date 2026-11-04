# Testing: the game client in the cloud sandbox
Read when: you need to see something in a real client (rendering, camera, client mixins) from the cloud sandbox.

The sandbox has Xvfb + Mesa (software OpenGL), ImageMagick `import` and the assets cache, so the dev client runs
headless (first done 2026-11-04 for gravity direction: screenshots showed the player upside down on a ceiling).
- Start a screen: `Xvfb :99 -screen 0 1280x720x24 &`, then
  `DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 ./gradlew :tardis:runClient -PquickPlay=<save>` (`-PquickPlay` is in
  `gradle/mod.gradle`; it opens `tardis/run/saves/<save>` straight away). Screenshot: `DISPLAY=:99 import -window root
  shot.png`. No mouse/keyboard tool, so drive the world with a datapack in the save (a `#minecraft:load` function that
  `schedule`s commands, `say` to mark steps in the log).
- First-run screens block quick play; `tardis/run` is git-ignored, so set these again in a fresh sandbox:
  `tardis/run/options.txt` with `onboardAccessibility:false` and `pauseOnLostFocus:false`;
  `tardis/run/config/immersive_portals.json` `"initialScreenShown": true`; and in the save's `level.dat`
  `Data.confirmedExperimentalSettings = 1b` (custom dimensions; edit with `pip install nbtlib`).
- A save to start from: copy `tardis/run/tardis-check` (made by the dedicated-server world check) to
  `tardis/run/saves/<name>` and delete its `session.lock`.
- Kill the client by PID afterwards (`pkill -f` on a pattern also in your own command line kills your shell).
