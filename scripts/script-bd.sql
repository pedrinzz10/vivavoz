-- VozViva - DDL do banco relacional (Azure SQL / SQL Server)
-- Gerado por ./gradlew gerarScriptBd a partir de src/main/resources/db/migration. Não edite à mão.

-- V1__usuarios.sql
-- SPEC-023: usuários do ecossistema (cidadãos e cuidadores). A API Java é a dona da identidade.
CREATE TABLE usuarios (
    id            BIGINT IDENTITY(1,1) NOT NULL,
    nome          NVARCHAR(120)        NOT NULL,
    email         NVARCHAR(160)        NOT NULL,
    senha_hash    NVARCHAR(100)        NOT NULL,
    papel         NVARCHAR(20)         NOT NULL,
    ativo         BIT                  NOT NULL DEFAULT 1,
    criado_em     DATETIME2            NOT NULL,
    atualizado_em DATETIME2            NOT NULL,
    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_papel CHECK (papel IN ('CIDADAO', 'CUIDADOR'))
);

-- V2__perfis_e_contatos_emergencia.sql
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

-- V3__emergencias.sql
-- SPEC-008: acionamentos do botão de emergência e os avisos enviados aos contatos.
CREATE TABLE emergencias (
    id                   BIGINT IDENTITY(1,1) NOT NULL,
    perfil_id            BIGINT               NOT NULL,
    status               NVARCHAR(20)         NOT NULL,
    latitude             DECIMAL(9,6)         NULL,
    longitude            DECIMAL(9,6)         NULL,
    texto_original       NVARCHAR(300)        NULL,
    contatos_notificados INT                  NOT NULL DEFAULT 0,
    reenvios             INT                  NOT NULL DEFAULT 0,
    acionada_em          DATETIME2            NOT NULL,
    notificada_em        DATETIME2            NULL,
    encerrada_em         DATETIME2            NULL,
    CONSTRAINT pk_emergencias PRIMARY KEY (id),
    CONSTRAINT fk_emergencias_perfil FOREIGN KEY (perfil_id) REFERENCES perfis_emergencia (id) ON DELETE CASCADE,
    CONSTRAINT ck_emergencias_status CHECK (status IN ('ACIONADA', 'NOTIFICADA', 'FALHA_NO_AVISO', 'ATENDIDA', 'CANCELADA')),
    CONSTRAINT ck_emergencias_coordenadas CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))
);

CREATE INDEX ix_emergencias_perfil_acionada ON emergencias (perfil_id, acionada_em);

CREATE TABLE avisos_emergencia (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    emergencia_id  BIGINT               NOT NULL,
    contato_nome   NVARCHAR(120)        NOT NULL,
    telefone       NVARCHAR(20)         NOT NULL,
    mensagem       NVARCHAR(500)        NOT NULL,
    enviado_em     DATETIME2            NOT NULL,
    CONSTRAINT pk_avisos_emergencia PRIMARY KEY (id),
    CONSTRAINT fk_avisos_emergencia_emergencia FOREIGN KEY (emergencia_id) REFERENCES emergencias (id) ON DELETE CASCADE
);

CREATE INDEX ix_avisos_emergencia_emergencia ON avisos_emergencia (emergencia_id);
