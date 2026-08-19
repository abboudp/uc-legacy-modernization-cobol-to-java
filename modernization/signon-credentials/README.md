# Slice 3 — sign-on credential verification

Replaces the credential-verification decision of the CardDemo sign-on program with a Java service over
**hashed** credentials, preserving the authorization outcome exactly.

## What COBOL this replaces

| Target | Legacy |
| --- | --- |
| `CredentialVerificationService` | `READ-USER-SEC-FILE`, `app/cbl/COSGN00C.cbl:209`–`:257` |
| `CredentialStore` / `InMemoryCredentialStore` | `EXEC CICS READ DATASET('USRSEC')`, `app/cbl/COSGN00C.cbl:211`–`:219` |
| `UserRecord` | `SEC-USER-DATA`, `app/cpy/CSUSR01Y.cpy:17`–`:23` |
| `PasswordHash` / `PasswordHasher` | `SEC-USR-PWD PIC X(08)` (`app/cpy/CSUSR01Y.cpy:21`) and the clear-text compare `IF SEC-USR-PWD = WS-USER-PWD` (`app/cbl/COSGN00C.cbl:223`) |
| `UserType` / `VerificationResult.nextProgram()` | `SEC-USR-TYPE` → `CDEMO-USER-TYPE` (`app/cbl/COSGN00C.cbl:227`), `IF CDEMO-USRTYP-ADMIN` (`:230`, condition name `VALUE 'A'` at `app/cpy/COCOM01Y.cpy:27`), `XCTL PROGRAM('COADM01C')` (`:232`) versus `XCTL PROGRAM('COMEN01C')` (`:237`) |
| `UsrsecMigration` | the cutover load path, `app/jcl/DUSRSECJ.jcl` |

## Migration technique

Behaviour is carried across verbatim; the *mechanism* is not. Three things are preserved exactly:

1. **The three-way outcome.** `SUCCESS`, `USER_NOT_FOUND` (RESP 13 from the read, `app/cbl/COSGN00C.cbl:247`)
   and `WRONG_PASSWORD` (record read, compare failed, `:241`).
2. **The routing decision**, as a value rather than as control flow. The COBOL routes by `EXEC CICS XCTL`;
   `SEC-USR-TYPE = 'A'` → `COADM01C`, and *everything else* → `COMEN01C`, because `:230` is a two-way
   `IF`/`ELSE` that only tests for `'A'`. `CDEMO-USRTYP-USER VALUE 'U'` (`app/cpy/COCOM01Y.cpy:28`) is
   declared but never tested at sign-on, so an unrecognised type code routes to the regular menu rather
   than failing. Covered by `unknownUserTypeCodeRoutesToTheRegularMenu`.
3. **Fixed-width, blank-padded field semantics.** `SEC-USR-ID PIC X(08)` and `SEC-USR-PWD PIC X(08)` mean
   `"USER0001"` and `"USER0001   "` are the same id, and `"PASSWORD "` is the same password. Submitted
   values are upper-cased and `MOVE`d into `PIC X(08)` items at `app/cbl/COSGN00C.cbl:132`–`:136`
   (receiving items declared at `:45`–`:46`), which left-justifies, truncates on the right and blank-pads.
   Getting this wrong locks every user out on cutover day, so it has its own tests
   (`trailingBlanksAreNotSignificantOnIdOrPassword`, `submittedValuesAreUpperCasedAsTheCobolDoes`).

The 8-character id width is **enforced in the target model** by `CobolField.require`, called from the
`UserRecord` constructor — not by a database column. It outlives the mainframe because it is the `USRSEC`
key (`app/cbl/COSGN00C.cbl:215`–`:216`) and is embedded in the communication area
(`app/cpy/COCOM01Y.cpy:25`) and in every screen.

What is replaced: the clear-text equality test becomes PBKDF2-HMAC-SHA256, 210 000 iterations, a 16-byte
per-user salt and a 256-bit derived key, compared with `MessageDigest.isEqual` (constant time for
equal-length inputs). PBKDF2 comes from the JDK so this module has no runtime dependencies; the stored form
is self-describing (`$alg$iterations$salt$key`), so moving to argon2id later is a re-hash on next sign-on
rather than a flag day.

### The one-shot migration, and why the legacy passwords are gone afterwards

`UsrsecMigration.migrate` takes a legacy `USRSEC` record — clear-text password included, because that field
is the only copy of the credential that exists — and returns the hashed `UserRecord`. It is a one-way,
one-shot function: **after migration the legacy passwords are unrecoverable, and that is deliberate.** There
is no reverse path, no "decrypt", and no way to rebuild the clear-text `SEC-USR-PWD` field for a rollback.
`CUTOVER_PLAN.md` §3.2 makes the same point at the phase level: on rollback, `USRSEC` is restored via
`DUSRSECJ` but passwords are the one-way part, and users created or changed after cutover need a reset.

