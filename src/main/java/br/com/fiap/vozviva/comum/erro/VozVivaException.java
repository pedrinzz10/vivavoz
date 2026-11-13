package br.com.fiap.vozviva.comum.erro;

/**
 * Base das exceções de domínio. O tratador global converte qualquer subclasse em uma
 * resposta padronizada usando o {@link CodigoErro} informado.
 * <p>
 * A mensagem (detalhe) é devolvida ao cliente no campo {@code detail}: escreva-a para o app,
 * sem dados internos, consultas ou dados pessoais.
 */
public abstract class VozVivaException extends RuntimeException {

	private final CodigoErro codigo;

	protected VozVivaException(CodigoErro codigo, String detalhe) {
		super(detalhe);
		this.codigo = codigo;
	}

	protected VozVivaException(CodigoErro codigo, String detalhe, Throwable causa) {
		super(detalhe, causa);
		this.codigo = codigo;
	}

	public CodigoErro getCodigo() {
		return codigo;
	}

}
