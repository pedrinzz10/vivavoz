-- SPEC-007: locais de atendimento (dados abertos do OpenStreetMap, licença ODbL). Somente leitura pela API;
-- a carga fica em R__carga_pontos_atendimento.sql, gerada por ./gradlew importarOsm.
CREATE TABLE pontos_atendimento (
    id                 BIGINT IDENTITY(1,1) NOT NULL,
    osm_id             NVARCHAR(30)         NOT NULL,
    nome               NVARCHAR(200)        NOT NULL,
    tipo               NVARCHAR(20)         NOT NULL,
    endereco           NVARCHAR(300)        NULL,
    telefone           NVARCHAR(40)         NULL,
    latitude           DECIMAL(9,6)         NOT NULL,
    longitude          DECIMAL(9,6)         NOT NULL,
    acessibilidade     NVARCHAR(20)         NOT NULL,
    banheiro_adaptado  BIT                  NULL,
    funciona_24h       BIT                  NULL,
    fonte              NVARCHAR(60)         NOT NULL,
    atualizado_em      DATETIME2            NOT NULL,
    CONSTRAINT pk_pontos_atendimento PRIMARY KEY (id),
    CONSTRAINT uq_pontos_atendimento_osm_id UNIQUE (osm_id),
    CONSTRAINT ck_pontos_atendimento_tipo CHECK (tipo IN
        ('HOSPITAL', 'UPA', 'UBS', 'FARMACIA', 'CRAS', 'DELEGACIA', 'DEFENSORIA')),
    CONSTRAINT ck_pontos_atendimento_acessibilidade CHECK (acessibilidade IN ('SIM', 'PARCIAL', 'NAO', 'DESCONHECIDA')),
    CONSTRAINT ck_pontos_atendimento_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_pontos_atendimento_longitude CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX ix_pontos_atendimento_tipo_posicao ON pontos_atendimento (tipo, latitude, longitude);
