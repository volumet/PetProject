#!/usr/bin/env bash
# Shared helpers for the build demo. Source this; don't run it directly.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# Use mvn from PATH, otherwise the wrapper that lives in backend/.
if command -v mvn >/dev/null 2>&1; then
    MVN=(mvn)
else
    MVN=(./backend/mvnw -f pom.xml)
fi

# Offline by default so network speed doesn't skew timings. Use EXTRA_ARGS= to go online.
EXTRA_ARGS="${EXTRA_ARGS--o}"

# timed <label> <command...>: runs the command, then prints its wall-clock time.
timed() {
    local label="$1"; shift
    local start end
    echo
    echo "==> $label"
    echo "    $*"
    start=$(date +%s%N)
    "$@"
    end=$(date +%s%N)
    echo
    printf '==> %s: %d.%03d s\n' "$label" $(((end - start) / 1000000000)) $((((end - start) / 1000000) % 1000))
}

# On Windows an mvnd daemon can keep project JARs locked, which makes a plain `mvn clean` fail.
stop_mvnd_daemons() {
    if command -v mvnd >/dev/null 2>&1; then
        mvnd --stop >/dev/null
    fi
}
