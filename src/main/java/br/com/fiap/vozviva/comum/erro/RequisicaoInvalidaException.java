package br.com.fiap.vozviva.comum.erro;

/**
 * Pedido bem formado, mas que viola uma regra de negócio (ex.: e-mail que não é de um cidadão).
 */
public class RequisicaoInvalidaException extends VozVivaException {

	public RequisicaoInvalidaException(String detalhe) {
		super(CodigoErro.REQUISICAO_INVALIDA, detalhe);
	}

}
