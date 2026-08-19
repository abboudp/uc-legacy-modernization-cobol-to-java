# Interest-rate lookup

This is a standalone, read-only Java 17 extraction of the disclosure-group
interest-rate lookup and monthly-interest calculation in `CBACT04C`.

## Scope and COBOL mapping

The module replaces these portions of the batch flow:

* `app/cbl/CBACT04C.cbl:47-51` selects `DISCGRP-FILE`; its fixed-width file
  layout is described by the FD at `:76-82` (`FD-DISCGRP-KEY` is 16 bytes and
  `FD-DISCGRP-DATA` is an untyped 34-byte view).
* `app/cbl/CBACT04C.cbl:210-217` supplies the composite key, performs the
  lookup, and guards the interest calculation.
* `app/cbl/CBACT04C.cbl:270-286` opens the file and
  `app/cbl/CBACT04C.cbl:559-575` closes it. This extraction is read-only and
  does not own lifecycle or physical-file mutation.
* `app/cbl/CBACT04C.cbl:415-440` implements the initial random read,
  `app/cbl/CBACT04C.cbl:443-460` implements the default-group read, and
  `app/cbl/CBACT04C.cbl:462-470` computes and accumulates monthly interest.
* `app/cpy/CVTRA02Y.cpy` defines the typed `DIS-GROUP-RECORD`:
  `DIS-ACCT-GROUP-ID` X(10), `DIS-TRAN-TYPE-CD` X(02),
  `DIS-TRAN-CAT-CD` 9(04), signed zoned `DIS-INT-RATE` S9(04)V99 (6 bytes),
  and 28 bytes of filler.
* `app/jcl/DISCGRP.jcl:40-41,61` establishes `KEYS(16 0)`,
  `RECORDSIZE(50 50)`, and the sequential-to-VSAM load. The runtime DD is
  `app/jcl/INTCALC.jcl:22,35-36`.

Character fields are decoded and encoded with the JDK `IBM037` charset,
without trimming leading zeroes or trailing spaces. The module's local
signed-zoned codec validates the five leading `F0`-`F9` magnitude bytes and
the positive `C0`-`C9`, negative `D0`-`D9`, or unsigned `F0`-`F9` final
overpunch byte. Record encoding preserves all 28 filler bytes verbatim, so a
decode/encode cycle is physically byte-exact. `decodeAll` rejects any input
whose length is not a multiple of 50.

The read model indexes records by the 16-byte composite key. Its reader
returns a two-character COBOL file status and optional record. The resolver
returns a value rather than throwing an abend or exiting: it reports direct
hit, DEFAULT fallback, default-missing abend, or initial-read-error abend,
along with statuses, rate, and DISPLAY messages in COBOL order.

## Reproduce

From the repository root:

```text
mvn -f modernization/interest-rate-lookup/pom.xml test
```

The test suite contains 20 tests.

The tests locate `app/data/` by walking upward from `user.dir`; the binary
fixture is intentionally not copied into test resources.

## Parity evidence

`app/data/EBCDIC/AWS.M2.CARDDEMO.DISCGRP.PS` is 2,550 bytes, exactly 51
50-byte records, encoded cp037/IBM037. The whole-fixture test decodes all 51
records and re-encodes them to a byte-identical 2,550-byte stream. The
fixture has:

* 17 rows with group id `'DEFAULT   '`;
* three distinct group ids: `A000000000`, `DEFAULT   `, and `ZEROAPR   `;
* seven type codes: `01` through `07`;
* four category codes: `0001` through `0004`;
* 51 positive rate overpunches ending in `x'C0'`, and every filler is 28
  bytes of `x'F0'`;
* only the distinct rates 1.50, 2.50, and 0.00.

Pinned rows include `A000000000|01|0001` = 1.50,
`A000000000|01|0002` = 2.50, `DEFAULT   |01|0002` = 2.50,
`DEFAULT   |02|0001` = 0.00, and
`ZEROAPR   |07|0001` = 0.00. Each of the three groups contains the same
17 `(type, category)` pairs:
`01`×{`0001`-`0004`}, `02`×{`0001`-`0003`},
`03`×{`0001`-`0003`}, `04`×{`0001`-`0003`},
`05`×{`0001`}, `06`×{`0001`,`0002`}, and `07`×{`0001`}.

The ASCII cross-check at `app/data/ASCII/discgrp.txt` renders the first
rate as `0150{`. That mangles the overpunch, so it is not a parity source
for numeric rates; the EBCDIC file is authoritative.

## Formula and findings

The source fields are `WS-MONTHLY-INT PIC S9(09)V99` and
`WS-TOTAL-INT PIC S9(09)V99` at `app/cbl/CBACT04C.cbl:168-169`, with
`TRAN-CAT-BAL PIC S9(09)V99` at `app/cpy/CVTRA01Y.cpy:9`. The source formula
at `:464-467` is `(TRAN-CAT-BAL * DIS-INT-RATE) / 1200`, with no `ROUNDED`;
the implementation therefore divides with scale 2 and `RoundingMode.DOWN`,
which is truncation toward zero, and accumulates at scale 2. The accumulator
has an explicit reset corresponding to `MOVE 0 TO WS-TOTAL-INT` at `:200`.

The caller's `IF DIS-INT-RATE NOT = 0` at `app/cbl/CBACT04C.cbl:214` is
modeled as `shouldComputeInterest(rate)` and is covered by a test. This
means a zero-rate hit is a valid lookup but does not require a computation.
The balance is supplied by the caller/`TCATBALF`; this module does not read
that file. The source has no `ON SIZE ERROR`, so COBOL would silently
high-order-truncate a value exceeding nine integer digits. This extraction
does not emulate that overflow behavior.

Other findings:

* The open-failure message at `app/cbl/CBACT04C.cbl:281` says
  `'ERROR OPENING DALY REJECTS FILE'`, a legacy copy-paste typo; it is not
  changed here.
* The initial lookup treats status `'23'` as success at `:422`, then makes
  the fallback decision at `:437`; the default lookup at `:446` accepts only
  `'00'`. The fallback replaces only the group id and retains type and
  category. Moving `DEFAULT` into X(10) produces the effective key
  `'DEFAULT   '`.
* All three fixture groups have the same key set. Therefore a `'23'` to
  DEFAULT fallback cannot be reached using a group id present in this
  fixture; `B000000000|01|0002` demonstrates the real DEFAULT row and its
  2.50 rate using a group id that would come from the account file, not this
  file. A pair absent from both groups, such as `05|0002`, produces the
  default-read error outcome.
* The FD's coarse X(34) data view and the typed copybook view describe the
  same 50 bytes; the typed rate exists only when the COBOL READ uses
  `INTO DIS-GROUP-RECORD`.
* Follow-up: consolidation into a shared codec module.

## Explicit exclusions

This module does not implement any writes or unrelated batch behavior:

* no `ACCTDATA` update (`1050-UPDATE-ACCOUNT` at `:350`);
* no `TCATBALF` read loop;
* no `TRANSACT` write (`1300-B-WRITE-TX` at `:473`);
* no transaction-id generation, `PARM` date handling, xref/account access,
  fees (`1400-COMPUTE-FEES`), totals, or report output;
* no REST/HTTP, UI, CLI, scheduler emulation, Db2, or framework;
* no generic copybook parser, code generator, or shared codec module.
