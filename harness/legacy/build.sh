#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HARNESS_DIR="$ROOT_DIR/harness/legacy"
BUILD_DIR="$HARNESS_DIR/build"
COBC="${COBC:-$(command -v cobc || true)}"

if [[ -z "$COBC" ]]; then
    echo "cobc is required; install gnucobol3 first" >&2
    exit 1
fi

mkdir -p "$BUILD_DIR/loaders"
rm -f "$BUILD_DIR"/CBACT01C "$BUILD_DIR"/CBCUS01C "$BUILD_DIR"/CBTRN03C
rm -f "$BUILD_DIR"/loaders/*

COMMON_FLAGS=(-x -Wall -std=ibm-strict -I "$ROOT_DIR/app/cpy")

"$COBC" "${COMMON_FLAGS[@]}" \
    -o "$BUILD_DIR/CBACT01C" \
    "$ROOT_DIR/app/cbl/CBACT01C.cbl" \
    "$HARNESS_DIR/support/COBDATFT.cbl"
"$COBC" "${COMMON_FLAGS[@]}" \
    -o "$BUILD_DIR/CBCUS01C" \
    "$ROOT_DIR/app/cbl/CBCUS01C.cbl"
"$COBC" "${COMMON_FLAGS[@]}" \
    -o "$BUILD_DIR/CBTRN03C" \
    "$ROOT_DIR/app/cbl/CBTRN03C.cbl"

generate_loader() {
    local template="$1"
    local output="$2"
    local record_size="$3"
    local key_size="$4"
    local data_size="$5"
    sed \
        -e "s/__RECORD_SIZE__/$record_size/g" \
        -e "s/__KEY_SIZE__/$key_size/g" \
        -e "s/__DATA_SIZE__/$data_size/g" \
        "$HARNESS_DIR/loaders/$template" > "$BUILD_DIR/loaders/$output.cbl"
    "$COBC" -x -Wall -free \
        -I "$HARNESS_DIR/loaders" \
        -o "$BUILD_DIR/loaders/$output" \
        "$BUILD_DIR/loaders/$output.cbl"
}

generate_loader indexed-loader.cbl.in acct-loader 300 11 289
generate_loader indexed-loader.cbl.in cust-loader 500 9 491
generate_loader indexed-loader.cbl.in xref-loader 50 16 34
generate_loader indexed-loader.cbl.in trantype-loader 60 2 58
generate_loader indexed-loader.cbl.in trancatg-loader 60 6 54
generate_loader sequential-loader.cbl.in tran-loader 350 0 0

echo "Built legacy COBOL programs and EBCDIC loaders with $("$COBC" --version | head -1)."
