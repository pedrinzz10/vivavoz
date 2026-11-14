package br.com.fiap.vozviva.golpe;

import br.com.fiap.vozviva.golpe.dto.VerificacaoRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Avaliação com o modelo de IA real sobre o conjunto rotulado (30 mensagens). Não roda no build normal,
 * porque chama a API paga: execute com {@code ./gradlew avaliarIa} (exige ANTHROPIC_API_KEY).
 * O relatório fica em build/reports/avaliacao-golpes/ia-e-regras.md.
 */
@Tag("llm")
@SpringBootTest(properties = "spring.ai.model.chat=anthropic")
@ActiveProfiles("test")
class AvaliacaoIaGolpeTest {

	@Autowired
	private AssistenteGolpeService assistente;

	@Test
	void desempenhoNoConjuntoRotulado() {
		assumeTrue(StringUtils.hasText(System.getenv("ANTHROPIC_API_KEY")), "Defina ANTHROPIC_API_KEY para avaliar");

		var metricas = MensagensRotuladas.avaliar(MensagensRotuladas.carregar(),
				m -> assistente.verificar(new VerificacaoRequest(m.texto(), m.canal())).classificacao());
		metricas.salvar("ia-e-regras.md", "Avaliação: IA (Spring AI + RAG) com regras de segurança");

		assertThat(metricas.acuracia()).isGreaterThanOrEqualTo(0.8);
		assertThat(metricas.recall()).isGreaterThanOrEqualTo(0.9);
	}

}
