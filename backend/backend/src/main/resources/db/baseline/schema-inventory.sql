-- Read-only catalog inventory. Column order, owners, grants and sequence *values*
-- are intentionally excluded; definitions and sequence ownership are included.
SELECT concat_ws('|', 'relation', c.relname, c.relkind, c.relpersistence,
    c.relrowsecurity, c.relforcerowsecurity, c.relreplident, coalesce(am.amname, '')) AS signature
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
LEFT JOIN pg_am am ON am.oid=c.relam
WHERE n.nspname=current_schema() AND c.relkind NOT IN ('i','I') AND c.relname <> 'flyway_schema_history'
UNION ALL
SELECT concat_ws('|', 'column', c.relname, a.attname, format_type(a.atttypid,a.atttypmod),
    a.attnotnull, a.attidentity, a.attgenerated, coalesce(pg_get_expr(d.adbin,d.adrelid),''),
    coalesce(coll.collname,''))
FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace
LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
LEFT JOIN pg_collation coll ON coll.oid=a.attcollation
WHERE n.nspname=current_schema() AND c.relkind IN ('r','p','v','m','f')
    AND a.attnum>0 AND NOT a.attisdropped AND c.relname <> 'flyway_schema_history'
UNION ALL
SELECT concat_ws('|', 'constraint', c.relname, k.conname, k.contype, k.convalidated,
    k.condeferrable, k.condeferred, pg_get_constraintdef(k.oid, true))
FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname=current_schema() AND c.relname <> 'flyway_schema_history'
UNION ALL
SELECT concat_ws('|', 'index', t.relname, i.relname, x.indisvalid, x.indisready,
    pg_get_indexdef(i.oid))
FROM pg_index x JOIN pg_class i ON i.oid=x.indexrelid JOIN pg_class t ON t.oid=x.indrelid
JOIN pg_namespace n ON n.oid=t.relnamespace
WHERE n.nspname=current_schema() AND t.relname <> 'flyway_schema_history'
UNION ALL
SELECT concat_ws('|', 'sequence', c.relname, format_type(s.seqtypid,null), s.seqstart,
    s.seqincrement, s.seqmin, s.seqmax, s.seqcache, s.seqcycle, coalesce(t.relname,''), coalesce(a.attname,''), coalesce(d.deptype::text,''))
FROM pg_sequence s JOIN pg_class c ON c.oid=s.seqrelid JOIN pg_namespace n ON n.oid=c.relnamespace
LEFT JOIN pg_depend d ON d.classid='pg_class'::regclass AND d.objid=c.oid AND d.deptype IN ('a','i')
LEFT JOIN pg_class t ON t.oid=d.refobjid
LEFT JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=d.refobjsubid
WHERE n.nspname=current_schema()
UNION ALL
SELECT concat_ws('|', 'trigger', c.relname, t.tgname, t.tgenabled, pg_get_triggerdef(t.oid))
FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname=current_schema() AND NOT t.tgisinternal
UNION ALL
SELECT concat_ws('|', 'function', p.proname, pg_get_function_identity_arguments(p.oid),
    pg_get_functiondef(p.oid))
FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
WHERE n.nspname=current_schema() AND p.prokind IN ('f','p')
UNION ALL
SELECT concat_ws('|', 'type', t.typname, t.typtype, format_type(t.typbasetype,t.typtypmod),
    coalesce((SELECT string_agg(e.enumlabel,',' ORDER BY e.enumsortorder) FROM pg_enum e WHERE e.enumtypid=t.oid),''))
FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace
WHERE n.nspname=current_schema() AND (t.typtype IN ('d','e') OR (t.typtype='c' AND NOT EXISTS
    (SELECT 1 FROM pg_class c WHERE c.oid=t.typrelid AND c.relkind IN ('r','p','v','m','f'))))
UNION ALL
SELECT concat_ws('|', 'policy', p.tablename, p.policyname, p.permissive, p.roles, p.cmd, coalesce(p.qual,''), coalesce(p.with_check,''))
FROM pg_policies p WHERE p.schemaname=current_schema()
UNION ALL
SELECT concat_ws('|', 'rule', c.relname, r.rulename, pg_get_ruledef(r.oid))
FROM pg_rewrite r JOIN pg_class c ON c.oid=r.ev_class JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname=current_schema()
UNION ALL
SELECT concat_ws('|', 'extension', e.extname, e.extversion, n.nspname)
FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace
ORDER BY signature;
