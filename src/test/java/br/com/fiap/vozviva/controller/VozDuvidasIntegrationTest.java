package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.ai.AnaliseIaIntencao;
import br.com.fiap.vozviva.ai.AnaliseIaIntencao.RespostaIa;
import br.com.fiap.vozviva.ai.LimiteUsoIa;
import br.com.fiap.vozviva.ai.RespostaIaDuvida;
import br.com.fiap.vozviva.ai.TurnoAnterior;
import br.com.fiap.vozviva.model.EntidadesFala;
import br.com.fiap.vozviva.model.Intencao;
import br.com.fiap.vozviva.model.Papel;
import br.com.fiap.vozviva.model.Urgencia;
import br.com.fiap.vozviva.model.Usuario;
import br.com.fiap.vozviva.repository.UsuarioRepository;
import br.com.fiap.vozviva.security.TokenDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo das dúvidas pela API, com o modelo simulado: contexto da conversa, resposta à pergunta (com a base
 * ou aberta) e limite de uso da IA.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VozDuvidasIntegrationTest {

	private static final String INTENCOES = "/api/v1/voz/intencoes";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@MockitoBean
	private AnaliseIaIntencao ia;

	@MockitoBean
	private RespostaIaDuvida respostas;

	@MockitoBean
	private LimiteUsoIa limite;

	private Usuario cidadao;

	@BeforeEach
	void preparar() {
		cidadao = usuarios.save(new Usuario("Maria", "d-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x", Papel.CIDADAO));
		when(limite.consumir(anyLong())).thenReturn(true);
		when(ia.analisar(anyString(), anyList(), anyList())).thenReturn(Optional.of(duvida()));
	}

	@Test
	void segundaPerguntaRecebeAConversaEOServicoDaPrimeira() throws Exception {
		when(respostas.responder(anyString(), anyList(), any()))
			.thenReturn(Optional.of("Costuma levar algumas semanas. Acompanhe no Meu INSS ou ligue 135."));

		classificar("quero pedir minha aposentadoria").andExpect(status().isOk());
		classificar("e quanto tempo demora pra sair?")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.intencao").value("TIRAR_DUVIDA"))
			.andExpect(jsonPath("$.acao").value("MOSTRAR_ORIENTACAO"))
			.andExpect(jsonPath("$.mensagemFalada").value(containsString("Meu INSS")))
			.andExpect(jsonPath("$.orientacao.titulo").value("Pedir aposentadoria"))
			.andExpect(jsonPath("$.orientacao.telefone").value("135"))
			.andExpect(jsonPath("$.orientacao.link").value(containsString("gov.br")));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<TurnoAnterior>> conversa = ArgumentCaptor.forClass(List.class);
		verify(ia).analisar(eq("e quanto tempo demora pra sair?"), anyList(), conversa.capture());
		assertThat(conversa.getValue()).extracting(TurnoAnterior::fala).containsExactly("quero pedir minha aposentadoria");
	}

	@Test
	void duvidaForaDaBaseVemSoComOTextoSemLinkNemTelefone() throws Exception {
		when(respostas.responder(anyString(), anyList(), eq(null)))
			.thenReturn(Optional.of("Sim, idosos têm prioridade no atendimento. Confirme no gov.br."));

		classificar("qual a diferença entre UPA e UBS?")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.intencao").value("TIRAR_DUVIDA"))
			.andExpect(jsonPath("$.orientacao.titulo").value("Resposta do assistente"))
			.andExpect(jsonPath("$.orientacao.link").value(nullValue()))
			.andExpect(jsonPath("$.orientacao.telefone").value(nullValue()));
	}

	@Test
	void acimaDoLimiteRespondeSoComAsRegrasEOSocorroContinua() throws Exception {
		when(limite.consumir(anyLong())).thenReturn(false);

		classificar("caí e não consigo levantar")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.intencao").value("SOCORRO"))
			.andExpect(jsonPath("$.analisadoPor").value("REGRAS"));
		classificar("como faço a prova de vida?")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.intencao").value("TIRAR_DUVIDA"))
			.andExpect(jsonPath("$.orientacao.titulo").value("Prova de vida do INSS"));

		verify(ia, never()).analisar(anyString(), anyList(), anyList());
		verify(respostas, never()).responder(anyString(), anyList(), any());
	}

	private static RespostaIa duvida() {
		return new RespostaIa(Intencao.TIRAR_DUVIDA, Urgencia.BAIXA, EntidadesFala.NENHUMA, "Vou mostrar como resolver.", 0.9);
	}

	private ResultActions classificar(String texto) throws Exception {
		return mvc.perform(post(INTENCOES).header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao(cidadao.getId().toString()).bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"texto\": \"" + texto + "\"}"));
	}

}
