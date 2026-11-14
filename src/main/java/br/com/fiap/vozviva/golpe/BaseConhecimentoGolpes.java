package br.com.fiap.vozviva.golpe;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Parte de recuperação do RAG: encontra, na base curada de golpes ({@code conhecimento/golpes.json}),
 * os documentos mais parecidos com a mensagem recebida, para que o modelo responda com base em
 * orientações oficiais e não só no que "acha".
 * <p>
 * A busca é por relevância de palavras (TF-IDF). Escolha consciente: a base é pequena e curada, a busca é
 * determinística e testável, e não depende de um modelo de embeddings (a Anthropic não oferece um).
 * A evolução natural é trocar por busca vetorial (embeddings da OCI ou Oracle 23ai Vector Search).
 */
@Component
public class BaseConhecimentoGolpes {

	static final String ARQUIVO = "conhecimento/golpes.json";

	private static final Pattern NAO_PALAVRA = Pattern.compile("[^a-z0-9]+");

	/** Palavras muito comuns que não ajudam a distinguir um golpe de outro. */
	private static final Set<String> IRRELEVANTES = Set.of("a", "o", "as", "os", "de", "da", "do", "das", "dos",
			"e", "em", "no", "na", "nos", "nas", "um", "uma", "para", "por", "com", "que", "se", "seu", "sua",
			"voce", "nao", "ou", "ao", "mais", "esta", "este", "isso", "ja", "pelo", "pela", "sem", "como");

	private final List<DocumentoConhecimento> documentos;
	private final List<Map<String, Double>> pesos;
	private final Map<String, Double> idf;

	public BaseConhecimentoGolpes(ObjectMapper json) {
		this.documentos = carregar(json);
		Map<String, Integer> frequenciaDocumentos = new HashMap<>();
		List<Map<String, Integer>> contagens = documentos.stream().map(d -> contar(d.textoIndexado())).toList();
		contagens.forEach(c -> c.keySet().forEach(t -> frequenciaDocumentos.merge(t, 1, Integer::sum)));
		this.idf = new HashMap<>();
		frequenciaDocumentos.forEach((termo, df) -> idf.put(termo, Math.log(1.0 + (double) documentos.size() / df)));
		this.pesos = contagens.stream().map(this::ponderar).toList();
	}

	/**
	 * @return até {@code limite} documentos com alguma relação com o texto, do mais para o menos relevante
	 */
	public List<DocumentoConhecimento> buscar(String texto, int limite) {
		Map<String, Integer> consulta = contar(texto);
		record Pontuado(int indice, double pontos) {
		}
		return IntStream.range(0, documentos.size())
			.mapToObj(i -> new Pontuado(i, pontuar(consulta, pesos.get(i))))
			.filter(p -> p.pontos() > 0)
			.sorted(Comparator.comparingDouble(Pontuado::pontos).reversed())
			.limit(limite)
			.map(p -> documentos.get(p.indice()))
			.toList();
	}

	public int tamanho() {
		return documentos.size();
	}

	private double pontuar(Map<String, Integer> consulta, Map<String, Double> pesosDocumento) {
		double total = 0;
		for (String termo : consulta.keySet()) {
			total += pesosDocumento.getOrDefault(termo, 0.0);
		}
		return total;
	}

	private Map<String, Double> ponderar(Map<String, Integer> contagem) {
		Map<String, Double> resultado = new HashMap<>();
		contagem.forEach((termo, tf) -> resultado.put(termo, (1 + Math.log(tf)) * idf.getOrDefault(termo, 0.0)));
		return resultado;
	}

	/** Conta os termos já normalizados (sem acento, minúsculos, sem palavras irrelevantes, radical simples). */
	static Map<String, Integer> contar(String texto) {
		Map<String, Integer> contagem = new HashMap<>();
		for (String palavra : NAO_PALAVRA.split(RegrasGolpe.normalizar(texto))) {
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

	private static List<DocumentoConhecimento> carregar(ObjectMapper json) {
		try (InputStream entrada = new ClassPathResource(ARQUIVO).getInputStream()) {
			Arquivo arquivo = json.readValue(entrada, Arquivo.class);
			Set<String> ids = new HashSet<>();
			for (DocumentoConhecimento d : arquivo.documentos()) {
				if (!ids.add(d.id())) {
					throw new IllegalStateException("Documento repetido na base de conhecimento: " + d.id());
				}
			}
			return List.copyOf(arquivo.documentos());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Não foi possível ler " + ARQUIVO, ex);
		}
	}

	record Arquivo(String descricao, String versao, List<DocumentoConhecimento> documentos) {
	}

	/**
	 * Um item da base: o golpe, como reconhecê-lo, o canal oficial para confirmar e a fonte.
	 */
	public record DocumentoConhecimento(String id, String titulo, TipoGolpe tipoGolpe, String conteudo,
			String canalOficial, Fonte fonte) {

		String textoIndexado() {
			return titulo + " " + conteudo;
		}

	}

	public record Fonte(String orgao, String url) {
	}

}
