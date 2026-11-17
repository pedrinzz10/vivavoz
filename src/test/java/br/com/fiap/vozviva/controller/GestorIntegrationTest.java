package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.model.AreaOrgao;
import br.com.fiap.vozviva.model.Usuario;
import br.com.fiap.vozviva.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Papel GESTOR: servidor da prefeitura que atende a zeladoria na API .NET. Não tem cadastro público, recebe o
 * órgão no token e não acessa nenhum dado de cidadão nesta API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GestorIntegrationTest {

	private static final String SENHA = "Girassol2026";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder senhas;

	@Autowired
	private JwtDecoder decoder;

	@Autowired
	private JdbcTemplate jdbc;

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void cadastroPublicoNaoCriaGestor() throws Exception {
		mvc.perform(post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
				{"nome": "Carlos", "email": "%s", "senha": "%s", "papel": "GESTOR", "consentimento": true}"""
				.formatted(email(), SENHA)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
	}

	@Test
	void loginDoGestorLevaOPapelEOOrgaoNoToken() throws Exception {
		String token = loginDeGestor(AreaOrgao.OBRAS);

		Jwt jwt = decoder.decode(token);
		assertThat(jwt.getClaimAsString("role")).isEqualTo("GESTOR");
		assertThat(jwt.getClaimAsString("orgao")).isEqualTo("OBRAS");

		mvc.perform(get("/api/v1/auth/eu").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.papel").value("GESTOR"))
			.andExpect(jsonPath("$.orgao").value("OBRAS"));
	}

	@Test
	void tokenDeCidadaoNaoTemOrgao() throws Exception {
		String email = email();
		mvc.perform(post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
				{"nome": "Maria", "email": "%s", "senha": "%s", "papel": "CIDADAO", "consentimento": true}"""
				.formatted(email, SENHA)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orgao").doesNotExist());

		assertThat(decoder.decode(login(email)).hasClaim("orgao")).isFalse();
	}

	@Test
	void gestorNaoAcessaNenhumDadoDeCidadao() throws Exception {
		String token = loginDeGestor(AreaOrgao.ILUMINACAO);

		for (MockHttpServletRequestBuilder requisicao : new MockHttpServletRequestBuilder[] {
				get("/api/v1/perfis-emergencia/meu"),
				get("/api/v1/perfis-emergencia"),
				post("/api/v1/perfis-emergencia").content("{}"),
				post("/api/v1/emergencias").content("{}"),
				get("/api/v1/emergencias"),
				post("/api/v1/voz/intencoes").content("{\"texto\": \"socorro\"}"),
				get("/api/v1/voz/interacoes"),
				post("/api/v1/verificacoes").content("{\"texto\": \"isso é golpe?\"}"),
				get("/api/v1/atendimentos/proximos?tipo=HOSPITAL&latitude=-23.5&longitude=-46.7") }) {
			mvc.perform(requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isForbidden());
		}
	}

	@Test
	void bancoExigeOrgaoDoGestorESoDele() {
		assertThatThrownBy(() -> inserir("GESTOR", null)).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> inserir("CIDADAO", "OBRAS")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> inserir("GESTOR", "SAUDE")).isInstanceOf(DataIntegrityViolationException.class);
	}

	private void inserir(String papel, String orgao) {
		jdbc.update("""
				insert into usuarios (nome, email, senha_hash, papel, orgao, ativo, consentimento, criado_em, atualizado_em)
				values ('X', ?, 'hash', ?, ?, 1, 1, ?, ?)""", email(), papel, orgao, Instant.now(), Instant.now());
	}

	private String loginDeGestor(AreaOrgao orgao) throws Exception {
		String email = email();
		Usuario gestor = Usuario.gestor("Gestor de teste", email, senhas.encode(SENHA), orgao);
		gestor.registrarConsentimento(Instant.now());
		usuarios.save(gestor);
		return login(email);
	}

	private String login(String email) throws Exception {
		String corpo = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, SENHA)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return json.readTree(corpo).get("accessToken").asString();
	}

	private static String email() {
		return "gestor-" + UUID.randomUUID() + "@teste.dev";
	}

}
