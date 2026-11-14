package br.com.fiap.vozviva.atendimento;

/**
 * Acessibilidade para cadeira de rodas, vinda da tag {@code wheelchair} do OpenStreetMap.
 * {@link #DESCONHECIDA} nunca é apresentada como acessível.
 */
public enum Acessibilidade {

	SIM,
	PARCIAL,
	NAO,
	DESCONHECIDA;

	public boolean confirmada() {
		return this == SIM || this == PARCIAL;
	}

}
