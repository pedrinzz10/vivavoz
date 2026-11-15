package br.com.fiap.vozviva.comum.documentacao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SPEC-020: o contrato OpenAPI cobre todos os endpoints, com descrição, exemplos e os erros da SPEC-014. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentacaoTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeamentos;

	private JsonNode documento;

	@BeforeEach
	void carregarDocumento() throws Exception {
		String json = mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andReturn().getResponse().getContentAsString();
		documento = JsonMapper.builder().build().readTree(json);
		// O contrato também fica salvo como artefato do build, para o time mobile e o .NET.
		Path destino = Path.of("build", "openapi", "openapi.json");
		Files.createDirectories(destino.getParent());
		Files.writeString(destino, json, StandardCharsets.UTF_8);
	}

	@Test
	void apiDocsDisponivelESwaggerUiPublico() throws Exception {
		assertThat(documento.path("openapi").asString()).startsWith("3.");
		assertThat(documento.path("info").path("title").asString()).isEqualTo("VozViva API");
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	@Test
	void esquemaBearerJwtConfigurado() {
		JsonNode esquema = documento.path("components").path("securitySchemes").path(OpenApiConfig.ESQUEMA_JWT);

		assertThat(esquema.path("type").asString()).isEqualTo("http");
		assertThat(esquema.path("scheme").asString()).isEqualTo("bearer");
		assertThat(esquema.path("bearerFormat").asString()).isEqualTo("JWT");
		assertThat(documento.path("security").toString()).contains(OpenApiConfig.ESQUEMA_JWT);
	}

	@Test
	void todosEndpointsDocumentados() {
		List<String> faltando = new ArrayList<>();
		mapeamentos.getHandlerMethods().forEach((info, metodo) -> {
			for (String caminho : caminhos(info)) {
				if (!caminho.startsWith("/api/")) {
					continue;
				}
				for (RequestMethod http : info.getMethodsCondition().getMethods()) {
					JsonNode operacao = documento.path("paths").path(caminho).path(http.name().toLowerCase(Locale.ROOT));
					if (operacao.isMissingNode()) {
						faltando.add(http + " " + caminho);
					}
				}
			}
		});

		assertThat(faltando).isEmpty();
		assertThat(documento.path("paths").size()).isGreaterThanOrEqualTo(18);
	}

	@Test
	void todaOperacaoTemModuloDescricaoEErrosPadrao() {
		List<String> problemas = new ArrayList<>();
		documento.path("paths").properties().forEach(caminho -> caminho.getValue().properties().forEach(op -> {
			String nome = op.getKey().toUpperCase(Locale.ROOT) + " " + caminho.getKey();
			JsonNode operacao = op.getValue();
			if (operacao.path("summary").asString("").isBlank()) {
				problemas.add(nome + ": sem descrição");
			}
			if (operacao.path("tags").isEmpty()) {
				problemas.add(nome + ": sem módulo");
			}
			JsonNode erro500 = operacao.path("responses").path("500").path("content").path("application/problem+json");
			if (!erro500.path("schema").path("$ref").asString("").endsWith("/ErroResponse")) {
				problemas.add(nome + ": sem o erro padrão");
			}
			if (erro500.path("examples").isEmpty()) {
				problemas.add(nome + ": erro sem exemplo");
			}
			boolean publica = operacao.has("security") && operacao.path("security").isEmpty();
			if (!publica && !(operacao.path("responses").has("401") && operacao.path("responses").has("403"))) {
				problemas.add(nome + ": sem 401/403");
			}
		}));

		assertThat(problemas).isEmpty();
	}

	@Test
	void todaOperacaoDocumentaASuaRespostaDeSucesso() {
		List<String> semSucesso = new ArrayList<>();
		documento.path("paths").properties().forEach(caminho -> caminho.getValue().properties().forEach(op -> {
			JsonNode respostas = op.getValue().path("responses");
			List<String> sucesso = new ArrayList<>();
			respostas.propertyNames().forEach(codigo -> {
				if (codigo.startsWith("2")) {
					sucesso.add(codigo);
				}
			});
			boolean ok = !sucesso.isEmpty() && sucesso.stream()
				.allMatch(c -> c.equals("204") || respostas.path(c).path("content").toString().contains("$ref"));
			if (!ok) {
				semSucesso.add(op.getKey().toUpperCase(Locale.ROOT) + " " + caminho.getKey() + " " + sucesso);
			}
		}));

		assertThat(semSucesso).isEmpty();
		assertThat(documento.path("paths").path("/api/v1/emergencias/{id}/cancelar").path("patch").path("responses")
			.path("200").toString()).contains("EmergenciaResponse");
	}

	@Test
	void corposDeRequisicaoTemExemplo() {
		List<String> semExemplo = new ArrayList<>();
		documento.path("paths").properties().forEach(caminho -> caminho.getValue().properties().forEach(op -> {
			JsonNode corpo = op.getValue().path("requestBody");
			if (!corpo.isMissingNode() && !corpo.toString().contains("\"examples\"")) {
				semExemplo.add(op.getKey() + " " + caminho.getKey());
			}
		}));

		assertThat(semExemplo).isEmpty();
	}

	@Test
	void cadastroELoginSaoPublicosELoginDocumenta429() {
		JsonNode login = documento.path("paths").path("/api/v1/auth/login").path("post");

		assertThat(login.path("security").isArray()).isTrue();
		assertThat(login.path("security").isEmpty()).isTrue();
		assertThat(login.path("responses").has("429")).isTrue();
		assertThat(documento.path("paths").path("/api/v1/auth/cadastro").path("post").path("responses").has("201")).isTrue();
		assertThat(documento.path("paths").path("/api/v1/emergencias").path("post").path("responses").has("202")).isTrue();
	}

	@Test
	void conflitoSoOndeElePodeAcontecerENoFormatoPadrao() {
		JsonNode conflito = documento.path("paths").path("/api/v1/perfis-emergencia/{perfilId}/contatos").path("post")
			.path("responses").path("409");

		assertThat(conflito.path("description").asString()).isEqualTo("Limite de 10 contatos atingido");
		assertThat(conflito.path("content").path("application/problem+json").path("schema").path("$ref").asString())
			.endsWith("/ErroResponse");
		assertThat(documento.path("paths").path("/api/v1/voz/intencoes").path("post").path("responses").has("409")).isFalse();
	}

	private static List<String> caminhos(RequestMappingInfo info) {
		return info.getPathPatternsCondition() == null ? List.of()
				: info.getPathPatternsCondition().getPatternValues().stream().toList();
	}

}
