---
name: ringbearer-test-instance
description: How to get TARDIS-RingBearer jars to Aaron's CurseForge test instance (rolling test-build release, mods folder path)
metadata:
  type: reference
  modified: 2026-11-03T21:22:03.824Z
---

- Aaron's test instance: `C:\Users\aadag\curseforge\minecraft\Instances\TARDIS RingBearer` (AARONPC, CurseForge,
  NeoForge 21.1.265, MC 1.21.1). Mods go in its `mods` folder; first installed 2026-11-03.
- CI (`.github/workflows/build.yml`) publishes every green push as the pre-release `test-build` with our three jars
  (ringbearer_api, tardis_rb, lotr_port) plus Create 6.0.10, Immersive Portals 6.0.7 and Cloth Config. No Sodium: Create needs 0.6.9+, IP 6.0.7 only works with 0.6.0 (Aaron removes it by hand; device tools cannot delete).
- The cloud sandbox can't download Actions artifacts (blob storage blocked) or reach Modrinth, but release assets
  work: `gh api -H "Accept: application/octet-stream" repos/PorcupineStudios/TARDIS-RingBearer/releases/assets/<id>`
  (list ids via `.../releases/tags/test-build`). `gh release` subcommands fail (GraphQL blocked).
- Then stage in `/mnt/project-files/test-build/` and copy with device_commit_files. Related: [[ringbearer-progress]].
- After copying, list the mods folder and compare sizes: on 2026-11-03 a copy made right after downloading wrote the
  previous jar (the shared folder served a stale file); copying again fixed it.
