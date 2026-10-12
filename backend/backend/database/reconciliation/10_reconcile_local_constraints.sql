-- MANUAL, SEPARATELY APPROVED LOCAL ADOPTION ONLY. Not a Flyway migration.
-- Requires exact target confirmation + approval session settings (see guide).
-- All four changes commit together or roll back together. No financial DML.
BEGIN;
SET LOCAL search_path = pg_catalog, public;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

DO $approval$
BEGIN
    IF current_setting('finance_tracker.reconciliation_approved', true)
            IS DISTINCT FROM 'approved-local-four-constraints'
       OR current_setting('finance_tracker.reconciliation_database', true)
            IS DISTINCT FROM current_database() THEN
        RAISE EXCEPTION 'Reconciliation requires reviewed local approval and exact database confirmation';
    END IF;
    IF inet_server_addr() IS NULL OR inet_server_addr() NOT IN ('127.0.0.1'::inet, '::1'::inet) THEN
        RAISE EXCEPTION 'This reconciliation is restricted to a loopback local PostgreSQL server';
    END IF;
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        RAISE EXCEPTION 'Flyway history already exists; pre-baseline reconciliation refused';
    END IF;
END
$approval$;

-- Stop application/cron writers first. Lock before schema/data rechecks so their
-- result cannot change between validation and the DDL within this transaction.
LOCK TABLE public.budgets, public.transactions, public.investments IN ACCESS EXCLUSIVE MODE;

DO $preconditions$
DECLARE
    legacy_definition constant text := $legacy$CHECK (category::text = ANY (ARRAY['HOUSING'::character varying, 'UTILITIES'::character varying, 'GROCERIES'::character varying, 'DINING'::character varying, 'TRANSPORTATION'::character varying, 'HEALTHCARE'::character varying, 'INSURANCE'::character varying, 'DEBT_PAYMENT'::character varying, 'ENTERTAINMENT'::character varying, 'SHOPPING'::character varying, 'EDUCATION'::character varying, 'TRAVEL'::character varying, 'SALARY'::character varying, 'INVESTMENT_INCOME'::character varying, 'TRANSFER'::character varying, 'OTHER'::character varying]::text[]))$legacy$;
    matched integer;
    observed text;
BEGIN
    SELECT count(*) INTO matched FROM pg_constraint
    WHERE ((conrelid='public.budgets'::regclass AND conname='budgets_category_check')
        OR (conrelid='public.transactions'::regclass AND conname='transactions_category_check'))
        AND contype='c' AND convalidated AND NOT connoinherit
        AND pg_get_constraintdef(oid, true)=legacy_definition;
    IF matched <> 2 THEN
        SELECT string_agg(conname || ': ' || pg_get_constraintdef(oid, true)
            || ' validated=' || convalidated || ' noinherit=' || connoinherit, E'\n') INTO observed
        FROM pg_constraint WHERE (conrelid='public.budgets'::regclass AND conname='budgets_category_check')
            OR (conrelid='public.transactions'::regclass AND conname='transactions_category_check');
        RAISE EXCEPTION 'Unexpected category constraint state; no changes applied. Reinspect schema'
            USING DETAIL = coalesce(observed, 'Expected constraints are missing');
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.investments'::regclass
        AND conname IN ('ck_investments_shares_positive','ck_investments_purchase_price_positive')) THEN
        RAISE EXCEPTION 'Unexpected investment constraint state; no changes applied. Reinspect schema';
    END IF;
    IF EXISTS (SELECT 1 FROM public.budgets WHERE category IS NULL OR category NOT IN ('FOOD','RENT','HOUSING','UTILITIES','GROCERIES','DINING','TRANSPORTATION','HEALTHCARE','INSURANCE','DEBT_PAYMENT','ENTERTAINMENT','SHOPPING','HEALTH','EDUCATION','TRAVEL','SALARY','INVESTMENT_INCOME','TRANSFER','OTHER'))
       OR EXISTS (SELECT 1 FROM public.transactions WHERE category IS NULL OR category NOT IN ('FOOD','RENT','HOUSING','UTILITIES','GROCERIES','DINING','TRANSPORTATION','HEALTHCARE','INSURANCE','DEBT_PAYMENT','ENTERTAINMENT','SHOPPING','HEALTH','EDUCATION','TRAVEL','SALARY','INVESTMENT_INCOME','TRANSFER','OTHER')) THEN
        RAISE EXCEPTION 'Existing category values violate V1; stop and review records. No automatic data correction';
    END IF;
    IF EXISTS (SELECT 1 FROM public.investments
        WHERE shares IS NULL OR purchase_price IS NULL OR NOT (shares > 0) OR NOT (purchase_price > 0)) THEN
        RAISE EXCEPTION 'Existing investment values violate positivity; stop and review records. No automatic data correction';
    END IF;
END
$preconditions$;

-- Install and validate the wider checks before replacing the narrower checks.
-- Temporary names exist only inside this atomic transaction.
ALTER TABLE public.budgets ADD CONSTRAINT budgets_category_v1_reconciliation
    CHECK (category IN ('FOOD','RENT','HOUSING','UTILITIES','GROCERIES','DINING','TRANSPORTATION','HEALTHCARE','INSURANCE','DEBT_PAYMENT','ENTERTAINMENT','SHOPPING','HEALTH','EDUCATION','TRAVEL','SALARY','INVESTMENT_INCOME','TRANSFER','OTHER')) NOT VALID;
ALTER TABLE public.budgets VALIDATE CONSTRAINT budgets_category_v1_reconciliation;
ALTER TABLE public.budgets DROP CONSTRAINT budgets_category_check;
ALTER TABLE public.budgets RENAME CONSTRAINT budgets_category_v1_reconciliation TO budgets_category_check;

ALTER TABLE public.transactions ADD CONSTRAINT transactions_category_v1_reconciliation
    CHECK (category IN ('FOOD','RENT','HOUSING','UTILITIES','GROCERIES','DINING','TRANSPORTATION','HEALTHCARE','INSURANCE','DEBT_PAYMENT','ENTERTAINMENT','SHOPPING','HEALTH','EDUCATION','TRAVEL','SALARY','INVESTMENT_INCOME','TRANSFER','OTHER')) NOT VALID;
ALTER TABLE public.transactions VALIDATE CONSTRAINT transactions_category_v1_reconciliation;
ALTER TABLE public.transactions DROP CONSTRAINT transactions_category_check;
ALTER TABLE public.transactions RENAME CONSTRAINT transactions_category_v1_reconciliation TO transactions_category_check;

ALTER TABLE public.investments ADD CONSTRAINT ck_investments_shares_positive CHECK (shares > 0) NOT VALID;
ALTER TABLE public.investments ADD CONSTRAINT ck_investments_purchase_price_positive CHECK (purchase_price > 0) NOT VALID;
ALTER TABLE public.investments VALIDATE CONSTRAINT ck_investments_shares_positive;
ALTER TABLE public.investments VALIDATE CONSTRAINT ck_investments_purchase_price_positive;
COMMIT;
