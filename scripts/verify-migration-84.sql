-- Verificación no destructiva posterior al deploy de Kredi+ 1.1.2.
SELECT version, description, applied_at
FROM versiones_esquema
WHERE version = 84;

SELECT
  tc.table_name,
  kcu.column_name,
  rc.delete_rule,
  tc.constraint_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
 AND tc.constraint_schema = kcu.constraint_schema
JOIN information_schema.referential_constraints rc
  ON tc.constraint_name = rc.constraint_name
 AND tc.constraint_schema = rc.constraint_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_schema = 'public'
  AND (
    (tc.table_name = 'cuentas_credito' AND kcu.column_name = 'user_id') OR
    (tc.table_name = 'usuarios_credimpulso' AND kcu.column_name = 'user_id') OR
    (tc.table_name = 'usuarios' AND kcu.column_name = 'suspended_by')
  )
ORDER BY tc.table_name, kcu.column_name;
