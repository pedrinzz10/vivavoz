package br.com.fiap.vozviva.golpe;

import br.com.fiap.vozviva.golpe.BaseConhecimentoGolpes.DocumentoConhecimento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Parte generativa do RAG: envia ao modelo (via Spring AI) a mensagem recebida junto com as orientações
 * oficiais recuperadas, e recebe a análise já estruturada (saída estruturada do Spring AI).
 * <p>
 * Nunca propaga erro: se não houver modelo configurado, se a chamada falhar ou a resposta vier fora do
 * formato, devolve vazio e o serviço usa só as regras determinísticas.
 */
@Component
public class AnaliseIaGolpe {

	private static final Logger log = LoggerFactory.getLogger(AnaliseIaGolpe.class);

	private final ChatClient chatClient;
	private final String instrucoes;

	/**
	 * Depende do {@link ChatModel} (e não do ChatClient.Builder, que o Spring AI registra mesmo sem
	 * modelo): sem modelo configurado, o assistente segue funcionando só com as regras.
	 */
	public AnaliseIaGolpe(ObjectProvider<ChatModel> modelo) {
		ChatModel chatModel = modelo.getIfAvailable();
		this.chatClient = chatModel == null ? null : ChatClient.builder(chatModel).build();
		this.instrucoes = lerInstrucoes();
		if (chatClient == null) {
			log.warn("Nenhum modelo de IA configurado: o assistente contra golpes usará só as regras.");
		}
	}

	Optional<RespostaIa> analisar(String mensagem, CanalMensagem canal, List<DocumentoConhecimento> orientacoes) {
		if (chatClient == null) {
			return Optional.empty();
		}
		try {
			// Mensagens montadas prontas (sem template): chaves como "{nome}" no texto recebido não são interpretadas.
			Prompt prompt = new Prompt(List.of(new SystemMessage(instrucoes),
					new UserMessage(montarPedido(mensagem, canal, orientacoes))));
			RespostaIa resposta = chatClient.prompt(prompt).call().entity(RespostaIa.class);
			return Optional.ofNullable(resposta).filter(r -> r.classificacao() != null);
		}
		catch (RuntimeException ex) {
			// O texto da mensagem nunca vai para o log (pode conter dados pessoais).
			log.warn("Falha ao consultar a IA; usando só as regras: {}", ex.getClass().getSimpleName());
			log.debug("Detalhe da falha ao consultar a IA", ex);
			return Optional.empty();
		}
	}

	static String montarPedido(String mensagem, CanalMensagem canal, List<DocumentoConhecimento> orientacoes) {
		String contexto = orientacoes.stream()
			.map(d -> "<orientacao titulo=\"" + d.titulo() + "\" fonte=\"" + d.fonte().orgao() + "\" canal=\""
					+ d.canalOficial() + "\">\n" + d.conteudo() + "\n</orientacao>")
			.collect(Collectors.joining("\n"));
		return "Canal por onde a mensagem chegou: " + canal + "\n\n"
				+ "<orientacoes_oficiais>\n" + contexto + "\n</orientacoes_oficiais>\n\n"
				+ "<mensagem_recebida>\n" + neutralizarDelimitadores(mensagem) + "\n</mensagem_recebida>";
	}

	/** Impede que a mensagem "feche" o delimitador e passe a ser lida como instrução. */
	static String neutralizarDelimitadores(String mensagem) {
		return mensagem.replaceAll("(?i)</?\\s*(mensagem_recebida|orientacoes_oficiais|orientacao)\\s*>", "[removido]");
	}

	private static String lerInstrucoes() {
		try {
			return new ClassPathResource("prompts/assistente-golpes.txt").getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Não foi possível ler o prompt do assistente contra golpes", ex);
		}
	}

	/** Formato que o modelo devolve (saída estruturada). */
	record RespostaIa(ClassificacaoGolpe classificacao, TipoGolpe tipoGolpe, List<String> sinais, String orientacao,
			String canalOficial, String mensagemFalada, Double confianca) {
	}

}
