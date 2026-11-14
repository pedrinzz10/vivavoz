package br.com.fiap.vozviva.usuario;

import br.com.fiap.vozviva.seguranca.Papel;
import br.com.fiap.vozviva.seguranca.TokenDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-023: cadastro, login e uso do token emitido pela própria API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutenticacaoIntegrationTest {

	private static final String SENHA = "SenhaForte123";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder senhas;

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void cadastroRetorna201SemExporSenhaEGuardaHashBcrypt() throws Exception {
		String email = emailUnico();

		mvc.perform(cadastro("Maria", email, SENHA, "CIDADAO"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.papel").value("CIDADAO"))
			.andExpect(jsonPath("$.senha").doesNotExist())
			.andExpect(jsonPath("$.senhaHash").doesNotExist());

		Usuario salvo = usuarios.findByEmail(email).orElseThrow();
		assertThat(salvo.getSenhaHash()).startsWith("{bcrypt}").doesNotContain(SENHA);
	}

	@Test
	void emailDuplicadoRetorna409IgnorandoMaiusculas() throws Exception {
		String email = emailUnico();
		mvc.perform(cadastro("Maria", email, SENHA, "CIDADAO")).andExpect(status().isCreated());

		mvc.perform(cadastro("Outra Maria", email.toUpperCase(), SENHA, "CUIDADOR"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.codigo").value("CONFLITO"));
	}

	@Test
	void senhaFracaOuEmailInvalidoRetornam400ComOsCampos() throws Exception {
		mvc.perform(cadastro("Maria", "nao-e-email", "semnumero", "CIDADAO"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.codigo").value("VALIDACAO"))
			.andExpect(jsonPath("$.campos[?(@.campo == 'email')]").exists())
			.andExpect(jsonPath("$.campos[?(@.campo == 'senha')]").exists());
	}

	@Test
	void papelInexistenteNaoECadastrado() throws Exception {
		mvc.perform(cadastro("Invasor", emailUnico(), SENHA, "ADMIN"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void loginDevolveTokenQueAcessaAsRotasDoPapel() throws Exception {
		String email = emailUnico();
		mvc.perform(cadastro("João", email, SENHA, "CUIDADOR")).andExpect(status().isCreated());

		MvcResult resultado = mvc.perform(login(email, SENHA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiraEm").isNotEmpty())
			.andExpect(jsonPath("$.usuario.email").value(email))
			.andReturn();
		String token = json.readTree(resultado.getResponse().getContentAsString()).get("accessToken").asString();

		mvc.perform(get("/api/v1/auth/eu").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.papel").value("CUIDADOR"));

		mvc.perform(put("/teste-seguranca/perfis/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());
	}

	@Test
	void loginAceitaEmailComMaiusculas() throws Exception {
		String email = emailUnico();
		mvc.perform(cadastro("Ana", email, SENHA, "CIDADAO")).andExpect(status().isCreated());

		mvc.perform(login(email.toUpperCase(), SENHA)).andExpect(status().isOk());
	}

	@Test
	void senhaErradaEEmailInexistenteTemAMesmaResposta() throws Exception {
		String email = emailUnico();
		mvc.perform(cadastro("Ana", email, SENHA, "CIDADAO")).andExpect(status().isCreated());

		String senhaErrada = corpoSemTraceIdETimestamp(mvc.perform(login(email, "OutraSenha999"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"))
			.andReturn());
		String emailInexistente = corpoSemTraceIdETimestamp(mvc.perform(login(emailUnico(), "OutraSenha999"))
			.andExpect(status().isUnauthorized())
			.andReturn());

		assertThat(senhaErrada).isEqualTo(emailInexistente);
	}

	@Test
	void usuarioDesativadoNaoEntra() throws Exception {
		String email = emailUnico();
		Usuario usuario = new Usuario("Inativo", email, senhas.encode(SENHA), Papel.CIDADAO);
		usuario.desativar();
		usuarios.save(usuario);

		mvc.perform(login(email, SENHA))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"));
	}

	@Test
	void cincoErrosSeguidosBloqueiamOLoginCom429() throws Exception {
		String email = emailUnico();
		mvc.perform(cadastro("Bia", email, SENHA, "CIDADAO")).andExpect(status().isCreated());

		for (int i = 0; i < 5; i++) {
			mvc.perform(login(email, "SenhaErrada123")).andExpect(status().isUnauthorized());
		}

		mvc.perform(login(email, SENHA))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.codigo").value("MUITAS_TENTATIVAS"));
	}

	@Test
	void euSemToken401() throws Exception {
		mvc.perform(get("/api/v1/auth/eu"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
	}

	@Test
	void euComIdentificadorNaoNumerico401() throws Exception {
		mvc.perform(get("/api/v1/auth/eu").header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("nao-numerico").bearer()))
			.andExpect(status().isUnauthorized());
	}

	private static org.springframework.test.web.servlet.RequestBuilder cadastro(String nome, String email, String senha,
			String papel) {
		return post("/api/v1/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content("""
				{"nome": "%s", "email": "%s", "senha": "%s", "papel": "%s"}
				""".formatted(nome, email, senha, papel));
	}

	private static org.springframework.test.web.servlet.RequestBuilder login(String email, String senha) {
		return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{"email": "%s", "senha": "%s"}
				""".formatted(email, senha));
	}

	private static String emailUnico() {
		return "teste-" + UUID.randomUUID() + "@vozviva.dev";
	}

	private String corpoSemTraceIdETimestamp(MvcResult resultado) throws Exception {
		var corpo = (tools.jackson.databind.node.ObjectNode) json.readTree(resultado.getResponse().getContentAsString());
		corpo.remove("traceId");
		corpo.remove("timestamp");
		return corpo.toString();
	}

}
