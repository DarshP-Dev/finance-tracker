-- Safe inspection only. No schema/history/record writes.
BEGIN READ ONLY;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
SELECT current_database() AS database_name, current_setting('server_version') AS postgres_version,
       to_regclass('public.flyway_schema_history') IS NOT NULL AS has_flyway_history;
SELECT c.relname AS table_name, k.conname, k.convalidated,
       pg_get_constraintdef(k.oid, true) AS definition
FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid
JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relname IN ('budgets','transactions','investments')
ORDER BY c.relname, k.conname;
SELECT 'budgets.category' AS rule, count(*) AS violating_rows FROM public.budgets
WHERE category IS NULL OR category NOT IN ('FOOD','RENT','HOUSING','UTILITIES','GROCERIES','DINING','TRANSPORTATION','HEALTHCARE','INSURANCE','DEBT_PAYMENT','ENTERTAINMENT','SHOPPING','HEALTH','EDUCATION','TRAVEL','SALARY','INVESTMENT_INCOME','TRANSFER','OTHER')
UNION ALL
SELECT 'transactions.category', count(*) FROM public.transactions
WHERE category IS NULL OR category NOT IN ('FOOD','RENT','HOUSING','UTILITIES','GROCERIES','DINING','TRANSPORTATION','HEALTHCARE','INSURANCE','DEBT_PAYMENT','ENTERTAINMENT','SHOPPING','HEALTH','EDUCATION','TRAVEL','SALARY','INVESTMENT_INCOME','TRANSFER','OTHER')
UNION ALL
SELECT 'investments.shares > 0', count(*) FROM public.investments WHERE shares IS NULL OR NOT (shares > 0)
UNION ALL
SELECT 'investments.purchase_price > 0', count(*) FROM public.investments WHERE purchase_price IS NULL OR NOT (purchase_price > 0);
-- Aggregates only: no account details, credentials, descriptions or financial amounts.
SELECT category, count(*) AS rows FROM public.budgets GROUP BY category ORDER BY category;
SELECT category, count(*) AS rows FROM public.transactions GROUP BY category ORDER BY category;
COMMIT;
