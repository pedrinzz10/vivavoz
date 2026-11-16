-- Contas excluídas (LGPD): só o id numérico, sem nenhum dado pessoal. O validador do JWT recusa tokens ainda
-- válidos de uma conta que já foi excluída. Ids de IDENTITY nunca são reaproveitados.
CREATE TABLE contas_excluidas (
    usuario_id   BIGINT     NOT NULL,
    excluida_em  DATETIME2  NOT NULL,
    CONSTRAINT pk_contas_excluidas PRIMARY KEY (usuario_id)
);
