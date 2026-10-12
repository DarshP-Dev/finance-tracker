-- Verify the four names/validation flags. The full Java V1 verifier is still required.
BEGIN READ ONLY;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
SELECT c.relname AS table_name, k.conname, k.convalidated,
       pg_get_constraintdef(k.oid, true) AS definition
FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid
WHERE (k.conrelid='public.budgets'::regclass AND k.conname='budgets_category_check')
   OR (k.conrelid='public.transactions'::regclass AND k.conname='transactions_category_check')
   OR (k.conrelid='public.investments'::regclass AND k.conname IN ('ck_investments_shares_positive','ck_investments_purchase_price_positive'))
ORDER BY c.relname, k.conname;
DO $postflight$
BEGIN
    IF (SELECT count(*) FROM pg_constraint WHERE contype='c' AND convalidated AND
        ((conrelid='public.budgets'::regclass AND conname='budgets_category_check')
        OR (conrelid='public.transactions'::regclass AND conname='transactions_category_check')
        OR (conrelid='public.investments'::regclass AND conname IN ('ck_investments_shares_positive','ck_investments_purchase_price_positive')))) <> 4 THEN
        RAISE EXCEPTION 'Expected four validated checks; do not baseline';
    END IF;
END
$postflight$;
COMMIT;
