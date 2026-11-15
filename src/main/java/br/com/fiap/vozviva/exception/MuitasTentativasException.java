package br.com.fiap.vozviva.exception;


public class MuitasTentativasException extends VozVivaException {

	public MuitasTentativasException() {
		super(CodigoErro.MUITAS_TENTATIVAS, "Muitas tentativas de login seguidas. Tente novamente mais tarde.");
	}

}
