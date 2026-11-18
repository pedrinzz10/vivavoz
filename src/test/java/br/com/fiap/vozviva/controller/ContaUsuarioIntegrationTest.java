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
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Correção dos próprios dados (LGPD, art. 18, III) e troca de senha: com isso a tabela usuarios tem CRUD completo
 * (cadastro, consulta, alteração e exclusão da conta).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContaUsuarioIntegrationTest {

	private static final String SENHA = "Girassol2026";
	private static final String NOVA_SENHA = "IpeAmarelo2027";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void corrigeONomeSemMudarPapelNemEmail() throws Exception {
		Conta conta = cadastrar("CIDADAO");

		mvc.perform(com(conta, put("/api/v1/auth/eu")).content("""
				{"nome": "  Maria da Silva Souza  ", "email": "outro@x.dev", "papel": "GESTOR"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nome").value("Maria da Silva Souza"))
			.andExpect(jsonPath("$.email").value(conta.email()))
			.andExpect(jsonPath("$.papel").value("CIDADAO"));

		assertThat(jdbc.queryForMap("select nome, email, papel from usuarios where id = ?", conta.id()))
			.containsEntry("NOME", "Maria da Silva Souza")
			.containsEntry("EMAIL", conta.email())
			.containsEntry("PAPEL", "CIDADAO");
	}

	@Test
	void nomeVazioOuLongoDemaisRetorna400() throws Exception {
		Conta conta = cadastrar("CUIDADOR");

		for (String nome : new String[] { "", "   ", "x".repeat(121) }) {
			mvc.perform(com(conta, put("/api/v1/auth/eu")).content("{\"nome\": \"%s\"}".formatted(nome)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos[*].campo", hasItem("nome")));
		}
	}

	@Test
	void trocaASenhaEOLoginPassaAUsarANova() throws Exception {
		Conta conta = cadastrar("CIDADAO");

		mvc.perform(com(conta, put("/api/v1/auth/eu/senha")).content(troca(SENHA, NOVA_SENHA)))
			.andExpect(status().isNoContent());

		mvc.perform(login(conta.email(), SENHA)).andExpect(status().isUnauthorized());
		mvc.perform(login(conta.email(), NOVA_SENHA)).andExpect(status().isOk());
	}

	@Test
	void senhaAtualErradaNovaFracaOuIgualSaoRecusadas() throws Exception {
		Conta conta = cadastrar("CIDADAO");

		mvc.perform(com(conta, put("/api/v1/auth/eu/senha")).content(troca("SenhaErrada123", NOVA_SENHA)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
		mvc.perform(com(conta, put("/api/v1/auth/eu/senha")).content(troca(SENHA, "somenteletras")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.campos[*].campo", hasItem("novaSenha")));
		mvc.perform(com(conta, put("/api/v1/auth/eu/senha")).content(troca(SENHA, SENHA)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));

		// Nada mudou: a senha original continua valendo.
		mvc.perform(login(conta.email(), SENHA)).andExpect(status().isOk());
	}

	@Test
	void cincoErrosDeSenhaAtualBloqueiamComoNoLogin() throws Exception {
		Conta conta = cadastrar("CIDADAO");

		for (int i = 0; i < 5; i++) {
			mvc.perform(com(conta, put("/api/v1/auth/eu/senha")).content(troca("SenhaErrada123", NOVA_SENHA)))
				.andExpect(status().isBadRequest());
		}

		mvc.perform(com(conta, put("/api/v1/auth/eu/senha")).content(troca(SENHA, NOVA_SENHA)))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.codigo").value("MUITAS_TENTATIVAS"));
		mvc.perform(login(conta.email(), SENHA)).andExpect(status().isTooManyRequests());
	}

	@Test
	void semTokenNaoAlteraNada() throws Exception {
		mvc.perform(put("/api/v1/auth/eu").contentType(MediaType.APPLICATION_JSON).content("{\"nome\": \"X\"}"))
			.andExpect(status().isUnauthorized());
		mvc.perform(put("/api/v1/auth/eu/senha").contentType(MediaType.APPLICATION_JSON).content(troca(SENHA, NOVA_SENHA)))
			.andExpect(status().isUnauthorized());
	}

	private Conta cadastrar(String papel) throws Exception {
		String email = "conta-" + UUID.randomUUID() + "@teste.dev";
		String cadastro = mvc.perform(post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
				{"nome": "Pessoa de teste", "email": "%s", "senha": "%s", "papel": "%s", "consentimento": true}"""
				.formatted(email, SENHA, papel)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String corpoLogin = mvc.perform(login(email, SENHA)).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		JsonNode usuario = json.readTree(cadastro);
		return new Conta(usuario.get("id").asLong(), email, json.readTree(corpoLogin).get("accessToken").asString());
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder com(Conta conta,
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requisicao) {
		return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + conta.token())
			.contentType(MediaType.APPLICATION_JSON);
	}

	private static RequestBuilder login(String email, String senha) {
		return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, senha));
	}

	private static String troca(String atual, String nova) {
		return "{\"senhaAtual\": \"%s\", \"novaSenha\": \"%s\"}".formatted(atual, nova);
	}

	private record Conta(long id, String email, String token) {
	}

}
