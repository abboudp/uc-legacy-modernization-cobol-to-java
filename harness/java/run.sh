#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
mapfile -t MODULES < <(find "$ROOT_DIR/modernization" -mindepth 2 -maxdepth 2 \
    -type f -name pom.xml -print 2>/dev/null | sort)

if ((${#MODULES[@]} == 0)); then
    echo "No modernization Java modules found; Java harness is green."
    exit 0
fi

failed=0
passed=0
for pom in "${MODULES[@]}"; do
    module="${pom#"$ROOT_DIR"/}"
    echo "Running Maven tests for $module"
    if mvn -f "$pom" test; then
        ((passed += 1))
        echo "PASS $module"
    else
        ((failed += 1))
        echo "FAIL $module" >&2
    fi
done

echo "Java module summary: $passed passed, $failed failed, ${#MODULES[@]} total"
((failed == 0))
