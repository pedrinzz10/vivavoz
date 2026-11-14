package br.com.fiap.vozviva.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-002: validação do JWT emitido pelo .NET e controle de acesso por papel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SegurancaIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void semToken401NoFormatoPadrao() throws Exception {
		mvc.perform(get("/teste-seguranca/eu"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
			.andExpect(jsonPath("$.mensagemFalada").isNotEmpty())
			.andExpect(jsonPath("$.traceId").isNotEmpty())
			.andExpect(header().exists("X-Trace-Id"))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void tokenComAssinaturaInvalida401() throws Exception {
		String forjado = TokenDeTeste.cuidador("invasor").assinadoCom("outro-segredo-com-mais-de-32-bytes-xx").bearer();

		mvc.perform(get("/teste-seguranca/eu").header(HttpHeaders.AUTHORIZATION, forjado))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
	}

	@Test
	void tokenExpirado401() throws Exception {
		String expirado = TokenDeTeste.cidadao("u1").expiraEm(Instant.now().minusSeconds(120)).bearer();

		mvc.perform(get("/teste-seguranca/eu").header(HttpHeaders.AUTHORIZATION, expirado))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSemExpiracao401() throws Exception {
		String eterno = TokenDeTeste.cidadao("u1").semExpiracao().bearer();

		mvc.perform(get("/teste-seguranca/eu").header(HttpHeaders.AUTHORIZATION, eterno))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenDeOutroEmissor401() throws Exception {
		String outroEmissor = TokenDeTeste.cidadao("u1").issuer("emissor-desconhecido").bearer();

		mvc.perform(get("/teste-seguranca/eu").header(HttpHeaders.AUTHORIZATION, outroEmissor))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenMalformado401() throws Exception {
		mvc.perform(get("/teste-seguranca/eu").header(HttpHeaders.AUTHORIZATION, "Bearer nao.e.jwt"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
	}

	@Test
	void identificaUsuarioEPapeisDoToken() throws Exception {
		mvc.perform(get("/teste-seguranca/eu").header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("cidadao-42").bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("cidadao-42"))
			.andExpect(jsonPath("$.papeis[0]").value("CIDADAO"));
	}

	@Test
	void aceitaPapeisComoListaNaClaimRoles() throws Exception {
		String token = TokenDeTeste.semPapel("u7").claim("roles", List.of("cidadao", "CUIDADOR")).bearer();

		mvc.perform(get("/teste-seguranca/eu").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.papeis", containsInAnyOrder("CIDADAO", "CUIDADOR")));
	}

	@Test
	void cidadaoAcessaDadosDeSuporte() throws Exception {
		mvc.perform(get("/teste-seguranca/suporte").header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("u1").bearer()))
			.andExpect(status().isOk());
	}

	@Test
	void cidadaoNaoEditaPerfil403NoFormatoPadrao() throws Exception {
		mvc.perform(put("/teste-seguranca/perfis/42").header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("42").bearer()))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"))
			.andExpect(jsonPath("$.mensagemFalada").isNotEmpty());
	}

	@Test
	void cuidadorEditaPerfilSemCsrf200() throws Exception {
		mvc.perform(put("/teste-seguranca/perfis/42").header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cuidador("c1").bearer()))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.cidadaoId").value("42"));
	}

	@Test
	void escritaSemTokenRetorna401EmVezDe403DoCsrf() throws Exception {
		mvc.perform(put("/teste-seguranca/perfis/42"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
	}

	@Test
	void escritaComCsrfValidoMasSemTokenContinuaExigindoLogin() throws Exception {
		mvc.perform(put("/teste-seguranca/perfis/42").with(csrf()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void csrfContinuaAtivoParaUsuarioAutenticadoSemHeaderAuthorization() throws Exception {
		// Simula um futuro login por cookie/sessão: autenticado, mas sem header Authorization.
		mvc.perform(put("/teste-seguranca/perfis/42").with(user("cuidador-web").roles("CUIDADOR")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));

		mvc.perform(put("/teste-seguranca/perfis/42").with(user("cuidador-web").roles("CUIDADOR")).with(csrf()))
			.andExpect(status().isOk());
	}

	@Test
	void papelDesconhecidoNaoConcedeAcesso403() throws Exception {
		String admin = TokenDeTeste.semPapel("u9").claim("role", "ADMIN").bearer();

		mvc.perform(get("/teste-seguranca/suporte").header(HttpHeaders.AUTHORIZATION, admin))
			.andExpect(status().isForbidden());
	}

	@Test
	void healthEInfoPublicos() throws Exception {
		mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
		mvc.perform(get("/actuator/info"))
			.andExpect(status().isOk());
	}

	@Test
	void outrosEndpointsDoActuatorProtegidos() throws Exception {
		mvc.perform(get("/actuator/env"))
			.andExpect(status().isUnauthorized());
	}

}
