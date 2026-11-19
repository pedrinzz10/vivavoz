package br.com.fiap.vozviva.ai;

import br.com.fiap.vozviva.ai.BaseServicosPublicos.ServicoPublico;
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
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parte generativa das dúvidas (TIRAR_DUVIDA): responde a pergunta da pessoa em poucas frases, para qualquer dúvida,
 * com a conversa recente como contexto. Quando a base curada tem o serviço, a resposta usa só os fatos dele (RAG); o
 * link, o telefone e os passos mostrados no app continuam vindo da base.
 * <p>
 * A resposta passa por um filtro antes de chegar ao cidadão ({@link #segura(String)}): site que não seja gov.br ou
 * telefone fora da lista das centrais oficiais descartam a resposta, e a API volta para a orientação da base. Assim o
 * modelo não consegue mandar ninguém para um site ou número inventado.
 */
@Component
public class RespostaIaDuvida {

	private static final Logger log = LoggerFactory.getLogger(RespostaIaDuvida.class);

	static final int MAXIMO_CARACTERES = 400;

	/** Centrais oficiais gratuitas que a resposta pode citar. */
	static final Set<String> TELEFONES_OFICIAIS = Set.of("192", "190", "193", "100", "135", "121", "136", "180", "188");

	private static final Pattern ENDERECO = Pattern.compile("(?i)\\b((https?://)?(www\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+)(/\\S*)?");
	private static final Pattern NUMERO_LONGO = Pattern.compile("\\d[\\d .-]{6,}\\d");
	private static final Pattern TELEFONE_CITADO = Pattern.compile(
			"(?i)\\b(ligue|ligar|liga|disque|telefone|central|n[uú]mero|whatsapp)\\D{0,20}?(\\d{3,4})\\b");

	private final ChatClient chatClient;
	private final String instrucoes;

	public RespostaIaDuvida(ObjectProvider<ChatModel> modelo, CredencialIa credencial) {
		ChatModel chatModel = credencial.configurada() ? modelo.getIfAvailable() : null;
		this.chatClient = chatModel == null ? null : ChatClient.builder(chatModel).build();
		this.instrucoes = lerInstrucoes();
	}

	/**
	 * @param servico serviço da base de que a pergunta trata, ou {@code null} (dúvida aberta)
	 * @return a resposta já filtrada, ou vazio sem IA, com falha ou com uma resposta que não passou no filtro
	 */
	public Optional<String> responder(String pergunta, List<TurnoAnterior> anteriores, ServicoPublico servico) {
		if (chatClient == null) {
			return Optional.empty();
		}
		try {
			Prompt prompt = new Prompt(List.of(new SystemMessage(instrucoes),
					new UserMessage(montarPedido(pergunta, anteriores, servico))));
			return segura(chatClient.prompt(prompt).call().content());
		}
		catch (RuntimeException ex) {
			// A pergunta nunca vai para o log (pode conter dados pessoais e de saúde).
			log.warn("Falha ao gerar a resposta da dúvida; usando a orientação da base: {}", ex.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	static String montarPedido(String pergunta, List<TurnoAnterior> anteriores, ServicoPublico servico) {
		StringBuilder pedido = new StringBuilder();
		if (servico != null) {
			pedido.append("<documento_oficial>\n").append(servico.titulo()).append(": ").append(servico.resumo())
				.append("\nPassos: ").append(String.join(" ", servico.passos()))
				.append("\nÓrgão: ").append(servico.orgao())
				.append(servico.telefone() != null ? "\nTelefone oficial: " + servico.telefone() : "")
				.append("\n</documento_oficial>\n\n");
		}
		if (!anteriores.isEmpty()) {
			pedido.append("<conversa_anterior>\n")
				.append(anteriores.stream().map(t -> "- " + AnaliseIaIntencao.neutralizarDelimitadores(t.fala()))
					.collect(Collectors.joining("\n")))
				.append("\n</conversa_anterior>\n\n");
		}
		return pedido.append("<pergunta>\n").append(AnaliseIaIntencao.neutralizarDelimitadores(pergunta))
			.append("\n</pergunta>").toString();
	}

	/**
	 * A resposta só passa se for curta e não citar site fora do gov.br nem telefone fora das centrais oficiais.
	 * Acima do limite, corta na última frase completa.
	 */
	static Optional<String> segura(String resposta) {
		if (!StringUtils.hasText(resposta)) {
			return Optional.empty();
		}
		String texto = resposta.strip().replaceAll("\\s+", " ");
		if (texto.length() > MAXIMO_CARACTERES) {
			int fim = Math.max(texto.lastIndexOf(". ", MAXIMO_CARACTERES), texto.lastIndexOf("? ", MAXIMO_CARACTERES));
			if (fim < 40) {
				return Optional.empty();
			}
			texto = texto.substring(0, fim + 1);
		}
		Matcher endereco = ENDERECO.matcher(texto);
		while (endereco.find()) {
			String dominio = endereco.group(1).toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
			boolean pareceSite = endereco.group(2) != null || dominio.matches(".*\\.(br|com|net|org|io|app)$");
			if (pareceSite && !dominio.endsWith("gov.br") && !dominio.endsWith("iti.br")) {
				return Optional.empty();
			}
		}
		if (NUMERO_LONGO.matcher(texto).find()) {
			return Optional.empty();
		}
		Matcher telefone = TELEFONE_CITADO.matcher(texto);
		while (telefone.find()) {
			if (!TELEFONES_OFICIAIS.contains(telefone.group(2))) {
				return Optional.empty();
			}
		}
		return Optional.of(texto);
	}

	private static String lerInstrucoes() {
		try {
			return new ClassPathResource("prompts/resposta-duvida.txt").getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Não foi possível ler o prompt das dúvidas", ex);
		}
	}

}
