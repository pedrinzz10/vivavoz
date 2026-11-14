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
