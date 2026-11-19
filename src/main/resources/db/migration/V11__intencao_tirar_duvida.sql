-- Nova intenção de voz: dúvida sobre um serviço público, respondida pela base curada (conhecimento/servicos-publicos.json).
ALTER TABLE interacoes_voz DROP CONSTRAINT ck_interacoes_voz_intencao;
ALTER TABLE interacoes_voz DROP CONSTRAINT ck_interacoes_voz_acao;
ALTER TABLE interacoes_voz ADD CONSTRAINT ck_interacoes_voz_intencao CHECK (intencao IN
    ('SOCORRO', 'BUSCAR_ATENDIMENTO', 'VERIFICAR_INFORMACAO', 'ZELADORIA_REPORT', 'TIRAR_DUVIDA', 'DESCONHECIDA'));
ALTER TABLE interacoes_voz ADD CONSTRAINT ck_interacoes_voz_acao CHECK (acao IN
    ('ACIONAR_SOCORRO', 'MOSTRAR_ATENDIMENTOS', 'MOSTRAR_VERIFICACAO', 'ABRIR_ZELADORIA_DOTNET', 'MOSTRAR_ORIENTACAO',
     'PEDIR_REPETICAO'));
