package br.com.fiap.vozviva.comum.erro;

public class ConflitoException extends VozVivaException {

	public ConflitoException(String detalhe) {
		super(CodigoErro.CONFLITO, detalhe);
	}

}
