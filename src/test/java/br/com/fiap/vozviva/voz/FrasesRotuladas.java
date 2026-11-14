package br.com.fiap.vozviva.voz;

import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Conjunto rotulado de falas para avaliar a classificação de intenção (só regras no build; IA + regras
 * com {@code ./gradlew avaliarIa}). O relatório vai para build/reports/avaliacao-intencoes/.
 */
final class FrasesRotuladas {

	private FrasesRotuladas() {
	}

	static List<Frase> carregar() {
		try (InputStream entrada = new ClassPathResource("avaliacao/frases-intencoes.json").getInputStream()) {
			return JsonMapper.builder().build().readValue(entrada, Arquivo.class).frases();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static Metricas avaliar(List<Frase> frases, Function<Frase, Intencao> classificador) {
		List<String> erros = new ArrayList<>();
		int acertos = 0;
		int socorros = 0;
		int socorrosReconhecidos = 0;
		int socorrosFalsos = 0;
		for (Frase frase : frases) {
			Intencao obtida = classificador.apply(frase);
			if (obtida == frase.intencao()) {
				acertos++;
			}
			else {
				erros.add("- \"" + frase.texto() + "\": esperado " + frase.intencao() + ", obtido " + obtida);
			}
			if (frase.intencao() == Intencao.SOCORRO) {
				socorros++;
				socorrosReconhecidos += obtida == Intencao.SOCORRO ? 1 : 0;
			}
			else if (obtida == Intencao.SOCORRO) {
				socorrosFalsos++;
			}
		}
		return new Metricas(frases.size(), (double) acertos / frases.size(),
				socorros == 0 ? 1 : (double) socorrosReconhecidos / socorros, socorrosFalsos, erros);
	}

	record Arquivo(String descricao, List<Frase> frases) {
	}

	record Frase(String texto, Intencao intencao) {
	}

	/**
	 * @param recallSocorro fração dos pedidos de socorro reconhecidos (o erro mais grave é deixar de reconhecer)
	 * @param socorrosFalsos falas comuns classificadas como socorro (o app só pede confirmação, então custa pouco)
	 */
	record Metricas(int total, double acuracia, double recallSocorro, int socorrosFalsos, List<String> erros) {

		void salvar(String arquivo, String titulo) {
			String relatorio = "# " + titulo + "\n\n| Métrica | Valor |\n|---|---|\n"
					+ "| Falas avaliadas | " + total + " |\n"
					+ "| Acurácia | " + String.format(Locale.ROOT, "%.1f%%", acuracia * 100) + " |\n"
					+ "| Recall de socorro | " + String.format(Locale.ROOT, "%.1f%%", recallSocorro * 100) + " |\n"
					+ "| Socorros falsos | " + socorrosFalsos + " |\n\n## Erros\n\n"
					+ (erros.isEmpty() ? "Nenhum.\n" : String.join("\n", erros) + "\n");
			try {
				Path destino = Path.of("build", "reports", "avaliacao-intencoes", arquivo);
				Files.createDirectories(destino.getParent());
				Files.writeString(destino, relatorio, StandardCharsets.UTF_8);
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

	}

}
