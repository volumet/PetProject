#!/usr/bin/env bash
# Stage 2 - adds parallel tests: test classes run concurrently, modules still build one at a time.
source "$(dirname "$0")/_common.sh"
stop_mvnd_daemons

timed "2. mvn verify + parallel Surefire" "${MVN[@]}" $EXTRA_ARGS clean verify \
    -b singlethreaded -T 1 \
    -Djunit.parallel.enabled=true \
    -Dmaven.build.cache.enabled=false
