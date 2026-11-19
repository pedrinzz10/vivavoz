package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.ai.BaseServicosPublicos;
import br.com.fiap.vozviva.ai.RegrasIntencao;
import br.com.fiap.vozviva.dto.IntencaoRequest;
import br.com.fiap.vozviva.dto.IntencaoResponse;
import br.com.fiap.vozviva.model.Intencao;
import br.com.fiap.vozviva.model.Papel;
import br.com.fiap.vozviva.model.Usuario;
import br.com.fiap.vozviva.repository.UsuarioRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Perguntas do dia a dia de idosos e pessoas com deficiência ({@code avaliacao/perguntas-cidadao.json}), sem IA (regras
 * e base de serviços) e com o modelo real. Mede a intenção e, nas dúvidas, se o serviço da orientação é o certo. Não
 * roda no build normal: {@code ./gradlew avaliarIa} (exige as credenciais da OCI). Relatório em
 * build/reports/avaliacao-intencoes/perguntas-cidadao.md.
 */
@Tag("llm")
@SpringBootTest(properties = "spring.ai.model.chat=openai")
@ActiveProfiles("test")
class AvaliacaoPerguntasCidadaoTest {

	@Autowired
	private IntencaoVozService voz;

	@Autowired
	private BaseServicosPublicos servicos;

	@Autowired
	private UsuarioRepository usuarios;

	@Test
	void perguntasDoCidadaoSemIaEComIa() throws IOException {
		assumeTrue(StringUtils.hasText(System.getenv("OCI_GENAI_API_KEY")) || StringUtils.hasText(System.getenv("OCI_GENAI_USER_OCID")),
				"Defina OCI_GENAI_API_KEY ou as variáveis OCI_GENAI_* da assinatura com o usuário da OCI para avaliar");
		List<Pergunta> perguntas = carregar();
		Long usuario = usuarios.save(new Usuario("Avaliação", "p-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x",
				Papel.CIDADAO)).getId();

		Resultado semIa = avaliar(perguntas, p -> {
			var orientada = IntencaoVozService.orientar(
					IntencaoVozService.decidir(RegrasIntencao.analisar(p.texto()), null, 1.0), p.texto(), servicos);
			return new Obtido(orientada.decisao().intencao(), orientada.servico() == null ? null : orientada.servico().titulo(), "REGRAS");
		});
		Resultado comIa = avaliar(perguntas, p -> {
			IntencaoResponse r = voz.classificar(usuario, new IntencaoRequest(p.texto(), null, null));
			return new Obtido(r.intencao(), r.orientacao() == null ? null : r.orientacao().titulo(), r.analisadoPor());
		});

		String relatorio = "# Avaliação: perguntas do cidadão (" + perguntas.size() + " perguntas)\n\n"
				+ "Conjunto `src/test/resources/avaliacao/perguntas-cidadao.json`, diferente dos exemplos do RAG.\n\n"
				+ "| Configuração | Intenção certa | Serviço certo nas dúvidas | Socorros reconhecidos | Socorros falsos | Respostas com IA |\n"
				+ "|---|---|---|---|---|---|\n"
				+ semIa.linha("Sem IA (regras e base de serviços)") + comIa.linha("IA + regras (modelo real)")
				+ "\n## Erros sem IA\n\n" + semIa.erros() + "\n## Erros com IA\n\n" + comIa.erros();
		Path saida = Path.of("build/reports/avaliacao-intencoes/perguntas-cidadao.md");
		Files.createDirectories(saida.getParent());
		Files.writeString(saida, relatorio);
		System.out.println(relatorio);

		assertThat(comIa.socorrosReconhecidos).isEqualTo(comIa.socorros);
	}

	private Resultado avaliar(List<Pergunta> perguntas, Function<Pergunta, Obtido> classificador) {
		Resultado r = new Resultado();
		for (Pergunta p : perguntas) {
			Obtido o = classificador.apply(p);
			r.total++;
			r.comIa += "IA_E_REGRAS".equals(o.analisadoPor()) ? 1 : 0;
			boolean intencaoCerta = o.intencao() == p.intencao();
			if (intencaoCerta) {
				r.intencoesCertas++;
			}
			else {
				r.listaDeErros.add("- \"" + p.texto() + "\": esperado " + p.intencao() + ", obtido " + o.intencao());
			}
			if (p.intencao() == Intencao.SOCORRO) {
				r.socorros++;
				r.socorrosReconhecidos += o.intencao() == Intencao.SOCORRO ? 1 : 0;
			}
			else if (o.intencao() == Intencao.SOCORRO) {
				r.socorrosFalsos++;
			}
			if (p.servico() != null) {
				r.duvidas++;
				String esperado = servicos.buscarPorId(p.servico()).titulo();
				if (esperado.equals(o.servico())) {
					r.servicosCertos++;
				}
				else if (intencaoCerta) {
					r.listaDeErros.add("- \"" + p.texto() + "\": serviço esperado \"" + esperado + "\", obtido \"" + o.servico() + "\"");
				}
			}
		}
		return r;
	}

	private static List<Pergunta> carregar() {
		try (InputStream entrada = new ClassPathResource("avaliacao/perguntas-cidadao.json").getInputStream()) {
			return JsonMapper.builder().build().readValue(entrada, Arquivo.class).perguntas();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	record Arquivo(String descricao, List<Pergunta> perguntas) {
	}

	record Pergunta(String texto, Intencao intencao, String servico) {
	}

	record Obtido(Intencao intencao, String servico, String analisadoPor) {
	}

	static final class Resultado {

		int total;
		int intencoesCertas;
		int duvidas;
		int servicosCertos;
		int socorros;
		int socorrosReconhecidos;
		int socorrosFalsos;
		int comIa;
		final List<String> listaDeErros = new ArrayList<>();

		String linha(String nome) {
			return "| " + nome + " | " + pct(intencoesCertas, total) + " (" + intencoesCertas + "/" + total + ") | "
					+ pct(servicosCertos, duvidas) + " (" + servicosCertos + "/" + duvidas + ") | "
					+ pct(socorrosReconhecidos, socorros) + " | " + socorrosFalsos + " | " + comIa + " de " + total + " |\n";
		}

		String erros() {
			return listaDeErros.isEmpty() ? "Nenhum.\n" : String.join("\n", listaDeErros) + "\n";
		}

		private static String pct(int parte, int todo) {
			return todo == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * parte / todo).replace('.', ',');
		}

	}

}
