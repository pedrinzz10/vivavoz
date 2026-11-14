-- SPEC-015: estatísticas anonimizadas das verificações de golpe. O texto da mensagem e o usuário
-- nunca são gravados (LGPD): só o tipo de golpe, a classificação, o canal e a data.
CREATE TABLE verificacoes_golpe (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    tipo_golpe     NVARCHAR(30)         NOT NULL,
    classificacao  NVARCHAR(30)         NOT NULL,
    canal          NVARCHAR(20)         NOT NULL,
    analisado_por  NVARCHAR(20)         NOT NULL,
    criada_em      DATETIME2            NOT NULL,
    CONSTRAINT pk_verificacoes_golpe PRIMARY KEY (id),
    CONSTRAINT ck_verificacoes_golpe_classificacao CHECK (classificacao IN
        ('SEM_SINAIS_DE_RISCO', 'NAO_E_POSSIVEL_AVALIAR', 'SUSPEITA', 'PROVAVEL_GOLPE')),
    CONSTRAINT ck_verificacoes_golpe_analisado_por CHECK (analisado_por IN ('IA_E_REGRAS', 'REGRAS'))
);

CREATE INDEX ix_verificacoes_golpe_criada_em ON verificacoes_golpe (criada_em);
