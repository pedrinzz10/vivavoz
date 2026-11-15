package br.com.fiap.vozviva.atendimento;

/**
 * Tipos de atendimento que o cidadão pode procurar. O nome e o gênero servem para montar a frase falada
 * ("O hospital mais perto...", "A farmácia mais perto...").
 */
public enum TipoAtendimento {

	HOSPITAL("hospital", true, true),
	UPA("UPA", false, true),
	UBS("UBS", false, false),
	FARMACIA("farmácia", false, false),
	CRAS("CRAS", true, false),
	DELEGACIA("delegacia", false, false),
	DEFENSORIA("Defensoria Pública", false, false);

	private final String nomeFalado;
	private final boolean masculino;
	private final boolean urgencia;

	TipoAtendimento(String nomeFalado, boolean masculino, boolean urgencia) {
		this.nomeFalado = nomeFalado;
		this.masculino = masculino;
		this.urgencia = urgencia;
	}

	public String nomeFalado() {
		return nomeFalado;
	}

	/** "O hospital", "A farmácia". */
	public String comArtigo() {
		return (masculino ? "O " : "A ") + nomeFalado;
	}

	/** Tipos procurados em urgências médicas: a resposta sempre orienta a ligar 192. */
	public boolean urgencia() {
		return urgencia;
	}

}
