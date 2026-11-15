#!/usr/bin/env bash
# Stage 3 - switches to mvnd: warm daemon plus parallel module builds. Still no cache.
# Runs twice: stages 1 and 2 stop any daemons, so after them the first run is a cold start.
source "$(dirname "$0")/_common.sh"

for run in first second; do
    timed "3. mvnd verify ($run run)" mvnd $EXTRA_ARGS clean verify \
        -Dmaven.build.cache.enabled=false
done
