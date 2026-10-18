#!/usr/bin/env bash
# Refreshes src/main/resources/packs/<era> from the pack branches of this repo.
# Run from anywhere after changing a pack branch, then commit the result.
set -euo pipefail
cd "$(dirname "$0")/.."

# era directory (must match PackEra.id) : git ref that holds that era's pack at its root
ERAS=(
    "1.21.4:1.21.4"
    "1.21.5:1.21.5"
    "1.21.8:1.21.8"
    "1.21.10:1.21.10"
    "1.21.11:1.21.11"
    "26.1:26.1"
    "26.2:26.2"
    "26.3:26.3"
    "26.4:26.4"
)

for entry in "${ERAS[@]}"; do
    era=${entry%%:*}
    ref=${entry#*:}
    dir=src/main/resources/packs/$era
    rm -rf "$dir"
    mkdir -p "$dir"
    git archive "$ref" assets pack.mcmeta pack.png | tar -x -C "$dir"
    echo "$era <- $ref"
done
