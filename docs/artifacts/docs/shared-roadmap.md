# Shared Roadmap

Nov 3, 2026 · @Aaron

## Summary

One order for everything in the shared repo: the shared API, the LOTR port (with RingRealms built in) and TARDIS: Ring Bearer. The LOTR port goes first, so Middle-earth, its people and its colonies exist before the TARDIS crash-lands there. The history log starts in the very first stage, so every world is recorded from day one and the past is full by the time time travel unlocks.

Phase numbers below refer to the build phases in each mod's plan.

## Stages

| Stage | Shared API | LOTR port | TARDIS: Ring Bearer | Ready when |
| --- | --- | --- | --- | --- |
| 1. Foundations | History log, conditions, providers; perf command and benchmark world in CI | Phase 1 |  | The history log records a block change, and an alignment survives a restart |
| 2. Middle-earth exists |  | Phases 2 to 5: converter, blocks, small Middle-earth, factions, full Middle-earth |  | Every biome generates, and the crash-site provider offers the Middle-earth spawn |
| 3. People and colonies |  | Phases 6 to 9: NPCs, structures, quests, RingRealms colonies |  | A colony grows, and a rare race joins by quest |
| 4. The wreck |  |  | Phases 1 to 5: record and unlocks, wreck and short hops, repair loop, rooms, the other dimension | The wreck lands at the start dimension's spawn and is repaired to dimension travel; colonists can research TARDIS tech |
| 5. Living world and space |  | Phases 10 and 11: AI rulers, colony wars, story engine, strategy map | Phase 6: seamless space, TARDIS free flight (no dematerialising, third-person view), seamless door | AI rulers fight over colonies, and the TARDIS flies itself from a planet through space to another planet |
| 6. Time travel |  |  | Phase 7: time travel | A trip visits a past rebuilt from the history log, and a future trip shows the AI's forecast |
| 7. The rest |  | Other ages of Middle-earth | Phases 8 to 10: other modules, integrations, pack-maker pass | Other ages are reachable by TARDIS, and a pack can be rebalanced by datapack alone |

### Changes from Aaron's vision (2026-11-03 evening)

Built on 2026-11-03 before stage 5 continues (CI green; not yet tried in a game client):

- **TARDIS repairs move to a basement repair room** with the repair and upgrade skill tree as one GUI. The broken part blocks from TARDIS phase 3 are removed.
- **A custom console model** like the original mod's, with clickable buttons, levers and dials that don't follow block shapes, replacing the plain console block and its flat screen.
- **AI rulers wait for the player:** none start until the first player colony is founded (a small change to LOTR phase 10).
- **Selective migration:** migrants come to the Hall and offer to settle, and the player accepts or turns each one away (replaces automatic move-in from LOTR phase 9).

Space (stage 5) now also covers TARDIS free flight and the seamless door; details in the TARDIS plan.

If you'd rather start the TARDIS as soon as Middle-earth exists, stages 3 and 4 swap; nothing in stage 4 needs NPCs or colonies except colonist research, which waits for stage 3.

## Repo and plans

Everything lives in the private repo [TARDIS-RingBearer](https://github.com/PorcupineStudios/TARDIS-RingBearer):

| Folder | Mod | Mod id |
| --- | --- | --- |
| `api/` | Shared Ring Bearer API | `ringbearer_api` |
| `lotr/` | LOTR port with RingRealms | `lotr_port` |
| `tardis/` | TARDIS: Ring Bearer | `tardis_rb` |
| `converter/` (later) | 1.7.10 exporter, its own Gradle build | none |

Both mods depend on the shared API and never on each other. The old LOTR-Port repo's history was merged in, so it can be archived.

Plans:

- [LOTR 1.21.1 Port Plan](https://claude.ai/artifact/AgjUf8mwNgX4omnWC5zzN4)
- [TARDIS 1.21.1 Port Plan](https://claude.ai/artifact/DD8D3rShdupt1xVSpx16XZ) and [TARDIS Code Architecture](https://claude.ai/artifact/ELT5R1mp3XythfpsNpyKJE)
- [Plan Review](https://claude.ai/artifact/1BciPgPdz8FbnnamU9E2Rs), the reasons behind this order
