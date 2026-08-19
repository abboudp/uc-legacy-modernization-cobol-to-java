# CardDemo Data Dictionary

Field-level dictionary for every copybook in `app/cpy/` (30 members) and in the sub-application copybook
libraries `app/app-authorization-ims-db2-mq/cpy/` (9 members) and `app/app-transaction-type-db2/cpy/`
(2 members). Copybooks are grouped by business entity. Every row cites `path:line`.

Derived-type conventions used below:

| PIC / USAGE | Derived type | Notes |
| :---------- | :----------- | :---- |
| `X(n)`, `XXX` | alphanumeric | EBCDIC characters; blank/`LOW-VALUES` used as "not set" |
| `9(n)` | unsigned zoned decimal | display numeric, one byte per digit |
| `S9(n)` | signed zoned decimal | sign in the last byte's zone unless `SEPARATE` |
| `S9(n)V99` | signed zoned decimal, implied 2-decimal scale | monetary value, no stored decimal point |
| `… COMP-3` | packed decimal | `ceil((digits+1)/2)` bytes |
| `… COMP` / `COMP-5` / `BINARY` | binary halfword/fullword | big-endian on z/Architecture |
| `-ZZZ,ZZZ,ZZZ.ZZ`, `+9(10).99` | numeric-edited | print/interchange only, never arithmetic source |

A `FILLER` at the end of each master record is reserve space that pads the record to the VSAM-defined
length; it is significant for byte-offset compatibility during migration.

