-- Relational target schema for the transaction-type reference data slice.
--
-- Derived from the table-creation JCL and its SYSIN member:
--   app/app-transaction-type-db2/jcl/CREADB21.jcl:59  (SYSIN DD DSN=&LBNM..CNTL(DB2CREAT))
--   app/app-transaction-type-db2/ctl/DB2CREAT.ctl:35   CREATE TABLE CARDDEMO.TRANSACTION_TYPE
--   app/app-transaction-type-db2/ctl/DB2CREAT.ctl:75   CREATE TABLE CARDDEMO.TRANSACTION_TYPE_CATEGORY
--   app/app-transaction-type-db2/ctl/DB2CREAT.ctl:85   CREATE UNIQUE INDEX CARDDEMO.X_TRAN_TYPE_CATG
--   app/app-transaction-type-db2/ctl/DB2CREAT.ctl:96   ALTER TABLE ... FOREIGN KEY ... ON DELETE RESTRICT
-- and cross-checked against the standalone DDL members
--   app/app-transaction-type-db2/ddl/TRNTYPE.ddl, TRNTYCAT.ddl, XTRNTYPE.ddl, XTRNTYCAT.ddl
-- and the DCLGEN declarations
--   app/app-transaction-type-db2/dcl/DCLTRTYP.dcl, DCLTRCAT.dcl
--
-- Mainframe column names are kept verbatim. Db2-specific physical clauses
-- (IN CARDDEMO.CARDSPC1 / CARDSTTC, USING STOGROUP AWST1STG, BUFFERPOOL BP0,
-- CCSID EBCDIC, ERASE NO, CLOSE NO, GRANT ... TO PUBLIC) are storage and
-- encoding directives with no logical content for the read model and are dropped.

CREATE SCHEMA IF NOT EXISTS CARDDEMO;

CREATE TABLE CARDDEMO.TRANSACTION_TYPE (
    TR_TYPE        CHAR(2)     NOT NULL,
    TR_DESCRIPTION VARCHAR(50) NOT NULL,
    CONSTRAINT PK_TRANSACTION_TYPE PRIMARY KEY (TR_TYPE)
);

CREATE TABLE CARDDEMO.TRANSACTION_TYPE_CATEGORY (
    TRC_TYPE_CODE     CHAR(2)     NOT NULL,
    TRC_TYPE_CATEGORY CHAR(4)     NOT NULL,
    TRC_CAT_DATA      VARCHAR(50) NOT NULL,
    CONSTRAINT PK_TRANSACTION_TYPE_CATEGORY
        PRIMARY KEY (TRC_TYPE_CODE, TRC_TYPE_CATEGORY),
    CONSTRAINT FK_TRC_TYPE_CODE FOREIGN KEY (TRC_TYPE_CODE)
        REFERENCES CARDDEMO.TRANSACTION_TYPE (TR_TYPE) ON DELETE RESTRICT
);

-- X_TRAN_TYPE_CATG (DB2CREAT.ctl:85) duplicates the primary key of
-- TRANSACTION_TYPE_CATEGORY, as XTRAN_TYPE (ddl/XTRNTYPE.ddl:1) duplicates the
-- primary key of TRANSACTION_TYPE; both are implied by the PK constraints above.
