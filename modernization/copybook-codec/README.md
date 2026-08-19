# CardDemo copybook codec

This standalone Java 17 module replaces the fixed-length record I/O represented
by the CardDemo `CVTRA03Y.cpy` and `CVTRA01Y.cpy` copybooks, used by programs
including `CBACT04C`, `CBTRN02C`, and `COTRTLIC`. It is deliberately a codec,
not a copybook parser or a batch file-writing application.

## Layouts

The layouts are taken directly from the copybooks:

* `app/cpy/CVTRA03Y.cpy:5-8` defines `TRAN-TYPE-RECORD`: a two-byte type,
  50-byte description, and eight-byte filler, for 60 bytes.
  `app/jcl/TRANTYPE.jcl:40-41` confirms `KEYS(2 0)` and `RECORDSIZE(60 60)`.
* `app/cpy/CVTRA01Y.cpy:5-12` defines `TRAN-CAT-BAL-RECORD`: an 11-digit
  account ID, two-byte type code, four-digit category, signed
  `S9(09)V99` balance, and 22-byte filler, for 50 bytes.
  `app/jcl/TCATBALF.jcl:40-41` confirms `KEYS(17 0)` and
  `RECORDSIZE(50 50)`.

`TransactionTypeCodec` and `TransactionCategoryBalanceCodec` decode at an
offset and encode immutable Java records. Display fields remove only trailing
EBCDIC spaces (`x'40'`) while preserving leading spaces. Filler is retained as
a Java `String`, so non-space filler data is reproduced literally.

## Numeric sign convention

The module uses IBM-037 (`cp037`) and zoned-decimal bytes. High-order digits
are `x'F0'` through `x'F9'`. In a signed field, the low-order digit uses zone
`C` for non-negative values and zone `D` for negative values; `F` is accepted
as an additional positive low-order zone. This convention is justified by the
real `TCATBALF` fixture: its balance bytes are
`F0 F0 F0 F0 F0 F0 F0 F0 F0 F0 C0`, representing `+0.00`, and all 50 records
use `x'C0'` for the low-order byte. Synthetic tests provide the negative
`D`-zone case because no fixture record is negative.

## Run and parity evidence

From the repository root:

```text
mvn -f modernization/copybook-codec/pom.xml test
```

The JUnit 5 tests read the read-only fixtures and assert:

* 420 bytes are seven 60-byte transaction-type records, and decoding then
  re-encoding every record is byte-identical.
* 2500 bytes are fifty 50-byte category-balance records, with the same
  byte-identical round-trip assertion.
* Decoded display fields and digit strings agree with the corresponding ASCII
  fixture lines, and real balances decode as `BigDecimal("0.00")` with scale 2.
* Synthetic negative and positive non-zero signed values round-trip, including
  the `D` and `C` low-order zones.

The ASCII conversion has a signed-numeric defect: EBCDIC `x'C0'` was translated
to the literal ASCII character `{`, so the ASCII balance column is
`0000000000{`, not a valid ASCII zoned-decimal digit string. The cross-check
test asserts this mangling rather than treating the ASCII file as authoritative
for the signed value.

## Not in this slice

This module does not implement COMP-3 packed decimal, COMP binary, REDEFINES
unions, OCCURS, the CVEXPORT five-way discriminated union, a copybook
parser/code generator, file writing, or a CLI. Natural follow-ups are
CVEXPORT, COMP-3, and REDEFINES support. A follow-up consolidation PR is
expected to merge duplicated helpers across the three parallel slices.