One deliberate asymmetry: the stored hash is derived from the password *as held in the file*, canonicalised
only by dropping trailing blanks, and is **not** upper-cased — because the COBOL upper-cases only the
submitted side of the compare (`app/cbl/COSGN00C.cbl:135`), never the stored side. A legacy record holding a
lower-case password is therefore unauthenticable, on the mainframe today and here after migration. The
shipped fixture is unaffected (all passwords are `PASSWORD`). See "Open questions".

## Parity evidence

Seeded from the repository's own user data: `app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS`, 800 bytes at 80
bytes per record — 10 users, 5 admin and 5 regular, every one with the clear-text password `PASSWORD`.
Field offsets are cross-checked against `app/cpy/CSUSR01Y.cpy:18`–`:23` by `UsrsecFixtureLayoutTest`.

The task brief asked for `app/data/ASCII/AWS.M2.CARDDEMO.USRSEC.PS`. **That file does not exist** —
`app/data/ASCII/` holds only nine lower-case `.txt` files and no `usrsec` fixture, and both
`RISK_REGISTER.md:617` and `CUTOVER_PLAN.md:313` cite the EBCDIC path. Rather than take a dependency on
another slice's codec, the EBCDIC record area was transcoded once with

```bash
iconv -f IBM-1047 -t ASCII \
  < app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS \
  > modernization/signon-credentials/src/test/resources/AWS.M2.CARDDEMO.USRSEC.ASCII.PS
```

and the 800-byte result committed as a test resource. The command above reproduces it byte for byte; the
data is unchanged in content, and this module owns no character-set code.

Tests (`mvn -f modernization/signon-credentials/pom.xml test`, 21 tests):

- every seeded user authenticates with its legacy clear-text password after migration;
- a wrong password is rejected and yields no routing target;
- an unknown user is distinguishable from a wrong password in the internal result;
- admin versus regular routing matches `SEC-USR-TYPE` for all ten seeded users, down to the `XCTL` target;
- ids and passwords with trailing blanks, and over-long submitted values, behave as the COBOL does;
- the stored hash never equals the password and never contains it;
- **two users sharing a password get different stored hashes** — the property the legacy file lacks
  entirely, since all ten records hold identical bytes on disk (`RISK_REGISTER.md` R-08).

### Is exposing "user not found" to the caller acceptable?

The result type keeps the distinction because the COBOL does, and does so *to the end user*: it displays
`'User not found. Try again ...'` (`app/cbl/COSGN00C.cbl:249`) versus `'Wrong Password. Try again ...'`
(`:242`). That is a user-enumeration oracle. The position taken here: the distinction is legitimate and
useful **internally** (audit, metrics, support), and it is what the parity tests assert; but a caller
building the replacement sign-on screen should collapse both to one message. Since this slice ships no
endpoint and no screen, the decision is left to the caller, and `SignonOutcome.legacyMessage()` is provided
as documentation of the legacy text rather than as something to render.

## How to run

```bash
mvn -f modernization/signon-credentials/pom.xml test
```

Java 17, Maven, JUnit 5. No parent POM, no runtime dependencies, no other slice required.

## Not in this slice

No HTTP endpoint, no session or token issuing, no external identity provider, no
`COUSR00C`–`COUSR03C` user-administration CRUD, no password-policy rules the COBOL does not implement, no
account lockout, no MFA, no COMMAREA shim, no character-set codec, and no persistent database (the store is
in-process; the dataset it replaces holds 10 records).

Follow-up slices: **identity-provider federation** (mapping `SEC-USR-TYPE` to a role claim, replacing this
local store) and **user administration CRUD** (`COUSR00C`–`COUSR03C` against the same store). A separate
consolidation PR is expected to de-duplicate the fixed-width field helpers shared with the sibling slices.

## Open questions

- The COBOL upper-cases the submitted password but not the stored one (`app/cbl/COSGN00C.cbl:135` versus
  `:223`). This slice reproduces that asymmetry. A migration of a real population may prefer to hash the
  upper-cased legacy password instead, so that mixed-case legacy records stay usable; that changes the
  authorization outcome for exactly those records and needs a decision.
- `README.md:47` names an external security product while sign-on plainly reads a VSAM file
  (`app/cbl/COSGN00C.cbl:211`). Whether `USRSEC` or RACF is the production authority is already open in
  `MODERNIZATION_BLUEPRINT.md` §7 and `CUTOVER_PLAN.md`:1155; if it is RACF, this slice's store becomes a
  replica rather than the authority.
