#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${1:---all}"

case "$mode" in
    --legacy) "$ROOT_DIR/harness/legacy/run.sh" ;;
    --java) "$ROOT_DIR/harness/java/run.sh" ;;
    --all)
        "$ROOT_DIR/harness/legacy/run.sh"
        "$ROOT_DIR/harness/java/run.sh"
        ;;
    -h|--help)
        echo "Usage: $0 [--legacy|--java|--all]"
        ;;
    *)
        echo "Usage: $0 [--legacy|--java|--all]" >&2
        exit 2
        ;;
esac
