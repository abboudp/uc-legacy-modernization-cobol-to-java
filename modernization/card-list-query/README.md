# Card list query modernization

This standalone Java 17 module replaces the read-only card-list slice of
`COCRDLIC` (`app/cbl/COCRDLIC.cbl:985`, `:1003-1069`, `:1123-1374`) and the
`CBACT02C` sequential card-file reader (`app/cbl/CBACT02C.cbl:29-70`,
`:92-161`).  The physical model is `CARD-RECORD` from
`app/cpy/CVACT02Y.cpy:1-13`; the misspelled source field
`CARD-EXPIRAION-DATE` is exposed as `cardExpirationDate` and cited at
`app/cpy/CVACT02Y.cpy:9`.

## Technique

`CardRecordCodec` decodes and encodes the 150-byte fixed-width record with
CP037 (`IBM037`).  `CardMaster` stores records in a `NavigableMap` keyed by
the 16-character `CARD-NUM`; paging uses keyset `ceilingEntry` and
`lowerEntry`, never offsets or index-based page computation.  The 59 filler
bytes are retained raw and are never normalized.  This module deliberately
duplicates any EBCDIC helper needed by this slice; **consolidation into a
shared codec module** is follow-up work.

Run the tests with:

```text
mvn -f modernization/card-list-query/pom.xml test
```

Fixtures are read at test runtime from `../../app/data`, not copied into this
module.

## Parity evidence

The EBCDIC fixture `app/data/EBCDIC/AWS.M2.CARDDEMO.CARDDATA.PS` is 7,500
bytes, exactly 50 records of 150.  The tests decode and re-encode every byte
and require byte-identical output.  Pinned measurements are 50 records, 50
distinct account IDs, 50 active `Y` statuses and 0 other statuses.  Every
fixture filler is x`40` (59 EBCDIC spaces).  The ASCII file
`app/data/ASCII/carddata.txt` is used only for a character-field cross-check,
never as a parity source for signed fields.  The JCL corroboration is
`app/jcl/CARDFILE.jcl:54-55` for the 16-byte key at offset 0 and
`app/jcl/CARDFILE.jcl:85-88` for the 11-byte alternate key at offset 16;
the dictionary agrees at `DATA_DICTIONARY.md:56-67`.

## Reproduced quirks

* Account and card edits preserve BLANK / NOT_OK / ISVALID distinctions
  (`COCRDLIC.cbl:1003-1069`); account errors suppress the card error
  (`:1056`), and the exact legacy literals are tested.
* Forward filtering occurs while filling rows; excluded records consume no
  slot (`COCRDLIC.cbl:1382-1409`).
* A full forward page performs one extra read and its last anchor becomes the
  first record of the next page (`COCRDLIC.cbl:1191-1213`).
* A short page retains the last physical record read, even if filtering
  excluded it (`COCRDLIC.cbl:1233-1244`).
* A no-match page overwrites `NO MORE RECORDS TO SHOW` with
  `NO RECORDS FOUND FOR THIS SEARCH CONDITION.` (`COCRDLIC.cbl:121-122`,
  `:1216-1219`).
* Backward paging discards the first READPREV anchor
  (`COCRDLIC.cbl:1291-1307`) and reports the assembled generic file error at
  beginning-of-file because its second loop has no ENDFILE branch
  (`:1361-1368`).
* Screen number increments only when it is zero (`COCRDLIC.cbl:1177-1178`).
* `previousPageExists` is derived from `screenNum > 1`, corresponding to
  `CA-FIRST-PAGE` and the PF7 check (`COCRDLIC.cbl:238`, `:440-445`, `:903`).
  The migration brief's premise that 9100 derives this with an extra read is
  false; `9100-READ-BACKWARDS` does not derive it.

Synthetic tests are clearly labelled and are not fixture parity evidence:
one gives an account eight cards to force filtered multi-page traversal, one
uses non-`Y` status, and one uses x`F0` filler to prove verbatim preservation.

## Explicit exclusions

This is a read-only slice: no writes, CICS, BMS, COMMAREA, XCTL, or CSSETATY;
no `COCRDUPC` or `COCRDSLC`; no cross-reference, account, customer, or
transaction models; no REST, CLI, or generic copybook parser; and no AIX
build.  Out-of-scope `2250-EDIT-ARRAY` and unrelated message setup are not
ported.

## Deviations from the migration brief

1. The brief's first-record values were wrong: the measured account is
   `00000000050` and CVV is `747`.
2. Fixture filler is x`40`, not x`F0`; x`F0` is covered only by a synthetic
   round-trip test.
3. 9100 does not derive `previousPageExists`; it comes from the screen state
   described above.
