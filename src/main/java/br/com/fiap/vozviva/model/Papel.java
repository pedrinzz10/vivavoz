package br.com.fiap.vozviva.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Papéis gravados na conta e enviados na claim {@code role} do token, que a API .NET também valida.
 */
public enum Papel {

	/** Idoso ou pessoa com deficiência: consome os serviços de apoio. */
	CIDADAO,

	/** Familiar ou responsável: gerencia o perfil de emergência do cidadão vinculado. */
	CUIDADOR,

	/**
	 * Servidor da prefeitura que atende os reportes de zeladoria na API .NET. Não tem cadastro público (a conta é
	 * criada pelo sistema) e não acessa nenhum dado de cidadão nesta API.
	 */
	GESTOR;

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
