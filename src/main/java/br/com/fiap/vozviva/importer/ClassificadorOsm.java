package br.com.fiap.vozviva.importer;

import br.com.fiap.vozviva.model.Acessibilidade;
import br.com.fiap.vozviva.model.TipoAtendimento;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Converte as tags de um elemento do OpenStreetMap no tipo de atendimento e na acessibilidade.
 * Elementos sem nome ou que não são atendimento público reconhecível (ex.: consultório particular) ficam de fora.
 */
public final class ClassificadorOsm {

	private static final Pattern DEFENSORIA = Pattern.compile("\\bdefensoria\\b");
	private static final Pattern CRAS = Pattern.compile("\\bcras\\b|centro de referencia (da|de) assistencia social");
	private static final Pattern UPA = Pattern.compile("\\bupa\\b|pronto atendimento|unidade de pronto");
	private static final Pattern UBS = Pattern.compile("\\bubs\\b|unidade basica|posto de saude|centro de saude");
	private static final Pattern DELEGACIA = Pattern.compile("\\bdelegacia\\b|distrito policial|\\bddm\\b|\\bdp\\b");

	private ClassificadorOsm() {
	}

	public static Optional<TipoAtendimento> tipo(Map<String, String> tags) {
		String nome = normalizar(tags.get("name"));
		if (nome.isBlank()) {
			return Optional.empty();
		}
		String amenity = tags.getOrDefault("amenity", "");
		String healthcare = tags.getOrDefault("healthcare", "");
		boolean saude = !healthcare.isBlank() || amenity.equals("hospital") || amenity.equals("clinic") || amenity.equals("doctors");

		if (DEFENSORIA.matcher(nome).find()) {
			return Optional.of(TipoAtendimento.DEFENSORIA);
		}
		if (CRAS.matcher(nome).find()) {
			return Optional.of(TipoAtendimento.CRAS);
		}
		if (saude && UPA.matcher(nome).find()) {
			return Optional.of(TipoAtendimento.UPA);
		}
		if (amenity.equals("hospital") || healthcare.equals("hospital")) {
			return Optional.of(TipoAtendimento.HOSPITAL);
		}
		if (saude && UBS.matcher(nome).find()) {
			return Optional.of(TipoAtendimento.UBS);
		}
		if (amenity.equals("pharmacy") || healthcare.equals("pharmacy")) {
			return Optional.of(TipoAtendimento.FARMACIA);
		}
		// Só delegacias: bases da PM e guardas municipais não registram boletim de ocorrência.
		if (amenity.equals("police") && DELEGACIA.matcher(nome).find()) {
			return Optional.of(TipoAtendimento.DELEGACIA);
		}
		return Optional.empty();
	}

	/** Tag {@code wheelchair}: yes, limited, no; ausente ou outro valor vira DESCONHECIDA. */
	public static Acessibilidade acessibilidade(Map<String, String> tags) {
		return switch (tags.getOrDefault("wheelchair", "").trim().toLowerCase(Locale.ROOT)) {
			case "yes", "designated" -> Acessibilidade.SIM;
			case "limited" -> Acessibilidade.PARCIAL;
			case "no" -> Acessibilidade.NAO;
			default -> Acessibilidade.DESCONHECIDA;
		};
	}

	/** Tag {@code toilets:wheelchair}: yes/no; ausente fica sem informação ({@code null}). */
	public static Boolean banheiroAdaptado(Map<String, String> tags) {
		return simOuNao(tags.get("toilets:wheelchair"));
	}

	/** {@code opening_hours=24/7}; com horário informado e diferente, {@code false}; sem horário, {@code null}. */
	public static Boolean funciona24h(Map<String, String> tags) {
		String horario = tags.get("opening_hours");
		if (horario == null || horario.isBlank()) {
			return null;
		}
		return horario.replace(" ", "").equalsIgnoreCase("24/7");
	}

	/** Rua, número - bairro, cidade (com o que estiver preenchido). */
	public static String endereco(Map<String, String> tags) {
		String rua = juntar(", ", tags.get("addr:street"), tags.get("addr:housenumber"));
		String local = juntar(", ", tags.get("addr:suburb"), tags.get("addr:city"));
		String endereco = juntar(" - ", rua, local);
		return endereco.isBlank() ? null : endereco;
	}

	/** Primeiro telefone de {@code phone} ou {@code contact:phone}. */
	public static String telefone(Map<String, String> tags) {
		for (String chave : List.of("phone", "contact:phone")) {
			String telefone = tags.get(chave);
			if (telefone != null && !telefone.isBlank()) {
				return telefone.split(";")[0].trim();
			}
		}
		return null;
	}

	static String normalizar(String texto) {
		if (texto == null) {
			return "";
		}
		return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
	}

	private static Boolean simOuNao(String valor) {
		if (valor == null) {
			return null;
		}
		return switch (valor.trim().toLowerCase(Locale.ROOT)) {
			case "yes" -> true;
			case "no" -> false;
			default -> null;
		};
	}

	private static String juntar(String separador, String... partes) {
		StringBuilder resultado = new StringBuilder();
		for (String parte : partes) {
			if (parte != null && !parte.isBlank()) {
				if (!resultado.isEmpty()) {
					resultado.append(separador);
				}
				resultado.append(parte.trim());
			}
		}
		return resultado.toString();
	}

}
