#!/usr/bin/env bash
# Stage 1 - baseline: plain Maven, one module at a time, tests in sequence, no cache.
source "$(dirname "$0")/_common.sh"
stop_mvnd_daemons

timed "1. mvn verify" "${MVN[@]}" $EXTRA_ARGS clean verify \
    -b singlethreaded -T 1 \
    -Djunit.parallel.enabled=false \
    -Dmaven.build.cache.enabled=false
