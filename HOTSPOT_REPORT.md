# CardDemo Modernization Hotspot Report

Per-program complexity metrics for all 44 COBOL programs in `app/cbl/`,
`app/app-authorization-ims-db2-mq/cbl/`, `app/app-transaction-type-db2/cbl/` and `app/app-vsam-mq/cbl/`,
followed by a consolidated ranking and a prioritized modernization order.

- [0. Counting conventions and limitations](#0-counting-conventions-and-limitations)
- [1. Lines of code](#1-lines-of-code)
- [2. Distinct copybooks referenced](#2-distinct-copybooks-referenced)
- [3. I/O operations](#3-io-operations)
- [4. Business-logic density](#4-business-logic-density)
- [5. Maximum nesting depth](#5-maximum-nesting-depth)
- [6. Inter-program dependencies](#6-inter-program-dependencies)
- [7. Consolidated ranking](#7-consolidated-ranking)
- [8. Prioritized modernization recommendation](#8-prioritized-modernization-recommendation)

## 0. Counting conventions and limitations

Read this before using the numbers; several are necessarily approximations of fixed-format COBOL.

| Metric | Definition used | Known limitation |
| :----- | :-------------- | :--------------- |
| LOC | Lines whose column 7 indicator is not `*` or `/` and whose columns 8–72 are not blank. Sequence numbers in columns 1–6 and 73–80 are ignored | A COBOL statement continued over several lines counts once per line, so verbose statements (long `EXEC CICS` blocks, `COPY … REPLACING`) inflate LOC. `COACTUPC` is the extreme case: 39 six-line `COPY CSSETATY REPLACING` blocks (`app/cbl/COACTUPC.cbl:3208`–`:3432`) contribute ≈230 LOC of pure macro expansion |
| Copybooks | Distinct names matched by `COPY name` or `COPY 'name'` (both forms occur — `app/cbl/COACTUPC.cbl:166` uses quotes, `:597` does not). Repeated `COPY` of the same member counts once | Includes the BMS map copybook (`COACTUP`) and the IBM-supplied `DFHAID`/`DFHBMSCA`, which are not application data. Subtract 3 from every online program for an "application copybook" count |
| I/O operations | `SELECT … ASSIGN` clauses + CICS file verbs (`READ`, `READNEXT`, `READPREV`, `WRITE`, `REWRITE`, `DELETE`, `STARTBR`, `ENDBR`, `RESETBR`, `UNLOCK`) + executable `EXEC SQL` blocks (`INCLUDE`/`DECLARE` excluded) | A batch `SELECT` is one static file, whereas a CICS or SQL count is one *call site*, so the two are not directly comparable. `SEND`/`RECEIVE MAP` are screen I/O and are excluded |
| Logic density | Count of `IF` + count of `EVALUATE` tokens, word-boundary matched | `END-IF`/`END-EVALUATE` are not counted as conditions. `WHEN` branches are not counted, so `EVALUATE`-heavy programs look simpler than they are |
| Max nesting | Running depth: `IF`/`EVALUATE` increment, `END-IF`/`END-EVALUATE` decrement, floored at 0 | Heuristic. Period-terminated `IF`s (no `END-IF`) are never popped, so depth can drift upward; conversely a program that always scopes with `END-IF` is measured accurately. Treat values as an upper bound and compare only within this table |
| Dependencies | In-degree + out-degree over the 62 unique program-to-program edges resolved in `DEPENDENCY_MAP.md` (`CALL`, `XCTL`, `LINK`, with dynamic targets resolved through `MOVE` chains and the menu tables) | Batch programs have degree 0 because they are coupled by datasets, not calls — for them use the dataset lineage in `DEPENDENCY_MAP.md` section 5 instead |

## 1. Lines of code

| # | Program | Directory | LOC | Comment |
| -: | :------ | :-------- | --: | :------ |
| 1 | `COACTUPC` | `app/cbl/` | 3368 | Account update; 2.1× the next largest. ≈230 LOC are the repeated `CSSETATY` attribute macros (`:3208`–`:3432`) |
| 2 | `COTRTLIC` | `app/app-transaction-type-db2/cbl/` | 1597 | Transaction-type list over Db2 |
| 3 | `COTRTUPC` | `app/app-transaction-type-db2/cbl/` | 1241 | Transaction-type add/update over Db2 |
| 4 | `COCRDUPC` | `app/cbl/` | 1194 | Card update |
| 5 | `COCRDLIC` | `app/cbl/` | 1093 | Card list with browse paging |
| 6 | `COPAUS0C` | `app/app-authorization-ims-db2-mq/cbl/` | 792 | Pending-authorization summary (IMS + Db2) |
| 7 | `CBSTM03A` | `app/cbl/` | 784 | Statement driver — largest batch program |
| 8 | `COPAUA0C` | `app/app-authorization-ims-db2-mq/cbl/` | 771 | MQ-driven authorization service |
| 9 | `COACTVWC` | `app/cbl/` | 703 | Account view |
| 10 | `COCRDSLC` | `app/cbl/` | 642 | Card detail |

Only 5 programs exceed 1000 LOC and all 5 are online update/list screens. The batch estate is small:
`CBACT02C`, `CBACT03C` and `CBCUS01C` are ~130 LOC each, and `COBSWAIT` is 13 LOC
(`app/cbl/COBSWAIT.cbl`).

## 2. Distinct copybooks referenced

| # | Program | Copybooks | Members |
| -: | :------ | --------: | :------ |
| 1 | `COACTUPC` | 18 | `COACTUP`, `COCOM01Y`, `COTTL01Y`, `CSDAT01Y`, `CSLKPCDY`, `CSMSG01Y`, `CSMSG02Y`, `CSSETATY`, `CSSTRPFY`, `CSUSR01Y`, `CSUTLDPY`, `CSUTLDWY`, `CVACT01Y`, `CVACT03Y`, `CVCRD01Y`, `CVCUS01Y`, `DFHAID`, `DFHBMSCA` |
| 2 | `COACTVWC` | 15 | as above minus `CSLKPCDY`/`CSSETATY`/`CSUTLDPY`/`CSUTLDWY`, plus `CVACT02Y` |
| 3 | `COPAUA0C` | 14 | MQ + authorization layouts (`CCPAURQY`, `CCPAURLY`, `CCPAUERY`, `CIPAUDTY`, `CIPAUSMY`, `IMSFUNCS`, …) |
| 3 | `COPAUS0C` | 14 | same family plus screen copybooks |
| 5 | `COTRTUPC` | 13 | Db2 (`CSDB2RWY`, `CSDB2RPY`) + screen |
| 5 | `COCRDSLC` | 13 | |
| 5 | `COCRDUPC` | 13 | |
| 8 | `COTRTLIC` | 11 | |
| 8 | `COCRDLIC` | 11 | |
| 10 | `COPAUS1C` | 10 | |
| 10 | `COBIL00C` | 10 | |
| 10 | `COTRN02C` | 10 | |

`COACTUPC` is the only program that pulls in **all four** validation copybooks at once — `CSLKPCDY`
(1318 lines of state/ZIP/area-code literals), `CSUTLDWY` + `CSUTLDPY` (the date-edit working storage and its
procedure paragraphs) and `CSSETATY` — which is why it is simultaneously the largest and the most coupled
program. Four programs use no copybooks at all: `CBSTM03B`, `COBTUPDT`, `CSUTLDTC`, `COBSWAIT`.

## 3. I/O operations

| # | Program | Total | `SELECT` | CICS file verbs | `EXEC SQL` |
| -: | :------ | ----: | -------: | --------------: | ---------: |
| 1 | `COTRTLIC` | 10 | 0 | 0 | 10 |
| 2 | `COCRDLIC` | 8 | 0 | 8 | 0 |
| 3 | `CBIMPORT` | 7 | 7 | 0 | 0 |
| 3 | `COACTUPC` | 7 | 0 | 7 | 0 |
| 3 | `COBIL00C` | 7 | 0 | 7 | 0 |
| 6 | `CBEXPORT` | 6 | 6 | 0 | 0 |
| 6 | `CBTRN01C` | 6 | 6 | 0 | 0 |
| 6 | `CBTRN02C` | 6 | 6 | 0 | 0 |
| 6 | `CBTRN03C` | 6 | 6 | 0 | 0 |
| 6 | `COTRN02C` | 6 | 0 | 6 | 0 |

Only four programs contain SQL at all — `COTRTLIC` (10), `COTRTUPC` (4), `COBTUPDT` (3), `COPAUS2C` (2) —
so the Db2 surface is tiny and well contained. `COBIL00C` is notable: a bill-payment screen that touches
seven files (account, xref, transaction master, …) inside one CICS task, i.e. a distributed-transaction
boundary in a converted system.

## 4. Business-logic density

`IF` + `EVALUATE` counts.

| # | Program | `IF` + `EVALUATE` | `IF` | `EVALUATE` | Density per 100 LOC |
| -: | :------ | ----------------: | ---: | ---------: | ------------------: |
| 1 | `COACTUPC` | 175 | 165 | 10 | 5.2 |
| 2 | `COTRTLIC` | 103 | 87 | 16 | 6.4 |
| 3 | `COCRDUPC` | 83 | 75 | 8 | 7.0 |
| 4 | `COCRDLIC` | 70 | 61 | 9 | 6.4 |
| 5 | `COTRTUPC` | 62 | 49 | 13 | 5.0 |
| 6 | `CBTRN02C` | 48 | 48 | 0 | 7.8 |
| 7 | `CBACT04C` | 43 | 43 | 0 | 7.8 |
| 8 | `CBTRN03C` | 40 | 38 | 2 | 7.3 |
| 8 | `COCRDSLC` | 40 | 36 | 4 | 6.2 |
| 10 | `COPAUS0C` | 36 | 25 | 11 | 4.5 |

The two highest *densities* in the table are batch: `CBTRN02C` and `CBACT04C` at 7.8 conditions per 100
LOC, with **zero** `EVALUATE` — all branching is nested `IF`, which is the hardest shape to translate
faithfully.

## 5. Maximum nesting depth

| # | Program | Max depth | Conditions | Comment |
| -: | :------ | --------: | ---------: | :------ |
| 1 | `COTRTLIC` | 6 | 103 | Deepest logic in the application |
| 1 | `COCRDUPC` | 6 | 83 | |
| 1 | `COCRDLIC` | 6 | 70 | Paging plus filter plus authorisation checks |
| 4 | `COACTUPC` | 5 | 175 | Broad rather than deep: many sibling field validations |
| 4 | `COCRDSLC` | 5 | 40 | |
| 6 | `CBACT04C` | 4 | 43 | Interest calculation |
| 6 | `CBTRN03C` | 4 | 40 | Report page/break handling |
| 6 | `COPAUS0C` | 4 | 36 | |
| 6 | `COTRN00C` | 4 | 34 | |
| 6 | `COUSR00C` | 4 | 33 | |

## 6. Inter-program dependencies

In-degree + out-degree over the resolved call graph (`DEPENDENCY_MAP.md` section 3).

| # | Program | Out | In | Total | Comment |
| -: | :------ | --: | -: | ----: | :------ |
| 1 | `COMEN01C` | 12 | 12 | 24 | The user-menu hub: 11 forward targets from `COMEN02Y.cpy` plus signon, and every one of them returns |
| 2 | `COADM01C` | 7 | 7 | 14 | Admin-menu hub, 6 targets from `COADM02Y.cpy` |
| 2 | `COSGN00C` | 2 | 12 | 14 | Highest in-degree: every screen's "exit" path returns to signon |
| 4 | `COTRN00C` | 3 | 2 | 5 | |
| 4 | `COTRN01C` | 3 | 2 | 5 | |
| 4 | `COUSR00C` | 4 | 1 | 5 | |
| 4 | `COPAUS0C` | 3 | 2 | 5 | |
| 8 | `COCRDLIC` | 3 | 1 | 4 | Only list screen that fans out to two children (`COCRDSLC`, `COCRDUPC`) |
| 8 | `CORPT00C` | 3 | 1 | 4 | Plus one non-program edge: submits the `TRANREPT` job through the internal reader (`app/cbl/CORPT00C.cbl:462`) |
| 8 | `COTRN02C` | 3 | 1 | 4 | |
| 8 | `COUSR02C` | 2 | 2 | 4 | |
| 8 | `COUSR03C` | 2 | 2 | 4 | |

The 18 programs driven by a scheduler, an MQ trigger or an IMS region score 0 here. Their real coupling is through datasets: by that measure the
hubs are `CBTRN02C` (6 datasets, 3 of them updated in place) and `CBACT04C` (6 datasets including an
alternate-index path) — see `DEPENDENCY_MAP.md` section 5.4.

## 7. Consolidated ranking

Score = sum of six metrics, each divided by its maximum across all programs (LOC, copybooks, I/O,
conditions, max nesting, dependency degree). Max possible 6.00. Equal weights are a deliberate choice:
they let a program qualify as a hotspot for any single reason (`COMEN01C` scores on connectivity alone,
`CBTRN02C` on I/O and conditions alone).

| # | Program | Score | LOC | Cpy | I/O | Cond | Nest | Deg | Class |
| -: | :------ | ----: | --: | --: | --: | ---: | ---: | --: | :---- |
| 1 | `COACTUPC` | 4.62 | 3368 | 18 | 7 | 175 | 5 | 2 | Online |
| 2 | `COTRTLIC` | 3.80 | 1597 | 11 | 10 | 103 | 6 | 3 | Online (Db2) |
| 3 | `COCRDLIC` | 3.30 | 1093 | 11 | 8 | 70 | 6 | 4 | Online |
| 4 | `COCRDUPC` | 2.98 | 1194 | 13 | 3 | 83 | 6 | 3 | Online |
| 5 | `COTRTUPC` | 2.47 | 1241 | 13 | 4 | 62 | 3 | 3 | Online (Db2) |
| 6 | `COPAUS0C` | 2.39 | 792 | 14 | 3 | 36 | 4 | 5 | Online (IMS/Db2) |
| 7 | `COTRN02C` | 2.33 | 614 | 10 | 6 | 27 | 4 | 4 | Online |
| 8 | `COCRDSLC` | 2.30 | 642 | 13 | 2 | 40 | 5 | 3 | Online |
| 9 | `COBIL00C` | 2.28 | 420 | 10 | 7 | 19 | 4 | 3 | Online |
| 10 | `COMEN01C` | 2.12 | 213 | 9 | 0 | 10 | 3 | 24 | Online |
| 11 | `COTRN00C` | 2.07 | 529 | 8 | 4 | 34 | 4 | 5 | Online |
| 11 | `COUSR00C` | 2.07 | 531 | 8 | 4 | 33 | 4 | 5 | Online |
| 13 | `COPAUA0C` | 1.98 | 771 | 14 | 3 | 31 | 3 | 0 | Online (MQ) |
| 14 | `COACTVWC` | 1.95 | 703 | 15 | 3 | 33 | 2 | 2 | Online |
| 15 | `CBTRN03C` | 1.93 | 545 | 5 | 6 | 40 | 4 | 0 | Batch |
| 16 | `CBACT04C` | 1.85 | 552 | 5 | 5 | 43 | 4 | 0 | Batch |
| 17 | `CBTRN02C` | 1.84 | 619 | 5 | 6 | 48 | 3 | 0 | Batch |
| 18 | `CBTRN01C` | 1.75 | 415 | 6 | 6 | 33 | 3 | 0 | Batch |
| 19 | `COSGN00C` | 1.72 | 172 | 8 | 1 | 7 | 3 | 14 | Online |
| 20 | `COADM01C` | 1.69 | 189 | 9 | 0 | 8 | 3 | 14 | Online |
| 21 | `COUSR02C` | 1.67 | 303 | 8 | 2 | 18 | 4 | 4 | Online |
| 22 | `COUSR03C` | 1.63 | 251 | 8 | 2 | 13 | 4 | 4 | Online |
| 23 | `COTRN01C` | 1.55 | 231 | 8 | 1 | 10 | 4 | 5 | Online |
| 24 | `COPAUS1C` | 1.44 | 461 | 10 | 0 | 22 | 3 | 3 | Online (IMS) |
| 25 | `CORPT00C` | 1.40 | 498 | 8 | 0 | 25 | 3 | 4 | Online |
| 26 | `CBIMPORT` | 1.39 | 337 | 6 | 7 | 15 | 1 | 0 | Batch |
| 27 | `CBSTM03A` | 1.31 | 784 | 4 | 2 | 20 | 3 | 1 | Batch |
| 27 | `CBEXPORT` | 1.31 | 396 | 6 | 6 | 16 | 1 | 0 | Batch |
| 29 | `COUSR01C` | 1.27 | 198 | 8 | 1 | 7 | 3 | 3 | Online |
| 30 | `CBACT01C` | 1.12 | 358 | 2 | 4 | 22 | 2 | 1 | Batch |
| 31 | `COACCT01` | 1.06 | 500 | 7 | 1 | 16 | 2 | 0 | Online (MQ) |
| 32 | `PAUDBLOD` | 0.93 | 251 | 4 | 2 | 17 | 2 | 0 | Batch (IMS) |
| 33 | `CBSTM03B` | 0.90 | 162 | 0 | 4 | 13 | 2 | 1 | Batch subprogram |
| 34 | `PAUDBUNL` | 0.88 | 207 | 4 | 2 | 11 | 2 | 0 | Batch (IMS) |
| 35 | `CODATE01` | 0.87 | 409 | 6 | 0 | 14 | 2 | 0 | Online (MQ) |
| 36 | `DBUNLDGS` | 0.78 | 198 | 6 | 0 | 9 | 2 | 0 | Batch (IMS) |
| 37 | `COPAUS2C` | 0.71 | 201 | 1 | 2 | 3 | 2 | 1 | Online (Db2) |
| 38 | `COBTUPDT` | 0.65 | 177 | 0 | 4 | 6 | 1 | 0 | Batch (Db2) |
| 39 | `CBPAUP0C` | 0.63 | 266 | 2 | 0 | 19 | 2 | 0 | Batch (IMS BMP) |
| 40 | `CBACT03C` | 0.59 | 130 | 1 | 1 | 11 | 2 | 0 | Batch |
| 40 | `CBCUS01C` | 0.59 | 130 | 1 | 1 | 11 | 2 | 0 | Batch |
| 40 | `CBACT02C` | 0.59 | 129 | 1 | 1 | 11 | 2 | 0 | Batch |
| 43 | `CSUTLDTC` | 0.29 | 114 | 0 | 0 | 1 | 1 | 2 | Batch subprogram |
| 44 | `COBSWAIT` | 0.00 | 13 | 0 | 0 | 0 | 0 | 0 | Batch utility |

## 8. Prioritized modernization recommendation

### Wave 1 — extract the shared assets first (enables everything else)

Nothing in the ranking should be converted before the cross-cutting copybooks are turned into services,
because 20+ programs depend on them and converting them per-program guarantees divergence.

1. **`CSLKPCDY.cpy` (1318 lines of literals) → reference data.** It is `88`-level condition lists for
   ~490 phone area codes (`CSLKPCDY.cpy:30`), ~410 general-purpose codes (`:521`), 56 US state codes
   (`:1013`) and ~240 state/ZIP-prefix combinations (`:1073`). Moving it to tables/config removes the
   single largest chunk of "logic" in the estate and de-risks `COACTUPC` before it is touched.
2. **Date validation `CSUTLDWY` + `CSUTLDPY` + `CSUTLDTC` → one date service.** `CSUTLDTC` is only 114 LOC
   and its real work is a `CALL "CEEDAYS"` (`app/cbl/CSUTLDTC.cbl:116`); `CSUTLDPY` supplies the
   `EDIT-DATE-CCYYMMDD` / `EDIT-DAY-MONTH-YEAR` / `EDIT-DATE-OF-BIRTH` paragraphs by inclusion. Replacing
   the LE dependency once is far cheaper than 8 times.
3. **`COCOM01Y.cpy` COMMAREA → an explicit session/navigation model.** 20 programs move a program name into
   `CDEMO-TO-PROGRAM` (`app/cpy/COCOM01Y.cpy:24`) and `XCTL` to it; `CDEMO-PGM-CONTEXT` (`:29`) is the
   pseudo-conversational enter/re-enter flag. This is the framework decision every screen conversion
   inherits.
4. **`CSUSR01Y.cpy` security record → a real identity provider.** `SEC-USR-PWD` is `PIC X(08)` compared as
   clear text at signon (`app/cpy/CSUSR01Y.cpy:21`); do not port that behaviour. This is a small change with a large risk reduction.

### Wave 2 — the batch pipeline core

`CBTRN02C` (rank 17 overall, but rank 6 on conditions and joint rank 6 on I/O) is the highest-value
functional target despite its modest score:

- It is the **only** posting program in the pipeline — `CBTRN01C` is executed by no JCL in the repository
  (`DEPENDENCY_MAP.md` section 7), so all posting rules live in one 619-LOC program with 48 nested `IF`s and
  no `EVALUATE`.
- It updates three datasets in place (`TRANSACT`, `ACCTDATA`, `TCATBALF` — `app/jcl/POSTTRAN.jcl:28`,
  `:39`, `:41`) with no transactional boundary other than job success, so it defines the consistency
  semantics the whole modernized system must honour.
- Its rejects go to `DALYREJS(+1)` (`:34`), which nothing in the repository consumes — a gap worth closing
  during conversion rather than replicating.

Convert it together with `CBACT04C` (interest, 43 `IF`s, depth 4, reads the xref alternate-index path,
`app/jcl/INTCALC.jcl:31`) and `CBTRN03C` (reporting). These three plus `CBSTM03A`/`CBSTM03B` are the
business core; they are also the easiest to verify, because each has a file-in/file-out contract that can be
tested by comparing output datasets. Note `CBACT04C`'s hard-coded run date `PARM='2022071800'`
(`app/jcl/INTCALC.jcl:22`) must become a real parameter, and `CBSTM03B` (162 LOC, no copybooks, called from
13 sites in `CBSTM03A`) is a hand-rolled file-access layer that should be deleted rather than translated.

### Wave 3 — `COACTUPC`, the single biggest online risk

Rank 1 on three of six metrics: 3368 LOC, 18 copybooks, 175 conditions. It is also the reason Wave 1 comes
first — 4 of its 18 copybooks are the validation/utility members listed above. Recommended sequence:
extract the field validations against the Wave 1 reference-data service, delete the 39 repeated
`CSSETATY REPLACING` attribute macros (`:3208`–`:3432`) in favour of a generated presentation layer, then
convert the remaining update logic. Expect this to be the longest single item in the programme; treat it as
several sessions of work, not one.

Then `COCRDUPC`, `COCRDLIC`, `COCRDSLC` as one card-management slice (they share `CVACT02Y`, `CVCRD01Y` and
the browse/paging idiom, and `COCRDLIC` is the only screen that fans out to two children), followed by
`COACTVWC` which becomes nearly free once `COACTUPC` is done (15 copybooks but depth 2 and only 33
conditions — it is a read-only projection of the same data).

### Wave 4 — hubs and sub-applications

- **`COMEN01C` / `COADM01C` / `COSGN00C`** (degrees 24/14/14, but only 213/189/172 LOC) are structurally
  central and logically trivial. Once the Wave 1 navigation model exists, all three collapse into
  configuration plus a dispatch, and doing them early gives every later screen a working shell. They are
  low-risk, high-leverage.
- **Db2 slice `COTRTLIC` + `COTRTUPC` + `COBTUPDT`** (ranks 2, 5, 38) shares the
  `CSDB2RPY`/`CSDB2RWY` error-handling copybooks. `COTRTLIC` is
  rank 2 overall and has the deepest nesting (6) of any program, but the slice is self-contained: it owns
  its own tables and reaches the rest of the system only via `COADM01C` (17 of the 19 executable SQL
  statements in the estate live here; the other 2 are in `COPAUS2C`). Good candidate to convert as an
  independent service, and its Db2 tables are already relational — no data remodelling needed.
- **Authorization slice** `COPAUS0C`/`COPAUS1C`/`COPAUS2C`/`COPAUA0C`/`CBPAUP0C` + the IMS load/unload
  utilities. Highest technology risk (IMS DL/I + MQ + Db2 + CICS in one module) but moderate logic. Do it
  last, and use the fact that `LOADPADB`/`UNLDPADB` round-trip the database through flat files
  (`DEPENDENCY_MAP.md` section 5.3) as the data-migration path. Note this slice holds the only
  `EXEC CICS LINK` in the application (`COPAUS1C.cbl:248`), so it needs a synchronous call, not a redirect.

### Low-risk quick wins

Useful early to build pipeline and test-harness confidence with almost no business risk:

| Program | Score | Why it is easy |
| :------ | ----: | :------------- |
| `COBSWAIT` | 0.00 | 13 LOC, one `CALL 'MVSWAIT'` (`app/cbl/COBSWAIT.cbl:38`) into HLASM (`app/asm/MVSWAIT.asm:17`). In a modernized scheduler it disappears entirely, assembler included |
| `CBACT02C`, `CBACT03C`, `CBCUS01C` | 0.59 | ~130 LOC each, 1 copybook, 1 file, read-and-print. Ideal first conversions and ideal record-layout regression tests for `CVACT02Y`, `CVACT03Y`, `CVCUS01Y` |
| `CSUTLDTC` | 0.29 | 114 LOC, no copybooks; becomes a one-method date service (see Wave 1) |
| `COPAUS2C` | 0.71 | 201 LOC, 1 copybook, 2 SQL statements, 3 conditions — the smallest Db2 program, a good pilot for the SQL conversion pattern |
| `COUSR01C` | 1.27 | 198 LOC, 7 conditions; simplest CRUD screen, a good pilot for the Wave 1 navigation framework |
| `CBACT01C` | 1.12 | 358 LOC, but note it `CALL`s `COBDATFT` (`app/cbl/CBACT01C.cbl:231`), which is **HLASM, not COBOL** (`app/asm/COBDATFT.asm:17`) — that date formatter has to be reimplemented by hand rather than translated |

### What the metrics say overall

The estate is **online-heavy in complexity and batch-heavy in risk**. Nine of the top ten consolidated
ranks are CICS programs, yet the programs that own the money — `CBTRN02C`, `CBACT04C` — sit at ranks 17 and
16 with the highest condition *density* (7.8 per 100 LOC) and zero `EVALUATE`. A conversion driven purely
by size would start in the wrong place: start with the shared copybooks (Wave 1), prove the toolchain on
the 130-LOC readers, then take the posting core before the big screens.
