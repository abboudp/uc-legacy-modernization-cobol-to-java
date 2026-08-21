# transaction-posting — Java port of `CBTRN02C`

Standalone, dependency-free Java 17 port of the daily transaction posting core:
`app/cbl/CBTRN02C.cbl`, run by `app/jcl/POSTTRAN.jcl`. This is the Wave 2 target of
`HOTSPOT_REPORT.md` section 8 — the only posting program in the estate, and the program that
defines the consistency semantics every other modernized component has to honour.

    mvn -f modernization/transaction-posting/pom.xml test

The COBOL and the fixtures under `app/` are the reference implementation and are not modified.

## What is ported

| COBOL | Java |
| :---- | :--- |
| `PROCEDURE DIVISION` read loop, `WS-TRANSACTION-COUNT` / `WS-REJECT-COUNT`, `RETURN-CODE` | `PostingRun` |
| `1500-VALIDATE-TRAN`, `2000-POST-TRANSACTION`, `2700-UPDATE-TCATBAL`, `2800-UPDATE-ACCOUNT-REC` | `PostingEngine` |
| `Z-GET-DB2-FORMAT-TIMESTAMP` | `Db2Timestamp` |
| `CVTRA06Y` / `CVTRA05Y` / `CVACT01Y` / `CVACT03Y` / `CVTRA01Y` record layouts | `DailyTransaction`, `PostedTransaction`, `AccountRecord`, `CardXrefRecord`, `TransactionCategoryBalance` |
| 430-byte `REJECT-RECORD` (`LRECL=430` on the `DALYREJS` DD) | `RejectedTransaction`, `RejectReason` |
| `DISPLAY` numerics with trailing overpunched signs | `CobolFields` |
| The six `SELECT`ed datasets | the ports in `PostingDatasets`, with in-memory implementations |

`PostingDatasets` keeps the dataset ports separate from the rules so that the next step —
backing `AccountStore` and `TransactionCategoryBalanceStore` with a relational store — does not
touch `PostingEngine`.

## Business rules, as the COBOL states them

Validation, in order, short-circuiting on the first two:

| Reason | Condition |
| -----: | :-------- |
| 100 | the card number has no `CARDXREF` row |
| 101 | the cross-referenced account has no `ACCTDATA` row |
| 102 | `ACCT-CREDIT-LIMIT < ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT` |
| 103 | `ACCT-EXPIRAION-DATE < DALYTRAN-ORIG-TS (1:10)`, compared as text |

Both 102 and 103 are evaluated, and 103 overwrites 102, so a transaction that is over limit *and*
past expiry is reported as expired. The limit test is against the **cycle** buckets, not the
current balance, and it uses the buckets as they stand after earlier transactions in the same run
have posted — so the outcome depends on the order of the daily transaction file.

Posting then performs three updates in this order: upsert the `TCATBALF` row keyed on
account + type + category, rewrite the account (`ACCT-CURR-BAL` always, then `ACCT-CURR-CYC-CREDIT`
for a non-negative amount or `ACCT-CURR-CYC-DEBIT` for a negative one), and write the transaction
to the master.

## Behaviours reproduced deliberately

* **A negative amount is *added* to `ACCT-CURR-CYC-DEBIT`**, so that bucket accumulates negative
  values and the available-limit expression above increases as debits arrive. Preserved because the
  102 threshold depends on it.
* **`TRANFILE` is opened `OUTPUT`**, which on a KSDS discards the existing cluster: after a run the
  transaction master holds only the transactions that run posted. `TransactionMaster` therefore
  starts empty rather than appending.
* **Numeric truncation is silent.** No `ON SIZE ERROR` handler exists, so `CobolFields.signed`
  drops high-order digits that do not fit and truncates rather than rounds below the field scale.
* **Money is decimal throughout** — `BigDecimal` at scale 2, never `double`.

## Behaviours deliberately not reproduced

* `2800-UPDATE-ACCOUNT-REC` sets reason 109 on a failed `REWRITE` but nothing inspects it, so the
  legacy program would write the transaction to the master with the account left unchanged. The
  account was just read under the same key, so this cannot happen; `PostingEngine` raises
  `PostingDataException` instead of silently diverging the two datasets.
* The legacy program has no transactional boundary other than job success: an abend mid-file leaves
  `TCATBALF` and `ACCTDATA` updated for the transactions processed so far while `TRANFILE` has been
  emptied. `PostingEngine.post` is the natural unit of work for a caller to wrap in a transaction;
  choosing that boundary is a cutover decision, not a translation one.
* Reason 109 aside, the reject file is a dead end in the repository — nothing consumes
  `DALYREJS(+1)`. `RejectStore` is a port so a real consumer can be attached.

## Golden-master tests

`RecordLayoutParityTest` decodes and re-encodes **every** record of `dailytran.txt`,
`acctdata.txt`, `cardxref.txt` and `tcatbal.txt` and requires byte equality, which is what makes a
one-byte offset error fail loudly instead of quietly producing a wrong balance. `cardxref.txt`
records are 36 bytes on disk — the 14-byte trailing `FILLER` is absent — and are compared with it
restored.

`PostingRunFixtureTest` runs a whole `POSTTRAN` step over the fixtures: 300 daily transactions
against 50 accounts, 50 cross-reference rows and 50 category balances. Its assertions are
recomputed from the fixtures rather than transcribed from a run: each account must move by exactly
the sum of the amounts posted to it, each category balance likewise, and each reject must still be
justified when rechecked against the input datasets.

Over the current fixtures the step posts 262 transactions, rejects 38 (all reason 102), grows
`TCATBALF` from 50 to 100 rows, and ends with `RETURN-CODE = 4`.

## Fixture quirk found while porting

Account records carry `A000000000` in `ACCT-ADDR-ZIP` and leave `ACCT-GROUP-ID` blank. Read by the
copybook, no fixture account names a disclosure group, which is why `CBACT04C` interest resolution
falls back to the `DEFAULT` group for all of them. Any later account or interest slice should treat
that as data, not as a decoding error.
