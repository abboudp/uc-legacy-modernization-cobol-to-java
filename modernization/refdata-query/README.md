# Slice 2 — transaction-type reference data read model

Read half of the transaction-type / transaction-type-category reference data slice: the browse
(query) logic of `app/app-transaction-type-db2/cbl/COTRTLIC.cbl` reimplemented as a plain
Java/JDBC query service over a relational schema. No CICS, no BMS, no HTTP, no framework.

## Run it

```bash
mvn -f modernization/refdata-query/pom.xml test
```

Java 17, Maven, JUnit 5, H2 in-process for tests. Standalone POM, no parent, no dependency on the
sibling slices.

## What COBOL this replaces

| Source | What is taken |
| --- | --- |
| `app/app-transaction-type-db2/cbl/COTRTLIC.cbl` | the two cursor declarations (`:338-352`, `:354-368`), `8000-READ-FORWARD` (`:1603-1726`), `8100-READ-BACKWARDS` (`:1727-1799`), the paging COMMAREA (`:397-410`), the browse messages (`:253-256`, `:1532-1549`) and the row count of `9100-CHECK-FILTERS` (`:1803-1806`) |
| `app/app-transaction-type-db2/cbl/COTRTUPC.cbl` | only the shape of the singleton read `9100-GET-TRANSACTION-TYPE` (`:1475-1494`) — none of its update logic |
| `app/app-transaction-type-db2/dcl/DCLTRTYP.dcl`, `DCLTRCAT.dcl` | column types and host-variable widths |
| `app/app-transaction-type-db2/ctl/DB2CREAT.ctl` (run by `jcl/CREADB21.jcl:59`), `ddl/TRNTYPE.ddl`, `ddl/TRNTYCAT.ddl`, `ddl/XTRNTYPE.ddl`, `ddl/XTRNTYCAT.ddl` | the target schema |
| `app/app-transaction-type-db2/jcl/TRANEXTR.jcl:76-116` | category key order, and the 60-byte record layout of the fixtures |
| `app/cpy/CVTRA03Y.cpy`, `app/cpy/CVTRA04Y.cpy` | fixture field layouts |

Everything lives in `src/main/java/com/carddemo/refdata/`: `TransactionTypeQueryService` (the
interface), `JdbcTransactionTypeQueryService` (the implementation), `BrowsePage`/`BrowseRow`/
`BrowseMessage` (the paging state the COMMAREA carries between screens), the two row records, and
`ReferenceDataLoader` (fixture reader + schema creator).

## Schema

`src/main/resources/schema.sql` — mainframe names kept verbatim, primary keys and the
`ON DELETE RESTRICT` foreign key of `DB2CREAT.ctl:96-99` kept. Db2 physical clauses
(`IN CARDDEMO.CARDSPC1`, `USING STOGROUP`, `BUFFERPOOL`, `CCSID EBCDIC`, `ERASE`/`CLOSE`,
`GRANT ... TO PUBLIC`) are storage and encoding directives with no logical content for a read model
and are dropped. The two unique indexes are dropped because they duplicate the primary keys.

Host variable vs column width, where they differ:

- `TR_DESCRIPTION` is `VARCHAR(50)` (`DCLTRTYP.dcl:30`), but the screen's filter field
  `WS-TYPE-DESC-FILTER` is `PIC X(52)` (`COTRTLIC.cbl:278`) — two characters wider than the column.
  It is a filter buffer, not a row buffer, and filtering is a screen concern, so it is not in this
  slice.
- `TR_TYPE`/`TRC_TYPE_CODE` are `CHAR(2)` and the host variables are `PIC X(2)` — same width. The
  screen redefines its input as `PIC 9(02)` (`COTRTLIC.cbl:312`, `:380`) for editing only; the stored
  semantics are character, so the Java type is `String`.
- `TRC_TYPE_CATEGORY` is `CHAR(4)` and the DCLGEN host variable is `PIC X(4)`
  (`DCLTRCAT.dcl:42-43`), while the VSAM copybook declares `PIC 9(04)` (`CVTRA04Y.cpy:7`). It is
  kept as a `String` so the leading zeroes the key is stored and compared with survive.
- The DCLGEN varying-character host variables carry an explicit length halfword
  (`DCL-TR-DESCRIPTION-LEN`, `DCLTRTYP.dcl:42-43`); JDBC handles that, so it does not surface.

## Paging: keyset, not OFFSET

The COBOL browse is pseudo-conversational. The cursor is opened and closed inside a single CICS
interaction (`9400-OPEN-FORWARD-CURSOR` at `COTRTLIC.cbl:1942`, `9450-CLOSE-FORWARD-CURSOR` at
`:1970`) and the only thing that survives to the next screen is a **key**: `WS-CA-FIRST-TR-CODE`
and `WS-CA-LAST-TR-CODE` (`:397-401`). Each page is re-derived from that key:

- **forward** (`C-TR-TYPE-FORWARD`, `:343`, `:351`): `WHERE TR_TYPE >= :WS-START-KEY ORDER BY
  TR_TYPE`, fetch up to `WS-MAX-SCREEN-LINES` rows (`:60`, 7 on the real screen), then fetch **one
  more** row to learn whether a next page exists; that peeked row's key becomes the anchor of the
  next page (`:1657-1673`). So `BrowsePage.lastKey()` is the first row of the *next* page, not the
  last row of this one.
