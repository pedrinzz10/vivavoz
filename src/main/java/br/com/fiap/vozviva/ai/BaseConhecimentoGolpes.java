package br.com.fiap.vozviva.ai;

import br.com.fiap.vozviva.model.TipoGolpe;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parte de recuperação do RAG: encontra, na base curada de golpes ({@code conhecimento/golpes.json}),
 * os documentos mais parecidos com a mensagem recebida, para que o modelo responda com base em
 * orientações oficiais e não só no que "acha". A busca é por relevância de palavras ({@link IndiceTfIdf}).
 */
@Component
public class BaseConhecimentoGolpes {

	static final String ARQUIVO = "conhecimento/golpes.json";

	private final IndiceTfIdf<DocumentoConhecimento> indice;

	public BaseConhecimentoGolpes(ObjectMapper json) {
		this.indice = new IndiceTfIdf<>(carregar(json), DocumentoConhecimento::textoIndexado);
	}

	/**
	 * @return até {@code limite} documentos com alguma relação com o texto, do mais para o menos relevante
	 */
	public List<DocumentoConhecimento> buscar(String texto, int limite) {
		return indice.buscar(texto, limite);
	}

	public int tamanho() {
		return indice.tamanho();
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
