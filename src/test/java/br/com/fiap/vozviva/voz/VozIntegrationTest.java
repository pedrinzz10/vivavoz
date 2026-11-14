package br.com.fiap.vozviva.voz;

import br.com.fiap.vozviva.seguranca.Papel;
import br.com.fiap.vozviva.seguranca.TokenDeTeste;
import br.com.fiap.vozviva.usuario.Usuario;
import br.com.fiap.vozviva.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-003 pela API. No perfil de teste não há modelo de IA: a classificação vem das regras, o que também
 * prova que o contrato é mantido com a IA fora do ar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VozIntegrationTest {

	private static final String INTENCOES = "/api/v1/voz/intencoes";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario cidadao;

	@BeforeEach
	void criarUsuario() {
		cidadao = usuario(Papel.CIDADAO);
	}

	@Test
	void pedidoDeSocorroAbreOSocorroSemExecutarNada() throws Exception {
		classificar(cidadao, "{\"texto\": \"Caí e não consigo levantar\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.intencao").value("SOCORRO"))
			.andExpect(jsonPath("$.acao").value("ACIONAR_SOCORRO"))
			.andExpect(jsonPath("$.urgencia").value("CRITICA"))
			.andExpect(jsonPath("$.analisadoPor").value("REGRAS"))
			.andExpect(jsonPath("$.mensagemFalada", containsString("192")));

		assertThat(jdbc.queryForObject("select count(*) from emergencias e join perfis_emergencia p on p.id = e.perfil_id "
				+ "where p.cidadao_id = ?", Integer.class, cidadao.getId())).isZero();
	}

	@Test
	void buracoQueImpedeACadeiraVaiParaAZeladoria() throws Exception {
		classificar(cidadao, "{\"texto\": \"Tem um buraco gigante na minha calçada e não passo com a cadeira\"}")
			.andExpect(jsonPath("$.intencao").value("ZELADORIA_REPORT"))
			.andExpect(jsonPath("$.acao").value("ABRIR_ZELADORIA_DOTNET"))
			.andExpect(jsonPath("$.urgencia").value("ALTA"))
			.andExpect(jsonPath("$.entidades.problemaRua").value("BURACO"))
			.andExpect(jsonPath("$.entidades.impedeCadeirante").value(true));
	}

	@Test
	void buscaComLocalizacaoJaTrazOsAtendimentosMaisProximos() throws Exception {
		classificar(cidadao, """
				{"texto": "onde tem um hospital aqui perto", "localizacao": {"latitude": -23.52648, "longitude": -46.77665}}""")
			.andExpect(jsonPath("$.intencao").value("BUSCAR_ATENDIMENTO"))
			.andExpect(jsonPath("$.acao").value("MOSTRAR_ATENDIMENTOS"))
			.andExpect(jsonPath("$.entidades.tipoAtendimento").value("HOSPITAL"))
			.andExpect(jsonPath("$.atendimentos.atendimentos", not(empty())))
			.andExpect(jsonPath("$.atendimentos.telefoneEmergencia").value("192"))
			.andExpect(jsonPath("$.mensagemFalada", containsString("hospital mais perto")));
	}

	@Test
	void buscaSemLocalizacaoNaoTrazAtendimentos() throws Exception {
		classificar(cidadao, "{\"texto\": \"tem farmácia perto?\"}")
			.andExpect(jsonPath("$.acao").value("MOSTRAR_ATENDIMENTOS"))
			.andExpect(jsonPath("$.atendimentos").value(nullValue()));
	}

	@Test
	void falaAmbiguaPedeRepeticao() throws Exception {
		classificar(cidadao, "{\"texto\": \"é... aquele negócio lá\"}")
			.andExpect(jsonPath("$.intencao").value("DESCONHECIDA"))
			.andExpect(jsonPath("$.acao").value("PEDIR_REPETICAO"))
			.andExpect(jsonPath("$.mensagemFalada", containsString("pedir ajuda")));
	}

	@Test
	void validacaoEntrada400() throws Exception {
		for (String corpo : List.of("{\"texto\": \"\"}", "{\"texto\": \"" + "a".repeat(501) + "\"}", "{}",
				"{\"texto\": \"oi\", \"localizacao\": {\"latitude\": 91, \"longitude\": 0}}",
				"{\"texto\": \"oi\", \"localizacao\": {\"latitude\": 10}}",
				"{\"texto\": \"hello\", \"idioma\": \"en\"}")) {
			classificar(cidadao, corpo)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensagemFalada").isNotEmpty());
		}
	}

	@Test
	void historicoMostraSoAsInteracoesDoProprioUsuarioSemALocalizacao() throws Exception {
		Usuario outro = usuario(Papel.CUIDADOR);
		classificar(cidadao, "{\"texto\": \"tem um buraco na rua\"}");
		classificar(cidadao, """
				{"texto": "onde tem farmácia", "localizacao": {"latitude": -23.111111, "longitude": -46.222222}}""");
		classificar(outro, "{\"texto\": \"bom dia\"}");

		mvc.perform(get("/api/v1/voz/interacoes").header(HttpHeaders.AUTHORIZATION, token(cidadao)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.conteudo", hasSize(2)))
			.andExpect(jsonPath("$.conteudo[0].texto").value("onde tem farmácia"))
			.andExpect(jsonPath("$.conteudo[0].intencao").value("BUSCAR_ATENDIMENTO"))
			.andExpect(jsonPath("$.conteudo[0].analisadoPor").value("REGRAS"))
			.andExpect(jsonPath("$.conteudo[1].acao").value("ABRIR_ZELADORIA_DOTNET"));
		mvc.perform(get("/api/v1/voz/interacoes?sort=texto").header(HttpHeaders.AUTHORIZATION, token(cidadao)))
			.andExpect(status().isBadRequest());

		assertThat(jdbc.queryForList("select * from interacoes_voz").toString()).doesNotContain("23.111111", "46.222222");
	}

	@Test
	void exigeLogin() throws Exception {
		mvc.perform(post(INTENCOES).contentType(MediaType.APPLICATION_JSON).content("{\"texto\": \"oi\"}"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions classificar(Usuario usuario, String corpo) throws Exception {
		return mvc.perform(post(INTENCOES).header(HttpHeaders.AUTHORIZATION, token(usuario))
			.contentType(MediaType.APPLICATION_JSON)
			.content(corpo));
	}

	private static String token(Usuario usuario) {
		return usuario.getPapel() == Papel.CUIDADOR ? TokenDeTeste.cuidador(usuario.getId().toString()).bearer()
				: TokenDeTeste.cidadao(usuario.getId().toString()).bearer();
	}

	private Usuario usuario(Papel papel) {
		return usuarios.save(new Usuario("Maria", "v-" + UUID.randomUUID() + "@vozviva.dev", "{noop}x", papel));
	}

}
