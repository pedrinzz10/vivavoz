package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.security.TokenDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-015 pela API. No perfil de teste não há modelo de IA (spring.ai.model.chat=none): a resposta
 * vem das regras, o que também prova que o assistente funciona com a IA fora do ar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GolpeIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void verificaAMensagemESemIaRespondePelasRegras() throws Exception {
		mvc.perform(post("/api/v1/verificacoes")
				.header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("1").bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"texto": "INSS: seu benefício será bloqueado. Informe sua senha no link http://inss-falso.com", "canal": "SMS"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.classificacao").value("PROVAVEL_GOLPE"))
			.andExpect(jsonPath("$.analisadoPor").value("REGRAS"))
			.andExpect(jsonPath("$.mensagemFalada").isNotEmpty())
			.andExpect(jsonPath("$.fontes[*].orgao", hasItem("INSS")));
	}

	@Test
	void naoGravaOTextoDaMensagem() throws Exception {
		String textoUnico = "Pix urgente para a conta 98765-4 do Fulano Unico";
		mvc.perform(post("/api/v1/verificacoes")
				.header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cuidador("2").bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\": \"" + textoUnico + "\"}"))
			.andExpect(status().isOk());

		List<Map<String, Object>> linhas = jdbc.queryForList("select * from verificacoes_golpe");
		assertThat(linhas).isNotEmpty();
		assertThat(linhas.toString()).doesNotContain("Fulano", "98765");
		assertThat(linhas.getFirst().keySet()).extracting(String::toLowerCase)
			.containsExactlyInAnyOrder("id", "tipo_golpe", "classificacao", "canal", "analisado_por", "criada_em");
	}

	@Test
	void estatisticasMostramOsGolpesMaisFrequentes() throws Exception {
		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/api/v1/verificacoes")
					.header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("1").bearer())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"texto\": \"Oi mãe, troquei de número, me faz um pix urgente\"}"))
				.andExpect(status().isOk());
		}

		mvc.perform(get("/api/v1/verificacoes/estatisticas").param("dias", "7")
				.header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cuidador("2").bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].tipoGolpe", hasItem("FALSO_PARENTE")));
	}

	@Test
	void textoVazioOuLongoDemais400() throws Exception {
		mvc.perform(post("/api/v1/verificacoes")
				.header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("1").bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\": \"\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/verificacoes")
				.header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("1").bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"texto\": \"" + "a".repeat(2001) + "\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void exigeLogin() throws Exception {
		mvc.perform(post("/api/v1/verificacoes").contentType(MediaType.APPLICATION_JSON).content("{\"texto\": \"oi\"}"))
			.andExpect(status().isUnauthorized());
	}

}
