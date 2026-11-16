package br.com.fiap.vozviva.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LGPD: consentimento obrigatório no cadastro (com a data do aceite) e exclusão da própria conta com tudo o que
 * depende dela. O banco é conferido diretamente: nada da conta excluída pode sobrar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContaLgpdIntegrationTest {

	private static final String SENHA = "Girassol2026";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private final JsonMapper json = JsonMapper.builder().build();

	// --- Consentimento --------------------------------------------------------

	@Test
	void cadastroSemConsentimentoOuComConsentimentoFalsoRetorna400() throws Exception {
		for (String consentimento : new String[] { "", ", \"consentimento\": false", ", \"consentimento\": null" }) {
			mvc.perform(post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
					{"nome": "Maria", "email": "%s", "senha": "%s", "papel": "CIDADAO"%s}"""
					.formatted(email(), SENHA, consentimento)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos[*].campo", hasItem("consentimento")));
		}
	}

	@Test
	void consentimentoFicaGravadoComADataDoAceite() throws Exception {
		Conta conta = cadastrar("CIDADAO");

		assertThat(conta.consentimentoEm()).isNotBlank();
		assertThat(jdbc.queryForObject("select consentimento from usuarios where id = ?", Boolean.class, conta.id()))
			.isTrue();
		assertThat(jdbc.queryForObject("select consentimento_em from usuarios where id = ?", Object.class, conta.id()))
			.isNotNull();
	}

	// --- Exclusão da conta ----------------------------------------------------

	@Test
	void cidadaoExcluiAContaETudoOQueDependeDela() throws Exception {
		Conta cidadao = cadastrar("CIDADAO");
		long perfil = id(mvc.perform(com(cidadao, post("/api/v1/perfis-emergencia")).content("{}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		mvc.perform(com(cidadao, post("/api/v1/perfis-emergencia/" + perfil + "/contatos")).content("""
				{"nome": "Ana", "telefone": "(11) 91234-5678", "prioridade": 1}"""))
			.andExpect(status().isCreated());
		mvc.perform(com(cidadao, post("/api/v1/emergencias")).content("{}")).andExpect(status().isAccepted());
		mvc.perform(com(cidadao, post("/api/v1/voz/intencoes")).content("{\"texto\": \"tem farmácia perto?\"}"))
			.andExpect(status().isOk());

		mvc.perform(com(cidadao, delete("/api/v1/auth/eu"))).andExpect(status().isNoContent());

		assertThat(contar("usuarios", "id", cidadao.id())).isZero();
		assertThat(contar("perfis_emergencia", "id", perfil)).isZero();
		assertThat(contar("contatos_emergencia", "perfil_id", perfil)).isZero();
		assertThat(contar("emergencias", "perfil_id", perfil)).isZero();
		assertThat(contar("interacoes_voz", "usuario_id", cidadao.id())).isZero();
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(cidadao.email(), SENHA)))
			.andExpect(status().isUnauthorized());
		mvc.perform(com(cidadao, delete("/api/v1/auth/eu"))).andExpect(status().isUnauthorized());
	}

	@Test
	void cuidadorExcluiAContaEOPerfilContinuaComOCidadao() throws Exception {
		Conta cidadao = cadastrar("CIDADAO");
		Conta cuidador = cadastrar("CUIDADOR");
		mvc.perform(com(cuidador, post("/api/v1/perfis-emergencia")).content("""
				{"cidadaoEmail": "%s", "mobilidade": "ANDADOR"}""".formatted(cidadao.email())))
			.andExpect(status().isCreated());

		mvc.perform(com(cuidador, delete("/api/v1/auth/eu"))).andExpect(status().isNoContent());

		assertThat(contar("usuarios", "id", cuidador.id())).isZero();
		mvc.perform(com(cidadao, get("/api/v1/perfis-emergencia/meu")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mobilidade").value("ANDADOR"))
			.andExpect(jsonPath("$.cuidador").doesNotExist());
	}

	@Test
	void excluirContaExigeLogin() throws Exception {
		mvc.perform(delete("/api/v1/auth/eu")).andExpect(status().isUnauthorized());
	}

	private Conta cadastrar(String papel) throws Exception {
		String email = email();
		String resposta = mvc.perform(post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
				{"nome": "Pessoa", "email": "%s", "senha": "%s", "papel": "%s", "consentimento": true}"""
				.formatted(email, SENHA, papel)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		JsonNode usuario = json.readTree(resposta);
		String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, SENHA)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return new Conta(usuario.get("id").asLong(), email, json.readTree(login).get("accessToken").asString(),
				usuario.path("consentimentoEm").asString(""));
	}

	private static MockHttpServletRequestBuilder com(Conta conta, MockHttpServletRequestBuilder requisicao) {
		return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + conta.token()).contentType(MediaType.APPLICATION_JSON);
	}

	private int contar(String tabela, String coluna, long valor) {
		return jdbc.queryForObject("select count(*) from " + tabela + " where " + coluna + " = ?", Integer.class, valor);
	}

	private long id(String corpo) {
		return json.readTree(corpo).get("id").asLong();
	}

	private static String email() {
		return "lgpd-" + UUID.randomUUID().toString().substring(0, 8) + "@vozviva.dev";
	}

	private record Conta(long id, String email, String token, String consentimentoEm) {
	}

}