- [Account](#1-account)
- [Card](#2-card)
- [Card cross-reference](#3-card-cross-reference)
- [Customer](#4-customer)
- [Transaction and related reference data](#5-transaction-and-related-reference-data)
- [Security](#6-security)
- [Common / screen / utility](#7-common--screen--utility)
- [Authorization sub-application](#8-authorization-sub-application)
- [Transaction-type Db2 sub-application](#9-transaction-type-db2-sub-application)
- [Cross-copybook observations](#10-cross-copybook-observations)

## 1. Account

### `app/cpy/CVACT01Y.cpy` — `ACCOUNT-RECORD` (ACCTDAT / `…ACCTDATA.VSAM.KSDS`, key `ACCT-ID`, 300 bytes)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `ACCOUNT-RECORD` (`CVACT01Y.cpy:4`) | group | group | Account master record | Read/updated by `CBACT04C`, `CBTRN02C`, `COACTUPC`, `COACTVWC`, `COBIL00C`, `COTRN02C` |
| 05 | `ACCT-ID` (`:5`) | `9(11)` | unsigned zoned, 11 digits | Primary account number (KSDS key) | Must be numeric and non-zero; screens edit it via `CC-ACCT-ID-N` (`CVCRD01Y.cpy:36`) |
| 05 | `ACCT-ACTIVE-STATUS` (`:6`) | `X(01)` | alphanumeric | Account active flag | Convention `Y`/`N`; enforced in program code, no 88-levels in the copybook |
| 05 | `ACCT-CURR-BAL` (`:7`) | `S9(10)V99` | signed zoned, 2 dp | Current outstanding balance | Updated by posting (`CBTRN02C`), interest (`CBACT04C`) and bill pay (`COBIL00C`) |
| 05 | `ACCT-CREDIT-LIMIT` (`:8`) | `S9(10)V99` | signed zoned, 2 dp | Purchase credit limit | Posting compares `ACCT-CREDIT-LIMIT` against balance + transaction amount |
| 05 | `ACCT-CASH-CREDIT-LIMIT` (`:9`) | `S9(10)V99` | signed zoned, 2 dp | Cash-advance sub-limit | |
| 05 | `ACCT-OPEN-DATE` (`:10`) | `X(10)` | alphanumeric date | Account open date | Character `YYYY-MM-DD`; validated by `CSUTLDPY`/`CSUTLDWY` logic in `COACTUPC` |
| 05 | `ACCT-EXPIRAION-DATE` (`:11`) | `X(10)` | alphanumeric date | Account expiry date | Name misspelled in the source ("EXPIRAION"); preserve or rename deliberately |
| 05 | `ACCT-REISSUE-DATE` (`:12`) | `X(10)` | alphanumeric date | Last card-reissue date | |
| 05 | `ACCT-CURR-CYC-CREDIT` (`:13`) | `S9(10)V99` | signed zoned, 2 dp | Credits posted in the current cycle | Incremented for credit transactions |
| 05 | `ACCT-CURR-CYC-DEBIT` (`:14`) | `S9(10)V99` | signed zoned, 2 dp | Debits posted in the current cycle | |
| 05 | `ACCT-ADDR-ZIP` (`:15`) | `X(10)` | alphanumeric | Account ZIP code | Validated against `CSLKPCDY` state/ZIP tables in `COACTUPC` |
| 05 | `ACCT-GROUP-ID` (`:16`) | `X(10)` | alphanumeric | Disclosure/pricing group | Joins to `DIS-ACCT-GROUP-ID` (`CVTRA02Y.cpy:6`); `CBACT04C` falls back to a `DEFAULT` group |
| 05 | `FILLER` (`:17`) | `X(178)` | reserve | Record padding to 300 bytes | |

## 2. Card

### `app/cpy/CVACT02Y.cpy` — `CARD-RECORD` (CARDDAT / `…CARDDATA.VSAM.KSDS`, key `CARD-NUM`, AIX `CARDAIX` on `CARD-ACCT-ID`, 150 bytes)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `CARD-RECORD` (`CVACT02Y.cpy:4`) | group | group | Card master record | |
| 05 | `CARD-NUM` (`:5`) | `X(16)` | alphanumeric | 16-digit card number (KSDS key) | Held as characters; edited numerically via `CC-CARD-NUM-N` (`CVCRD01Y.cpy:39`) |
| 05 | `CARD-ACCT-ID` (`:6`) | `9(11)` | unsigned zoned | Owning account | Alternate-index key (`CARDAIX`), used by `COCRDLIC` browse |
| 05 | `CARD-CVV-CD` (`:7`) | `9(03)` | unsigned zoned | Card verification value | 3 digits; `COCRDUPC` edits for numeric |
| 05 | `CARD-EMBOSSED-NAME` (`:8`) | `X(50)` | alphanumeric | Name embossed on the card | |
| 05 | `CARD-EXPIRAION-DATE` (`:9`) | `X(10)` | alphanumeric date | Card expiry date | Same misspelling as the account field |
| 05 | `CARD-ACTIVE-STATUS` (`:10`) | `X(01)` | alphanumeric | Card active flag | `Y`/`N` by convention |
| 05 | `FILLER` (`:11`) | `X(59)` | reserve | Record padding to 150 bytes | |

### `app/cpy/CVCRD01Y.cpy` — `CC-WORK-AREAS` (online card/account work area)

Shared working-storage used by every card and account screen for AID decoding, navigation targets, messages
and numeric editing of the three key identifiers.

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `CC-WORK-AREAS` (`CVCRD01Y.cpy:1`) | group | group | Card-module work area | |
| 05 | `CC-WORK-AREA` (`:2`) | group | group | | |
| 10 | `CCARD-AID` (`:3`) | `X(5)` | alphanumeric | Normalised attention identifier | 19 condition names: `CCARD-AID-ENTER` `'ENTER'` (`:4`), `-CLEAR` `'CLEAR'` (`:5`), `-PA1` `'PA1 '` (`:6`), `-PA2` `'PA2 '` (`:7`), `-PFK01`…`-PFK12` `'PFK01'`…`'PFK12'` (`:8`–`:19`). Set by `CSSTRPFY.cpy:21` from `EIBAID`, incl. PF13–PF24 folding onto PFK01–PFK12 |
| 10 | `CCARD-NEXT-PROG` (`:21`) | `X(8)` | alphanumeric | Next program for `XCTL` | Dynamic-routing variable (see `DEPENDENCY_MAP.md`) |
| 10 | `CCARD-NEXT-MAPSET` (`:23`) | `X(7)` | alphanumeric | Next BMS mapset | |
| 10 | `CCARD-NEXT-MAP` (`:24`) | `X(7)` | alphanumeric | Next BMS map | |
| 10 | `CCARD-ERROR-MSG` (`:28`) | `X(75)` | alphanumeric | Error message line | |
| 10 | `CCARD-RETURN-MSG` (`:29`) | `X(75)` | alphanumeric | Informational message line | `88 CCARD-RETURN-MSG-OFF VALUE LOW-VALUES` (`:30`) — "no message pending" |
| 10 | `CC-ACCT-ID` (`:34`) | `X(11)`, `VALUE SPACES` | alphanumeric | Account id as keyed on screen | Blank-initialised so "not entered" is distinguishable from zero |
| 10 | `CC-ACCT-ID-N` (`:36`) | `9(11)` **REDEFINES** `CC-ACCT-ID` | unsigned zoned | Numeric view of the same 11 bytes | Only valid after an `IS NUMERIC` test |
| 10 | `CC-CARD-NUM` (`:37`) | `X(16)`, `VALUE SPACES` | alphanumeric | Card number as keyed | |
| 10 | `CC-CARD-NUM-N` (`:39`) | `9(16)` **REDEFINES** `CC-CARD-NUM` | unsigned zoned | Numeric view | |
| 10 | `CC-CUST-ID` (`:40`) | `X(09)`, `VALUE SPACES` | alphanumeric | Customer id as keyed | |
| 10 | `CC-CUST-ID-N` (`:42`) | `9(9)` **REDEFINES** `CC-CUST-ID` | unsigned zoned | Numeric view | |

## 3. Card cross-reference

### `app/cpy/CVACT03Y.cpy` — `CARD-XREF-RECORD` (CCXREF / `…CARDXREF.VSAM.KSDS`, key `XREF-CARD-NUM`, AIX `CXACAIX` on `XREF-ACCT-ID`, 50 bytes)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `CARD-XREF-RECORD` (`CVACT03Y.cpy:4`) | group | group | Card ↔ customer ↔ account join record | The hub record of the data model: every posting and online flow resolves through it |
| 05 | `XREF-CARD-NUM` (`:5`) | `X(16)` | alphanumeric | Card number (KSDS key) | |
| 05 | `XREF-CUST-ID` (`:6`) | `9(09)` | unsigned zoned | Customer id | Joins `CUST-ID` (`CVCUS01Y.cpy:5`) |
| 05 | `XREF-ACCT-ID` (`:7`) | `9(11)` | unsigned zoned | Account id | Alternate-index key (`CXACAIX`); used by `CBACT04C` via `XREFFIL1` and by `COBIL00C`/`COTRN02C` |
| 05 | `FILLER` (`:8`) | `X(14)` | reserve | Padding to 50 bytes | |

## 4. Customer

### `app/cpy/CVCUS01Y.cpy` — `CUSTOMER-RECORD` (CUSTDAT / `…CUSTDATA.VSAM.KSDS`, key `CUST-ID`, 500 bytes)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `CUSTOMER-RECORD` (`CVCUS01Y.cpy:4`) | group | group | Customer master record | |
| 05 | `CUST-ID` (`:5`) | `9(09)` | unsigned zoned | Customer id (KSDS key) | |
| 05 | `CUST-FIRST-NAME` (`:6`) | `X(25)` | alphanumeric | First name | `COACTUPC` rejects blanks / non-alphabetic |
| 05 | `CUST-MIDDLE-NAME` (`:7`) | `X(25)` | alphanumeric | Middle name | Optional |
| 05 | `CUST-LAST-NAME` (`:8`) | `X(25)` | alphanumeric | Last name | Mandatory |
| 05 | `CUST-ADDR-LINE-1` (`:9`) | `X(50)` | alphanumeric | Address line 1 | Mandatory |
| 05 | `CUST-ADDR-LINE-2` (`:10`) | `X(50)` | alphanumeric | Address line 2 | Optional |
| 05 | `CUST-ADDR-LINE-3` (`:11`) | `X(50)` | alphanumeric | Address line 3 / city | |
| 05 | `CUST-ADDR-STATE-CD` (`:12`) | `X(02)` | alphanumeric | US state code | Validated against `VALID-US-STATE-CODE` (`CSLKPCDY.cpy:1013`) |
| 05 | `CUST-ADDR-COUNTRY-CD` (`:13`) | `X(03)` | alphanumeric | Country code | `USA` in sample data |
| 05 | `CUST-ADDR-ZIP` (`:14`) | `X(10)` | alphanumeric | ZIP code | State + first two ZIP digits validated by `VALID-US-STATE-ZIP-CD2-COMBO` (`CSLKPCDY.cpy:1073`) |
| 05 | `CUST-PHONE-NUM-1` (`:15`) | `X(15)` | alphanumeric | Primary phone | Area code validated by `VALID-PHONE-AREA-CODE` (`CSLKPCDY.cpy:30`); stored as `(999)999-9999` |
| 05 | `CUST-PHONE-NUM-2` (`:16`) | `X(15)` | alphanumeric | Secondary phone | Same edit |
| 05 | `CUST-SSN` (`:17`) | `9(09)` | unsigned zoned | Social security number | `COACTUPC` edits digit groups; **PII** |
| 05 | `CUST-GOVT-ISSUED-ID` (`:18`) | `X(20)` | alphanumeric | Government id reference | **PII** |
| 05 | `CUST-DOB-YYYY-MM-DD` (`:19`) | `X(10)` | alphanumeric date | Date of birth | Validated by `EDIT-DATE-OF-BIRTH` (`CSUTLDPY.cpy:341`), which also rejects future dates |
| 05 | `CUST-EFT-ACCOUNT-ID` (`:20`) | `X(10)` | alphanumeric | EFT/bank account reference | |
| 05 | `CUST-PRI-CARD-HOLDER-IND` (`:21`) | `X(01)` | alphanumeric | Primary cardholder indicator | `Y`/`N` |
| 05 | `CUST-FICO-CREDIT-SCORE` (`:22`) | `9(03)` | unsigned zoned | FICO score | `COACTUPC` enforces the 300–850 range in code |
| 05 | `FILLER` (`:23`) | `X(168)` | reserve | Padding to 500 bytes | |

### `app/cpy/CUSTREC.cpy` — `CUSTOMER-RECORD` (statement-program variant)

Byte-for-byte the same layout as `CVCUS01Y`, used only by `CBSTM03A.CBL`. The single difference is the
date-of-birth field name:

| Level | Field | PIC | Derived type | Difference vs. `CVCUS01Y` |
| ----: | :---- | :-- | :----------- | :------------------------ |
| 05 | `CUST-DOB-YYYYMMDD` (`CUSTREC.cpy:19`) | `X(10)` | alphanumeric date | Named `CUST-DOB-YYYY-MM-DD` in `CVCUS01Y.cpy:19`. Same offset/length; the name implies a different edit mask than the data actually carries |

All other fields (`CUST-ID` `9(09)` `:5` … `FILLER` `X(168)` `:23`) are identical in level, name, PIC and
order to the table above. Two copybooks defining the same `01 CUSTOMER-RECORD` name is a duplication risk:
a converted codebase should keep exactly one customer type.

## 5. Transaction and related reference data

### `app/cpy/CVTRA01Y.cpy` — `TRAN-CAT-BAL-RECORD` (TCATBALF / `…TCATBALF.VSAM.KSDS`, 50 bytes)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `TRAN-CAT-BAL-RECORD` (`CVTRA01Y.cpy:4`) | group | group | Per account/type/category running balance | Updated by `CBTRN02C`, consumed by `CBACT04C` and `PRTCATBL` |
| 05 | `TRAN-CAT-KEY` (`:5`) | group | group (17 bytes) | Composite KSDS key | |
| 10 | `TRANCAT-ACCT-ID` (`:6`) | `9(11)` | unsigned zoned | Account id | |
| 10 | `TRANCAT-TYPE-CD` (`:7`) | `X(02)` | alphanumeric | Transaction type | Joins `TRAN-TYPE` (`CVTRA03Y.cpy:5`) |
| 10 | `TRANCAT-CD` (`:8`) | `9(04)` | unsigned zoned | Transaction category | Joins `TRAN-CAT-CD` (`CVTRA04Y.cpy:7`) |
| 05 | `TRAN-CAT-BAL` (`:9`) | `S9(09)V99` | signed zoned, 2 dp | Accumulated balance for the key | Interest base in `CBACT04C` |
| 05 | `FILLER` (`:10`) | `X(22)` | reserve | Padding | |

### `app/cpy/CVTRA02Y.cpy` — `DIS-GROUP-RECORD` (DISCGRP / `…DISCGRP.VSAM.KSDS`, 50 bytes)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `DIS-GROUP-RECORD` (`CVTRA02Y.cpy:4`) | group | group | Interest rate by disclosure group / type / category | |
| 05 | `DIS-GROUP-KEY` (`:5`) | group | group (16 bytes) | Composite key | |
| 10 | `DIS-ACCT-GROUP-ID` (`:6`) | `X(10)` | alphanumeric | Disclosure group | `CBACT04C` retries with a `DEFAULT` group when the account's group is absent |
| 10 | `DIS-TRAN-TYPE-CD` (`:7`) | `X(02)` | alphanumeric | Transaction type | |
| 10 | `DIS-TRAN-CAT-CD` (`:8`) | `9(04)` | unsigned zoned | Transaction category | |
| 05 | `DIS-INT-RATE` (`:9`) | `S9(04)V99` | signed zoned, 2 dp | Annual interest rate (percent) | Monthly interest = balance × rate ÷ 1200 in `CBACT04C` |
| 05 | `FILLER` (`:10`) | `X(28)` | reserve | Padding | |

### `app/cpy/CVTRA03Y.cpy` — `TRAN-TYPE-RECORD` (TRANTYPE / `…TRANTYPE.VSAM.KSDS`, 60 bytes)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `TRAN-TYPE-RECORD` (`CVTRA03Y.cpy:4`) | group | group | Transaction-type reference | Same content as Db2 `CARDDEMO.TRANSACTION_TYPE` |
| 05 | `TRAN-TYPE` (`:5`) | `X(02)` | alphanumeric | Type code (key) | |
| 05 | `TRAN-TYPE-DESC` (`:6`) | `X(50)` | alphanumeric | Description | Printed by `CBTRN03C` truncated to 15 chars (`CVTRA07Y.cpy:22`) |
| 05 | `FILLER` (`:7`) | `X(08)` | reserve | Padding | |

### `app/cpy/CVTRA04Y.cpy` — `TRAN-CAT-RECORD` (TRANCATG / `…TRANCATG.VSAM.KSDS`, 60 bytes)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `TRAN-CAT-RECORD` (`CVTRA04Y.cpy:4`) | group | group | Transaction-category reference | |
| 05 | `TRAN-CAT-KEY` (`:5`) | group | group (6 bytes) | Composite key | Note the same group name `TRAN-CAT-KEY` also exists in `CVTRA01Y.cpy:5` with a different layout — programs copying both must qualify references |
| 10 | `TRAN-TYPE-CD` (`:6`) | `X(02)` | alphanumeric | Type code | |
| 10 | `TRAN-CAT-CD` (`:7`) | `9(04)` | unsigned zoned | Category code | |
| 05 | `TRAN-CAT-TYPE-DESC` (`:8`) | `X(50)` | alphanumeric | Category description | |
| 05 | `FILLER` (`:9`) | `X(04)` | reserve | Padding | |

### `app/cpy/CVTRA05Y.cpy` — `TRAN-RECORD` (TRANSACT / `…TRANSACT.VSAM.KSDS`, key `TRAN-ID`, 350 bytes)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `TRAN-RECORD` (`CVTRA05Y.cpy:4`) | group | group | Posted transaction | Written by `CBTRN02C`, `CBACT04C`, `COTRN02C`, `COBIL00C` |
| 05 | `TRAN-ID` (`:5`) | `X(16)` | alphanumeric | Transaction id (KSDS key) | Online programs derive the next id by reading the last record backwards and incrementing |
| 05 | `TRAN-TYPE-CD` (`:6`) | `X(02)` | alphanumeric | Transaction type | |
| 05 | `TRAN-CAT-CD` (`:7`) | `9(04)` | unsigned zoned | Transaction category | |
| 05 | `TRAN-SOURCE` (`:8`) | `X(10)` | alphanumeric | Origin channel | e.g. `POS TERM`, `System`, bill-pay literal |
| 05 | `TRAN-DESC` (`:9`) | `X(100)` | alphanumeric | Description | Interest transactions carry a generated description |
| 05 | `TRAN-AMT` (`:10`) | `S9(09)V99` | signed zoned, 2 dp | Amount, sign indicates debit/credit | Edited on screen through a `-99999999.99` mask |
| 05 | `TRAN-MERCHANT-ID` (`:11`) | `9(09)` | unsigned zoned | Merchant id | Mandatory numeric in `COTRN02C` |
| 05 | `TRAN-MERCHANT-NAME` (`:12`) | `X(50)` | alphanumeric | Merchant name | |
| 05 | `TRAN-MERCHANT-CITY` (`:13`) | `X(50)` | alphanumeric | Merchant city | |
| 05 | `TRAN-MERCHANT-ZIP` (`:14`) | `X(10)` | alphanumeric | Merchant ZIP | |
| 05 | `TRAN-CARD-NUM` (`:15`) | `X(16)` | alphanumeric | Card number | Resolved to account through the xref |
| 05 | `TRAN-ORIG-TS` (`:16`) | `X(26)` | alphanumeric timestamp | Origination timestamp | `YYYY-MM-DD HH:MM:SS.ffffff`, matching `WS-TIMESTAMP` (`CSDAT01Y.cpy:42`) |
| 05 | `TRAN-PROC-TS` (`:17`) | `X(26)` | alphanumeric timestamp | Processing timestamp | Set at posting time |
| 05 | `FILLER` (`:18`) | `X(20)` | reserve | Padding | |

### `app/cpy/CVTRA06Y.cpy` — `DALYTRAN-RECORD` (DALYTRAN / `…DALYTRAN.PS`, 350 bytes)

Identical field order and sizes to `TRAN-RECORD` with a `DALYTRAN-` prefix, i.e. the input file is the
posted-record layout: `DALYTRAN-ID` `X(16)` (`CVTRA06Y.cpy:5`), `DALYTRAN-TYPE-CD` `X(02)` (`:6`),
`DALYTRAN-CAT-CD` `9(04)` (`:7`), `DALYTRAN-SOURCE` `X(10)` (`:8`), `DALYTRAN-DESC` `X(100)` (`:9`),
`DALYTRAN-AMT` `S9(09)V99` (`:10`), `DALYTRAN-MERCHANT-ID` `9(09)` (`:11`), `DALYTRAN-MERCHANT-NAME`
`X(50)` (`:12`), `DALYTRAN-MERCHANT-CITY` `X(50)` (`:13`), `DALYTRAN-MERCHANT-ZIP` `X(10)` (`:14`),
`DALYTRAN-CARD-NUM` `X(16)` (`:15`), `DALYTRAN-ORIG-TS` `X(26)` (`:16`), `DALYTRAN-PROC-TS` `X(26)` (`:17`),
`FILLER` `X(20)` (`:18`).

Business meaning: an unvalidated inbound transaction. `CBTRN02C` copies the record straight into
`TRAN-RECORD` after validation, and writes the *unchanged* record to `DALYREJS` prefixed with a reject
reason when validation fails.

### `app/cpy/CVTRA07Y.cpy` — transaction report print lines

Report layout copybook used by `CBTRN03C`; all values are print constants or numeric-edited totals.

| Level | Field | PIC | Derived type | Business meaning / VALUE |
| ----: | :---- | :-- | :----------- | :----------------------- |
| 01 | `REPORT-NAME-HEADER` (`CVTRA07Y.cpy:4`) | group | group | Report heading |
| 05 | `REPT-SHORT-NAME` (`:5`) | `X(38)` | alphanumeric | `VALUE 'DALYREPT'` |
| 05 | `REPT-LONG-NAME` (`:7`) | `X(41)` | alphanumeric | `VALUE 'Daily Transaction Report'` |
| 05 | `REPT-DATE-HEADER` (`:9`) | `X(12)` | alphanumeric | `VALUE 'Date Range: '` |
| 05 | `REPT-START-DATE` (`:11`) | `X(10)` | alphanumeric | Range start, `VALUE SPACES`, filled from `DATEPARM` |
| 05 | `FILLER` (`:12`) | `X(04)` | alphanumeric | `VALUE ' to '` |
| 05 | `REPT-END-DATE` (`:13`) | `X(10)` | alphanumeric | Range end, `VALUE SPACES` |
| 01 | `TRANSACTION-DETAIL-REPORT` (`:15`) | group | group | One detail line |
| 05 | `TRAN-REPORT-TRANS-ID` (`:16`) | `X(16)` | alphanumeric | Transaction id |
| 05 | `TRAN-REPORT-ACCOUNT-ID` (`:18`) | `X(11)` | alphanumeric | Account id |
| 05 | `TRAN-REPORT-TYPE-CD` (`:20`) | `X(02)` | alphanumeric | Type code |
| 05 | `FILLER` (`:21`) | `X(01)` | alphanumeric | `VALUE '-'` separator |
| 05 | `TRAN-REPORT-TYPE-DESC` (`:22`) | `X(15)` | alphanumeric | Type description truncated to 15 |
| 05 | `TRAN-REPORT-CAT-CD` (`:24`) | `9(04)` | unsigned zoned | Category code |
| 05 | `FILLER` (`:25`) | `X(01)` | alphanumeric | `VALUE '-'` |
| 05 | `TRAN-REPORT-CAT-DESC` (`:26`) | `X(29)` | alphanumeric | Category description truncated to 29 |
| 05 | `TRAN-REPORT-SOURCE` (`:28`) | `X(10)` | alphanumeric | Source |
| 05 | `TRAN-REPORT-AMT` (`:30`) | `-ZZZ,ZZZ,ZZZ.ZZ` | numeric-edited | Amount, leading minus, zero-suppressed |
| 01 | `TRANSACTION-HEADER-1` (`:33`) | group | group | Column headings (`FILLER` constants at `:34`–`:45`) |
| 01 | `TRANSACTION-HEADER-2` (`:48`) | `X(133)` | alphanumeric | `VALUE ALL '-'` rule line — implies a 133-byte print line |
| 01 | `REPORT-PAGE-TOTALS` (`:50`) | group | group | `'Page Total'` + `VALUE ALL '.'` leader (`:51`, `:53`) |
| 05 | `REPT-PAGE-TOTAL` (`:54`) | `+ZZZ,ZZZ,ZZZ.ZZ` | numeric-edited | Page total, signed |
| 01 | `REPORT-ACCOUNT-TOTALS` (`:56`) | group | group | `'Account Total'` + leader |
| 05 | `REPT-ACCOUNT-TOTAL` (`:60`) | `+ZZZ,ZZZ,ZZZ.ZZ` | numeric-edited | Per-account total |
| 01 | `REPORT-GRAND-TOTALS` (`:62`) | group | group | `'Grand Total'` + leader |
| 05 | `REPT-GRAND-TOTAL` (`:66`) | `+ZZZ,ZZZ,ZZZ.ZZ` | numeric-edited | Grand total |

### `app/cpy/COSTM01.CPY` — `TRNX-RECORD` (statement work file `…TRXFL.VSAM.KSDS`)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `TRNX-RECORD` (`COSTM01.CPY:20`) | group | group | Transaction re-keyed by card for statements | Built by `CREASTMT.JCL` steps `STEP010`/`STEP020` and read by `CBSTM03B` |
| 05 | `TRNX-KEY` (`:21`) | group | group (32 bytes) | Composite key card+transaction | This re-keying is the only structural difference from `TRAN-RECORD` |
| 10 | `TRNX-CARD-NUM` (`:22`) | `X(16)` | alphanumeric | Card number (high-order key) | |
| 10 | `TRNX-ID` (`:23`) | `X(16)` | alphanumeric | Transaction id | |
| 05 | `TRNX-REST` (`:24`) | group | group | Remaining transaction data | |
| 10 | `TRNX-TYPE-CD` (`:25`) | `X(02)` | alphanumeric | Type | |
| 10 | `TRNX-CAT-CD` (`:26`) | `9(04)` | unsigned zoned | Category | |
| 10 | `TRNX-SOURCE` (`:27`) | `X(10)` | alphanumeric | Source | |
| 10 | `TRNX-DESC` (`:28`) | `X(100)` | alphanumeric | Description | |
| 10 | `TRNX-AMT` (`:29`) | `S9(09)V99` | signed zoned, 2 dp | Amount | |
| 10 | `TRNX-MERCHANT-ID` (`:30`) | `9(09)` | unsigned zoned | Merchant id | |
| 10 | `TRNX-MERCHANT-NAME` (`:31`) | `X(50)` | alphanumeric | Merchant name | |
| 10 | `TRNX-MERCHANT-CITY` (`:32`) | `X(50)` | alphanumeric | Merchant city | |
| 10 | `TRNX-MERCHANT-ZIP` (`:33`) | `X(10)` | alphanumeric | Merchant ZIP | |
| 10 | `TRNX-ORIG-TS` (`:34`) | `X(26)` | alphanumeric timestamp | Origination timestamp | |
| 10 | `TRNX-PROC-TS` (`:35`) | `X(26)` | alphanumeric timestamp | Processing timestamp | |
| 10 | `FILLER` (`:36`) | `X(20)` | reserve | Padding | |

## 6. Security

### `app/cpy/CSUSR01Y.cpy` — `SEC-USER-DATA` (USRSEC / `…USRSEC.VSAM.KSDS`, key `SEC-USR-ID`, 80 bytes)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `SEC-USER-DATA` (`CSUSR01Y.cpy:17`) | group | group | Signon/security record | |
| 05 | `SEC-USR-ID` (`:18`) | `X(08)` | alphanumeric | Userid (KSDS key) | Sample data `ADMIN001`, `USER0001` (`README.md:203`, `:204`) |
| 05 | `SEC-USR-FNAME` (`:19`) | `X(20)` | alphanumeric | First name | |
| 05 | `SEC-USR-LNAME` (`:20`) | `X(20)` | alphanumeric | Last name | |
| 05 | `SEC-USR-PWD` (`:21`) | `X(08)` | alphanumeric | Password | **Stored in clear text and compared literally in `COSGN00C`** — the highest-priority security defect to fix during modernization |
| 05 | `SEC-USR-TYPE` (`:22`) | `X(01)` | alphanumeric | User type | `A` admin / `U` user; drives the menu chosen by `COSGN00C` and the option filter `CDEMO-MENU-OPT-USRTYPE` (`COMEN02Y.cpy:98`) |
| 05 | `SEC-USR-FILLER` (`:23`) | `X(23)` | reserve | Padding to 80 bytes | |

### `app/cpy/UNUSED1Y.cpy` — `UNUSED-DATA`

Structural clone of `SEC-USER-DATA` with `UNUSED-` names: `UNUSED-ID` `X(08)` (`UNUSED1Y.cpy:2`),
`UNUSED-FNAME` `X(20)` (`:3`), `UNUSED-LNAME` `X(20)` (`:4`), `UNUSED-PWD` `X(08)` (`:5`), `UNUSED-TYPE`
`X(01)` (`:6`), `UNUSED-FILLER` `X(23)` (`:7`). No `COPY UNUSED1Y` exists in any program in this
repository — dead code, safe to drop.

## 7. Common / screen / utility

### `app/cpy/COCOM01Y.cpy` — `CARDDEMO-COMMAREA` (the online navigation contract)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `CARDDEMO-COMMAREA` (`COCOM01Y.cpy:19`) | group | group | COMMAREA passed on every `XCTL`/`RETURN TRANSID` | Copied by 17 of the 19 online programs |
| 05 | `CDEMO-GENERAL-INFO` (`:20`) | group | group | Routing + identity | |
| 10 | `CDEMO-FROM-TRANID` (`:21`) | `X(04)` | alphanumeric | Calling transaction | Blank/`LOW-VALUES` means "no caller" and triggers the menu fallback |
| 10 | `CDEMO-FROM-PROGRAM` (`:22`) | `X(08)` | alphanumeric | Calling program | |
| 10 | `CDEMO-TO-TRANID` (`:23`) | `X(04)` | alphanumeric | Target transaction | |
| 10 | `CDEMO-TO-PROGRAM` (`:24`) | `X(08)` | alphanumeric | Target program | The variable named on nearly every `EXEC CICS XCTL PROGRAM(...)` |
| 10 | `CDEMO-USER-ID` (`:25`) | `X(08)` | alphanumeric | Signed-on user | Carried instead of re-reading `USRSEC` |
| 10 | `CDEMO-USER-TYPE` (`:26`) | `X(01)` | alphanumeric | User type | `88 CDEMO-USRTYP-ADMIN VALUE 'A'` (`:27`), `88 CDEMO-USRTYP-USER VALUE 'U'` (`:28`) |
| 10 | `CDEMO-PGM-CONTEXT` (`:29`) | `9(01)` | unsigned zoned | First-entry vs. re-entry flag | `88 CDEMO-PGM-ENTER VALUE 0` (`:30`), `88 CDEMO-PGM-REENTER VALUE 1` (`:31`) — the pseudo-conversational state machine |
| 05 | `CDEMO-CUSTOMER-INFO` (`:32`) | group | group | Selected customer | |
| 10 | `CDEMO-CUST-ID` (`:33`) | `9(09)` | unsigned zoned | Customer id | |
| 10 | `CDEMO-CUST-FNAME` (`:34`) | `X(25)` | alphanumeric | First name | |
| 10 | `CDEMO-CUST-MNAME` (`:35`) | `X(25)` | alphanumeric | Middle name | |
| 10 | `CDEMO-CUST-LNAME` (`:36`) | `X(25)` | alphanumeric | Last name | |
| 05 | `CDEMO-ACCOUNT-INFO` (`:37`) | group | group | Selected account | |
| 10 | `CDEMO-ACCT-ID` (`:38`) | `9(11)` | unsigned zoned | Account id | Handover key between list, view and update screens |
| 10 | `CDEMO-ACCT-STATUS` (`:39`) | `X(01)` | alphanumeric | Account status | |
| 05 | `CDEMO-CARD-INFO` (`:40`) | group | group | Selected card | |
| 10 | `CDEMO-CARD-NUM` (`:41`) | `9(16)` | unsigned zoned | Card number | Numeric here but `X(16)` in the master record (`CVACT02Y.cpy:5`) — conversion must handle the mismatch |
| 05 | `CDEMO-MORE-INFO` (`:42`) | group | group | Screen return context | |
| 10 | `CDEMO-LAST-MAP` (`:43`) | `X(7)` | alphanumeric | Last BMS map | |
| 10 | `CDEMO-LAST-MAPSET` (`:44`) | `X(7)` | alphanumeric | Last BMS mapset | |

### `app/cpy/COMEN02Y.cpy` — `CARDDEMO-MAIN-MENU-OPTIONS` (user menu table)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `CARDDEMO-MAIN-MENU-OPTIONS` (`COMEN02Y.cpy:19`) | group | group | Menu definition consumed by `COMEN01C` | |
| 05 | `CDEMO-MENU-OPT-COUNT` (`:21`) | `9(02)`, `VALUE 11` | unsigned zoned | Number of live options | **11 options are populated but the `OCCURS` is 12** (`:94`) — entry 12 is uninitialised |
| 05 | `CDEMO-MENU-OPTIONS-DATA` (`:23`) | group of `FILLER` | group | Literal option rows, 46 bytes each | Program names: `COACTVWC` (`:28`), `COACTUPC` (`:34`), `COCRDLIC` (`:40`), `COCRDSLC` (`:46`), `COCRDUPC` (`:52`), `COTRN00C` (`:58`), `COTRN01C` (`:64`), `COTRN02C` (`:71`), `CORPT00C` (`:77`), `COBIL00C` (`:83`), `COPAUS0C` (`:89`) — all rows carry user type `'U'` |
| 05 | `CDEMO-MENU-OPTIONS` (`:93`) | **REDEFINES** `CDEMO-MENU-OPTIONS-DATA` | table | Structured view of the literals | |
| 10 | `CDEMO-MENU-OPT` (`:94`) | `OCCURS 12 TIMES` | table entry | One menu row | |
| 15 | `CDEMO-MENU-OPT-NUM` (`:95`) | `9(02)` | unsigned zoned | Option number | Compared with the number keyed on the screen |
| 15 | `CDEMO-MENU-OPT-NAME` (`:96`) | `X(35)` | alphanumeric | Display text | |
| 15 | `CDEMO-MENU-OPT-PGMNAME` (`:97`) | `X(08)` | alphanumeric | Program to `XCTL` | The dynamic navigation target of `COMEN01C` |
| 15 | `CDEMO-MENU-OPT-USRTYPE` (`:98`) | `X(01)` | alphanumeric | Allowed user type | `'U'` in all rows; compared with `CDEMO-USER-TYPE` |

### `app/cpy/COADM02Y.cpy` — `CARDDEMO-ADMIN-MENU-OPTIONS` (admin menu table)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `CARDDEMO-ADMIN-MENU-OPTIONS` (`COADM02Y.cpy:19`) | group | group | Admin menu consumed by `COADM01C` | A commented-out `VALUE 4` count at `:21` shows the table grew with the Db2 release |
| 05 | `CDEMO-ADMIN-OPT-COUNT` (`:22`) | `9(02)`, `VALUE 6` | unsigned zoned | Live option count | 6 populated rows, `OCCURS 9` (`:56`) |
| 05 | `CDEMO-ADMIN-OPTIONS-DATA` (`:24`) | group of `FILLER` | group | Literal rows, 45 bytes each | `COUSR00C` (`:29`), `COUSR01C` (`:34`), `COUSR02C` (`:39`), `COUSR03C` (`:44`), `COTRTLIC` (`:49`), `COTRTUPC` (`:54`) |
| 05 | `CDEMO-ADMIN-OPTIONS` (`:55`) | **REDEFINES** `CDEMO-ADMIN-OPTIONS-DATA` | table | Structured view | |
| 10 | `CDEMO-ADMIN-OPT` (`:56`) | `OCCURS 9 TIMES` | table entry | One admin row | No user-type byte here — admin authority is implied |
| 15 | `CDEMO-ADMIN-OPT-NUM` (`:57`) | `9(02)` | unsigned zoned | Option number | |
| 15 | `CDEMO-ADMIN-OPT-NAME` (`:58`) | `X(35)` | alphanumeric | Display text | |
| 15 | `CDEMO-ADMIN-OPT-PGMNAME` (`:59`) | `X(08)` | alphanumeric | Program to `XCTL` | |

### `app/cpy/COTTL01Y.cpy` — `CCDA-SCREEN-TITLE`

| Level | Field | PIC | Derived type | VALUE |
| ----: | :---- | :-- | :----------- | :---- |
| 01 | `CCDA-SCREEN-TITLE` (`COTTL01Y.cpy:17`) | group | group | Screen title block |
| 05 | `CCDA-TITLE01` (`:18`) | `X(40)` | alphanumeric | `'AWS Mainframe Modernization'` (centred) |
| 05 | `CCDA-TITLE02` (`:20`) | `X(40)` | alphanumeric | `'CardDemo'` (centred) |
| 05 | `CCDA-THANK-YOU` (`:23`) | `X(40)` | alphanumeric | `'Thank you for using CCDA application... '` |

### `app/cpy/CSMSG01Y.cpy` — `CCDA-COMMON-MESSAGES`

| Level | Field | PIC | Derived type | VALUE |
| ----: | :---- | :-- | :----------- | :---- |
| 01 | `CCDA-COMMON-MESSAGES` (`CSMSG01Y.cpy:17`) | group | group | Shared message texts |
| 05 | `CCDA-MSG-THANK-YOU` (`:18`) | `X(50)` | alphanumeric | `'Thank you for using CardDemo application... '` |
| 05 | `CCDA-MSG-INVALID-KEY` (`:20`) | `X(50)` | alphanumeric | `'Invalid key pressed. Please see below... '` |

### `app/cpy/CSMSG02Y.cpy` — `ABEND-DATA` (header comment names it `CABENDD.CPY`, `CSMSG02Y.cpy:2`)

| Level | Field | PIC | Derived type | Business meaning | VALUE |
| ----: | :---- | :-- | :----------- | :--------------- | :---- |
| 01 | `ABEND-DATA` (`CSMSG02Y.cpy:21`) | group | group | Work area for the abend routine | |
| 05 | `ABEND-CODE` (`:22`) | `X(4)` | alphanumeric | CICS abend code issued | `VALUE SPACES` |
| 05 | `ABEND-CULPRIT` (`:24`) | `X(8)` | alphanumeric | Failing program/paragraph | `VALUE SPACES` |
| 05 | `ABEND-REASON` (`:26`) | `X(50)` | alphanumeric | Reason text | `VALUE SPACES` |
| 05 | `ABEND-MSG` (`:28`) | `X(72)` | alphanumeric | Formatted message | `VALUE SPACES` |

### `app/cpy/CSDAT01Y.cpy` — `WS-DATE-TIME` (current date/time work area)

| Level | Field | PIC | Derived type | Business meaning | Validation / notes |
| ----: | :---- | :-- | :----------- | :--------------- | :----------------- |
| 01 | `WS-DATE-TIME` (`CSDAT01Y.cpy:17`) | group | group | Date/time work area populated from `EIBDATE`/`EIBTIME` or `FUNCTION CURRENT-DATE` | |
| 05 | `WS-CURDATE-DATA` (`:18`) | group | group | | |
| 10 | `WS-CURDATE` (`:19`) | group | group (8) | Current date | |
| 15 | `WS-CURDATE-YEAR` (`:20`) | `9(04)` | unsigned zoned | Year | |
| 15 | `WS-CURDATE-MONTH` (`:21`) | `9(02)` | unsigned zoned | Month | |
| 15 | `WS-CURDATE-DAY` (`:22`) | `9(02)` | unsigned zoned | Day | |
| 10 | `WS-CURDATE-N` (`:23`) | `9(08)` **REDEFINES** `WS-CURDATE` | unsigned zoned | Whole date as one number | Used for comparisons |
| 10 | `WS-CURTIME` (`:24`) | group | group (8) | Current time | |
| 15 | `WS-CURTIME-HOURS` (`:25`) | `9(02)` | unsigned zoned | Hours | |
| 15 | `WS-CURTIME-MINUTE` (`:26`) | `9(02)` | unsigned zoned | Minutes | |
| 15 | `WS-CURTIME-SECOND` (`:27`) | `9(02)` | unsigned zoned | Seconds | |
| 15 | `WS-CURTIME-MILSEC` (`:28`) | `9(02)` | unsigned zoned | Hundredths | Only 2 digits despite the name |
| 10 | `WS-CURTIME-N` (`:29`) | `9(08)` **REDEFINES** `WS-CURTIME` | unsigned zoned | Whole time as one number | |
| 05 | `WS-CURDATE-MM-DD-YY` (`:30`) | group | group (8) | Display date | `FILLER` `X(01) VALUE '/'` at `:32`, `:34`; `WS-CURDATE-MM` (`:31`), `WS-CURDATE-DD` (`:33`), `WS-CURDATE-YY` (`:35`) each `9(02)` |
| 05 | `WS-CURTIME-HH-MM-SS` (`:36`) | group | group (8) | Display time | `FILLER` `X(01) VALUE ':'` at `:38`, `:40`; `WS-CURTIME-HH` (`:37`), `-MM` (`:39`), `-SS` (`:41`) each `9(02)` |
| 05 | `WS-TIMESTAMP` (`:42`) | group | group (26) | Db2/transaction timestamp | `9(04)`+`'-'`+`9(02)`+`'-'`+`9(02)`+`' '`+`9(02)`+`':'`+`9(02)`+`':'`+`9(02)`+`'.'`+`9(06)` (`:43`–`:55`); the exact layout stored in `TRAN-ORIG-TS`/`TRAN-PROC-TS` |

### `app/cpy/CSUTLDWY.cpy` — date-edit working storage

Included by `COACTUPC` and `COTRTUPC` together with the `CSUTLDPY` procedure code. Note the copybook
starts at level `10`, so it must be copied *inside* an existing group.

| Level | Field | PIC | Derived type | Business meaning | Validation values |
| ----: | :---- | :-- | :----------- | :--------------- | :---------------- |
| 10 | `WS-EDIT-DATE-CCYYMMDD` (`CSUTLDWY.cpy:4`) | group (8) | group | Date being edited | |
| 20 | `WS-EDIT-DATE-CCYY` (`:5`) | group (4) | group | Year | |
| 25 | `WS-EDIT-DATE-CC` (`:6`) | `X(2)` | alphanumeric | Century digits | |
| 25 | `WS-EDIT-DATE-CC-N` (`:7`) | `9(2)` **REDEFINES** `WS-EDIT-DATE-CC` | unsigned zoned | Numeric century | `88 THIS-CENTURY VALUE 20` (`:9`), `88 LAST-CENTURY VALUE 19` (`:10`) — only 19xx/20xx accepted (`CSUTLDPY.cpy:70`) |
| 25 | `WS-EDIT-DATE-YY` (`:11`) | `X(2)` | alphanumeric | Year within century | |
| 25 | `WS-EDIT-DATE-YY-N` (`:12`) | `9(2)` **REDEFINES** | unsigned zoned | Numeric year | |
| 20 | `WS-EDIT-DATE-CCYY-N` (`:14`) | `9(4)` **REDEFINES** `WS-EDIT-DATE-CCYY` | unsigned zoned | Whole year | |
| 20 | `WS-EDIT-DATE-MM` (`:16`) | `X(2)` | alphanumeric | Month | |
| 20 | `WS-EDIT-DATE-MM-N` (`:17`) | `9(2)` **REDEFINES** | unsigned zoned | Numeric month | `88 WS-VALID-MONTH VALUES 1 THROUGH 12` (`:19`), `88 WS-31-DAY-MONTH VALUES 1, 3, 5, 7, 8, 10, 12` (`:21`), `88 WS-FEBRUARY VALUE 2` (`:24`) |
| 20 | `WS-EDIT-DATE-DD` (`:25`) | `X(2)` | alphanumeric | Day | |
| 20 | `WS-EDIT-DATE-DD-N` (`:26`) | `9(2)` **REDEFINES** | unsigned zoned | Numeric day | `88 WS-VALID-DAY VALUES 1 THROUGH 31` (`:28`), `88 WS-DAY-31 VALUE 31` (`:30`), `88 WS-DAY-30 VALUE 30` (`:31`), `88 WS-DAY-29 VALUE 29` (`:32`), `88 WS-VALID-FEB-DAY VALUES 1 THROUGH 28` (`:33`) |
| 10 | `WS-EDIT-DATE-CCYYMMDD-N` (`:35`) | `9(8)` **REDEFINES** `WS-EDIT-DATE-CCYYMMDD` | unsigned zoned | Whole date | |
| 10 | `WS-EDIT-DATE-BINARY` (`:37`) | `S9(9) BINARY` | binary fullword | Lilian day number from `CEEDAYS` | |
| 10 | `WS-CURRENT-DATE` (`:38`) | group | group | Today, for range checks | `WS-CURRENT-DATE-YYYYMMDD` `X(8)` (`:39`), `-N` `9(8)` REDEFINES (`:40`), `WS-CURRENT-DATE-BINARY` `S9(9) BINARY` (`:42`) |
| 10 | `WS-EDIT-DATE-FLGS` (`:43`) | group (3) | group | Composite edit result | `88 WS-EDIT-DATE-IS-VALID VALUE LOW-VALUES` (`:44`), `88 WS-EDIT-DATE-IS-INVALID VALUE '000'` (`:45`) |
| 20 | `WS-EDIT-YEAR-FLG` (`:46`) | `X(01)` | alphanumeric | Year result | `88 FLG-YEAR-ISVALID VALUE LOW-VALUES` (`:47`), `88 FLG-YEAR-NOT-OK VALUE '0'` (`:48`), `88 FLG-YEAR-BLANK VALUE 'B'` (`:49`) |
| 20 | `WS-EDIT-MONTH` (`:50`) | `X(01)` | alphanumeric | Month result | `FLG-MONTH-ISVALID`/`-NOT-OK`/`-BLANK` (`:51`–`:53`) |
| 20 | `WS-EDIT-DAY` (`:54`) | `X(01)` | alphanumeric | Day result | `FLG-DAY-ISVALID`/`-NOT-OK`/`-BLANK` (`:55`–`:57`) |
| 10 | `WS-DATE-FORMAT` (`:58`) | `X(08)`, `VALUE 'YYYYMMDD'` | alphanumeric | Picture string passed to `CSUTLDTC`/`CEEDAYS` | |
| 10 | `WS-DATE-VALIDATION-RESULT` (`:60`) | group | group | Formatted LE feedback message | `WS-SEVERITY` `X(04)` (`:61`) + `9(4)` REDEFINES (`:62`), `FILLER 'Mesg Code:'` (`:64`), `WS-MSG-NO` `X(04)` (`:66`) + `9(4)` REDEFINES (`:67`), `WS-RESULT` `X(15)` (`:71`), `FILLER 'TstDate:'` (`:74`), `WS-DATE` `X(10)` (`:76`), `FILLER 'Mask used:'` (`:79`), `WS-DATE-FMT` `X(10)` (`:81`) |

### `app/cpy/CSUTLDPY.cpy` — date-edit procedure code (no data fields)

A `PROCEDURE DIVISION` copybook (paragraphs, not data) operating on the `CSUTLDWY` fields. Documented here
because the business rules live in it:

| Paragraph | Line | Rule implemented |
| :-------- | ---: | :--------------- |
| `EDIT-DATE-CCYYMMDD` | `CSUTLDPY.cpy:18` | Driver: sets "invalid" then performs year → month → day → combination → LE checks |
| `EDIT-YEAR-CCYY` | `:25` | Year mandatory (`:30`), must be 4 numeric digits (`:48`), century must be 19 or 20 (`:70`) |
| `EDIT-MONTH` | `:91` | Month mandatory, numeric, `WS-VALID-MONTH` 1–12 |
| `EDIT-DAY` | `:150` | Day mandatory, numeric, `WS-VALID-DAY` 1–31 |
| `EDIT-DAY-MONTH-YEAR` | `:209` | Day-vs-month combination: 31-day months, 30-day months, February 1–28 plus leap-year handling of 29 |
| `EDIT-DATE-LE` | `:284` | Final check via LE `CEEDAYS` (through `CSUTLDTC`) using `WS-DATE-FORMAT` |
| `EDIT-DATE-OF-BIRTH` | `:341` | Date-of-birth specific rules — a DOB may not be in the future |

Messages are built with `STRING FUNCTION TRIM(WS-EDIT-VARIABLE-NAME)` so the *calling screen's* field name
appears in the error text (e.g. `:36`), which is why the copybook depends on caller-provided variables.

### `app/cpy/CSSTRPFY.cpy` — PF-key decode procedure (no data fields)

`YYYY-STORE-PFKEY` (`CSSTRPFY.cpy:17`) is an `EVALUATE TRUE` (`:21`) over `EIBAID`, mapping `DFHENTER`,
`DFHCLEAR`, `DFHPA1`/`PA2` and `DFHPF1`–`DFHPF24` onto the `CCARD-AID-*` conditions of `CVCRD01Y`
(PF13–PF24 fold onto `PFK01`–`PFK12`, `:54`–`:77`). Included with the quoted form `COPY 'CSSTRPFY'`.

### `app/cpy/CSSETATY.cpy` — screen-attribute macro (template, not compilable alone)

Contains a parameterised fragment (`CSSETATY.cpy:18`–`:27`) that, for a given screen variable, turns the
field red (`MOVE DFHRED`) when `FLG-(TESTVAR1)-NOT-OK` or `FLG-(TESTVAR1)-BLANK` is set and the program is
in re-entry (`CDEMO-PGM-REENTER`), and writes `'*'` into a blank field. The `(TESTVAR1)`, `(SCRNVAR2)` and
`(MAPNAME3)` placeholders are substituted with `COPY … REPLACING`, so the member is a code template rather
than a data structure.

### `app/cpy/CODATECN.cpy` — `CODATECN-REC` (date-conversion interface, used by `CBACT01C`)

| Level | Field | PIC | Derived type | Business meaning | Validation values |
| ----: | :---- | :-- | :----------- | :--------------- | :---------------- |
| 01 | `CODATECN-REC` (`CODATECN.cpy:17`) | group | group | Date-conversion request/response | |
| 05 | `CODATECN-IN-REC` (`:18`) | group | group | Request | |
| 10 | `CODATECN-TYPE` (`:19`) | `X` | alphanumeric | Input format selector | `88 YYYYMMDD-IN VALUE "1"` (`:20`), `88 YYYY-MM-DD-IN VALUE "2"` (`:21`) |
| 10 | `CODATECN-INP-DATE` (`:22`) | `X(20)` | alphanumeric | Input date buffer | |
| 10 | `CODATECN-1INP` (`:23`) | **REDEFINES** `CODATECN-INP-DATE` | group | Compact view | `CODATECN-1YYYY` `XXXX` (`:24`), `CODATECN-1MM` `XX` (`:25`), `CODATECN-1DD` `XX` (`:26`), `CODATECN-1FIL` `X(12)` (`:27`) |
| 10 | `CODATECN-2INP` (`:28`) | **REDEFINES** `CODATECN-INP-DATE` | group | Separated view | `CODATECN-1O-YYYY` `XXXX` (`:29`), `CODATECN-1I-S1` `X` (`:30`), `CODATECN-1MM` `XX` (`:31`), `CODATECN-1I-S2` `X` (`:32`), `CODATECN-2YY` `XX` (`:33`), `CODATECN-2FIL` `X(10)` (`:34`). **`CODATECN-1MM` is declared twice** (`:25` and `:31`) and **`CODATECN-1O-YYYY` twice** (`:29` and `:41`), so those references need `OF` qualification |
| 05 | `CODATECN-OUT-REC` (`:35`) | group | group | Response | |
| 10 | `CODATECN-OUTTYPE` (`:36`) | `X` | alphanumeric | Output format selector | `88 YYYY-MM-DD-OP VALUE "1"` (`:37`), `88 YYYYMMDD-OP VALUE "2"` (`:38`) — note the value/meaning pairing is the reverse of the input selector |
| 10 | `CODATECN-0UT-DATE` (`:39`) | `X(20)` | alphanumeric | Output buffer | Field name starts with a zero, not the letter O |
| 10 | `CODATECN-1OUT` (`:40`) | **REDEFINES** `CODATECN-0UT-DATE` | group | Separated view | `:41`–`:46` |
| 10 | `CODATECN-2OUT` (`:47`) | **REDEFINES** `CODATECN-0UT-DATE` | group | Compact view | `:48`–`:51` |
| 05 | `CODATECN-ERROR-MSG` (`:52`) | `X(38)` | alphanumeric | Error text | |

### `app/cpy/CVEXPORT.cpy` — `EXPORT-RECORD` (branch-migration interchange record)

The only copybook in `app/cpy/` that mixes packed, binary and display numerics, and the heaviest `REDEFINES`
user in the whole library: one 460-byte payload area carries five different entity layouts, discriminated by
`EXPORT-REC-TYPE`.

| Level | Field | PIC / USAGE | Derived type | Business meaning | Notes |
| ----: | :---- | :---------- | :----------- | :--------------- | :---- |
| 01 | `EXPORT-RECORD` (`CVEXPORT.cpy:9`) | group | group | One exported entity occurrence | Written by `CBEXPORT`, read by `CBIMPORT` |
| 05 | `EXPORT-REC-TYPE` (`:10`) | `X(1)` | alphanumeric | Record discriminator | Selects which `REDEFINES` view is valid; `CBIMPORT` `EVALUATE`s it |
| 05 | `EXPORT-TIMESTAMP` (`:11`) | `X(26)` | alphanumeric | Export timestamp | |
| 05 | `EXPORT-TIMESTAMP-R` (`:12`) | **REDEFINES** `EXPORT-TIMESTAMP` | group | Split timestamp | `EXPORT-DATE` `X(10)` (`:13`), `EXPORT-DATE-TIME-SEP` `X(1)` (`:14`), `EXPORT-TIME` `X(15)` (`:15`) |
| 05 | `EXPORT-SEQUENCE-NUM` (`:16`) | `9(9) COMP` | binary fullword | Sequence within the export | Binary — endianness matters off-platform |
| 05 | `EXPORT-BRANCH-ID` (`:17`) | `X(4)` | alphanumeric | Originating branch | |
| 05 | `EXPORT-REGION-CODE` (`:18`) | `X(5)` | alphanumeric | Region | |
| 05 | `EXPORT-RECORD-DATA` (`:19`) | `X(460)` | alphanumeric | Generic payload | Base of all five entity views below |
| 05 | `EXPORT-CUSTOMER-DATA` (`:24`) | **REDEFINES** `EXPORT-RECORD-DATA` | group | Customer view of the payload | `EXP-CUST-ID` `9(09) COMP` **binary** (`:25`); names `X(25)` (`:26`–`:28`); `EXP-CUST-ADDR-LINES OCCURS 3 TIMES` of `EXP-CUST-ADDR-LINE X(50)` (`:29`, `:30`) — the three `CVCUS01Y` address lines turned into a table; state/country/ZIP (`:31`–`:33`); `EXP-CUST-PHONE-NUMS OCCURS 2 TIMES` of `EXP-CUST-PHONE-NUM X(15)` (`:34`, `:35`); `EXP-CUST-SSN` `9(09)` (`:36`); `EXP-CUST-FICO-CREDIT-SCORE` `9(03) COMP-3` **packed** (`:41`); `FILLER X(134)` (`:42`) |
| 05 | `EXPORT-ACCOUNT-DATA` (`:47`) | **REDEFINES** `EXPORT-RECORD-DATA` | group | Account view | `EXP-ACCT-ID` `9(11)` zoned (`:48`); **mixed usages for money in one record**: `EXP-ACCT-CURR-BAL` `COMP-3` (`:50`), `EXP-ACCT-CREDIT-LIMIT` zoned display (`:51`), `EXP-ACCT-CASH-CREDIT-LIMIT` `COMP-3` (`:52`), `EXP-ACCT-CURR-CYC-CREDIT` zoned (`:56`), `EXP-ACCT-CURR-CYC-DEBIT` `S9(10)V99 COMP` **binary with an implied decimal** (`:57`); `FILLER X(352)` (`:60`) |
| 05 | `EXPORT-TRANSACTION-DATA` (`:65`) | **REDEFINES** `EXPORT-RECORD-DATA` | group | Transaction view | `EXP-TRAN-ID` `X(16)` (`:66`); `EXP-TRAN-AMT` `S9(09)V99 COMP-3` packed (`:71`); `EXP-TRAN-MERCHANT-ID` `9(09) COMP` binary (`:72`); `FILLER X(140)` (`:79`) |
| 05 | `EXPORT-CARD-XREF-DATA` (`:84`) | **REDEFINES** `EXPORT-RECORD-DATA` | group | Cross-reference view | `EXP-XREF-CARD-NUM` `X(16)` (`:85`), `EXP-XREF-CUST-ID` `9(09)` zoned (`:86`), `EXP-XREF-ACCT-ID` `9(11) COMP` binary (`:87`), `FILLER X(427)` (`:88`) |
| 05 | `EXPORT-CARD-DATA` (`:93`) | **REDEFINES** `EXPORT-RECORD-DATA` | group | Card view | `EXP-CARD-NUM` `X(16)` (`:94`), `EXP-CARD-ACCT-ID` `9(11) COMP` (`:95`), `EXP-CARD-CVV-CD` `9(03) COMP` (`:96`), embossed name/expiry/status (`:97`–`:99`), `FILLER X(373)` (`:100`) |

Migration consequence: this is the one CardDemo layout where a byte-level converter is mandatory — packed
decimal, binary integers, a discriminated union and `OCCURS` tables in the same record. The usages are
deliberately inconsistent field-by-field (compare `:50` vs. `:51` vs. `:57`), so a converter cannot assume a
single numeric encoding per record type; it must be generated from this copybook.

### `app/cpy/CSLKPCDY.cpy` — reference-data lookup tables (1318 lines)

The largest copybook: no record layout, only elementary fields with very large `88`-level value lists used
by `COACTUPC` to validate customer address and phone data. Because the values are compiled into a condition
name, each list is effectively a hard-coded reference table.

| Level | Field | PIC | Condition name | Line | Approx. literals | Business meaning |
| ----: | :---- | :-- | :------------- | ---: | ---------------: | :--------------- |
| 01 | `WS-US-PHONE-AREA-CODE-TO-EDIT` (`CSLKPCDY.cpy:24`) | `XXX` | `VALID-PHONE-AREA-CODE` | `:30` | ~490 | Every assignable NANP area code |
| | same field | | `VALID-GENERAL-PURP-CODE` | `:521` | ~410 | Geographic (general-purpose) area codes only |
| | same field | | `VALID-EASY-RECOG-AREA-CODE` | `:931` | ~80 | "Easily recognisable" codes (`200`, `211`, …, service/toll patterns) |
| 01 | `US-STATE-CODE-TO-EDIT` (`:1012`) | `X(2)` | `VALID-US-STATE-CODE` | `:1013` | ~56 | US states, DC and territories |
| 01 | `US-STATE-ZIPCODE-TO-EDIT` (`:1071`) | group | — | | | State + ZIP combination check |
| 02 | `US-STATE-AND-FIRST-ZIP2` (`:1072`) | `X(4)` | `VALID-US-STATE-ZIP-CD2-COMBO` | `:1073` | ~240 | State code concatenated with the first two ZIP digits (`'AA34'` … `'WY83'`), i.e. ZIP-belongs-to-state validation |
| 02 | `LAST-3-OF-ZIP` (`:1314`) | `X(3)` | — | | | Remaining ZIP digits, not validated |

Modernization note: these three lists are static reference data masquerading as code. Externalising them
(table or configuration) removes ~1300 lines from the recompile surface and is the single cheapest
structural win in the copybook library.

## 8. Authorization sub-application

Copybooks in `app/app-authorization-ims-db2-mq/cpy/`. `CCPAURQY`, `CCPAURLY`, `CIPAUDTY` and `CIPAUSMY`
begin at level `05` (their `01` is supplied by the including program's own group or `LINKAGE SECTION`).

### `CIPAUDTY.cpy` — pending-authorization detail segment (IMS `PAUTBDTL`)

| Level | Field | PIC / USAGE | Derived type | Business meaning | Validation values |
| ----: | :---- | :---------- | :----------- | :--------------- | :---------------- |
| 05 | `PA-AUTHORIZATION-KEY` (`CIPAUDTY.cpy:19`) | group | group | IMS segment key | |
| 10 | `PA-AUTH-DATE-9C` (`:20`) | `S9(05) COMP-3` | packed, 3 bytes | Authorization date | Packed key field — cannot be treated as text |
| 10 | `PA-AUTH-TIME-9C` (`:21`) | `S9(09) COMP-3` | packed, 5 bytes | Authorization time | |
| 05 | `PA-AUTH-ORIG-DATE` (`:22`) | `X(06)` | alphanumeric | Original date `YYMMDD` | |
| 05 | `PA-AUTH-ORIG-TIME` (`:23`) | `X(06)` | alphanumeric | Original time `HHMMSS` | |
| 05 | `PA-CARD-NUM` (`:24`) | `X(16)` | alphanumeric | Card number | |
| 05 | `PA-AUTH-TYPE` (`:25`) | `X(04)` | alphanumeric | Authorization type | |
| 05 | `PA-CARD-EXPIRY-DATE` (`:26`) | `X(04)` | alphanumeric | Card expiry `MMYY` | Compared with `CARD-EXPIRAION-DATE` |
| 05 | `PA-MESSAGE-TYPE` (`:27`) | `X(06)` | alphanumeric | Network message type | |
| 05 | `PA-MESSAGE-SOURCE` (`:28`) | `X(06)` | alphanumeric | Message source | |
| 05 | `PA-AUTH-ID-CODE` (`:29`) | `X(06)` | alphanumeric | Approval code | |
| 05 | `PA-AUTH-RESP-CODE` (`:30`) | `X(02)` | alphanumeric | Response code | `88 PA-AUTH-APPROVED VALUE '00'` (`:31`) |
| 05 | `PA-AUTH-RESP-REASON` (`:32`) | `X(04)` | alphanumeric | Decline reason | |
| 05 | `PA-PROCESSING-CODE` (`:33`) | `9(06)` | unsigned zoned | ISO processing code | |
| 05 | `PA-TRANSACTION-AMT` (`:34`) | `S9(10)V99 COMP-3` | packed, 7 bytes | Requested amount | |
| 05 | `PA-APPROVED-AMT` (`:35`) | `S9(10)V99 COMP-3` | packed, 7 bytes | Approved amount | May be less than requested |
| 05 | `PA-MERCHANT-CATAGORY-CODE` (`:36`) | `X(04)` | alphanumeric | MCC | Field name misspelled ("CATAGORY") |
| 05 | `PA-ACQR-COUNTRY-CODE` (`:37`) | `X(03)` | alphanumeric | Acquirer country | |
| 05 | `PA-POS-ENTRY-MODE` (`:38`) | `9(02)` | unsigned zoned | POS entry mode | |
| 05 | `PA-MERCHANT-ID` (`:39`) | `X(15)` | alphanumeric | Merchant id | Wider than `TRAN-MERCHANT-ID` `9(09)` (`CVTRA05Y.cpy:11`) |
| 05 | `PA-MERCHANT-NAME` (`:40`) | `X(22)` | alphanumeric | Merchant name | Narrower than the `X(50)` transaction field — truncation on match |
| 05 | `PA-MERCHANT-CITY` (`:41`) | `X(13)` | alphanumeric | Merchant city | Also narrower than `X(50)` |
| 05 | `PA-MERCHANT-STATE` (`:42`) | `X(02)` | alphanumeric | Merchant state | |
| 05 | `PA-MERCHANT-ZIP` (`:43`) | `X(09)` | alphanumeric | Merchant ZIP | |
| 05 | `PA-TRANSACTION-ID` (`:44`) | `X(15)` | alphanumeric | Network transaction id | `X(16)` in `TRAN-RECORD` |
| 05 | `PA-MATCH-STATUS` (`:45`) | `X(01)` | alphanumeric | Authorization lifecycle state | `88 PA-MATCH-PENDING VALUE 'P'` (`:46`), `88 PA-MATCH-AUTH-DECLINED VALUE 'D'` (`:47`), `88 PA-MATCH-PENDING-EXPIRED VALUE 'E'` (`:48`), `88 PA-MATCHED-WITH-TRAN VALUE 'M'` (`:49`) — `CBPAUP0C` purges `'E'`/expired entries |
| 05 | `PA-AUTH-FRAUD` (`:50`) | `X(01)` | alphanumeric | Fraud marking | `88 PA-FRAUD-CONFIRMED VALUE 'F'` (`:51`), `88 PA-FRAUD-REMOVED VALUE 'R'` (`:52`) — set by `COPAUS2C` |
| 05 | `PA-FRAUD-RPT-DATE` (`:53`) | `X(08)` | alphanumeric | Fraud report date | |
| 05 | `FILLER` (`:54`) | `X(17)` | reserve | Segment padding | |

### `CIPAUSMY.cpy` — pending-authorization summary segment (IMS root)

| Level | Field | PIC / USAGE | Derived type | Business meaning | Notes |
| ----: | :---- | :---------- | :----------- | :--------------- | :---- |
| 05 | `PA-ACCT-ID` (`CIPAUSMY.cpy:19`) | `S9(11) COMP-3` | packed, 6 bytes | Account id (root key) | Packed here, zoned `9(11)` in `ACCOUNT-RECORD` |
| 05 | `PA-CUST-ID` (`:20`) | `9(09)` | unsigned zoned | Customer id | |
| 05 | `PA-AUTH-STATUS` (`:21`) | `X(01)` | alphanumeric | Overall authorization status | |
| 05 | `PA-ACCOUNT-STATUS` (`:22`) | `X(02)` **OCCURS 5 TIMES** | table of alphanumeric | Up to 5 account status codes | Fixed-size array; the meaning of each slot is positional and undocumented in the copybook |
| 05 | `PA-CREDIT-LIMIT` (`:23`) | `S9(09)V99 COMP-3` | packed | Credit limit snapshot | One digit narrower than `ACCT-CREDIT-LIMIT` `S9(10)V99` |
| 05 | `PA-CASH-LIMIT` (`:24`) | `S9(09)V99 COMP-3` | packed | Cash limit snapshot | |
| 05 | `PA-CREDIT-BALANCE` (`:25`) | `S9(09)V99 COMP-3` | packed | Credit balance incl. pending authorizations | |
| 05 | `PA-CASH-BALANCE` (`:26`) | `S9(09)V99 COMP-3` | packed | Cash balance | |
| 05 | `PA-APPROVED-AUTH-CNT` (`:27`) | `S9(04) COMP` | binary halfword | Approved authorization count | |
| 05 | `PA-DECLINED-AUTH-CNT` (`:28`) | `S9(04) COMP` | binary halfword | Declined count | |
| 05 | `PA-APPROVED-AUTH-AMT` (`:29`) | `S9(09)V99 COMP-3` | packed | Approved amount total | |
| 05 | `PA-DECLINED-AUTH-AMT` (`:30`) | `S9(09)V99 COMP-3` | packed | Declined amount total | |
| 05 | `FILLER` (`:31`) | `X(34)` | reserve | Padding | |

### `CCPAURQY.cpy` — MQ authorization request message

| Level | Field | PIC | Derived type | Business meaning | Notes |
| ----: | :---- | :-- | :----------- | :--------------- | :---- |
| 05 | `PA-RQ-AUTH-DATE` (`CCPAURQY.cpy:19`) | `X(06)` | alphanumeric | Request date | Header comment "PENDING AUTHORIZATION REQUEST" (`:19` block header at `:15`) |
| 05 | `PA-RQ-AUTH-TIME` (`:20`) | `X(06)` | alphanumeric | Request time | |
| 05 | `PA-RQ-CARD-NUM` (`:21`) | `X(16)` | alphanumeric | Card number | |
| 05 | `PA-RQ-AUTH-TYPE` (`:22`) | `X(04)` | alphanumeric | Authorization type | |
| 05 | `PA-RQ-CARD-EXPIRY-DATE` (`:23`) | `X(04)` | alphanumeric | Expiry `MMYY` | |
| 05 | `PA-RQ-MESSAGE-TYPE` (`:24`) | `X(06)` | alphanumeric | Message type | |
| 05 | `PA-RQ-MESSAGE-SOURCE` (`:25`) | `X(06)` | alphanumeric | Message source | |
| 05 | `PA-RQ-PROCESSING-CODE` (`:26`) | `9(06)` | unsigned zoned | Processing code | |
| 05 | `PA-RQ-TRANSACTION-AMT` (`:27`) | `+9(10).99` | numeric-edited | Requested amount **as text with an explicit sign and decimal point** | Wire format; must be de-edited before arithmetic |
| 05 | `PA-RQ-MERCHANT-CATAGORY-CODE` (`:28`) | `X(04)` | alphanumeric | MCC | |
| 05 | `PA-RQ-ACQR-COUNTRY-CODE` (`:29`) | `X(03)` | alphanumeric | Acquirer country | |
| 05 | `PA-RQ-POS-ENTRY-MODE` (`:30`) | `9(02)` | unsigned zoned | POS entry mode | |
| 05 | `PA-RQ-MERCHANT-ID` (`:31`) | `X(15)` | alphanumeric | Merchant id | |
| 05 | `PA-RQ-MERCHANT-NAME` (`:32`) | `X(22)` | alphanumeric | Merchant name | |
| 05 | `PA-RQ-MERCHANT-CITY` (`:33`) | `X(13)` | alphanumeric | Merchant city | |
| 05 | `PA-RQ-MERCHANT-STATE` (`:34`) | `X(02)` | alphanumeric | Merchant state | |
| 05 | `PA-RQ-MERCHANT-ZIP` (`:35`) | `X(09)` | alphanumeric | Merchant ZIP | |
| 05 | `PA-RQ-TRANSACTION-ID` (`:36`) | `X(15)` | alphanumeric | Transaction id | |

### `CCPAURLY.cpy` — MQ authorization reply message

| Level | Field | PIC | Derived type | Business meaning | Notes |
| ----: | :---- | :-- | :----------- | :--------------- | :---- |
| 05 | `PA-RL-CARD-NUM` (`CCPAURLY.cpy:19`) | `X(16)` | alphanumeric | Card number echoed | |
| 05 | `PA-RL-TRANSACTION-ID` (`:20`) | `X(15)` | alphanumeric | Transaction id echoed | |
| 05 | `PA-RL-AUTH-ID-CODE` (`:21`) | `X(06)` | alphanumeric | Approval code | Blank when declined |
| 05 | `PA-RL-AUTH-RESP-CODE` (`:22`) | `X(02)` | alphanumeric | Response code | `'00'` = approved (`CIPAUDTY.cpy:31`) |
| 05 | `PA-RL-AUTH-RESP-REASON` (`:23`) | `X(04)` | alphanumeric | Reason code | |
| 05 | `PA-RL-APPROVED-AMT` (`:24`) | `+9(10).99` | numeric-edited | Approved amount as text | Same wire-format caveat as the request |

### `CCPAUERY.cpy` — `ERROR-LOG-RECORD`

| Level | Field | PIC | Derived type | Business meaning | Validation values |
| ----: | :---- | :-- | :----------- | :--------------- | :---------------- |
| 01 | `ERROR-LOG-RECORD` (`CCPAUERY.cpy:19`) | group | group | Common error-log record for the authorization module | |
| 05 | `ERR-DATE` (`:20`) | `X(06)` | alphanumeric | Date `YYMMDD` | |
| 05 | `ERR-TIME` (`:21`) | `X(06)` | alphanumeric | Time `HHMMSS` | |
| 05 | `ERR-APPLICATION` (`:22`) | `X(08)` | alphanumeric | Application name | |
| 05 | `ERR-PROGRAM` (`:23`) | `X(08)` | alphanumeric | Failing program | |
| 05 | `ERR-LOCATION` (`:24`) | `X(04)` | alphanumeric | Location/paragraph tag | |
| 05 | `ERR-LEVEL` (`:25`) | `X(01)` | alphanumeric | Severity | `88 ERR-LOG 'L'` (`:26`), `ERR-INFO 'I'` (`:27`), `ERR-WARNING 'W'` (`:28`), `ERR-CRITICAL 'C'` (`:29`) |
| 05 | `ERR-SUBSYSTEM` (`:30`) | `X(01)` | alphanumeric | Failing subsystem | `88 ERR-APP 'A'` (`:31`), `ERR-CICS 'C'` (`:32`), `ERR-IMS 'I'` (`:33`), `ERR-DB2 'D'` (`:34`), `ERR-MQ 'M'` (`:35`), `ERR-FILE 'F'` (`:36`) — an explicit map of the five runtimes this module depends on |
| 05 | `ERR-CODE-1` (`:37`) | `X(09)` | alphanumeric | Primary code (RESP/SQLCODE/MQRC) | |
| 05 | `ERR-CODE-2` (`:38`) | `X(09)` | alphanumeric | Secondary code | |
| 05 | `ERR-MESSAGE` (`:39`) | `X(50)` | alphanumeric | Message text | |
| 05 | `ERR-EVENT-KEY` (`:40`) | `X(20)` | alphanumeric | Business key of the failing event | |

### `IMSFUNCS.cpy` — `FUNC-CODES` (DL/I function literals)

| Level | Field | PIC | Derived type | VALUE | Meaning |
| ----: | :---- | :-- | :----------- | :---- | :------ |
| 01 | `FUNC-CODES` (`IMSFUNCS.cpy:17`) | group | group | | DL/I call function codes |
| 05 | `FUNC-GU` (`:18`) | `X(04)` | alphanumeric | `'GU '` | Get unique |
| 05 | `FUNC-GHU` (`:19`) | `X(04)` | alphanumeric | `'GHU '` | Get hold unique |
| 05 | `FUNC-GN` (`:20`) | `X(04)` | alphanumeric | `'GN '` | Get next |
| 05 | `FUNC-GHN` (`:21`) | `X(04)` | alphanumeric | `'GHN '` | Get hold next |
| 05 | `FUNC-GNP` (`:22`) | `X(04)` | alphanumeric | `'GNP '` | Get next in parent |
| 05 | `FUNC-GHNP` (`:23`) | `X(04)` | alphanumeric | `'GHNP'` | Get hold next in parent |
| 05 | `FUNC-REPL` (`:24`) | `X(04)` | alphanumeric | `'REPL'` | Replace |
| 05 | `FUNC-ISRT` (`:25`) | `X(04)` | alphanumeric | `'ISRT'` | Insert |
| 05 | `FUNC-DLET` (`:26`) | `X(04)` | alphanumeric | `'DLET'` | Delete |
| 05 | `PARMCOUNT` (`:27`) | `S9(05) COMP-5, VALUE +4` | binary | `+4` | Parameter count for `CBLTDLI` |

### `PAUTBPCB.CPY`, `PASFLPCB.CPY`, `PADFLPCB.CPY` — IMS PCB masks

All three share the standard DB-PCB mask; only the prefix and the key-feedback length differ.

| Level | Field (prefix `PAUT-` / `PASFL-` / `PADFL-`) | PIC / USAGE | Derived type | Business meaning |
| ----: | :------------------------------------------- | :---------- | :----------- | :--------------- |
| 01 | `PAUTBPCB` (`PAUTBPCB.CPY:17`) / `PASFLPCB` (`PASFLPCB.CPY:17`) / `PADFLPCB` (`PADFLPCB.CPY:17`) | group | group | PCB mask addressed via the `ENTRY`/`PROCEDURE DIVISION USING` linkage |
| 05 | `…-DBDNAME` (`:18`) | `X(08)` | alphanumeric | DBD name |
| 05 | `…-SEG-LEVEL` (`:19`) | `X(02)` | alphanumeric | Segment level returned |
| 05 | `…-PCB-STATUS` (`:20`) | `X(02)` | alphanumeric | DL/I status code — `'  '` = OK, `'GB'` = end of database, `'GE'` = not found |
| 05 | `…-PCB-PROCOPT` (`:21`) | `X(04)` | alphanumeric | Processing options |
| 05 | `FILLER` (`:22`) | `S9(05) COMP` | binary | Reserved (JCB address) |
| 05 | `…-SEG-NAME` (`:23`) | `X(08)` | alphanumeric | Segment name returned |
| 05 | `…-KEYFB-NAME` (`:24`) | `S9(05) COMP` | binary | Length of key feedback |
| 05 | `…-NUM-SENSEGS` (`:25`) | `S9(05) COMP` | binary | Sensitive segment count |
| 05 | `…-KEYFB` (`:26`) | `X(255)` (`PAUTBPCB`, `PADFLPCB`) / `X(100)` (`PASFLPCB`) | alphanumeric | Concatenated key feedback area |

## 9. Transaction-type Db2 sub-application

### `app/app-transaction-type-db2/cpy/CSDB2RWY.cpy` — Db2 common working storage

| Level | Field | PIC / USAGE | Derived type | Business meaning | Validation values |
| ----: | :---- | :---------- | :----------- | :--------------- | :---------------- |
| 05 | `WS-DB2-COMMON-VARS` (`CSDB2RWY.cpy:21`) | group | group | Shared Db2 status area | |
| 10 | `WS-DISP-SQLCODE` (`:22`) | `----9` | numeric-edited | Displayable SQLCODE | Floating minus, 4 digits |
| 10 | `WS-DUMMY-DB2-INT` (`:23`) | `S9(4) COMP-3, VALUE 0` | packed | Target of the connectivity probe | Receives `SELECT 1 FROM SYSIBM.SYSDUMMY1` (`CSDB2RPY.cpy:24`) |
| 10 | `WS-DB2-PROCESSING-FLAG` (`:25`) | `X(1)` | alphanumeric | Db2 outcome flag | `88 WS-DB2-OK VALUE '0'` (`:26`), `88 WS-DB2-ERROR VALUE '1'` (`:27`) |
| 10 | `WS-DB2-CURRENT-ACTION` (`:28`) | `X(72), VALUE SPACES` | alphanumeric | Description of the SQL being attempted | Prefixed onto the formatted error message |
| 05 | `WS-DSNTIAC-FORMATTED` (`:33`) | group | group | DSNTIAC message buffer | |
| 10 | `WS-DSNTIAC-MESG-LEN` (`:34`) | `S9(4) COMP, VALUE +720` | binary halfword | Buffer length | 10 × 72 |
| 10 | `WS-DSNTIAC-FMTD-TEXT` (`:35`) | group | group | Message text | |
| 15 | `WS-DSNTIAC-FMTD-TEXT-LINE` (`:36`) | `X(72) OCCURS 10 TIMES, VALUE SPACES` | table | Formatted message lines | Fixed 10-line capacity |
| 05 | `WS-DSNTIAC-LRECL` (`:41`) | `S9(4) COMP, VALUE +72` | binary halfword | Line length passed to DSNTIAC | |
| 05 | `WS-DSNTIAC-ERROR` (`:42`) | group | group | DSNTIAC failure info | |
| 10 | `WS-DSNTIAC-ERR-MSG` (`:43`) | `X(10), VALUE 'DSNTIAC CD'` | alphanumeric | Literal prefix | |
| 10 | `WS-DSNTIAC-ERR-CD-X` (`:44`) | `X(02), VALUE SPACES` | alphanumeric | Return code as text | |
| 10 | `WS-DSNTIAC-ERR-CD` (`:45`) | `9(02)` **REDEFINES** `WS-DSNTIAC-ERR-CD-X` | unsigned zoned | Numeric view of the same 2 bytes | |

### `app/app-transaction-type-db2/cpy/CSDB2RPY.cpy` — Db2 common procedures (no data fields)

| Paragraph | Line | Behaviour |
| :-------- | ---: | :-------- |
| `9998-PRIMING-QUERY` | `CSDB2RPY.cpy:21` | Connectivity probe: `SELECT 1 INTO :WS-DUMMY-DB2-INT FROM SYSIBM.SYSDUMMY1 FETCH FIRST 1 ROW ONLY` (`:23`–`:28`); on non-zero SQLCODE sets `WS-DB2-ERROR` and formats a message (`:33`–`:44`) |
| `9999-FORMAT-DB2-MESSAGE` | `:53` | `CALL LIT-DSNTIAC USING DFHEIBLK, DFHCOMMAREA, SQLCA, WS-DSNTIAC-FORMATTED, WS-DSNTIAC-LRECL` (`:57`–`:62`), then builds `WS-RETURN-MSG` from the action text, SQLCODE and DSNTIAC output (`:74`–`:84`) |

This is the only Db2 error-handling path in the sub-application, so both online Db2 programs share the same
diagnostic contract — useful when replacing it with a JDBC/SQLSTATE handler.

## 10. Cross-copybook observations

Findings that matter when turning these layouts into a modern schema:

1. **The cross-reference record is the join hub.** `CARD-XREF-RECORD` (`CVACT03Y.cpy:4`) is the only place
   card, customer and account identifiers meet; both alternate indexes in the application exist to traverse
   it (`CXACAIX` on `XREF-ACCT-ID`, `CARDAIX` on `CARD-ACCT-ID`).
2. **Duplicate record definitions.** `CVCUS01Y` vs. `CUSTREC` (identical layout, one differing name), and
   `CSUSR01Y` vs. `UNUSED1Y` (identical layout, unused). `TRAN-RECORD` (`CVTRA05Y.cpy:4`),
   `DALYTRAN-RECORD` (`CVTRA06Y.cpy:4`) and `TRNX-RECORD` (`COSTM01.CPY:20`) are three names for the same
   350-byte transaction shape, differing only in prefix and key composition.
3. **Type inconsistency for the same business key.** Card number is `X(16)` in the master
   (`CVACT02Y.cpy:5`) but `9(16)` in the COMMAREA (`COCOM01Y.cpy:41`); account id is zoned `9(11)`
   (`CVACT01Y.cpy:5`) but packed `S9(11) COMP-3` in the authorization summary (`CIPAUSMY.cpy:19`). Screen
   fields deliberately keep the alphanumeric/numeric `REDEFINES` pair (`CVCRD01Y.cpy:34`–`:42`) to tell
   "blank" apart from "zero" — a distinction that must survive into any nullable-field mapping.
4. **Money is uniformly 2-decimal fixed point**, either zoned `S9(n)V99` or packed `COMP-3`; there is no
   floating point anywhere. `BigDecimal`-style fixed-scale arithmetic is the faithful target, and the
   interchange copybooks (`CCPAURQY.cpy:27`, `CCPAURLY.cpy:24`) carry the same amounts as edited text.
5. **Field-width mismatches across module boundaries** will truncate on conversion: merchant name
   `X(50)` (`CVTRA05Y.cpy:12`) vs. `X(22)` (`CIPAUDTY.cpy:40`), merchant city `X(50)` vs. `X(13)`,
   transaction id `X(16)` vs. `X(15)` (`CIPAUDTY.cpy:44`), credit limit `S9(10)V99` vs. `S9(09)V99`
   (`CIPAUSMY.cpy:23`).
6. **Dates are almost always character strings** (`X(10)` `YYYY-MM-DD`, `X(06)` `YYMMDD`, `X(04)` `MMYY`),
   validated procedurally by `CSUTLDPY`/`CSUTLDWY` and LE `CEEDAYS` rather than by type. The only true
   date-arithmetic values are the Lilian binaries in `CSUTLDWY.cpy:37`, `:42`.
7. **`REDEFINES` carries real semantics** in three places: numeric views of screen input (`CVCRD01Y`),
   table views of literal menu data (`COMEN02Y.cpy:93`, `COADM02Y.cpy:55`), and the five-way discriminated
   union in `CVEXPORT.cpy:36`–`:91`. Only the last one needs a byte-level converter; the first two map to
   ordinary parsing and a static list.
8. **Reference data lives in code**: `CSLKPCDY` (~1276 literals across five condition names) and the two
   menu tables. Externalising all three removes the largest recompile-triggering copybooks.
9. **Sizes to preserve**: account 300, card 150, xref 50, customer 500, transaction/daily 350, category
   balance 50, disclosure group 50, type/category 60, security 80, export 500 bytes. Trailing `FILLER`
   accounts for the difference between the meaningful fields and these lengths in every case.
