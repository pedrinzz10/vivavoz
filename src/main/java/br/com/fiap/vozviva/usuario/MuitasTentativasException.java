package br.com.fiap.vozviva.usuario;

import br.com.fiap.vozviva.comum.erro.CodigoErro;
import br.com.fiap.vozviva.comum.erro.VozVivaException;

public class MuitasTentativasException extends VozVivaException {

	public MuitasTentativasException() {
		super(CodigoErro.MUITAS_TENTATIVAS, "Muitas tentativas de login seguidas. Tente novamente mais tarde.");
	}

}
