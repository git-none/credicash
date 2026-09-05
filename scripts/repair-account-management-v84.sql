-- Kredi+ / Credicash backend 7.2.9
-- Hotfix manual equivalente a la migración 84 incluida en schema.sql.
-- Preferencia: desplegar el backend completo; usar este script solo si necesitas reparar el esquema antes.

-- Migración 84 / Robustez de eliminación de cuentas sin historial.
-- Las filas de estado recreable no deben impedir borrar una cuenta vacía. La historia real
-- (pedidos, pagos, créditos, conciliaciones, auditoría, etc.) continúa protegida por RESTRICT.
-- Se eliminan las FK por metadatos en vez de asumir nombres de constraint; esto soporta bases
-- antiguas cuyas tablas fueron renombradas desde los nombres ingleses y conservaron la FK vieja.
DO $$
DECLARE fk_name TEXT;
BEGIN
    FOR fk_name IN
        SELECT tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_name=kcu.constraint_name AND tc.constraint_schema=kcu.constraint_schema
        JOIN information_schema.referential_constraints rc
          ON tc.constraint_name=rc.constraint_name AND tc.constraint_schema=rc.constraint_schema
        JOIN information_schema.constraint_column_usage ccu
          ON rc.unique_constraint_name=ccu.constraint_name AND rc.unique_constraint_schema=ccu.constraint_schema
        WHERE tc.constraint_type='FOREIGN KEY' AND tc.table_schema='public'
          AND tc.table_name='cuentas_credito' AND kcu.column_name='user_id'
          AND ccu.table_name='usuarios' AND ccu.column_name='id'
    LOOP
        EXECUTE format('ALTER TABLE cuentas_credito DROP CONSTRAINT %I', fk_name);
    END LOOP;
END $$;
ALTER TABLE cuentas_credito
    ADD CONSTRAINT cuentas_credito_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES usuarios(id) ON DELETE CASCADE;

DO $$
DECLARE fk_name TEXT;
BEGIN
    FOR fk_name IN
        SELECT tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_name=kcu.constraint_name AND tc.constraint_schema=kcu.constraint_schema
        JOIN information_schema.referential_constraints rc
          ON tc.constraint_name=rc.constraint_name AND tc.constraint_schema=rc.constraint_schema
        JOIN information_schema.constraint_column_usage ccu
          ON rc.unique_constraint_name=ccu.constraint_name AND rc.unique_constraint_schema=ccu.constraint_schema
        WHERE tc.constraint_type='FOREIGN KEY' AND tc.table_schema='public'
          AND tc.table_name='usuarios_credimpulso' AND kcu.column_name='user_id'
          AND ccu.table_name='usuarios' AND ccu.column_name='id'
    LOOP
        EXECUTE format('ALTER TABLE usuarios_credimpulso DROP CONSTRAINT %I', fk_name);
    END LOOP;
END $$;
ALTER TABLE usuarios_credimpulso
    ADD CONSTRAINT usuarios_credimpulso_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES usuarios(id) ON DELETE CASCADE;

DO $$
DECLARE fk_name TEXT;
BEGIN
    FOR fk_name IN
        SELECT tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_name=kcu.constraint_name AND tc.constraint_schema=kcu.constraint_schema
        JOIN information_schema.referential_constraints rc
          ON tc.constraint_name=rc.constraint_name AND tc.constraint_schema=rc.constraint_schema
        JOIN information_schema.constraint_column_usage ccu
          ON rc.unique_constraint_name=ccu.constraint_name AND rc.unique_constraint_schema=ccu.constraint_schema
        WHERE tc.constraint_type='FOREIGN KEY' AND tc.table_schema='public'
          AND tc.table_name='usuarios' AND kcu.column_name='suspended_by'
          AND ccu.table_name='usuarios' AND ccu.column_name='id'
    LOOP
        EXECUTE format('ALTER TABLE usuarios DROP CONSTRAINT %I', fk_name);
    END LOOP;
END $$;
ALTER TABLE usuarios
    ADD CONSTRAINT usuarios_suspended_by_fkey
    FOREIGN KEY (suspended_by) REFERENCES usuarios(id) ON DELETE SET NULL;

INSERT INTO versiones_esquema(version, description)
VALUES (84, 'Robustez de eliminación: estado recreable en cascada, historial financiero protegido y suspensión con SET NULL')
ON CONFLICT(version) DO UPDATE SET description=EXCLUDED.description, applied_at=NOW();
