package br.com.fiap.vozviva.comum.erro;

/**
 * Dependência externa (API .NET, LLM, broker) fora do ar ou lenta demais.
 */
public class ServicoIndisponivelException extends VozVivaException {

	public ServicoIndisponivelException(String detalhe) {
		super(CodigoErro.SERVICO_INDISPONIVEL, detalhe);
	}

	public ServicoIndisponivelException(String detalhe, Throwable causa) {
		super(CodigoErro.SERVICO_INDISPONIVEL, detalhe, causa);
	}

}
