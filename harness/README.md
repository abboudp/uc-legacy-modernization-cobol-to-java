# Migration test harness

This directory contains two independent test runners:

* `legacy/` builds the v1 batch COBOL readers with system GnuCOBOL, converts
  the committed CP037 EBCDIC fixtures, runs the programs, and compares stdout
  and output datasets with committed golden masters.
* `java/` discovers Maven modules below `modernization/*/pom.xml` and runs
  their tests.

Run both suites with:

```sh
harness/run.sh --all
```

Individual suites can be run with `harness/run.sh --legacy` and
`harness/run.sh --java`. The legacy runner accepts a program name:

```sh
harness/legacy/run.sh CBACT01C
harness/legacy/run.sh CBCUS01C
harness/legacy/run.sh CBTRN03C
```

## Legacy golden masters

`harness/legacy/build.sh` uses `/usr/bin/cobc` (or `$COBC`) with:

```text
-x -Wall -std=ibm-strict -I app/cpy
```

It builds `CBACT01C`, `CBCUS01C`, and `CBTRN03C` under the ignored
`harness/legacy/build/` directory. GnuCOBOL 3.1.2 with the BDB indexed-file
handler is required. `CBACT01C` also links the small `COBDATFT` compatibility
routine in `legacy/support/`; it preserves the program's `YYYY-MM-DD` date
conversion call without changing the application source.

The loader sources under `legacy/loaders/` read fixed-length binary records
sequentially, translate CP037 EBCDIC characters to ASCII, and write either
GnuCOBOL BDB indexed files or converted sequential files. The v1 input
copybooks contain only DISPLAY character/numeric fields; no COMP, COMP-3, or
other packed/binary input fields occur in the six datasets below. Whole-record
translation is therefore safe for these fixtures. If a future fixture contains
packed decimal or binary fields, the loader must translate only its DISPLAY
spans and preserve those bytes verbatim.

The primary keys below are verified against the corresponding IDCAMS
definitions in `app/jcl/` and the program file controls:

| Dataset | Program use | Record length | Primary key | Fixture records |
| --- | --- | ---: | --- | ---: |
| `ACCTDATA.PS` | `CBACT01C` | 300 | bytes 1-11; `KEYS(11 0)` | 50 |
| `CUSTDATA.PS` | `CBCUS01C` | 500 | bytes 1-9; `KEYS(9 0)` | 50 |
| `DALYTRAN.PS` | `CBTRN03C` | 350 | sequential input | 300 |
| `CARDXREF.PS` | `CBTRN03C` | 50 | bytes 1-16; `KEYS(16 0)` | 50 |
| `TRANTYPE.PS` | `CBTRN03C` | 60 | bytes 1-2; `KEYS(2 0)` | 7 |
| `TRANCATG.PS` | `CBTRN03C` | 60 | bytes 1-6; `KEYS(6 0)` | 18 |

The transaction report date parameter is the 80-byte sequential record
`2022-01-01 2022-07-06`, matching `TRANREPT.jcl` and `TRANREPT.prc`.
The committed transaction fixture has blank bytes in the program's
`FD-TRAN-PROC-TS` span (positions 305-330), so this date filter excludes all
300 transactions. `CBTRN03C` nevertheless opens, reads, and closes all of
its inputs successfully; its committed report golden master is consequently
an empty file containing zero 133-byte records. The fixture count remains
enforced by the runner before execution.

Generate or replace golden masters intentionally with:

```sh
harness/legacy/run.sh --update-golden --all
```

The expected files are under `legacy/expected/<program>/`. Build and run
artifacts are ignored. Golden-master updates should be reviewed as binary
and text changes, not generated automatically in CI.

`CBSTM03A` is excluded because the committed `CUSTREC.cpy` is tab-corrupted
for GnuCOBOL fixed-format parsing and the program depends on `CBSTM03B` and
other unavailable file-service routines. CICS `CO*` programs are also out of
scope for this offline batch runner.

## Java module discovery

The Java runner discovers every `modernization/*/pom.xml` on each invocation,
so a newly migrated Java module is picked up without editing the harness.
The current repository has no such modules; in that case the runner prints a
notice and exits successfully.
