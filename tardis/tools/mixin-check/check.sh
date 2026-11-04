#!/bin/sh
# Checks that every mixin injector in gravity/gc finds its target in NeoForge's Minecraft (client classes too, which
# the GameTest server never loads). Run from the repo root after a build has made the merged jar.
# Prints MISS / NOMETHOD lines; "bad 0" means every target was found (wildcard methods like "tick*" show as NOMETHOD).
set -e
DIR=$(dirname "$0")
ASM=$(find ~/.gradle/caches -name "asm-9.*.jar" ! -name "*sources*" | head -1)
TREE=$(find ~/.gradle/caches -name "asm-tree-9.*.jar" ! -name "*sources*" | head -1)
JAR=$(ls tardis/build/moddev/artifacts/neoforge-*-merged.jar | head -1)
python3 "$DIR/extract.py" tardis/src/main/java/com/porcupinestudios/tardisrb/gravity/gc/mixin > /tmp/mixin-specs.tsv
java -cp "$ASM:$TREE" "$DIR/Check.java" "$JAR" /tmp/mixin-specs.tsv
