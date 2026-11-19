package br.com.fiap.vozviva.ai;

import br.com.fiap.vozviva.model.TipoAtendimento;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Base curada das dúvidas mais frequentes de pessoas idosas e pessoas com deficiência sobre serviços públicos
 * ({@code conhecimento/servicos-publicos.json}): aposentadoria, BPC, prova de vida, conta e assinatura gov.br, entre
 * outras. A orientação que o cidadão recebe (resumo, passos, link e telefone) vem sempre daqui, nunca do modelo: assim
 * a IA não inventa telefone nem site.
 * <p>
 * Um serviço só responde se a fala citar um dos seus termos (ex.: "aposentadoria", "prova de vida"): palavras comuns
 * como "onde", "quanto" ou "lugar" nunca bastam. Entre os citados, vale o mais parecido pela mesma busca do RAG dos
 * golpes ({@link IndiceTfIdf}). Um serviço de apoio (a conta gov.br, meio para quase todos os outros) só vence se a
 * fala não citar outro: "como pedir aposentadoria com a conta gov.br" é sobre aposentadoria.
 */
@Component
public class BaseServicosPublicos {

	static final String ARQUIVO = "conhecimento/servicos-publicos.json";

	private final IndiceTfIdf<ServicoPublico> indice;
	private final ServicoPublico geral;

	public BaseServicosPublicos(ObjectMapper json) {
		Arquivo arquivo = carregar(json);
		this.indice = new IndiceTfIdf<>(arquivo.servicos(), ServicoPublico::textoIndexado);
		this.geral = arquivo.geral();
	}

	/** O serviço de que a fala trata, se a base tiver um com relação suficiente. */
	public Optional<ServicoPublico> buscar(String fala) {
		Set<String> palavrasDaFala = IndiceTfIdf.contar(fala).keySet();
		List<ServicoPublico> citados = indice.buscar(fala, indice.tamanho()).stream()
			.filter(s -> s.citadoEm(palavrasDaFala))
			.toList();
		return citados.stream().filter(s -> !s.apoio()).findFirst().or(() -> citados.stream().findFirst());
	}

	/** Orientação para uma dúvida que a base não cobre: CRAS e Disque Social 121. */
	public ServicoPublico geral() {
		return geral;
	}

	public int tamanho() {
		return indice.tamanho();
	}

	private static Arquivo carregar(ObjectMapper json) {
		try (InputStream entrada = new ClassPathResource(ARQUIVO).getInputStream()) {
			Arquivo arquivo = json.readValue(entrada, Arquivo.class);
			Set<String> ids = new HashSet<>();
			for (ServicoPublico s : arquivo.servicos()) {
				if (!ids.add(s.id())) {
					throw new IllegalStateException("Serviço repetido na base de serviços públicos: " + s.id());
				}
			}
			return arquivo;
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Não foi possível ler " + ARQUIVO, ex);
		}
	}

	record Arquivo(String descricao, String versao, List<ServicoPublico> servicos, ServicoPublico geral) {
	}

	/**
	 * Um serviço da base.
	 *
	 * @param termos          palavras que a fala precisa citar para o serviço responder (todas as de um termo)
	 * @param apoio           serviço que é meio para os outros (conta gov.br): perde para qualquer outro citado
	 * @param perguntas       jeitos comuns de perguntar sobre o serviço (só para a busca)
	 * @param resumo          frase curta (até 200 caracteres) que o app lê em voz alta
	 * @param link            página oficial do serviço
	 * @param telefone        central oficial gratuita, quando houver (ex.: 135 do INSS)
	 * @param localPresencial onde a pessoa pode ser atendida perto de casa, quando fizer sentido (ex.: CRAS)
	 */
	public record ServicoPublico(String id, String titulo, List<String> termos, boolean apoio, List<String> perguntas,
			String resumo, List<String> passos, String link, String telefone, String orgao,
			TipoAtendimento localPresencial) {

		String textoIndexado() {
			return titulo + " " + String.join(" ", termos) + " " + String.join(" ", perguntas);
		}

		boolean citadoEm(Set<String> palavrasDaFala) {
			return termos.stream().anyMatch(t -> palavrasDaFala.containsAll(IndiceTfIdf.contar(t).keySet()));
		}

	}

}
