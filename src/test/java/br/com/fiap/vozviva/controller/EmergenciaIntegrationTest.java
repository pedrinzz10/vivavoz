package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.model.Mobilidade;
import br.com.fiap.vozviva.model.Papel;
import br.com.fiap.vozviva.model.PerfilEmergencia;
import br.com.fiap.vozviva.model.Usuario;
import br.com.fiap.vozviva.repository.ContatoEmergenciaRepository;
import br.com.fiap.vozviva.repository.PerfilEmergenciaRepository;
import br.com.fiap.vozviva.repository.UsuarioRepository;
import br.com.fiap.vozviva.security.TokenDeTeste;
import br.com.fiap.vozviva.service.AvisosDeEmergencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-008: API do botão de emergência e regras de acesso. O consumidor é chamado diretamente aqui;
 * o caminho real pela fila é coberto por {@link EmergenciaRabbitIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmergenciaIntegrationTest {

	private static final String EMERGENCIAS = "/api/v1/emergencias";
	private static final String ACIONAMENTO = """
			{"latitude": -23.532900, "longitude": -46.791700, "textoOriginal": "caí e não consigo levantar"}""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PerfilEmergenciaRepository perfis;

	@Autowired
	private ContatoEmergenciaRepository contatos;

	@Autowired
	private AvisosDeEmergencia avisosDeEmergencia;

	private final JsonMapper json = JsonMapper.builder().build();

	private Usuario cidadao;
	private Usuario cuidador;

	@BeforeEach
	void criarPerfilComContatos() {
		cidadao = usuario("Maria", Papel.CIDADAO);
		cuidador = usuario("João", Papel.CUIDADOR);
		PerfilEmergencia perfil = new PerfilEmergencia(cidadao, cuidador);
		perfil.atualizar(Mobilidade.CADEIRANTE, null, null, null);
		perfis.save(perfil);
		adicionarContato(perfil, "Bruno (vizinho)", 2);
		adicionarContato(perfil, "Ana (filha)", 1);
	}

	@Test
	void acionarResponde202NaHoraComAEmergenciaAcionada() throws Exception {
		mvc.perform(como(cidadao, post(EMERGENCIAS)).content(ACIONAMENTO))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("ACIONADA"))
			.andExpect(jsonPath("$.latitude").value(-23.5329))
			.andExpect(jsonPath("$.textoOriginal").value("caí e não consigo levantar"))
			.andExpect(jsonPath("$.mensagemFalada").value(containsString("avisando seus contatos")));
	}

	@Test
	void consumidorAvisaOsContatosEmOrdemDePrioridade() throws Exception {
		long id = acionar();

		avisosDeEmergencia.avisarContatos(id);

		mvc.perform(como(cidadao, get(EMERGENCIAS + "/" + id)))
			.andExpect(jsonPath("$.status").value("NOTIFICADA"))
			.andExpect(jsonPath("$.contatosNotificados").value(2));
		mvc.perform(como(cuidador, get(EMERGENCIAS + "/" + id + "/avisos")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].contatoNome").value("Ana (filha)"))
			.andExpect(jsonPath("$[1].contatoNome").value("Bruno (vizinho)"))
			.andExpect(jsonPath("$[0].mensagem").value(containsString("Maria acionou o botão de emergência")))
			.andExpect(jsonPath("$[0].mensagem").value(containsString("https://www.google.com/maps?q=-23.532900,-46.791700")))
			.andExpect(jsonPath("$[0].mensagem").value(containsString("usa cadeira de rodas")));
	}

	@Test
	void mensagemRepetidaNaoAvisaEmDobro() throws Exception {
		long id = acionar();

		avisosDeEmergencia.avisarContatos(id);
		avisosDeEmergencia.avisarContatos(id);

		mvc.perform(como(cidadao, get(EMERGENCIAS + "/" + id + "/avisos"))).andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void toqueDuploDevolveAMesmaEmergencia() throws Exception {
		long primeira = acionar();
		long segunda = acionar();

		assertThat(segunda).isEqualTo(primeira);
	}

	@Test
	void semLocalizacaoTambemFunciona() throws Exception {
		mvc.perform(como(cidadao, post(EMERGENCIAS)).content("{}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.latitude").doesNotExist());
	}

	@Test
	void latitudeSemLongitudeOuForaDaFaixa400() throws Exception {
		mvc.perform(como(cidadao, post(EMERGENCIAS)).content("{\"latitude\": -23.5}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
		mvc.perform(como(cidadao, post(EMERGENCIAS)).content("{\"latitude\": 123, \"longitude\": 10}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.codigo").value("VALIDACAO"));
	}

	@Test
	void cidadaoSemPerfilRecebeOrientacao() throws Exception {
		Usuario semPerfil = usuario("Sem perfil", Papel.CIDADAO);

		mvc.perform(como(semPerfil, post(EMERGENCIAS)).content(ACIONAMENTO))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("Peça ao seu cuidador")));
	}

	@Test
	void cuidadorNaoAcionaPeloCidadao() throws Exception {
		mvc.perform(como(cuidador, post(EMERGENCIAS)).content(ACIONAMENTO)).andExpect(status().isForbidden());
	}

	@Test
	void cidadaoCancelaECuidadorNaoReabre() throws Exception {
		long id = acionar();

		mvc.perform(como(cidadao, patch(EMERGENCIAS + "/" + id + "/cancelar")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELADA"))
			.andExpect(jsonPath("$.encerradaEm").isNotEmpty());
		mvc.perform(como(cuidador, patch(EMERGENCIAS + "/" + id + "/atender")))
			.andExpect(status().isConflict());
	}

	@Test
	void cuidadorAtendeECidadaoNaoPodeAtender() throws Exception {
		long id = acionar();

		mvc.perform(como(cidadao, patch(EMERGENCIAS + "/" + id + "/atender"))).andExpect(status().isForbidden());
		mvc.perform(como(cuidador, patch(EMERGENCIAS + "/" + id + "/atender")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ATENDIDA"));
	}

	@Test
	void outrasPessoasNaoVeemNemAlteram() throws Exception {
		long id = acionar();
		Usuario outroCuidador = usuario("Pedro", Papel.CUIDADOR);
		Usuario outroCidadao = usuario("Vizinho", Papel.CIDADAO);

		mvc.perform(como(outroCuidador, get(EMERGENCIAS + "/" + id))).andExpect(status().isForbidden());
		mvc.perform(como(outroCuidador, patch(EMERGENCIAS + "/" + id + "/atender"))).andExpect(status().isForbidden());
		mvc.perform(como(outroCidadao, get(EMERGENCIAS + "/" + id + "/avisos"))).andExpect(status().isForbidden());
		mvc.perform(como(outroCidadao, patch(EMERGENCIAS + "/" + id + "/cancelar"))).andExpect(status().isForbidden());
	}

	@Test
	void historicoPaginadoParaCidadaoECuidador() throws Exception {
		long id = acionar();

		mvc.perform(como(cidadao, get(EMERGENCIAS)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElementos").value(1))
			.andExpect(jsonPath("$.conteudo[0].id").value(id));
		mvc.perform(como(cuidador, get(EMERGENCIAS)))
			.andExpect(jsonPath("$.conteudo[0].cidadaoNome").value("Maria"));
		mvc.perform(como(usuario("Pedro", Papel.CUIDADOR), get(EMERGENCIAS)))
			.andExpect(jsonPath("$.totalElementos").value(0));
		mvc.perform(como(cidadao, get(EMERGENCIAS).param("sort", "perfil.cidadao.senhaHash")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void perfilSemContatosFicaNotificadoComZero() throws Exception {
		Usuario sozinha = usuario("Lúcia", Papel.CIDADAO);
		PerfilEmergencia perfil = new PerfilEmergencia(sozinha, cuidador);
		perfil.atualizar(Mobilidade.NENHUMA, null, null, null);
		perfis.save(perfil);
		long id = id(mvc.perform(como(sozinha, post(EMERGENCIAS)).content(ACIONAMENTO)).andReturn());

		avisosDeEmergencia.avisarContatos(id);

		mvc.perform(como(sozinha, get(EMERGENCIAS + "/" + id)))
			.andExpect(jsonPath("$.status").value("NOTIFICADA"))
			.andExpect(jsonPath("$.contatosNotificados").value(0));
	}

	// --- Apoio --------------------------------------------------------------

	private long acionar() throws Exception {
		return id(mvc.perform(como(cidadao, post(EMERGENCIAS)).content(ACIONAMENTO))
			.andExpect(status().isAccepted())
			.andReturn());
	}

	private Usuario usuario(String nome, Papel papel) {
		return usuarios.save(new Usuario(nome, "u-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x", papel));
	}

	private void adicionarContato(PerfilEmergencia perfil, String nome, int prioridade) {
		var contato = new br.com.fiap.vozviva.model.ContatoEmergencia(perfil);
		contato.atualizar(nome, "+5511912345678", "família", prioridade);
		contatos.save(contato);
	}

	private static MockHttpServletRequestBuilder como(Usuario usuario, MockHttpServletRequestBuilder requisicao) {
		TokenDeTeste token = usuario.getPapel() == Papel.CUIDADOR
				? TokenDeTeste.cuidador(usuario.getId().toString())
				: TokenDeTeste.cidadao(usuario.getId().toString());
		return requisicao.header(HttpHeaders.AUTHORIZATION, token.bearer()).contentType(MediaType.APPLICATION_JSON);
	}

	private long id(MvcResult resultado) throws Exception {
		return json.readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
	}

}
