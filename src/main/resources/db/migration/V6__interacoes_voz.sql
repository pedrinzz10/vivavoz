-- SPEC-003: histórico das falas classificadas, visível só para o próprio usuário. A localização nunca é gravada.
CREATE TABLE interacoes_voz (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    usuario_id     BIGINT               NOT NULL,
    texto          NVARCHAR(500)        NOT NULL,
    intencao       NVARCHAR(30)         NOT NULL,
    acao           NVARCHAR(30)         NOT NULL,
    urgencia       NVARCHAR(10)         NOT NULL,
    confianca      DECIMAL(4,3)         NULL,
    latencia_ms    INT                  NOT NULL,
    analisado_por  NVARCHAR(20)         NOT NULL,
    criada_em      DATETIME2            NOT NULL,
    CONSTRAINT pk_interacoes_voz PRIMARY KEY (id),
    CONSTRAINT fk_interacoes_voz_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT ck_interacoes_voz_intencao CHECK (intencao IN
        ('SOCORRO', 'BUSCAR_ATENDIMENTO', 'VERIFICAR_INFORMACAO', 'ZELADORIA_REPORT', 'DESCONHECIDA')),
    CONSTRAINT ck_interacoes_voz_acao CHECK (acao IN
        ('ACIONAR_SOCORRO', 'MOSTRAR_ATENDIMENTOS', 'MOSTRAR_VERIFICACAO', 'ABRIR_ZELADORIA_DOTNET', 'PEDIR_REPETICAO')),
    CONSTRAINT ck_interacoes_voz_urgencia CHECK (urgencia IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')),
    CONSTRAINT ck_interacoes_voz_analisado_por CHECK (analisado_por IN ('IA_E_REGRAS', 'REGRAS'))
);

CREATE INDEX ix_interacoes_voz_usuario_criada ON interacoes_voz (usuario_id, criada_em);
