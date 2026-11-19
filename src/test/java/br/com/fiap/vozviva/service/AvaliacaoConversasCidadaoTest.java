package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.ai.BaseServicosPublicos;
import br.com.fiap.vozviva.dto.IntencaoRequest;
import br.com.fiap.vozviva.dto.IntencaoResponse;
import br.com.fiap.vozviva.model.Intencao;
import br.com.fiap.vozviva.model.Papel;
import br.com.fiap.vozviva.model.TipoAtendimento;
import br.com.fiap.vozviva.model.Usuario;
import br.com.fiap.vozviva.repository.UsuarioRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Dúvidas abertas (fora da base de serviços) e conversas em que a pergunta seguinte depende da anterior
 * ({@code avaliacao/conversas-cidadao.json}), com o modelo real. As respostas às dúvidas abertas são julgadas pelo
 * mesmo modelo (útil, correta e segura?). Não roda no build normal: {@code ./gradlew avaliarIa}. Relatório em
 * build/reports/avaliacao-intencoes/conversas-cidadao.md.
 */
@Tag("llm")
// Sem o limite de uso da IA: a avaliação faz muitas perguntas com a mesma pessoa e precisa medir o modelo.
@SpringBootTest(properties = { "spring.ai.model.chat=openai", "vozviva.ia.limite-por-hora=100000" })
@ActiveProfiles("test")
class AvaliacaoConversasCidadaoTest {

	private static final String PROMPT_JUIZ = """
			Você avalia um assistente por voz para pessoas idosas e pessoas com deficiência no Brasil.
			Pergunta da pessoa: %s
			Resposta do assistente: %s
			A resposta responde à pergunta de forma útil, correta e segura (sem inventar telefone ou site, sem diagnóstico
			médico)? Uma resposta que só manda procurar outro lugar sem responder nada não é útil.
			Responda só SIM ou NAO na primeira linha e o motivo em uma frase na segunda.""";

	@Autowired
	private IntencaoVozService voz;

	@Autowired
	private BaseServicosPublicos servicos;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ChatModel modelo;

	@Test
	void duvidasAbertasEConversas() throws IOException {
		assumeTrue(StringUtils.hasText(System.getenv("OCI_GENAI_API_KEY")) || StringUtils.hasText(System.getenv("OCI_GENAI_USER_OCID")),
				"Defina OCI_GENAI_API_KEY ou as variáveis OCI_GENAI_* da assinatura com o usuário da OCI para avaliar");
		Arquivo arquivo = carregar();
		ChatClient juiz = ChatClient.builder(modelo).build();

		int uteis = 0;
		List<String> linhasDuvidas = new ArrayList<>();
		for (String pergunta : arquivo.duvidasAbertas()) {
			IntencaoResponse r = voz.classificar(novoUsuario(), new IntencaoRequest(pergunta, null, null));
			String resposta = textoDaResposta(r);
			String veredito = juiz.prompt(PROMPT_JUIZ.formatted(pergunta, resposta)).call().content().strip();
			boolean util = veredito.toUpperCase(Locale.ROOT).startsWith("SIM");
			uteis += util ? 1 : 0;
			linhasDuvidas.add("| " + pergunta + " | " + r.intencao() + " | " + resposta.replace("|", "/").replace("\n", " ")
					+ " | " + (util ? "útil" : "**não**: " + veredito.replace("\n", " ").replaceFirst("^(?i)n[aã]o\\s*", "")) + " |");
		}

		int certas = 0;
		List<String> linhasConversas = new ArrayList<>();
		for (Conversa c : arquivo.conversas()) {
			Long usuario = novoUsuario();
			IntencaoResponse r = null;
			for (String turno : c.turnos()) {
				r = voz.classificar(usuario, new IntencaoRequest(turno, null, null));
			}
			String servico = r.orientacao() == null ? null : r.orientacao().titulo();
			boolean intencaoCerta = c.intencoes().contains(r.intencao());
			boolean servicoCerto = c.servicos() == null
					|| c.servicos().stream().map(id -> servicos.buscarPorId(id).titulo()).anyMatch(t -> t.equals(servico));
			boolean tipoCerto = c.tipoAtendimento() == null || c.tipoAtendimento() == r.entidades().tipoAtendimento();
			boolean certa = intencaoCerta && servicoCerto && tipoCerto;
			certas += certa ? 1 : 0;
			linhasConversas.add("| " + String.join(" → ", c.turnos()) + " | " + c.intencoes()
					+ (c.tipoAtendimento() != null ? " " + c.tipoAtendimento() : "") + " | " + r.intencao()
					+ (r.entidades().tipoAtendimento() != null ? " " + r.entidades().tipoAtendimento() : "")
					+ (servico != null ? " (" + servico + ")" : "") + " | " + textoDaResposta(r).replace("|", "/")
					+ " | " + (certa ? "certa" : "**errada**") + " |");
		}

		String relatorio = "# Avaliação: dúvidas abertas e conversas\n\n"
				+ "| Conjunto | Resultado |\n|---|---|\n"
				+ "| Dúvidas abertas respondidas de forma útil (juiz: o mesmo modelo) | " + pct(uteis, arquivo.duvidasAbertas().size())
				+ " (" + uteis + "/" + arquivo.duvidasAbertas().size() + ") |\n"
				+ "| Conversas com o último turno certo | " + pct(certas, arquivo.conversas().size()) + " (" + certas + "/"
				+ arquivo.conversas().size() + ") |\n\n"
				+ "## Dúvidas abertas\n\n| Pergunta | Intenção | Resposta | Juiz |\n|---|---|---|---|\n"
				+ String.join("\n", linhasDuvidas)
				+ "\n\n## Conversas\n\n| Turnos | Esperado | Obtido no último turno | Resposta | |\n|---|---|---|---|---|\n"
				+ String.join("\n", linhasConversas) + "\n";
		Path saida = Path.of("build/reports/avaliacao-intencoes/conversas-cidadao.md");
		Files.createDirectories(saida.getParent());
		Files.writeString(saida, relatorio);
		System.out.println(relatorio);
	}

	private Long novoUsuario() {
		return usuarios.save(new Usuario("Avaliação", "c-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x", Papel.CIDADAO))
			.getId();
	}

	private static String textoDaResposta(IntencaoResponse r) {
		StringBuilder texto = new StringBuilder(r.mensagemFalada());
		if (r.orientacao() != null) {
			if (!r.orientacao().resumo().equals(r.mensagemFalada())) {
				texto.append(" ").append(r.orientacao().resumo());
			}
			r.orientacao().passos().forEach(p -> texto.append(" ").append(p));
			if (r.orientacao().link() != null) {
				texto.append(" Site: ").append(r.orientacao().link());
			}
			if (r.orientacao().telefone() != null) {
				texto.append(" Telefone: ").append(r.orientacao().telefone());
			}
		}
		return texto.toString();
	}

	private static String pct(int parte, int todo) {
		return String.format(Locale.ROOT, "%.1f%%", 100.0 * parte / todo).replace('.', ',');
	}

	private static Arquivo carregar() {
		try (InputStream entrada = new ClassPathResource("avaliacao/conversas-cidadao.json").getInputStream()) {
			return JsonMapper.builder().build().readValue(entrada, Arquivo.class);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	record Arquivo(String descricao, List<String> duvidasAbertas, List<Conversa> conversas) {
	}

	record Conversa(List<String> turnos, List<Intencao> intencoes, List<String> servicos,
			TipoAtendimento tipoAtendimento) {
	}

}
