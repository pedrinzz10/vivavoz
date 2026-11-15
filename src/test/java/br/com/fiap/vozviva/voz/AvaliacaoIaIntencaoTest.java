package br.com.fiap.vozviva.voz;

import br.com.fiap.vozviva.seguranca.Papel;
import br.com.fiap.vozviva.usuario.Usuario;
import br.com.fiap.vozviva.usuario.UsuarioRepository;
import br.com.fiap.vozviva.voz.dto.IntencaoRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Avaliação com o modelo real (OCI Generative AI) sobre o conjunto rotulado de falas. Não roda no build
 * normal, porque consome créditos: execute com {@code ./gradlew avaliarIa} (exige OCI_GENAI_API_KEY).
 * O relatório fica em build/reports/avaliacao-intencoes/ia-e-regras.md.
 */
@Tag("llm")
@SpringBootTest(properties = "spring.ai.model.chat=openai")
@ActiveProfiles("test")
class AvaliacaoIaIntencaoTest {

	@Autowired
	private IntencaoVozService voz;

	@Autowired
	private UsuarioRepository usuarios;

	@Test
	void desempenhoNoConjuntoRotulado() {
		assumeTrue(StringUtils.hasText(System.getenv("OCI_GENAI_API_KEY")), "Defina OCI_GENAI_API_KEY para avaliar");
		Long usuario = usuarios.save(new Usuario("Avaliação", "a-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x",
				Papel.CIDADAO)).getId();

		var metricas = FrasesRotuladas.avaliar(FrasesRotuladas.carregar(),
				f -> voz.classificar(usuario, new IntencaoRequest(f.texto(), null, null)).intencao());
		metricas.salvar("ia-e-regras.md", "Avaliação: IA (Spring AI + exemplos recuperados) com regras de segurança");

		assertThat(metricas.recallSocorro()).isEqualTo(1.0);
		assertThat(metricas.acuracia()).isGreaterThanOrEqualTo(0.9);
	}

}
