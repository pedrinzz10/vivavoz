-- SPEC-017: perfil de emergência do cidadão, gerenciado pelo cuidador, e seus contatos.
CREATE TABLE perfis_emergencia (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    cidadao_id      BIGINT               NOT NULL,
    cuidador_id     BIGINT               NOT NULL,
    mobilidade      NVARCHAR(30)         NOT NULL,
    condicoes_saude NVARCHAR(500)        NULL,
    medicamentos    NVARCHAR(500)        NULL,
    observacoes     NVARCHAR(500)        NULL,
    criado_em       DATETIME2            NOT NULL,
    atualizado_em   DATETIME2            NOT NULL,
    CONSTRAINT pk_perfis_emergencia PRIMARY KEY (id),
    CONSTRAINT uk_perfis_emergencia_cidadao UNIQUE (cidadao_id),
    CONSTRAINT fk_perfis_emergencia_cidadao FOREIGN KEY (cidadao_id) REFERENCES usuarios (id),
    CONSTRAINT fk_perfis_emergencia_cuidador FOREIGN KEY (cuidador_id) REFERENCES usuarios (id),
    CONSTRAINT ck_perfis_emergencia_mobilidade CHECK (mobilidade IN
        ('CADEIRANTE', 'ANDADOR', 'BENGALA', 'DEFICIENCIA_VISUAL', 'DEFICIENCIA_AUDITIVA', 'NENHUMA'))
);

CREATE INDEX ix_perfis_emergencia_cuidador ON perfis_emergencia (cuidador_id);

CREATE TABLE contatos_emergencia (
    id            BIGINT IDENTITY(1,1) NOT NULL,
    perfil_id     BIGINT               NOT NULL,
    nome          NVARCHAR(120)        NOT NULL,
    telefone      NVARCHAR(20)         NOT NULL,
    parentesco    NVARCHAR(60)         NULL,
    prioridade    INT                  NOT NULL,
    criado_em     DATETIME2            NOT NULL,
    atualizado_em DATETIME2            NOT NULL,
    CONSTRAINT pk_contatos_emergencia PRIMARY KEY (id),
    CONSTRAINT fk_contatos_emergencia_perfil FOREIGN KEY (perfil_id) REFERENCES perfis_emergencia (id) ON DELETE CASCADE,
    CONSTRAINT ck_contatos_emergencia_prioridade CHECK (prioridade BETWEEN 1 AND 10)
);

CREATE INDEX ix_contatos_emergencia_perfil ON contatos_emergencia (perfil_id);
