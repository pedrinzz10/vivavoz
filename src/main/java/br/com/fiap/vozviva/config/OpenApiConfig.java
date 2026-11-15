package br.com.fiap.vozviva.config;

import br.com.fiap.vozviva.dto.ErroResponse;
import br.com.fiap.vozviva.exception.CodigoErro;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Contrato OpenAPI da API (Swagger em {@code /swagger-ui.html}, JSON em {@code /v3/api-docs}).
 * <p>
 * Além do que o springdoc gera dos controllers, aqui ficam o esquema Bearer JWT, os módulos (tags) e as
 * respostas de erro padrão da SPEC-014 ({@link ErroResponse}), acrescentadas a todas as operações para o app
 * saber o que tratar em cada uma.
 */
@Configuration
public class OpenApiConfig {

	public static final String ESQUEMA_JWT = "bearerJwt";
	public static final String TAG_AUTENTICACAO = "Autenticação";
	public static final String TAG_PERFIL = "Perfil de emergência";
	public static final String TAG_CONTATOS = "Contatos de emergência";
	public static final String TAG_EMERGENCIA = "Botão de emergência";
	public static final String TAG_GOLPES = "Assistente contra golpes";
	public static final String TAG_ATENDIMENTOS = "Atendimento acessível";
	public static final String TAG_VOZ = "Voz";

	private static final String REF_ERRO = "#/components/schemas/ErroResponse";

	@Bean
	OpenAPI contratoVozViva() {
		return new OpenAPI()
			.info(new Info()
				.title("VozViva API")
				.version("v1")
				.description("""
						Microsserviço Java do ecossistema Fala Cidadão (CitizenTech): voz, socorro, rede de apoio, \
						assistente contra golpes e atendimento acessível para pessoas idosas e com deficiência.

						Para testar: faça `POST /api/v1/auth/login` (no perfil dev há os usuários \
						`cidadao.teste@vozviva.dev` e `cuidador.teste@vozviva.dev`), copie o `accessToken` da resposta e \
						clique em **Authorize**. Todo erro segue o formato `ErroResponse`, com `mensagemFalada` para \
						o app ler em voz alta.""")
				.contact(new Contact().name("Equipe VozViva - FIAP Global Solution 2026/2")
					.url("https://github.com/pedrinzz10/vivavoz"))
				.license(new License().name("Dados de atendimentos © colaboradores do OpenStreetMap (ODbL)")
					.url("https://www.openstreetmap.org/copyright")))
			.components(new Components()
				.addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("Token emitido por POST /api/v1/auth/login (HS256, claims sub, role e name). "
							+ "A API .NET valida o mesmo token.")))
			.addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT))
			.tags(List.of(
					new Tag().name(TAG_AUTENTICACAO).description("Cadastro, login e emissão do JWT"),
					new Tag().name(TAG_VOZ).description("Classificação da fala transcrita (Spring AI + RAG)"),
					new Tag().name(TAG_EMERGENCIA).description("Socorro assíncrono pelo RabbitMQ"),
					new Tag().name(TAG_PERFIL).description("Perfil de emergência mantido pelo cuidador"),
					new Tag().name(TAG_CONTATOS).description("Quem é avisado quando o cidadão pede socorro"),
					new Tag().name(TAG_GOLPES).description("\"Isso é golpe?\" (Spring AI + RAG + regras)"),
					new Tag().name(TAG_ATENDIMENTOS).description("Atendimento mais próximo, com acessibilidade e rota")));
	}

	/** Acrescenta a cada operação as respostas de erro que ela pode devolver, no formato da SPEC-014. */
	@Bean
	OpenApiCustomizer respostasDeErroPadrao() {
		return openApi -> {
			Map<String, Schema> esquemas = ModelConverters.getInstance().readAll(ErroResponse.class);
			esquemas.forEach(openApi.getComponents()::addSchemas);
			openApi.getPaths().forEach((caminho, item) -> item.readOperationsMap()
				.forEach((metodo, operacao) -> acrescentarErros(caminho, operacao)));
		};
	}

	private static void acrescentarErros(String caminho, Operation operacao) {
		ApiResponses respostas = operacao.getResponses() == null ? new ApiResponses() : operacao.getResponses();
		boolean publica = operacao.getSecurity() != null && operacao.getSecurity().isEmpty();
		boolean temEntrada = operacao.getRequestBody() != null
				|| (operacao.getParameters() != null && !operacao.getParameters().isEmpty());

		// Erros declarados no controller (ex.: 409 onde há conflito de verdade) ganham o formato padrão.
		respostas.forEach((status, resposta) -> {
			if (status.startsWith("4") || status.startsWith("5")) {
				CodigoErro codigo = CodigoErro.porStatus(Integer.parseInt(status));
				respostas.addApiResponse(status, resposta(status, codigo, caminho).description(resposta.getDescription()));
			}
		});

		Map<String, CodigoErro> erros = new LinkedHashMap<>();
		if (temEntrada) {
			erros.put("400", CodigoErro.VALIDACAO);
		}
		if (!publica) {
			erros.put("401", CodigoErro.NAO_AUTENTICADO);
			erros.put("403", CodigoErro.ACESSO_NEGADO);
		}
		else if (caminho.endsWith("/login")) {
			erros.put("401", CodigoErro.CREDENCIAIS_INVALIDAS);
			erros.put("429", CodigoErro.MUITAS_TENTATIVAS);
		}
		if (caminho.contains("{")) {
			erros.put("404", CodigoErro.RECURSO_NAO_ENCONTRADO);
		}
		erros.put("500", CodigoErro.ERRO_INTERNO);

		erros.forEach((status, codigo) -> {
			if (!respostas.containsKey(status)) {
				respostas.addApiResponse(status, resposta(status, codigo, caminho));
			}
		});
		operacao.setResponses(respostas);
	}

	private static ApiResponse resposta(String status, CodigoErro codigo, String caminho) {
		Map<String, Object> exemplo = new LinkedHashMap<>();
		exemplo.put("type", "urn:vozviva:erro:" + codigo.name().toLowerCase(Locale.ROOT));
		exemplo.put("title", codigo.titulo());
		exemplo.put("status", Integer.parseInt(status));
		exemplo.put("detail", codigo.titulo() + ".");
		exemplo.put("instance", caminho);
		exemplo.put("codigo", codigo.name());
		exemplo.put("mensagemFalada", codigo.mensagemFalada());
		exemplo.put("timestamp", "2026-11-19T14:30:00Z");
		exemplo.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
		if (codigo == CodigoErro.VALIDACAO) {
			exemplo.put("campos", List.of(Map.of("campo", "texto", "mensagem", "não deve estar em branco")));
		}
		return new ApiResponse()
			.description(codigo.titulo())
			.content(new Content().addMediaType("application/problem+json", new MediaType()
				.schema(new Schema<ErroResponse>().$ref(REF_ERRO))
				.examples(Map.of(codigo.name(), new Example().value(exemplo)))));
	}

}
