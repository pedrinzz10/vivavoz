package br.com.fiap.vozviva.comum.erro;

import br.com.fiap.vozviva.comum.rastreio.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-014: formato padronizado de erros.
 */
@WebMvcTest(ErroTesteController.class)
@Import(ProblemDetailFactory.class)
@ActiveProfiles("test")
@WithMockUser
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void formato400ListaCamposInvalidos() throws Exception {
		mvc.perform(post("/teste-erros/validacao").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\": \"\", \"idade\": -1}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.codigo").value("VALIDACAO"))
			.andExpect(jsonPath("$.type").value("urn:vozviva:erro:validacao"))
			.andExpect(jsonPath("$.instance").value("/teste-erros/validacao"))
			.andExpect(jsonPath("$.mensagemFalada").value(CodigoErro.VALIDACAO.mensagemFalada()))
			.andExpect(jsonPath("$.timestamp").isNotEmpty())
			.andExpect(jsonPath("$.traceId").isNotEmpty())
			.andExpect(jsonPath("$.campos", hasSize(2)))
			.andExpect(jsonPath("$.campos[*].campo", containsInAnyOrder("nome", "idade")))
			.andExpect(jsonPath("$.campos[0].mensagem", not(emptyOrNullString())));
	}

	@Test
	void parametroInvalidoListaOParametro() throws Exception {
		mvc.perform(get("/teste-erros/parametro").param("lat", "200"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.codigo").value("VALIDACAO"))
			.andExpect(jsonPath("$.campos[0].campo").value("lat"));
	}

	@Test
	void jsonMalformadoRetornaRequisicaoInvalida() throws Exception {
		mvc.perform(post("/teste-erros/validacao").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\": "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"))
			.andExpect(jsonPath("$.mensagemFalada").isNotEmpty());
	}

	@Test
	void formato404DeDominio() throws Exception {
		mvc.perform(get("/teste-erros/nao-encontrado"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"))
			.andExpect(jsonPath("$.title").value("Recurso não encontrado"))
			.andExpect(jsonPath("$.detail").value("Obstrução 123 não encontrada."));
	}

	@Test
	void rotaInexistenteRetorna404Padronizado() throws Exception {
		mvc.perform(get("/rota-que-nao-existe"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"))
			.andExpect(jsonPath("$.mensagemFalada").isNotEmpty());
	}

	@Test
	void metodoNaoSuportadoRetorna405Padronizado() throws Exception {
		mvc.perform(delete("/teste-erros/nao-encontrado").with(csrf()))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.codigo").value("METODO_NAO_PERMITIDO"));
	}

	@Test
	void conflitoRetorna409() throws Exception {
		mvc.perform(get("/teste-erros/conflito"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.codigo").value("CONFLITO"));
	}

	@Test
	void servicoIndisponivelRetorna503() throws Exception {
		mvc.perform(get("/teste-erros/indisponivel"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.codigo").value("SERVICO_INDISPONIVEL"));
	}

	@Test
	void timeoutRetorna503SemExporDetalheInterno() throws Exception {
		MvcResult resultado = mvc.perform(get("/teste-erros/timeout"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.codigo").value("SERVICO_INDISPONIVEL"))
			.andReturn();

		assertThat(resultado.getResponse().getContentAsString()).doesNotContain("interno:5000");
	}

	@Test
	void formato500SemStackTraceNemDetalhesInternos() throws Exception {
		MvcResult resultado = mvc.perform(get("/teste-erros/inesperado"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
			.andExpect(jsonPath("$.mensagemFalada").value(CodigoErro.ERRO_INTERNO.mensagemFalada()))
			.andReturn();

		String corpo = resultado.getResponse().getContentAsString();
		assertThat(corpo)
			.doesNotContain("IllegalStateException")
			.doesNotContain("senha")
			.doesNotContain("br.com.fiap")
			.doesNotContain("trace\"")
			.doesNotContain("exception");
	}

	@Test
	void traceIdDaRespostaIgualAoDoHeaderEDoLog(CapturedOutput saida) throws Exception {
		MvcResult resultado = mvc.perform(get("/teste-erros/inesperado"))
			.andExpect(status().isInternalServerError())
			.andExpect(header().exists(TraceIdFilter.HEADER))
			.andReturn();

		String traceId = resultado.getResponse().getHeader(TraceIdFilter.HEADER);
		assertThat(resultado.getResponse().getContentAsString()).contains("\"traceId\":\"" + traceId + "\"");
		assertThat(saida.getOut()).contains("[" + traceId + "]").contains("Erro inesperado");
	}

	@Test
	void reaproveitaTraceIdValidoEnviadoPeloCliente() throws Exception {
		mvc.perform(get("/teste-erros/nao-encontrado").header(TraceIdFilter.HEADER, "app-1234-abcd"))
			.andExpect(header().string(TraceIdFilter.HEADER, "app-1234-abcd"))
			.andExpect(jsonPath("$.traceId").value("app-1234-abcd"));
	}

	@Test
	void ignoraTraceIdComFormatoInseguro() throws Exception {
		String malicioso = "x\r\nlinha falsa de log";
		MvcResult resultado = mvc.perform(get("/teste-erros/nao-encontrado").header(TraceIdFilter.HEADER, malicioso))
			.andReturn();

		assertThat(resultado.getResponse().getHeader(TraceIdFilter.HEADER))
			.isNotEqualTo(malicioso)
			.matches("[0-9a-f-]{36}");
	}

	@Test
	void acessoNegadoFicaComSpringSecurityENaoViraErro500() throws Exception {
		mvc.perform(get("/teste-erros/acesso-negado"))
			.andExpect(status().isForbidden());
	}

}
