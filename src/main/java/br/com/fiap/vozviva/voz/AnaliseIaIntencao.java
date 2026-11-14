package br.com.fiap.vozviva.voz;

import br.com.fiap.vozviva.voz.ExemplosIntencao.Exemplo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Parte generativa da classificação por voz: envia ao modelo (Spring AI) a fala transcrita com os exemplos
 * parecidos recuperados e recebe a intenção já estruturada (saída estruturada, sem texto livre).
 * <p>
 * Nunca propaga erro: sem modelo, sem chave, com falha na chamada ou resposta fora do formato, devolve
 * vazio e o serviço usa as regras determinísticas.
 */
@Component
public class AnaliseIaIntencao {

	private static final Logger log = LoggerFactory.getLogger(AnaliseIaIntencao.class);

	private final ChatClient chatClient;
	private final String instrucoes;

	public AnaliseIaIntencao(ObjectProvider<ChatModel> modelo, @Value("${spring.ai.openai.api-key:}") String chaveApi) {
		ChatModel chatModel = StringUtils.hasText(chaveApi) ? modelo.getIfAvailable() : null;
		this.chatClient = chatModel == null ? null : ChatClient.builder(chatModel).build();
		this.instrucoes = lerInstrucoes();
		if (chatClient == null) {
			log.warn("Sem modelo de IA ou sem OCI_GENAI_API_KEY: a classificação por voz usará só as regras.");
		}
	}

	Optional<RespostaIa> analisar(String fala, List<Exemplo> exemplos) {
		if (chatClient == null) {
			return Optional.empty();
		}
		try {
			// Mensagens montadas prontas (sem template): chaves como "{nome}" na fala não são interpretadas.
			Prompt prompt = new Prompt(List.of(new SystemMessage(instrucoes), new UserMessage(montarPedido(fala, exemplos))));
			RespostaIa resposta = chatClient.prompt(prompt).call().entity(RespostaIa.class);
			return Optional.ofNullable(resposta).filter(r -> r.intencao() != null);
		}
		catch (RuntimeException ex) {
			// A fala nunca vai para o log (pode conter dados pessoais e de saúde).
			log.warn("Falha ao consultar a IA; usando só as regras: {}", ex.getClass().getSimpleName());
			log.debug("Detalhe da falha ao consultar a IA", ex);
			return Optional.empty();
		}
	}

	static String montarPedido(String fala, List<Exemplo> exemplos) {
		String contexto = exemplos.stream().map(AnaliseIaIntencao::descrever).collect(Collectors.joining("\n"));
		return "<exemplos_parecidos>\n" + contexto + "\n</exemplos_parecidos>\n\n"
				+ "<fala_do_cidadao>\n" + neutralizarDelimitadores(fala) + "\n</fala_do_cidadao>";
	}

	/** Impede que a fala "feche" o delimitador e passe a ser lida como instrução. */
	static String neutralizarDelimitadores(String fala) {
		return fala.replaceAll("(?i)</?\\s*(fala_do_cidadao|exemplos_parecidos)\\s*>", "[removido]");
	}

	private static String descrever(Exemplo exemplo) {
		List<String> campos = new ArrayList<>();
		campos.add("intencao=" + exemplo.intencao());
		campos.add("urgencia=" + exemplo.urgencia());
		EntidadesFala e = exemplo.entidades() == null ? EntidadesFala.NENHUMA : exemplo.entidades();
		if (e.tipoAtendimento() != null) {
			campos.add("tipoAtendimento=" + e.tipoAtendimento());
		}
		if (e.exigeAcessibilidade() != null) {
			campos.add("exigeAcessibilidade=" + e.exigeAcessibilidade());
		}
		if (e.problemaRua() != null) {
			campos.add("problemaRua=" + e.problemaRua());
		}
		if (e.impedeCadeirante() != null) {
			campos.add("impedeCadeirante=" + e.impedeCadeirante());
		}
		return "- \"" + exemplo.fala() + "\" -> " + String.join(", ", campos);
	}

	private static String lerInstrucoes() {
		try {
			return new ClassPathResource("prompts/intencao-voz.txt").getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Não foi possível ler o prompt da classificação por voz", ex);
		}
	}

	/** Formato que o modelo devolve (saída estruturada). A ação não vem do modelo: é decidida pela intenção. */
	record RespostaIa(Intencao intencao, Urgencia urgencia, EntidadesFala entidades, String mensagemFalada,
			Double confianca) {
	}

}
