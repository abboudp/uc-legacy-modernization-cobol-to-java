#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HARNESS_DIR="$ROOT_DIR/harness/legacy"
BUILD_DIR="$HARNESS_DIR/build"
RUN_DIR="$BUILD_DIR/run"
UPDATE_GOLDEN=false
REQUESTED="--all"

usage() {
    echo "Usage: $0 [--update-golden] [PROGRAM|--all]"
}

while (($#)); do
    case "$1" in
        --update-golden) UPDATE_GOLDEN=true ;;
        --all|CBACT01C|CBCUS01C|CBTRN03C) REQUESTED="$1" ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; exit 2 ;;
    esac
    shift
done

"$HARNESS_DIR/build.sh"
rm -rf "$RUN_DIR"
mkdir -p "$RUN_DIR"/{acct,cust,tran,xref,trantype,trancatg,date}

load_indexed() {
    local loader="$1" input="$2" output="$3"
    INFILE="$input" OUTFILE="$output" "$BUILD_DIR/loaders/$loader"
}

load_sequential() {
    local input="$1" output="$2"
    INFILE="$input" OUTFILE="$output" "$BUILD_DIR/loaders/tran-loader"
}

load_indexed acct-loader \
    "$ROOT_DIR/app/data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS" \
    "$RUN_DIR/acct/ACCTFILE"
load_indexed cust-loader \
    "$ROOT_DIR/app/data/EBCDIC/AWS.M2.CARDDEMO.CUSTDATA.PS" \
    "$RUN_DIR/cust/CUSTFILE"
load_indexed xref-loader \
    "$ROOT_DIR/app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS" \
    "$RUN_DIR/xref/CARDXREF"
load_indexed trantype-loader \
    "$ROOT_DIR/app/data/EBCDIC/AWS.M2.CARDDEMO.TRANTYPE.PS" \
    "$RUN_DIR/trantype/TRANTYPE"
load_indexed trancatg-loader \
    "$ROOT_DIR/app/data/EBCDIC/AWS.M2.CARDDEMO.TRANCATG.PS" \
    "$RUN_DIR/trancatg/TRANCATG"
load_sequential \
    "$ROOT_DIR/app/data/EBCDIC/AWS.M2.CARDDEMO.DALYTRAN.PS" \
    "$RUN_DIR/tran/TRANFILE"
printf '%-10s %-10s' 2022-01-01 2022-07-06 |
    dd of="$RUN_DIR/date/DATEPARM" bs=80 count=1 conv=sync status=none

check_fixture() {
    local file="$1" record_size="$2" expected_records="$3"
    local bytes
    bytes="$(stat -c '%s' "$file")"
    if ((bytes != record_size * expected_records)); then
        echo "Fixture size mismatch: $file ($bytes bytes)" >&2
        exit 1
    fi
}

check_fixture "$ROOT_DIR/app/data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS" 300 50
check_fixture "$ROOT_DIR/app/data/EBCDIC/AWS.M2.CARDDEMO.CUSTDATA.PS" 500 50
check_fixture "$ROOT_DIR/app/data/EBCDIC/AWS.M2.CARDDEMO.DALYTRAN.PS" 350 300
check_fixture "$ROOT_DIR/app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS" 50 50
check_fixture "$ROOT_DIR/app/data/EBCDIC/AWS.M2.CARDDEMO.TRANTYPE.PS" 60 7
check_fixture "$ROOT_DIR/app/data/EBCDIC/AWS.M2.CARDDEMO.TRANCATG.PS" 60 18

run_and_compare() {
    local program="$1"
    local expected="$HARNESS_DIR/expected/$program"
    local actual="$RUN_DIR/$program"
    local output
    mkdir -p "$actual"
    case "$program" in
        CBACT01C)
            ACCTFILE="$RUN_DIR/acct/ACCTFILE" \
            OUTFILE="$actual/out.ps" \
            ARRYFILE="$actual/array.bin" \
            VBRCFILE="$actual/vbr.bin" \
            "$BUILD_DIR/$program" > "$actual/stdout.txt"
            ;;
        CBCUS01C)
            CUSTFILE="$RUN_DIR/cust/CUSTFILE" \
            "$BUILD_DIR/$program" > "$actual/stdout.txt"
            ;;
        CBTRN03C)
            TRANFILE="$RUN_DIR/tran/TRANFILE" \
            CARDXREF="$RUN_DIR/xref/CARDXREF" \
            TRANTYPE="$RUN_DIR/trantype/TRANTYPE" \
            TRANCATG="$RUN_DIR/trancatg/TRANCATG" \
            DATEPARM="$RUN_DIR/date/DATEPARM" \
            TRANREPT="$actual/report.ps" \
            "$BUILD_DIR/$program" > "$actual/stdout.txt"
            ;;
    esac

    if [[ "$UPDATE_GOLDEN" == true ]]; then
        rm -rf "$expected"
        mkdir -p "$expected"
        cp "$actual"/* "$expected/"
        echo "Updated golden master: $program"
        return
    fi

    local failed=false
    for file in "$expected"/*; do
        local name="${file##*/}"
        if [[ ! -f "$actual/$name" ]] || ! cmp -s "$file" "$actual/$name"; then
            echo "Mismatch: $program/$name" >&2
            if [[ -f "$actual/$name" ]]; then
                diff -u "$file" "$actual/$name" || true
            fi
            failed=true
        fi
    done
    for file in "$actual"/*; do
        local name="${file##*/}"
        [[ -f "$expected/$name" ]] || {
            echo "Unexpected output: $program/$name" >&2
            failed=true
        }
    done
    [[ "$failed" == false ]] || return 1
    echo "PASS $program"
}

case "$REQUESTED" in
    --all)
        run_and_compare CBACT01C
        run_and_compare CBCUS01C
        run_and_compare CBTRN03C
        ;;
    *) run_and_compare "$REQUESTED" ;;
esac
