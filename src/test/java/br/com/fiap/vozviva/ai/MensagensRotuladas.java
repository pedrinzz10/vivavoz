package br.com.fiap.vozviva.ai;

import br.com.fiap.vozviva.model.CanalMensagem;
import br.com.fiap.vozviva.model.ClassificacaoGolpe;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Conjunto de avaliação versionado (src/test/resources/avaliacao/mensagens-rotuladas.json) e o cálculo
 * das métricas usadas no relatório da disciplina de IA.
 */
final class MensagensRotuladas {

	private MensagensRotuladas() {
	}

	record Mensagem(int id, String rotulo, CanalMensagem canal, String texto) {

		boolean ehGolpe() {
			return "GOLPE".equals(rotulo);
		}

	}

	static List<Mensagem> carregar() {
		try (InputStream entrada = MensagensRotuladas.class.getResourceAsStream("/avaliacao/mensagens-rotuladas.json")) {
			return JsonMapper.builder().build().readValue(entrada, new TypeReference<List<Mensagem>>() {
			});
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Métricas tratando "golpe detectado" como classificação PROVAVEL_GOLPE ou SUSPEITA.
	 */
	record Metricas(int verdadeirosPositivos, int falsosPositivos, int verdadeirosNegativos, int falsosNegativos,
			List<String> erros) {

		int total() {
			return verdadeirosPositivos + falsosPositivos + verdadeirosNegativos + falsosNegativos;
		}

		double acuracia() {
			return (double) (verdadeirosPositivos + verdadeirosNegativos) / total();
		}

		double recall() {
			return (double) verdadeirosPositivos / (verdadeirosPositivos + falsosNegativos);
		}

		double precisao() {
			int detectados = verdadeirosPositivos + falsosPositivos;
			return detectados == 0 ? 0 : (double) verdadeirosPositivos / detectados;
		}

		String emMarkdown(String titulo) {
			StringBuilder md = new StringBuilder("# ").append(titulo).append("\n\n")
				.append("| Métrica | Valor |\n|---|---|\n")
				.append(String.format(Locale.ROOT, "| Mensagens avaliadas | %d |%n", total()))
				.append(String.format(Locale.ROOT, "| Acurácia | %.1f%% |%n", acuracia() * 100))
				.append(String.format(Locale.ROOT, "| Recall (golpes detectados) | %.1f%% |%n", recall() * 100))
				.append(String.format(Locale.ROOT, "| Precisão | %.1f%% |%n", precisao() * 100))
				.append(String.format(Locale.ROOT, "| Golpes não detectados | %d |%n", falsosNegativos))
				.append(String.format(Locale.ROOT, "| Falsos alarmes | %d |%n", falsosPositivos))
				.append("\n## Erros\n\n");
			if (erros.isEmpty()) {
				md.append("Nenhum.\n");
			}
			erros.forEach(e -> md.append("- ").append(e).append('\n'));
			return md.toString();
		}

		void salvar(String arquivo, String titulo) {
			try {
				Path destino = Path.of("build", "reports", "avaliacao-golpes", arquivo);
				Files.createDirectories(destino.getParent());
				Files.writeString(destino, emMarkdown(titulo), StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

	}

	static Metricas avaliar(List<Mensagem> mensagens, Function<Mensagem, ClassificacaoGolpe> classificador) {
		int vp = 0, fp = 0, vn = 0, fn = 0;
		List<String> erros = new java.util.ArrayList<>();
		for (Mensagem m : mensagens) {
			ClassificacaoGolpe c = classificador.apply(m);
			boolean detectou = c != null && c.indicaRisco();
			if (m.ehGolpe() && detectou) {
				vp++;
			}
			else if (m.ehGolpe()) {
				fn++;
				erros.add("#" + m.id() + " golpe não detectado (" + c + "): " + m.texto());
			}
			else if (detectou) {
				fp++;
				erros.add("#" + m.id() + " falso alarme (" + c + "): " + m.texto());
			}
			else {
				vn++;
			}
		}
		return new Metricas(vp, fp, vn, fn, erros);
	}

}
