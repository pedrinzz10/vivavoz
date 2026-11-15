package br.com.fiap.vozviva.ai;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Busca por relevância de palavras (TF-IDF) sobre uma lista pequena e curada de itens: a parte de
 * recuperação do RAG do assistente contra golpes e da classificação de intenção por voz.
 * <p>
 * Escolha consciente: com bases pequenas, a busca é determinística, testável e não depende de um modelo de
 * embeddings (o endpoint compatível com OpenAI da OCI não expõe embeddings). A evolução natural é a busca
 * vetorial (embeddings da OCI ou Oracle 23ai Vector Search).
 *
 * @param <T> tipo do item indexado
 */
public final class IndiceTfIdf<T> {

	private static final Pattern NAO_PALAVRA = Pattern.compile("[^a-z0-9]+");

	/** Palavras muito comuns, que não ajudam a distinguir um item do outro. */
	private static final Set<String> IRRELEVANTES = Set.of("a", "o", "as", "os", "de", "da", "do", "das", "dos",
			"e", "em", "no", "na", "nos", "nas", "um", "uma", "para", "por", "com", "que", "se", "seu", "sua",
			"voce", "nao", "ou", "ao", "mais", "esta", "este", "isso", "ja", "pelo", "pela", "sem", "como");

	private final List<T> itens;
	private final List<Map<String, Double>> pesos;
	private final Map<String, Double> idf = new HashMap<>();

	public IndiceTfIdf(List<T> itens, Function<T, String> textoIndexado) {
		this.itens = List.copyOf(itens);
		List<Map<String, Integer>> contagens = this.itens.stream().map(i -> contar(textoIndexado.apply(i))).toList();
		Map<String, Integer> frequenciaItens = new HashMap<>();
		contagens.forEach(c -> c.keySet().forEach(t -> frequenciaItens.merge(t, 1, Integer::sum)));
		frequenciaItens.forEach((termo, df) -> idf.put(termo, Math.log(1.0 + (double) this.itens.size() / df)));
		this.pesos = contagens.stream().map(this::ponderar).toList();
	}

	/** Até {@code limite} itens com alguma relação com o texto, do mais para o menos relevante. */
	public List<T> buscar(String texto, int limite) {
		return buscarComPontos(texto, limite).stream().map(Resultado::item).toList();
	}

	public List<Resultado<T>> buscarComPontos(String texto, int limite) {
		Map<String, Integer> consulta = contar(texto);
		return IntStream.range(0, itens.size())
			.mapToObj(i -> new Resultado<>(itens.get(i), pontuar(consulta, pesos.get(i))))
			.filter(r -> r.pontos() > 0)
			.sorted(Comparator.comparingDouble((Resultado<T> r) -> r.pontos()).reversed())
			.limit(limite)
			.toList();
	}

	public int tamanho() {
		return itens.size();
	}

	/** Minúsculas e sem acentos, para "Benefício" e "beneficio" serem a mesma palavra. */
	public static String normalizar(String texto) {
		String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return semAcento.toLowerCase(Locale.ROOT);
	}

	/** Conta os termos já normalizados (sem acento, minúsculos, sem palavras irrelevantes, radical simples). */
	static Map<String, Integer> contar(String texto) {
		Map<String, Integer> contagem = new HashMap<>();
		for (String palavra : NAO_PALAVRA.split(normalizar(texto))) {
			if (palavra.length() > 2 && !IRRELEVANTES.contains(palavra)) {
				contagem.merge(radical(palavra), 1, Integer::sum);
			}
		}
		return contagem;
	}

	/** Radical bem simples para português: "bloqueado", "bloqueio" e "bloquear" viram "bloque". */
	private static String radical(String palavra) {
		return palavra.length() > 6 ? palavra.substring(0, 6) : palavra;
	}

	private double pontuar(Map<String, Integer> consulta, Map<String, Double> pesosItem) {
		double total = 0;
		for (String termo : consulta.keySet()) {
			total += pesosItem.getOrDefault(termo, 0.0);
		}
		return total;
	}

	private Map<String, Double> ponderar(Map<String, Integer> contagem) {
		Map<String, Double> resultado = new HashMap<>();
		contagem.forEach((termo, tf) -> resultado.put(termo, (1 + Math.log(tf)) * idf.getOrDefault(termo, 0.0)));
		return resultado;
	}

	public record Resultado<T>(T item, double pontos) {
	}

}
