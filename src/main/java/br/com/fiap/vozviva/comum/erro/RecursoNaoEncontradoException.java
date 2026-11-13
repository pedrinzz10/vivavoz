package br.com.fiap.vozviva.comum.erro;

public class RecursoNaoEncontradoException extends VozVivaException {

	public RecursoNaoEncontradoException(String detalhe) {
		super(CodigoErro.RECURSO_NAO_ENCONTRADO, detalhe);
	}

}
