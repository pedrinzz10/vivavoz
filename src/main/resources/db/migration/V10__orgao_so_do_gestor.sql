-- Separado da V9: no SQL Server, uma restrição que cita a coluna criada no mesmo lote falha na compilação do lote.
-- Todo gestor tem órgão, e só o gestor tem.
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_orgao_do_gestor
    CHECK ((papel = 'GESTOR' AND orgao IS NOT NULL) OR (papel <> 'GESTOR' AND orgao IS NULL));
