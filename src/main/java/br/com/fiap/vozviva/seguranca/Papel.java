package br.com.fiap.vozviva.seguranca;

import java.util.Locale;
import java.util.Optional;

/**
 * Papéis aceitos nos tokens emitidos pela API .NET.
 */
public enum Papel {

	/** Idoso ou pessoa com deficiência: consome os serviços de apoio. */
	CIDADAO,

	/** Familiar ou responsável: gerencia o perfil de emergência do cidadão vinculado. */
	CUIDADOR;

	public static final String PREFIXO = "ROLE_";

	public String authority() {
		return PREFIXO + name();
	}

	/**
	 * Converte o valor vindo do token, sem diferenciar maiúsculas/minúsculas e aceitando
	 * o prefixo ROLE_. Valores desconhecidos são ignorados (nunca concedem acesso).
	 */
	public static Optional<Papel> deValor(String valor) {
		if (valor == null || valor.isBlank()) {
			return Optional.empty();
		}
		String normalizado = valor.trim().toUpperCase(Locale.ROOT);
		if (normalizado.startsWith(PREFIXO)) {
			normalizado = normalizado.substring(PREFIXO.length());
		}
		for (Papel papel : values()) {
			if (papel.name().equals(normalizado)) {
				return Optional.of(papel);
			}
		}
		return Optional.empty();
	}

}
