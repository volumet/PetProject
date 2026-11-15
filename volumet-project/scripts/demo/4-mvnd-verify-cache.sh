#!/usr/bin/env bash
# Stage 4 - turns the build cache on: the first run fills it, the second restores from it.
# Uses a throwaway cache directory so the demo always starts cold and ~/.m2/build-cache is untouched.
# Afterwards target/classes is empty, because a cached restore only brings back the JARs.
source "$(dirname "$0")/_common.sh"

CACHE_DIR="$(mktemp -d)"
trap 'rm -rf "$CACHE_DIR"' EXIT

timed "4. mvnd verify (cache cold)" mvnd $EXTRA_ARGS clean verify \
    -Dmaven.build.cache.location="$CACHE_DIR"
timed "4. mvnd verify (cache warm)" mvnd $EXTRA_ARGS clean verify \
    -Dmaven.build.cache.location="$CACHE_DIR"
