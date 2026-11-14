-- SPEC-008: acionamentos do botão de emergência e os avisos enviados aos contatos.
CREATE TABLE emergencias (
    id                   BIGINT IDENTITY(1,1) NOT NULL,
    perfil_id            BIGINT               NOT NULL,
    status               NVARCHAR(20)         NOT NULL,
    latitude             DECIMAL(9,6)         NULL,
    longitude            DECIMAL(9,6)         NULL,
    texto_original       NVARCHAR(300)        NULL,
    contatos_notificados INT                  NOT NULL DEFAULT 0,
    acionada_em          DATETIME2            NOT NULL,
    notificada_em        DATETIME2            NULL,
    encerrada_em         DATETIME2            NULL,
    CONSTRAINT pk_emergencias PRIMARY KEY (id),
    CONSTRAINT fk_emergencias_perfil FOREIGN KEY (perfil_id) REFERENCES perfis_emergencia (id) ON DELETE CASCADE,
    CONSTRAINT ck_emergencias_status CHECK (status IN ('ACIONADA', 'NOTIFICADA', 'ATENDIDA', 'CANCELADA')),
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
