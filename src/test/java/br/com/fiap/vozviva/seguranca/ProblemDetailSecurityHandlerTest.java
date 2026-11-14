package br.com.fiap.vozviva.seguranca;

import br.com.fiap.vozviva.comum.erro.ProblemDetailFactory;
import br.com.fiap.vozviva.comum.rastreio.TraceIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailSecurityHandlerTest {

	private final JsonMapper json = JsonMapper.builder().build();
	private final ProblemDetailSecurityHandler handler = new ProblemDetailSecurityHandler(new ProblemDetailFactory(), json);

	private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/perfis-emergencia/42");
	private final MockHttpServletResponse response = new MockHttpServletResponse();

	@BeforeEach
	void definirTraceId() {
		MDC.put(TraceIdFilter.MDC_KEY, "trace-seguranca-01");
	}

	@AfterEach
	void limparTraceId() {
		MDC.remove(TraceIdFilter.MDC_KEY);
		SecurityContextHolder.clearContext();
	}

	@Test
	void falhaDeCsrfSemUsuarioAutenticadoViraNaoAutenticado401() throws Exception {
		handler.handle(request, response, new MissingCsrfTokenException("sem token csrf"));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(json.readTree(response.getContentAsString()).get("codigo").asString()).isEqualTo("NAO_AUTENTICADO");
	}

	@Test
	void falhaDeCsrfComUsuarioAutenticadoContinuaAcessoNegado403() throws Exception {
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("cuidador-1").build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

		handler.handle(request, response, new MissingCsrfTokenException("sem token csrf"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(json.readTree(response.getContentAsString()).get("codigo").asString()).isEqualTo("ACESSO_NEGADO");
	}

	@Test
	void naoAutenticadoRetorna401Padronizado() throws Exception {
		handler.commence(request, response, new BadCredentialsException("token expirado"));

		JsonNode corpo = json.readTree(response.getContentAsString());
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(corpo.get("codigo").asString()).isEqualTo("NAO_AUTENTICADO");
		assertThat(corpo.get("status").asInt()).isEqualTo(401);
		assertThat(corpo.get("mensagemFalada").asString()).isNotBlank();
		assertThat(corpo.get("traceId").asString()).isEqualTo("trace-seguranca-01");
		assertThat(corpo.get("instance").asString()).isEqualTo("/api/v1/perfis-emergencia/42");
		assertThat(response.getContentAsString()).doesNotContain("token expirado");
	}

	@Test
	void acessoNegadoRetorna403Padronizado() throws Exception {
		handler.handle(request, response, new AccessDeniedException("sem papel CUIDADOR"));

		JsonNode corpo = json.readTree(response.getContentAsString());
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(corpo.get("codigo").asString()).isEqualTo("ACESSO_NEGADO");
		assertThat(corpo.get("type").asString()).isEqualTo("urn:vozviva:erro:acesso_negado");
		assertThat(response.getContentAsString()).doesNotContain("CUIDADOR");
	}

}
