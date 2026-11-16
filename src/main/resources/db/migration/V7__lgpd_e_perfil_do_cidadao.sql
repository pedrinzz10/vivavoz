-- LGPD e autonomia do cidadão:
-- 1) o cidadão pode criar e manter o próprio perfil de emergência, sem cuidador (cuidador_id opcional);
-- 2) a mobilidade é dado de saúde: pode ficar NAO_INFORMADO;
-- 3) o consentimento do cadastro fica registrado, com a data do aceite.
-- O SQL Server não altera a nulabilidade de uma coluna usada em índice ou chave estrangeira: os dois são
-- recriados em volta da alteração.
ALTER TABLE perfis_emergencia DROP CONSTRAINT fk_perfis_emergencia_cuidador;
DROP INDEX ix_perfis_emergencia_cuidador ON perfis_emergencia;
ALTER TABLE perfis_emergencia ALTER COLUMN cuidador_id BIGINT NULL;
CREATE INDEX ix_perfis_emergencia_cuidador ON perfis_emergencia (cuidador_id);
ALTER TABLE perfis_emergencia ADD CONSTRAINT fk_perfis_emergencia_cuidador
    FOREIGN KEY (cuidador_id) REFERENCES usuarios (id);

ALTER TABLE perfis_emergencia DROP CONSTRAINT ck_perfis_emergencia_mobilidade;
ALTER TABLE perfis_emergencia ADD CONSTRAINT ck_perfis_emergencia_mobilidade CHECK (mobilidade IN
    ('CADEIRANTE', 'ANDADOR', 'BENGALA', 'DEFICIENCIA_VISUAL', 'DEFICIENCIA_AUDITIVA', 'NENHUMA', 'NAO_INFORMADO'));

ALTER TABLE usuarios ADD consentimento BIT NOT NULL DEFAULT 0;
ALTER TABLE usuarios ADD consentimento_em DATETIME2 NULL;