- **backward** (`C-TR-TYPE-BACKWARD`, `:359`, `:367`): `WHERE TR_TYPE < :WS-START-KEY ORDER BY
  TR_TYPE DESC`, filling the screen array from the bottom up so the page is ascending again
  (`:1762-1774`), and adopting the anchor as the new forward anchor (`:1731`).
- **end of data** is `SQLCODE +100` on a fetch: it sets `CA-NEXT-PAGE-NOT-EXISTS` and, if PF8 was
  pressed, `WS-MESG-NO-MORE-RECORDS` = *"No more pages for these search conditions"*
  (`:1674-1680` on the peek fetch, `:1694-1701` in the main loop). A further PF8 latches
  `CA-LAST-PAGE-SHOWN` (`:1541-1549`) and reports *"No more pages to display"* (`:1536-1540`). PF7
  on screen 1 never reads backwards at all: *"No previous pages to display"* (`:1532-1535`,
  `:726-734`). An empty result on screen 1 gives *"No records found for this search condition."*
  (`:1702-1705`).

Why not `LIMIT/OFFSET`: an offset counts rows, a key names one. Between two screens of the same
browse the row set can change — the very same screen deletes rows (`9300-DELETE-RECORD`,
`:1900-1903`) and `COTRTUPC` inserts them (`COTRTUPC.cbl:1598`). Delete a row the user has already
seen and `OFFSET n` skips an unseen row; insert one before the current page and `OFFSET n` shows a
row twice. Keyset paging is unaffected in both cases, because the anchor is a key that either still
exists or is simply passed by. `OffsetPagingWouldDivergeTest` demonstrates both divergences against
the seeded data. Offset paging also degrades on large tables (the server must count and discard the
skipped rows) whereas the keyset predicate is an index seek — that is why the mainframe browse was
written this way in the first place.

Two source quirks are reproduced rather than tidied up, because parity is the point:

- After a **short** page (fewer rows than the screen holds), the forward anchor is *spaces*, not the
  last row read: the host area is re-initialised before every fetch (`:1624`) and the `+100` branch
  moves that blanked value into `WS-CA-LAST-TR-CODE` (`:1696-1697`). See
  `KeysetPagingParityTest.shortPageLeavesTheForwardAnchorBlank`. It is harmless in the program
  because `CA-NEXT-PAGE-NOT-EXISTS` stops the anchor from ever being used.
- The **backward** fetch loop has no `SQLCODE +100` branch, so running out of rows before the page
  is full falls into `WHEN OTHER` and is reported as a Db2 error (`:1776-1790`), not as end of data.
  Modelled as `BrowseMessage.BACKWARD_CURSOR_ERROR`; it is unreachable in a normal browse because
  PF7 is guarded on screen 1 (`:780-781`).

## Parity evidence

Tests are seeded from the repository's own ASCII fixtures — `app/data/ASCII/trantype.txt` and
`app/data/ASCII/trancatg.txt` (the lowercase ASCII copies of `AWS.M2.CARDDEMO.TRANTYPE.PS` /
`.TRANCATG.PS`; the uppercase dataset names given in the task do not exist in the tree). Using the
ASCII copies means this slice needs no EBCDIC codec, which slice 1 owns.

- `ReferenceDataFixtureTest` — pins the row counts (7 types, 18 categories), asserts every fixture
  record is exactly 60 bytes (`RECLN = 60`, `CVTRA03Y.cpy:2`, `CVTRA04Y.cpy:2`), and checks the
  parsed fields against the copybook layouts including the 4-digit category's leading zeroes.
- `KeysetPagingParityTest` — pages forward through the whole table and back again with page size 3
  and asserts the backward pages are the exact inverse of the forward pages; that page 2 starts on
  the anchor page 1 peeked; that the first page is stable across re-reads; that PF7-then-PF8 returns
  to the same page; that the order is the cursor's `ORDER BY TR_TYPE`; and each end-of-data /
  no-more-pages / no-previous-pages / no-records-found message with its COBOL literal.
- `SingleRowLookupTest` — key-equality reads for a type and for a type/category pair, `+100`
  mapped to `Optional.empty()`, and category order.
- `OffsetPagingWouldDivergeTest` — the `OFFSET` counter-example described above.

## Where the source contradicted the plan

No COBOL program in the estate reads `CARDDEMO.TRANSACTION_TYPE_CATEGORY`: the browse screen reads
only `TRANSACTION_TYPE`, and the category table is touched solely by the unload job
(`TRANEXTR.jcl:107-116`) and the loader (`ctl/DB2LTCAT.ctl`). The type/category read model here
therefore follows the table's primary key and that job's `ORDER BY TRC_TYPE_CODE,
TRC_TYPE_CATEGORY`, not a program's access path. Likewise, the table-creation evidence is not in
the JCL itself but in the SYSIN member `ctl/DB2CREAT.ctl` that `jcl/CREADB21.jcl:59` runs.

## Not in this slice

- The update/insert/delete path — `COTRTUPC`, `COBTUPDT`. **Follow-up slice: transaction-type
  reference data update path.**
- The VSAM replica emitter for `TRANTYPE`/`TRANCATG` (`TRANEXTR.jcl`). **Follow-up slice:
  transaction-type reference data VSAM replica emitter.**
- The screen's filter predicates (`WS-EDIT-TYPE-FLAG`, `WS-TYPE-DESC-FILTER`, `LIKE TRIM(...)`),
  BMS/screen rendering, row-selection actions, authentication, HTTP/REST, any Db2 driver or
  Db2-specific SQL dialect work, EBCDIC decoding.
- Consolidation of helpers duplicated with the sibling slices — a follow-up PR.
