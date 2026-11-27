# Plans and roadmaps

Every plan and roadmap for the project lives in this repo (Aaron, 2026-11-20). Edit them here. Don't keep a plan only
in a claude.ai doc or artifact: Claude sessions often cannot open those.

| Plan | File |
|---|---|
| Colony core, hired units, multiplayer, colonists (2026-11-10) | `docs/plans/colony-core.md` |
| Roads that change: caravans wear paths, waymakers build roads, tunnels (2026-11-20) | `docs/plans/roads.md` |
| TARDIS free flight (built in slices 2-4) | `tardis/docs/free-flight-plan.md` |
| LOTR feature audit: every LOTR 1.7.10 class sorted into a feature, with its port status and the biggest gaps (2026-11-26) | `docs/plans/lotr-feature-audit.md` |
| Read LOTR's files from the player's jar instead of shipping them: what's left and the steps (2026-11-27) | `docs/plans/legacy-jar-references.md` |
| User stories for the partial and not-started work in every plan and design doc, with done-when, size and status (2026-11-27) | `docs/plans/user-stories.md` |

## Tickets

Work items are GitHub issues in the project "Ring Bearer sprints" (https://github.com/users/PorcupineStudios/projects/1).
An issue body has the headings `### Story`, `### Mod`, `### Done when`, `### Size` and `### Plan it comes from`, and the
labels `story`, `mod:<api|tardis|lotr|harness>` and `size:S/M/L`. New ones go in with Status Todo and no milestone; a
sprint gives them its milestone. LOTR's missing features (from the audit above) are issues #14 to #72. Filing by
script: `gh issue create --repo PorcupineStudios/ModernMiddleEarth_TARDIS ...`, then `gh project item-add 1 --owner
PorcupineStudios --url <issue>`.

## Still only on claude.ai

These are linked from the CLAUDE.md files, but this session could not open them, so they are not copied here yet.
To bring one in: open it, use Export > Markdown, and save it here under the file name given (or paste it into a
session and ask for it to be saved).

| Plan | Link | Save as |
|---|---|---|
| Shared roadmap, both mods in one order | https://claude.ai/artifact/H9foA8JKxCZEc4ArAyaXjZ | `docs/plans/roadmap.md` |
| Plan review that shaped the roadmap | https://claude.ai/artifact/1BciPgPdz8FbnnamU9E2Rs | `docs/plans/plan-review.md` |
| TARDIS port plan (decisions, progression, phases) | https://claude.ai/artifact/DD8D3rShdupt1xVSpx16XZ | `docs/plans/tardis-port-plan.md` |
| TARDIS code architecture and hooks | https://claude.ai/artifact/ELT5R1mp3XythfpsNpyKJE | `docs/plans/tardis-architecture.md` |
| LOTR class map | https://claude.ai/artifact/KqhSSTCuKXGuwKHBoYJdcu | `docs/plans/lotr-class-map.md` |
| RingRealms colonies (referenced by `lotr/docs/colonies.md`) | https://claude.ai/artifact/VLwZ5N7pkVMJW4PCYXh41G | `docs/plans/colonies-design.md` |
| Creature rigs proposal (referenced by `lotr/docs/rig.md`) | https://claude.ai/artifact/SAvtwnRnAbBWj2ZwUMXNbX | `docs/plans/rig-proposal.md` |

When one is copied in, move its row to the table above and point the CLAUDE.md or doc that links it at the file.
