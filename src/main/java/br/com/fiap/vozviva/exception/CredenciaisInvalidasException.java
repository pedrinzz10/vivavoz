package br.com.fiap.vozviva.exception;


/**
 * Login recusado. A mensagem é a mesma para e-mail inexistente, senha errada e conta desativada,
 * para não revelar quais e-mails têm conta.
 */
public class CredenciaisInvalidasException extends VozVivaException {

	public CredenciaisInvalidasException() {
		super(CodigoErro.CREDENCIAIS_INVALIDAS, "E-mail ou senha inválidos.");
	}

}
