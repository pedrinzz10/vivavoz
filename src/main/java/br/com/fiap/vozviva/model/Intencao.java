package br.com.fiap.vozviva.model;

/**
 * O que o cidadão quer, segundo o catálogo de convenções. Cada intenção leva a uma única ação do app,
 * decidida aqui no servidor (nunca pelo modelo): assim a IA não consegue disparar uma ação incoerente.
 */
public enum Intencao {

	SOCORRO(AcaoApp.ACIONAR_SOCORRO),
	BUSCAR_ATENDIMENTO(AcaoApp.MOSTRAR_ATENDIMENTOS),
	VERIFICAR_INFORMACAO(AcaoApp.MOSTRAR_VERIFICACAO),
	/** Problema na rua: a zeladoria fica na API .NET; aqui só se encaminha. */
	ZELADORIA_REPORT(AcaoApp.ABRIR_ZELADORIA_DOTNET),
	/** Dúvida sobre um serviço público (aposentadoria, BPC, gov.br...): a resposta vem da base curada, com o link oficial. */
	TIRAR_DUVIDA(AcaoApp.MOSTRAR_ORIENTACAO),
	DESCONHECIDA(AcaoApp.PEDIR_REPETICAO);

	private final AcaoApp acao;

	Intencao(AcaoApp acao) {
		this.acao = acao;
	}

	public AcaoApp acao() {
		return acao;
	}

}
