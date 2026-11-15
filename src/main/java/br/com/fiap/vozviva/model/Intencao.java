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
	DESCONHECIDA(AcaoApp.PEDIR_REPETICAO);

	private final AcaoApp acao;

	Intencao(AcaoApp acao) {
		this.acao = acao;
	}

	public AcaoApp acao() {
		return acao;
	}

}
