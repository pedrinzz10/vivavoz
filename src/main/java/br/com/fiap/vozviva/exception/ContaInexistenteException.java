package br.com.fiap.vozviva.exception;

/**
 * O token é válido, mas a conta dele não existe mais (ex.: removida depois do login). Responde 401 para o
 * app pedir um novo login, em vez de tentar gravar dados de uma conta que não existe.
 */
public class ContaInexistenteException extends VozVivaException {

	public ContaInexistenteException() {
		super(CodigoErro.NAO_AUTENTICADO, "A conta deste acesso não existe mais. Entre de novo.");
	}

}
