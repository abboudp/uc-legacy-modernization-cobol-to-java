# CARDXREF read model

This module is a standalone, read-only Java 17 projection of the CARDXREF
card/account/customer cross-reference. It replaces the sequential XREFFILE
read in `CBACT03C` and the two VSAM lookup directions without requiring a
mainframe runtime.

## Legacy contract

* `app/cpy/CVACT03Y.cpy` defines `CARD-XREF-RECORD`: card `X(16)` at byte
  offset 0, customer `9(9)` at offset 16, account `9(11)` at offset 25, and
  a 14-byte filler (the copybook record is 50 bytes).
* `app/jcl/XREFFILE.jcl:43-44` defines the base KSDS with
  `KEYS(16 0)` and `RECORDSIZE(50 50)`.
* `app/jcl/XREFFILE.jcl:74-77` defines alternate index `CXACAIX` with
  `KEYS(11,25)`, `NONUNIQUEKEY`, and 50-byte records. Its PATH is at
  `app/jcl/XREFFILE.jcl:85-92`, and BLDINDEX is at line 100.
* `app/cbl/CBACT03C.cbl:92-116` performs the sequential read,
  `:118-134` opens XREFFILE, `:136-152` closes it, and `:161-176` formats
  I/O status. `app/cbl/CBACT03C.cbl:154-159` abends with code 999.

`CardXrefReadModel` sorts records by the base card key and provides the
card-to-record direction plus the account-to-many-records direction. The
account result is a collection because the AIX is `NONUNIQUEKEY`; records
within an account remain in base card-key order, matching the indexed path's
base-key ordering. `CardXrefSequentialReader` preserves the important
paragraph semantics: status `00` returns a record, status `10` is a normal
end-of-file outcome, and other statuses return an abend outcome with code
999. No method calls `System.exit`.

## Technique and parity evidence

`CardXrefRecord` decodes and encodes the fixed-width bytes with IBM037
(CP037). Numeric fields are retained as zero-padded strings, preserving
their COBOL representation. The test suite reads the real
`app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS` fixture, verifies byte-for-byte
decode/encode round-trip, and pins the counts at 50 records, 50 distinct
accounts, and 50 distinct customers. The fixture's records are already
sorted in ascending card-number order; ordering tests explicitly preserve
that observed order.

The loader rejects a file whose length is not a multiple of 50 rather than
silently dropping a truncated final record. It also rejects non-EBCDIC-digit
bytes in the customer or account fields during decode. This is deliberate:
the COBOL fields are zoned decimal `PIC 9(n)` fields in `CVACT03Y.cpy`, and
the legacy program's numeric validation/abend convention is represented by
rejecting malformed numeric data at the codec boundary (see
`CBACT03C.cbl:161-176` for the I/O-status/abend reporting path). The
non-digit test is labelled by behavior, while the multi-card account test is
explicitly synthetic because the supplied fixture has only 1:1:1 records.

## Run

From the repository root:

```text
mvn -f modernization/cardxref-readmodel/pom.xml test
```

To use another fixture path in tests, set the `cardxref.fixture` system
property. This module has no writes, REPRO, or BLDINDEX emulation; no REST,
UI, CLI, or CICS surface; no other entity models; and no generic copybook
parser.

## Follow-up

Consolidation into a shared codec module is a follow-up. The EBCDIC decode
helper is deliberately duplicated per slice so this module remains
standalone and has no dependency on another modernization module.
