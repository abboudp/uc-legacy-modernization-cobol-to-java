# CardDemo Application Inventory

Static inventory of the AWS Mainframe Modernization **CardDemo** credit-card application as it exists in this
repository. Every statement below was derived by reading the source files listed in
[Scope and method](#scope-and-method); each claim cites `path:line`.

- 44 COBOL programs (31 in `app/cbl/`, 13 in the three optional sub-applications)
- 30 copybooks in `app/cpy/` plus sub-application copybook libraries (field-level detail lives in `DATA_DICTIONARY.md`)
- 38 JCL members in `app/jcl/`, 8 in sub-application `jcl/` libraries, 2 PROCs in `app/proc/`

Related artifacts: `DATA_DICTIONARY.md` (record layouts), `DEPENDENCY_MAP.md` (call graph + dataset lineage),
`HOTSPOT_REPORT.md` (complexity metrics and modernization order).

## Scope and method

| Source group | Location | Count |
| :----------- | :------- | ----: |
| Core COBOL | `app/cbl/*.cbl`, `app/cbl/*.CBL` | 31 |
| Authorization sub-app COBOL | `app/app-authorization-ims-db2-mq/cbl/` | 8 |
| Transaction-type sub-app COBOL | `app/app-transaction-type-db2/cbl/` | 3 |
| VSAM/MQ sub-app COBOL | `app/app-vsam-mq/cbl/` | 2 |
| Core copybooks | `app/cpy/*.cpy`, `app/cpy/*.CPY` | 30 |
| Core JCL | `app/jcl/*.jcl`, `app/jcl/*.JCL` | 38 |
| Sub-app JCL | `app/app-*/jcl/` | 8 |
| PROCs | `app/proc/*.prc` | 2 |

Classification rule used in the tables below:

- **Online** — contains `EXEC CICS` verbs, copies a BMS mapset symbolic map, and/or exchanges the
  `CARDDEMO-COMMAREA` defined in `app/cpy/COCOM01Y.cpy:17`.
- **Batch** — standalone `PROCEDURE DIVISION` driven by `OPEN`/`READ`/`WRITE`/`CLOSE` on `FILE-CONTROL`
  files (or IMS DL/I / Db2 under a batch initiator) and terminating with `GOBACK`.
- **Subroutine** — no own transaction/job identity; entered from another program via `CALL`.

BMS symbolic-map copybooks (`COACTUP`, `COCRDLI`, `COPAU00`, …) live in `app/cpy-bms/` and IBM-supplied
copybooks (`DFHAID`, `DFHBMSCA`, `CMQV`, …) are external; both are listed in the "Copybooks referenced"
column because they appear in `COPY` statements, but they are outside the copybook analysis scope of
`DATA_DICTIONARY.md`.

## 1. Core programs — `app/cbl/`

### 1.1 Batch programs

| Filename | Directory | Purpose | Classification | Key I/O | Copybooks referenced |
| :------- | :-------- | :------ | :------------- | :------ | :------------------- |
| `CBACT01C.cbl` | `app/cbl/` | "READ THE ACCOUNT FILE AND WRITE INTO FILES" (`CBACT01C.cbl:5`) — reads the account KSDS and reformats it into a fixed record file, an array/table file and a variable-length file; used as the COMP-3/array/VB demo reader. | Batch | IN: `ACCTFILE` (indexed, `CBACT01C.cbl:29`); OUT: `OUTFILE` (`:35`), `ARRYFILE` (`:40`), `VBRCFILE` (`:45`) | `CVACT01Y`, `CODATECN` |
| `CBACT02C.cbl` | `app/cbl/` | "Read and print card data file" (`CBACT02C.cbl:5`) — sequential dump of the card master. | Batch | IN: `CARDFILE` indexed KSDS (`CBACT02C.cbl:29`) | `CVACT02Y` |
| `CBACT03C.cbl` | `app/cbl/` | "Read and print account cross reference data file" (`CBACT03C.cbl:5`). | Batch | IN: `XREFFILE` indexed KSDS (`CBACT03C.cbl:29`) | `CVACT03Y` |
| `CBACT04C.cbl` | `app/cbl/` | "This is a interest calculator program" (`CBACT04C.cbl:5`) — walks transaction-category balances, resolves the account's disclosure group, computes monthly interest, updates the account and writes interest transactions. | Batch | IN: `TCATBALF` (`:28`), `XREFFILE` (`:34`), `DISCGRP` (`:47`); I-O: `ACCTFILE` (`:41`); OUT: `TRANSACT` (`:53`) | `CVACT01Y`, `CVACT03Y`, `CVTRA01Y`, `CVTRA02Y`, `CVTRA05Y` |
| `CBCUS01C.cbl` | `app/cbl/` | "Read and print customer data file" (`CBCUS01C.cbl:5`). | Batch | IN: `CUSTFILE` indexed KSDS (`CBCUS01C.cbl:29`) | `CVCUS01Y` |
| `CBEXPORT.cbl` | `app/cbl/` | "Export Customer Data for Branch Migration" (`CBEXPORT.cbl:8`) — reads all five master files and writes one typed export record per source record. | Batch | IN: `CUSTFILE` (`:35`), `ACCTFILE` (`:41`), `XREFFILE` (`:47`), `TRANSACT` (`:53`), `CARDFILE` (`:59`); OUT: `EXPFILE` (`:65`) | `CVCUS01Y`, `CVACT01Y`, `CVACT03Y`, `CVTRA05Y`, `CVACT02Y`, `CVEXPORT` |
| `CBIMPORT.cbl` | `app/cbl/` | "Import Customer Data from Branch Migration Export" (`CBIMPORT.cbl:8`) — demultiplexes the export file back into per-entity sequential files plus an error file. | Batch | IN: `EXPFILE` (`:37`); OUT: `CUSTOUT` (`:43`), `ACCTOUT` (`:48`), `XREFOUT` (`:53`), `TRNXOUT` (`:58`), `CARDOUT` (`:63`), `ERROUT` (`:68`) | `CVCUS01Y`, `CVACT01Y`, `CVACT03Y`, `CVTRA05Y`, `CVACT02Y`, `CVEXPORT` |
| `CBTRN01C.cbl` | `app/cbl/` | "Post the records from daily transaction file" (`CBTRN01C.cbl:5`) — read-only validation pass: for each daily transaction it looks up the xref, card, account and customer records and reports lookup failures. Not wired into any JCL in this repo (see [3.4](#34-readme-run-order-vs-actual-jcl)). | Batch | IN: `DALYTRAN` (`:29`), `CUSTFILE` (`:34`), `XREFFILE` (`:40`), `CARDFILE` (`:46`), `ACCTFILE` (`:52`), `TRANFILE` (`:58`) | `CVTRA05Y`, `CVTRA06Y`, `CVACT01Y`, `CVACT02Y`, `CVACT03Y`, `CVCUS01Y` |
| `CBTRN02C.cbl` | `app/cbl/` | "Post the records from daily transaction file" (`CBTRN02C.cbl:5`) — the posting core: validates each daily transaction against xref/account, enforces credit limit, updates account balances and transaction-category balances, writes accepted transactions and rejects. | Batch | IN: `DALYTRAN` (`:29`), `XREFFILE` (`:40`); I-O: `TRANFILE` (`:34`), `ACCTFILE` (`:51`), `TCATBALF` (`:57`); OUT: `DALYREJS` (`:46`) | `CVTRA05Y`, `CVTRA06Y`, `CVTRA01Y`, `CVACT01Y`, `CVACT03Y` |
| `CBTRN03C.cbl` | `app/cbl/` | "Print the transaction detail report" (`CBTRN03C.cbl:5`) — date-windowed transaction detail report with page/account/grand totals, decoding type and category descriptions. | Batch | IN: `TRANFILE` (`:29`), `CARDXREF` (`:33`), `TRANTYPE` (`:39`), `TRANCATG` (`:45`), `DATEPARM` (`:55`); OUT: `TRANREPT` (`:51`) | `CVTRA03Y`, `CVTRA04Y`, `CVTRA05Y`, `CVTRA07Y`, `CVACT03Y` |
| `CBSTM03A.CBL` | `app/cbl/` | "Print Account Statements from Transaction data" (`CBSTM03A.CBL:8`) — statement driver; obtains customer/account/xref/transaction data through the `CBSTM03B` I/O subroutine and emits plain-text and HTML statements. | Batch | OUT: `STMTFILE` (`:39`), `HTMLFILE` (`:40`); all master-file access delegated to `CBSTM03B` | `CUSTREC`, `CVACT01Y`, `CVACT03Y`, `COSTM01` |
| `CBSTM03B.CBL` | `app/cbl/` | "Does file processing related to Transact Report" (`CBSTM03B.CBL:8`) — generic open/read/close service routine driven by a request area passed from `CBSTM03A`. | Subroutine (batch) | IN/I-O: `TRNXFILE` (`:31`), `XREFFILE` (`:37`), `CUSTFILE` (`:43`), `ACCTFILE` (`:49`) | none (record areas passed in `LINKAGE SECTION`) |
| `COBSWAIT.cbl` | `app/cbl/` | "UTILITY PROGRAM TO WAIT (PARM IN CENTISECONDS)" (`COBSWAIT.cbl:5`) — thin wrapper that calls `MVSWAIT`; used by `WAITSTEP`. | Batch (utility) | none | none |
| `CSUTLDTC.cbl` | `app/cbl/` | "CALL TO CEEDAYS" (`CSUTLDTC.cbl:2`) — date-validation service: converts a date with a caller-supplied picture through LE `CEEDAYS` and maps the feedback code to a return code. | Subroutine (called from online) | none | none |

### 1.2 Online (CICS) programs

All programs in this section pass control with `EXEC CICS XCTL` / `RETURN TRANSID` and share the
`CARDDEMO-COMMAREA` (`app/cpy/COCOM01Y.cpy:17`). CICS file names are held in working-storage literals; the
cited line is the literal declaration.

| Filename | Directory | Purpose | Classification | Key I/O | Copybooks referenced |
| :------- | :-------- | :------ | :------------- | :------------- | :------------------- |
| `COSGN00C.cbl` | `app/cbl/` | "Signon Screen for the CardDemo Application" (`COSGN00C.cbl:5`) — validates userid/password against the security file and routes admins vs. regular users. | Online | READ `USRSEC` (`COSGN00C.cbl:39`) | `COCOM01Y`, `COSGN00`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSUSR01Y`, `DFHAID`, `DFHBMSCA` |
| `COMEN01C.cbl` | `app/cbl/` | "Main Menu for the Regular users" (`COMEN01C.cbl:5`) — table-driven menu; the selected option's program name is XCTL'd. | Online | none (menu only) | `COCOM01Y`, `COMEN01`, `COMEN02Y`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSUSR01Y`, `DFHAID`, `DFHBMSCA` |
| `COADM01C.cbl` | `app/cbl/` | "Admin Menu for Admin users" (`COADM01C.cbl:5`) — same pattern over the admin option table. | Online | none (menu only) | `COCOM01Y`, `COADM01`, `COADM02Y`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSUSR01Y`, `DFHAID`, `DFHBMSCA` |
| `COACTVWC.cbl` | `app/cbl/` | "Accept and process Account View request" (`COACTVWC.cbl:4`) — resolves account → xref → customer and displays the composite account view. | Online | READ `ACCTDAT` (`COACTVWC.cbl:184`), `CARDDAT` (`:186`), `CUSTDAT` (`:188`), `CARDAIX` (`:190`), `CXACAIX` (`:192`) | `COACTVW`, `COCOM01Y`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSMSG02Y`, `CSSTRPFY`, `CSUSR01Y`, `CVACT01Y`, `CVACT02Y`, `CVACT03Y`, `CVCRD01Y`, `CVCUS01Y`, `DFHAID`, `DFHBMSCA` |
| `COACTUPC.cbl` | `app/cbl/` | "Accept and process ACCOUNT UPDATE" (`COACTUPC.cbl:4`) — the largest program: full field-level edit of account + customer data, optimistic-locking re-read/compare, then `REWRITE` of both records under `SYNCPOINT`. | Online | READ/REWRITE `ACCTDAT` (`COACTUPC.cbl:573`), `CUSTDAT` (`:575`), READ `CARDDAT` (`:577`), `CARDAIX` (`:579`), `CXACAIX` (`:581`) | `COACTUP`, `COCOM01Y`, `COTTL01Y`, `CSDAT01Y`, `CSLKPCDY`, `CSMSG01Y`, `CSMSG02Y`, `CSSETATY`, `CSSTRPFY`, `CSUSR01Y`, `CSUTLDPY`, `CSUTLDWY`, `CVACT01Y`, `CVACT03Y`, `CVCRD01Y`, `CVCUS01Y`, `DFHAID`, `DFHBMSCA` |
| `COCRDLIC.cbl` | `app/cbl/` | "List Credit Cards" (`COCRDLIC.cbl:4`) — browse/paging list of cards (7 rows/page) with optional account filter and select-for-view/update. | Online | STARTBR/READNEXT/READPREV/ENDBR `CARDDAT` (`COCRDLIC.cbl:213`) and `CARDAIX` path (`:215`) | `COCOM01Y`, `COCRDLI`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSSTRPFY`, `CSUSR01Y`, `CVACT02Y`, `CVCRD01Y`, `DFHAID`, `DFHBMSCA` |
| `COCRDSLC.cbl` | `app/cbl/` | "Accept and process credit card detail request" (`COCRDSLC.cbl:4`) — single-card detail view keyed by account and/or card number. | Online | READ `CARDDAT` (`COCRDSLC.cbl:187`), `CARDAIX` (`:189`) | `COCOM01Y`, `COCRDSL`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSMSG02Y`, `CSSTRPFY`, `CSUSR01Y`, `CVACT02Y`, `CVCRD01Y`, `CVCUS01Y`, `DFHAID`, `DFHBMSCA` |
| `COCRDUPC.cbl` | `app/cbl/` | "Accept and process credit card detail request" (`COCRDUPC.cbl:4`) — card update: edits name/expiry/status, re-reads for change detection and `REWRITE`s the card record. | Online | READ/REWRITE `CARDDAT` (`COCRDUPC.cbl:251`), READ `CARDAIX` (`:253`) | `COCOM01Y`, `COCRDUP`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSMSG02Y`, `CSSTRPFY`, `CSUSR01Y`, `CVACT02Y`, `CVCRD01Y`, `CVCUS01Y`, `DFHAID`, `DFHBMSCA` |
| `COTRN00C.cbl` | `app/cbl/` | "List Transactions from TRANSACT file" (`COTRN00C.cbl:5`) — paged transaction browse with select-to-view. | Online | STARTBR/READNEXT/READPREV `TRANSACT` (`COTRN00C.cbl:39`) | `COCOM01Y`, `COTRN00`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CVTRA05Y`, `DFHAID`, `DFHBMSCA` |
| `COTRN01C.cbl` | `app/cbl/` | "View a Transaction from TRANSACT file" (`COTRN01C.cbl:5`). | Online | READ `TRANSACT` (`COTRN01C.cbl:39`) | `COCOM01Y`, `COTRN01`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CVTRA05Y`, `DFHAID`, `DFHBMSCA` |
| `COTRN02C.cbl` | `app/cbl/` | "Add a new Transaction to TRANSACT file" (`COTRN02C.cbl:5`) — validates account/card via xref, edits amount and dates (via `CSUTLDTC`), assigns the next transaction id and writes the record. | Online | READ `ACCTDAT` (`COTRN02C.cbl:40`), `CCXREF` (`:41`), `CXACAIX` (`:42`); STARTBR/READPREV/WRITE `TRANSACT` (`:39`) | `COCOM01Y`, `COTRN02`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CVACT01Y`, `CVACT03Y`, `CVTRA05Y`, `DFHAID`, `DFHBMSCA` |
| `COBIL00C.cbl` | `app/cbl/` | "Bill Payment - Pay account balance in full" (`COBIL00C.cbl:5`) — reads the account, writes a payment transaction for the full current balance and zeroes the balance. | Online | READ `CXACAIX` (`COBIL00C.cbl:42`), READ/REWRITE `ACCTDAT` (`:41`), STARTBR/READPREV/WRITE `TRANSACT` (`:40`) | `COCOM01Y`, `COBIL00`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CVACT01Y`, `CVACT03Y`, `CVTRA05Y`, `DFHAID`, `DFHBMSCA` |
| `CORPT00C.cbl` | `app/cbl/` | "Print Transaction reports by submitting batch job" (`CORPT00C.cbl:5`) — builds monthly/yearly/custom date ranges (validated through `CSUTLDTC`) and submits the report JCL to the internal reader through a CICS TD queue. | Online | TDQ write of generated JCL; no VSAM I/O | `COCOM01Y`, `CORPT00`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CVTRA05Y`, `DFHAID`, `DFHBMSCA` |
| `COUSR00C.cbl` | `app/cbl/` | "List all users from USRSEC file" (`COUSR00C.cbl:5`) — admin user browse with select-for-update/delete. | Online | STARTBR/READNEXT/READPREV `USRSEC` (`COUSR00C.cbl:39`) | `COCOM01Y`, `COUSR00`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSUSR01Y`, `DFHAID`, `DFHBMSCA` |
| `COUSR01C.cbl` | `app/cbl/` | "Add a new Regular/Admin user to USRSEC file" (`COUSR01C.cbl:5`). | Online | WRITE `USRSEC` | `COCOM01Y`, `COUSR01`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSUSR01Y`, `DFHAID`, `DFHBMSCA` |
| `COUSR02C.cbl` | `app/cbl/` | "Update a user in USRSEC file" (`COUSR02C.cbl:5`). | Online | READ/REWRITE `USRSEC` | `COCOM01Y`, `COUSR02`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSUSR01Y`, `DFHAID`, `DFHBMSCA` |
| `COUSR03C.cbl` | `app/cbl/` | "Delete a user from USRSEC file" (`COUSR03C.cbl:5`). | Online | READ/DELETE `USRSEC` | `COCOM01Y`, `COUSR03`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSUSR01Y`, `DFHAID`, `DFHBMSCA` |

## 2. Sub-application programs

### 2.1 `app/app-authorization-ims-db2-mq/cbl/` — pending authorizations (CICS + IMS DB + Db2 + MQ)

| Filename | Directory | Purpose | Classification | Key I/O | Copybooks referenced |
| :------- | :-------- | :------ | :------------- | :------ | :------------------- |
| `COPAUA0C.cbl` | `app/app-authorization-ims-db2-mq/cbl/` | "Card Authorization Decision Program" (`COPAUA0C.cbl:5`) — MQ-triggered: reads an authorization request from a queue, validates card/account/customer, applies limit and status rules, stores the pending authorization in IMS and replies on the reply queue. | Online (CICS, MQ-driven) | MQ `MQOPEN`/`MQGET`/`MQPUT1`/`MQCLOSE`; CICS READ `ACCTDAT` (`COPAUA0C.cbl:35`), `CUSTDAT` (`:36`), `CARDDAT` (`:37`), `CARDAIX` (`:38`), `CCXREF` (`:39`); IMS DL/I via `CBLTDLI` | `CCPAUERY`, `CCPAURLY`, `CCPAURQY`, `CIPAUDTY`, `CIPAUSMY`, `CVACT01Y`, `CVACT03Y`, `CVCUS01Y`, `CMQV`, `CMQGMOV`, `CMQMDV`, `CMQODV`, `CMQPMOV`, `CMQTML` |
| `COPAUS0C.cbl` | `app/app-authorization-ims-db2-mq/cbl/` | "Summary View of Authoriation Messages" (`COPAUS0C.cbl:5`) — 3270 summary list of pending authorizations for an account, with drill-down to the detail program. | Online | CICS READ `ACCTDAT` (`COPAUS0C.cbl:38`), `CUSTDAT` (`:39`), `CARDDAT` (`:40`), `CXACAIX` (`:41`), `CCXREF` (`:42`); IMS DL/I for authorization segments | `CIPAUDTY`, `CIPAUSMY`, `COCOM01Y`, `COPAU00`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSMSG02Y`, `CVACT01Y`, `CVACT02Y`, `CVACT03Y`, `CVCUS01Y`, `DFHAID`, `DFHBMSCA` |
| `COPAUS1C.cbl` | `app/app-authorization-ims-db2-mq/cbl/` | "Detail View of Authorization Message" (`COPAUS1C.cbl:5`) — single authorization detail; PF-key path LINKs the fraud-marking program. | Online | IMS DL/I authorization segment reads; `EXEC CICS LINK` to fraud program | `CIPAUDTY`, `CIPAUSMY`, `COCOM01Y`, `COPAU01`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSMSG02Y`, `DFHAID`, `DFHBMSCA` |
| `COPAUS2C.cbl` | `app/app-authorization-ims-db2-mq/cbl/` | "Mark Authorization Message Fraud" (`COPAUS2C.cbl:5`) — LINKed sub-program that inserts/updates the fraud table in Db2. | Online (LINKed) | Db2 `CARDDEMO.AUTHFRDS` (2 `EXEC SQL` statements) | `CIPAUDTY` |
| `CBPAUP0C.cbl` | `app/app-authorization-ims-db2-mq/cbl/` | "Delete Expired Pending Authoriation Messages" (`CBPAUP0C.cbl:5`) — IMS BMP purge of expired pending authorizations, driven by a `SYSIN` parameter card. | Batch (IMS BMP) | IMS DL/I on the pending-authorization database (`PSBPAUTB`, `app/app-authorization-ims-db2-mq/jcl/CBPAUP0J.jcl:25`) | `CIPAUDTY`, `CIPAUSMY` |
| `PAUDBLOD.CBL` | `app/app-authorization-ims-db2-mq/cbl/` | Loads the IMS pending-authorization database from two sequential files (root + child segments). | Batch (IMS DLI) | IN: `INFILE1` (`PAUDBLOD.CBL:26`), `INFILE2` (`:32`); IMS `ISRT` via `CBLTDLI` | `CIPAUDTY`, `CIPAUSMY`, `IMSFUNCS`, `PAUTBPCB` |
| `PAUDBUNL.CBL` | `app/app-authorization-ims-db2-mq/cbl/` | Unloads the IMS pending-authorization database to two sequential files. | Batch (IMS DLI) | OUT: `OUTFIL1` (`PAUDBUNL.CBL:26`), `OUTFIL2` (`:32`); IMS `GN`/`GNP` via `CBLTDLI` | `CIPAUDTY`, `CIPAUSMY`, `IMSFUNCS`, `PAUTBPCB` |
| `DBUNLDGS.CBL` | `app/app-authorization-ims-db2-mq/cbl/` | Unloads the same database to GSAM output PCBs instead of QSAM files. | Batch (IMS DLI + GSAM) | GSAM PCBs `PASFLPCB`/`PADFLPCB`; IMS DL/I via `CBLTDLI` | `CIPAUDTY`, `CIPAUSMY`, `IMSFUNCS`, `PAUTBPCB`, `PASFLPCB`, `PADFLPCB` |

### 2.2 `app/app-transaction-type-db2/cbl/` — transaction-type maintenance (Db2)

| Filename | Directory | Purpose | Classification | Key I/O | Copybooks referenced |
| :------- | :-------- | :------ | :------------- | :------ | :------------------- |
| `COTRTLIC.cbl` | `app/app-transaction-type-db2/cbl/` | "List Transaction Type for updates and deletes … Demonstrates paging with cursors in Db2" (`COTRTLIC.cbl:4`–`:6`). | Online | Db2 `CARDDEMO.TRANSACTION_TYPE` — declared/opened cursors and singleton `SELECT`s (`COTRTLIC.cbl:340`, `:356`, `:1804`) | `COCOM01Y`, `COTRTLI`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSSTRPFY`, `CSUSR01Y`, `CVACT02Y`, `CVCRD01Y`, `DFHAID`, `DFHBMSCA` |
| `COTRTUPC.cbl` | `app/app-transaction-type-db2/cbl/` | "Accept and process TRANSACTION TYPE UPDATE" (`COTRTUPC.cbl:4`) — add/change/delete of a transaction type with field edits. | Online | Db2 `CARDDEMO.TRANSACTION_TYPE` `SELECT`/`INSERT`/`UPDATE`/`DELETE` (e.g. `COTRTUPC.cbl:1476`) | `COCOM01Y`, `COTRTUP`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSMSG02Y`, `CSSETATY`, `CSSTRPFY`, `CSUSR01Y`, `CSUTLDWY`, `CVCRD01Y`, `DFHAID`, `DFHBMSCA` |
| `COBTUPDT.cbl` | `app/app-transaction-type-db2/cbl/` | "Update Transaction type based on user input" (`COBTUPDT.cbl:4`) — batch loader that applies a sequential input file of transaction-type changes to Db2. | Batch (Db2, run under `IKJEFT01`) | IN: `INPFILE` (`COBTUPDT.cbl:31`); Db2 `CARDDEMO.TRANSACTION_TYPE` (3 `EXEC SQL`) | none |

### 2.3 `app/app-vsam-mq/cbl/` — MQ-triggered VSAM services

| Filename | Directory | Purpose | Classification | Key I/O | Copybooks referenced |
| :------- | :-------- | :------ | :------------- | :------ | :------------------- |
| `COACCT01.cbl` | `app/app-vsam-mq/cbl/` | `PROGRAM-ID. COACCT01 IS INITIAL` (`COACCT01.cbl:2`) — MQ request/reply service: `MQGET`s an account-inquiry message, reads the account record and `MQPUT`s the formatted reply. | Online (CICS, MQ-triggered) | MQ `MQOPEN`/`MQGET`/`MQPUT`/`MQCLOSE`; CICS READ `ACCTDAT` (`COACCT01.cbl:115`) | `CVACT01Y`, `CMQV`, `CMQGMOV`, `CMQMDV`, `CMQODV`, `CMQPMOV`, `CMQTML` |
| `CODATE01.cbl` | `app/app-vsam-mq/cbl/` | `PROGRAM-ID. CODATE01 IS INITIAL` (`CODATE01.cbl:2`) — MQ request/reply date-conversion service (no file I/O). | Online (CICS, MQ-triggered) | MQ `MQOPEN`/`MQGET`/`MQPUT`/`MQCLOSE` only | `CMQV`, `CMQGMOV`, `CMQMDV`, `CMQODV`, `CMQPMOV`, `CMQTML` |

## 3. JCL catalog

Step → program → DD dataset mapping, taken from the JCL members themselves. Utility-only DDs
(`SYSPRINT`, `SYSOUT`, `SYSUDUMP`, `STEPLIB`, `ISFOUT`/`CMDOUT`) are omitted unless they carry meaning.
Instream `SYSIN` control statements are summarised.

### 3.1 Application-program jobs (`app/jcl/`)

| Job (member) | Step | Program | DD → dataset | Interpretation |
| :----------- | :--- | :------ | :----------- | :------------- |
| `POSTTRAN.jcl` | `STEP15` (`:23`) | `CBTRN02C` | `TRANFILE`→`…TRANSACT.VSAM.KSDS` (`:28`), `DALYTRAN`→`…DALYTRAN.PS` (`:30`), `XREFFILE`→`…CARDXREF.VSAM.KSDS` (`:32`), `DALYREJS`→`…DALYREJS(+1)` new GDG (`:34`), `ACCTFILE`→`…ACCTDATA.VSAM.KSDS` (`:39`), `TCATBALF`→`…TCATBALF.VSAM.KSDS` (`:41`) | Core posting run: in = daily transactions + xref; in/out = transaction master, accounts, category balances; out = rejects GDG. |
| `INTCALC.jcl` | `STEP15` (`:22`) | `CBACT04C`, `PARM='2022071800'` | `TCATBALF` (`:27`), `XREFFILE` (`:29`), `XREFFIL1`→`…CARDXREF.VSAM.AIX.PATH` (`:31`), `ACCTFILE` (`:33`), `DISCGRP` (`:35`), `TRANSACT`→`…SYSTRAN(+1)` new GDG (`:37`) | Interest calculation; writes system-generated interest transactions to a new `SYSTRAN` generation and updates accounts in place. |
| `CREASTMT.JCL` | `DELDEF01` (`:22`) | `IDCAMS` | instream delete/define of `…TRXFL` KSDS | Rebuild the statement work KSDS. |
| | `STEP010` (`:44`) | `SORT` | `SORTIN`→`…TRANSACT.VSAM.KSDS` (`:45`), `SORTOUT`→`…TRXFL.SEQ` (`:48`) | Sort transactions into card/transaction order for statements. |
| | `STEP020` (`:56`) | `IDCAMS` | `INFILE`→`…TRXFL.SEQ` (`:58`), `OUTFILE`→`…TRXFL.VSAM.KSDS` (`:59`) | REPRO sorted transactions into the statement KSDS. |
| | `STEP030` (`:66`) | `IEFBR14` | `HTMLFILE`, `STMTFILE` (`:67`, `:72`) | Delete previous statement outputs. |
| | `STEP040` (`:79`) | `CBSTM03A` | `TRNXFILE`→`…TRXFL.VSAM.KSDS` (`:83`), `XREFFILE` (`:84`), `ACCTFILE` (`:85`), `CUSTFILE` (`:86`), `STMTFILE`→`…STATEMNT.PS` (`:87`), `HTMLFILE`→`…STATEMNT.HTML` (`:92`) | Produce text + HTML statements. |
| `TRANREPT.jcl` | `STEP05R` (`:23`) | `PROC=REPROC` → `IDCAMS` | `PRC001.FILEIN`→`…TRANSACT.VSAM.KSDS` (`:26`), `PRC001.FILEOUT`→`…TRANSACT.BKUP(+1)` (`:29`) | Flatten the transaction KSDS to a backup generation. |
| | `STEP05R` (`:37`) | `SORT` | `SORTIN`→`…TRANSACT.BKUP(+1)` (`:38`), `SORTOUT`→`…TRANSACT.DALY(+1)` (`:51`) | Filter/sort to the reporting generation. Note the duplicated step name `STEP05R` in this member. |
| | `STEP10R` (`:59`) | `CBTRN03C` | `TRANFILE`→`…TRANSACT.DALY(+1)` (`:65`), `CARDXREF` (`:67`), `TRANTYPE` (`:69`), `TRANCATG` (`:71`), `DATEPARM`→`…DATEPARM` (`:73`), `TRANREPT`→`…TRANREPT(+1)` (`:76`) | Transaction detail report for the date range in `DATEPARM`. |
| `PRTCATBL.jcl` | `DELDEF` (`:21`) | `IEFBR14` | `THEFILE`→`…TCATBALF.REPT` (`:22`) | Delete previous report. |
| | `STEP05R` (`:29`) | `PROC=REPROC` → `IDCAMS` | `PRC001.FILEIN`→`…TCATBALF.VSAM.KSDS` (`:32`), `PRC001.FILEOUT`→`…TCATBALF.BKUP(+1)` (`:35`) | Unload category balances. |
| | `STEP10R` (`:43`) | `SORT` | `SORTIN`→`…TCATBALF.BKUP(+1)` (`:44`), `SORTOUT`→`…TCATBALF.REPT` (`:59`) | Formatted category-balance listing (utility only, no COBOL). |
| `COMBTRAN.jcl` | `STEP05R` (`:22`) | `SORT` | `SORTIN`→`…TRANSACT.BKUP(0)` + `…SYSTRAN(0)` (`:23`, `:25`), `SORTOUT`→`…TRANSACT.COMBINED(+1)` (`:33`) | Merge posted transactions with system (interest) transactions. |
| | `STEP10` (`:41`) | `IDCAMS` | `TRANSACT`→`…TRANSACT.COMBINED(+1)` (`:43`), `TRANVSAM`→`…TRANSACT.VSAM.KSDS` (`:45`) | REPRO merged file back into the transaction master. |
| `TRANBKP.jcl` | `STEP05R` (`:23`) | `PROC=REPROC` → `IDCAMS` | `PRC001.FILEIN`→`…TRANSACT.VSAM.KSDS` (`:26`), `PRC001.FILEOUT`→`…TRANSACT.BKUP(+1)` (`:29`) | Backup transaction master. |
| | `STEP05` (`:37`), `STEP10` (`:51`) | `IDCAMS` | instream | Delete/define the transaction KSDS (initial create) — this is why the README lists `TRANBKP` both as "Creates Transaction database" and later as "Backup Transaction database". |
| `READACCT.jcl` | `PREDEL` (`:22`) | `IEFBR14` | deletes `…ACCTDATA.PSCOMP`, `.ARRYPS`, `.VBPS` (`:23`–`:27`) | Housekeeping. |
| | `STEP05` (`:32`) | `CBACT01C` | `ACCTFILE`→`…ACCTDATA.VSAM.KSDS` (`:35`), `OUTFILE`→`…ACCTDATA.PSCOMP` (`:37`), `ARRYFILE`→`…ACCTDATA.ARRYPS` (`:41`), `VBRCFILE`→`…ACCTDATA.VBPS` (`:45`) | Account extract in three formats. |
| `READCARD.jcl` | `STEP05` (`:22`) | `CBACT02C` | `CARDFILE`→`…CARDDATA.VSAM.KSDS` (`:25`) | Card master listing. |
| `READCUST.jcl` | `STEP05` (`:21`) | `CBCUS01C` | `CUSTFILE`→`…CUSTDATA.VSAM.KSDS` (`:24`) | Customer master listing. |
| `READXREF.jcl` | `STEP05` (`:22`) | `CBACT03C` | `XREFFILE`→`…CARDXREF.VSAM.KSDS` (`:25`) | Cross-reference listing. |
| `CBEXPORT.jcl` | `STEP01` (`:24`) | `IDCAMS` | instream define of `…EXPORT.DATA` | Allocate the export file. |
| | `STEP02` (`:43`) | `CBEXPORT` | `CUSTFILE` (`:49`), `ACCTFILE` (`:51`), `XREFFILE` (`:53`), `TRANSACT` (`:55`), `CARDFILE` (`:57`), `EXPFILE`→`…EXPORT.DATA` (`:62`) | Branch-migration export. |
| `CBIMPORT.jcl` | `STEP01` (`:22`) | `CBIMPORT` | `EXPFILE`→`…EXPORT.DATA` (`:28`), `CUSTOUT` (`:33`), `ACCTOUT` (`:38`), `XREFOUT` (`:43`), `TRNXOUT` (`:48`), `ERROUT`→`…IMPORT.ERRORS` (`:56`) | Branch-migration import; note the `CARDOUT` DD used by `CBIMPORT.cbl:63` is **not** coded in this JCL. |
| `WAITSTEP.jcl` | `WAIT` (`:22`) | `COBSWAIT` | `SYSIN` instream centiseconds (`:25`) | Scheduler pacing step. |
| `TXT2PDF1.JCL` | `TXT2PDF` (`:24`) | `IKJEFT1B` (REXX `TXT2PDF`) | `INDD`→`…STATEMNT.PS` (`:33`) | Convert the text statement to PDF. |

### 3.2 File setup / utility jobs (`app/jcl/`)

| Job | Steps → program | Datasets touched | Purpose |
| :-- | :-------------- | :--------------- | :------ |
| `CLOSEFIL.jcl` | `CLCIFIL`→`SDSF` (`:22`) | CICS FCT entries via `ISFIN` MODIFY commands | Close CICS files before batch. |
| `OPENFIL.jcl` | `OPCIFIL`→`SDSF` (`:22`) | as above | Re-open CICS files after batch. |
| `ACCTFILE.jcl` | `STEP05`, `STEP10`, `STEP15`→`IDCAMS` (`:22`, `:33`, `:54`) | `ACCTDATA`→`…ACCTDATA.PS` (`:56`), `ACCTVSAM`→`…ACCTDATA.VSAM.KSDS` (`:58`) | Delete/define/REPRO the account KSDS. |
| `CARDFILE.jcl` | `CLCIFIL`→`SDSF`; `STEP05`/`STEP10`/`STEP15`/`STEP40`/`STEP50`/`STEP60`→`IDCAMS`; `OPCIFIL`→`SDSF` | `CARDDATA`→`…CARDDATA.PS` (`:70`), `CARDVSAM`→`…CARDDATA.VSAM.KSDS` (`:72`), plus AIX + PATH define/BLDINDEX (`:80`–`:107`) | Load card KSDS and build the `CARDAIX` alternate index. |
| `CUSTFILE.jcl` | `CLCIFIL`; `STEP05`/`STEP10`/`STEP15`→`IDCAMS`; `OPCIFIL` | `CUSTDATA`→`…CUSTDATA.PS` (`:66`), `CUSTVSAM`→`…CUSTDATA.VSAM.KSDS` (`:68`) | Load customer KSDS. |
| `XREFFILE.jcl` | `STEP05`–`STEP30`→`IDCAMS` | `XREFDATA`→`…CARDXREF.PS` (`:59`), `XREFVSAM`→`…CARDXREF.VSAM.KSDS` (`:61`), AIX/PATH/BLDINDEX (`:69`–`:97`) | Load xref KSDS and build `CXACAIX`. |
| `TRANFILE.jcl` | `CLCIFIL`; `STEP05`–`STEP30`→`IDCAMS`; `OPCIFIL` | `TRANSACT`→`…DALYTRAN.PS.INIT` (`:69`), `TRANVSAM`→`…TRANSACT.VSAM.KSDS` (`:71`) | Initialise the transaction master from seed data and define its AIX/PATH. |
| `TRANIDX.jcl` | `STEP20`/`STEP25`/`STEP30`→`IDCAMS` (`:22`, `:39`, `:49`) | transaction AIX + PATH | Define/build the transaction alternate index only. |
| `TRANTYPE.jcl` | `STEP05`/`STEP10`/`STEP15`→`IDCAMS` | `TRANTYPE`→`…TRANTYPE.PS` (`:56`), `TTYPVSAM`→`…TRANTYPE.VSAM.KSDS` (`:58`) | Load transaction-type KSDS. |
| `TRANCATG.jcl` | `STEP05`/`STEP10`/`STEP15`→`IDCAMS` | `TRANCATG`→`…TRANCATG.PS` (`:56`), `TCATVSAM`→`…TRANCATG.VSAM.KSDS` (`:58`) | Load transaction-category KSDS. |
| `DISCGRP.jcl` | `STEP05`/`STEP10`/`STEP15`→`IDCAMS` | `DISCGRP`→`…DISCGRP.PS` (`:56`), `DISCVSAM`→`…DISCGRP.VSAM.KSDS` (`:58`) | Load disclosure-group KSDS. |
| `TCATBALF.jcl` | `STEP05`/`STEP10`/`STEP15`→`IDCAMS` | `TCATBAL`→`…TCATBALF.PS` (`:56`), `TCATBALV`→`…TCATBALF.VSAM.KSDS` (`:58`) | Load transaction-category-balance KSDS. |
| `DUSRSECJ.jcl` | `PREDEL`→`IEFBR14`; `STEP01`→`IEBGENER`; `STEP02`/`STEP03`→`IDCAMS` | `SYSUT2`→`…USRSEC.PS` (`:46`), `IN`/`OUT`→`…USRSEC.PS` / `…USRSEC.VSAM.KSDS` (`:82`, `:83`) | Build the signon security KSDS from instream user records. |
| `ESDSRRDS.jcl` | `PREDEL`→`IEFBR14`; `STEP01`→`IEBGENER`; `STEP02`–`STEP05`→`IDCAMS` | `…ESDSRRDS.PS`, `…USRSEC.VSAM.ESDS` (`:82`), `…USRSEC.VSAM.RRDS` (`:116`) | ESDS/RRDS variants of the security file (demo of VSAM organisations). |
| `DALYREJS.jcl` | `STEP05`→`IDCAMS` (`:21`) | GDG base `…DALYREJS` | Define the reject GDG used by `POSTTRAN`. |
| `REPTFILE.jcl` | `STEP05`→`IDCAMS` (`:22`) | GDG base `…TRANREPT` | Define the report GDG used by `TRANREPT`. |
| `DEFGDGB.jcl` | `STEP05`→`IDCAMS` (`:21`) | GDG bases for transaction backups | GDG definitions. |
| `DEFGDGD.jcl` | `STEP10`–`STEP60`→`IDCAMS`/`IEBGENER` | `…TRANTYPE.BKUP(+1)` (`:39`), `…TRANCATG.PS.BKUP(+1)` (`:62`), `…DISCGRP.BKUP(+1)` (`:85`) | Define reference-data GDGs and seed the first generation. |
| `DEFCUST.jcl` | `STEP05` twice→`IDCAMS` (`:22`, `:32`) | customer KSDS define | Alternate customer-file define (duplicate step name `STEP05`). |
| `CBADMCDJ.jcl` | `STEP1`→`DFHCSDUP` (`:27`) | `DFHCSD`→`OEM.CICSTS.DFHCSD` (`:30`) | Define the CardDemo CICS group (programs, transactions, files) in the CSD. |
| `FTPJCL.JCL` | `STEP1`→`FTP` (`:30`) | instream FTP commands | File transfer demo. |
| `INTRDRJ1.JCL` | `IDCAMS` (`:6`), `STEP01`→`IEBGENER` (`:14`) | `SYSUT1`→`…JCL(INTRDRJ2)` (`:17`), `SYSUT2`→`INTRDR` (`:18`) | Submit `INTRDRJ2` through the internal reader (same mechanism `CORPT00C` uses). |
| `INTRDRJ2.JCL` | `IDCAMS` (`:7`) | `…FTP.TEST.BKUP` → `…BKUP.INTRDR` (`:9`, `:10`) | Job submitted by `INTRDRJ1`. |

### 3.3 Sub-application JCL and PROCs

| Job | Step → program | Key DDs | Purpose |
| :-- | :------------- | :------ | :------ |
| `app/app-authorization-ims-db2-mq/jcl/CBPAUP0J.jcl` | `STEP01`→`DFSRRC00 PARM='BMP,CBPAUP0C,PSBPAUTB'` (`:24`) | `IMS`→`IMS.PSBLIB`/`IMS.DBDLIB` (`:33`), `SYSIN` purge parameters (`:36`) | Purge expired pending authorizations (IMS BMP). |
| `app/app-authorization-ims-db2-mq/jcl/LOADPADB.JCL` | `STEP01`→`DFSRRC00 PARM='BMP,PAUDBLOD,PSBPAUTB'` (`:26`) | `INFILE1`→`…PAUTDB.ROOT.FILEO` (`:36`), `INFILE2`→`…PAUTDB.CHILD.FILEO` (`:38`), `DDPAUTP0`/`DDPAUTX0` IMS DB/index (`:40`, `:41`) | Load the pending-authorization IMS database. |
| `app/app-authorization-ims-db2-mq/jcl/UNLDPADB.JCL` | `STEP0`→`IEFBR14` (`:25`); `STEP01`→`DFSRRC00 PARM='DLI,PAUDBUNL,PAUTBUNL,…'` (`:38`) | `OUTFIL1`/`OUTFIL2`→`…PAUTDB.ROOT.FILEO`/`…CHILD.FILEO` (`:48`, `:53`) | Unload the IMS database to QSAM. |
| `app/app-authorization-ims-db2-mq/jcl/UNLDGSAM.JCL` | `STEP01`→`DFSRRC00 PARM='DLI,DBUNLDGS,DLIGSAMP,…'` (`:26`) | `PASFILOP`→`…PAUTDB.ROOT.GSAM` (`:36`), `PADFILOP`→`…PAUTDB.CHILD.GSAM` (`:39`) | Unload via GSAM PCBs. |
| `app/app-authorization-ims-db2-mq/jcl/DBPAUTP0.jcl` | `STEPDEL`→`IEFBR14` (`:7`); `UNLOAD`→`DFSRRC00` (`:15`) | `DFSURGU1`→`…IMSDATA.DBPAUTP0` (`:25`), `RECON1-3` (`:40`–`:42`) | IMS image-copy/unload utility for the authorization DB. |
| `app/app-transaction-type-db2/jcl/CREADB21.jcl` | `FREEPLN` (`:40`), `CRCRDDB` (`:52`), `LDTTYPE` (`:64`), `RUNTEP2` (`:65`), `LDTCCAT` (`:77`) → `IKJEFT01`/`IEFBR14` | `SYSTSIN`/`SYSIN`→`&LBNM..CNTL(…)` members `DB2FREE`, `DB2TIAD1`, `DB2CREAT`, `DB2TEP41`, `DB2LTTYP`, `DB2LTCAT` | Create the Db2 objects and load transaction type/category tables. |
| `app/app-transaction-type-db2/jcl/TRANEXTR.jcl` | `STEP10`/`STEP20`→`IEBGENER` (`:31`, `:42`); `STEP30`→`IEFBR14` (`:53`); `STEP40`/`STEP50`→`IKJEFT01` running `DSNTIAUL` (`:65`, `:95`) | backup `…TRANTYPE.BKUP(+1)` (`:35`), `…TRANCATG.PS.BKUP(+1)` (`:46`); unload `SYSREC00`→`…TRANTYPE.PS` (`:72`) and `…TRANCATG.PS` (`:102`) | Refresh the VSAM-bound sequential reference files from the Db2 tables. This is the job the README places before `TRANCATG`/`TRANTYPE`. |
| `app/app-transaction-type-db2/jcl/MNTTRDB2.jcl` | `STEP1`→`IKJEFT01`, `RUN PROGRAM(COBTUPDT) PLAN(CARDDEMO)` (`:21`, `:30`) | `INPFILE`→`INPFILE` (`:27`), `DBRMLIB` (`:25`) | Batch transaction-type maintenance via Db2. |
| `app/proc/REPROC.prc` | `PRC001`→`IDCAMS` (`:21`) | `FILEIN`/`FILEOUT` overridden by caller (`:23`, `:25`), `SYSIN`→`&CNTLLIB(REPROCT)` (`:27`) | Reusable REPRO PROC used by `TRANBKP`, `TRANREPT`, `PRTCATBL`. |
| `app/proc/TRANREPT.prc` | `STEP01R`→`PROC=REPROC` (`:21`); `STEP05R`→`SORT` (`:35`); `STEP10R`→`CBTRN03C` (`:57`) | same DDs as `TRANREPT.jcl` | PROC form of the transaction-report pipeline (backup → sort → report). |

### 3.4 README run order vs. actual JCL

The README's "Running Batch Jobs" table (`README.md:211`–`:233`) is a conceptual sequence. Verified against
the JCL, the differences worth knowing before modernization are:

| README step (`README.md`) | Actual JCL content | Assessment |
| :------------------------ | :----------------- | :--------- |
| `CLOSEFIL` (`:213`) | `SDSF` MODIFY commands only (`app/jcl/CLOSEFIL.jcl:22`) | Matches; operational, not application logic. |
| `ACCTFILE`/`CARDFILE`/`XREFFILE`/`CUSTFILE` (`:214`–`:217`) | pure `IDCAMS` (+`SDSF`, +`BLDINDEX` for card/xref) | Matches. Alternate indexes `CARDAIX`/`CXACAIX` are created here, and the online programs depend on them. |
| `TRANBKP` "Creates Transaction database" (`:218`) | `TRANBKP.jcl` first REPROs the KSDS to a backup GDG, then deletes/defines it (`:23`, `:37`, `:51`) | The same member serves both README roles (`:218` create and `:227` backup). `TRANFILE.jcl` is the member that actually seeds the master from `…DALYTRAN.PS.INIT` (`:69`) and is **not** in the README table. |
| `TRANEXTR` (`:219`) | `app/app-transaction-type-db2/jcl/TRANEXTR.jcl` unloads Db2 to `…TRANTYPE.PS`/`…TRANCATG.PS` | Matches; only meaningful when the Db2 sub-application is installed. |
| `TRANCATG`, `TRANTYPE`, `DISCGRP`, `TCATBALF`, `DUSRSECJ` (`:220`–`:224`) | `IDCAMS`-only loads | Match. |
| `POSTTRAN` (`:225`) | `CBTRN02C` only (`app/jcl/POSTTRAN.jcl:23`) | **`CBTRN01C` is never executed by any JCL in this repository** (no member references it). The README's "validation then posting" story is realised entirely inside `CBTRN02C`; `CBTRN01C` is a standalone read-only validator. |
| `INTCALC` (`:226`) | `CBACT04C` with `PARM='2022071800'` (`app/jcl/INTCALC.jcl:22`) | Matches, but the processing date is **hard-coded in the JCL**, not derived from the system date. |
| `COMBTRAN` (`:228`) | `SORT` merge of `…TRANSACT.BKUP(0)` + `…SYSTRAN(0)` then REPRO into the master (`app/jcl/COMBTRAN.jcl:22`, `:41`) | Matches; consumes the `SYSTRAN` generation produced by `INTCALC` and the backup generation produced by the preceding `TRANBKP`. Ordering therefore matters: `TRANBKP` must run between `POSTTRAN` and `COMBTRAN`. |
| `CREASTMT` (`:229`) | 5 steps ending in `CBSTM03A` (`app/jcl/CREASTMT.JCL:79`) | Matches; adds an intermediate `TRXFL` KSDS not mentioned in the README. |
| `TRANIDX` (`:230`) | `IDCAMS` AIX define/build (`app/jcl/TRANIDX.jcl:22`) | Matches. |
| `OPENFIL`, `WAITSTEP` (`:231`, `:232`) | `SDSF`; `COBSWAIT` | Match. |
| `CBPAUP0J` (`:233`) | IMS BMP `CBPAUP0C` (`app/app-authorization-ims-db2-mq/jcl/CBPAUP0J.jcl:24`) | Matches; optional module. |
| not in README | `TRANREPT.jcl`, `PRTCATBL.jcl`, `READACCT/READCARD/READCUST/READXREF`, `CBEXPORT`/`CBIMPORT`, `TXT2PDF1`, `FTPJCL`, `INTRDRJ1/2`, `DEFGDGB/DEFGDGD`, `DEFCUST`, `ESDSRRDS`, `CBADMCDJ`, `TRANFILE`, `DALYREJS`, `REPTFILE` | Reporting, extract, demo and setup jobs. `TRANREPT` is the job `CORPT00C` submits from the online report screen. |

Additional JCL-level observations:

- `TRANREPT.jcl` contains two steps both named `STEP05R` (`:23` and `:37`), as does `DEFCUST.jcl`
  (`STEP05` at `:22` and `:32`) — duplicate step names within a job are a portability hazard for
  converted schedulers.
- `CBIMPORT.jcl` omits the `CARDOUT` DD that `CBIMPORT.cbl:63` selects, so the card branch of the import
  would fail at `OPEN` if exercised.
- GDG relative references (`(+1)`, `(0)`) carry cross-job state: `…DALYREJS`, `…SYSTRAN`,
  `…TRANSACT.BKUP`, `…TRANSACT.DALY`, `…TRANSACT.COMBINED`, `…TRANREPT`, `…TCATBALF.BKUP`. Any
  modernized scheduler must reproduce generation semantics, not just file names.
