package br.com.fiap.vozviva.controller;

import br.com.fiap.vozviva.security.TokenDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-007 pela API. Os locais de teste ficam em uma área sem dados reais (latitude e longitude 10), para não
 * se misturarem com a carga do OpenStreetMap aplicada pelo Flyway. 0,0045° de latitude ≈ 500 m.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AtendimentoIntegrationTest {

	private static final double LAT = 10.0;
	private static final double LON = 10.0;
	private static final double GRAU_POR_KM = 0.009;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void limpar() {
		jdbc.update("delete from pontos_atendimento where osm_id like 'teste/%'");
	}

	@Test
	void ordenacaoERaio() throws Exception {
		inserir("teste/1", "UBS a 3 km", "UBS", 3, "DESCONHECIDA");
		inserir("teste/2", "UBS a 300 m", "UBS", 0.3, "DESCONHECIDA");
		inserir("teste/3", "UBS a 8 km", "UBS", 8, "SIM");
		inserir("teste/4", "UBS a 1 km", "UBS", 1, "SIM");
		inserir("teste/5", "Farmácia a 100 m", "FARMACIA", 0.1, "SIM");

		buscar("tipo=UBS&limite=10")
			.andExpect(jsonPath("$.atendimentos[*].nome", contains("UBS a 300 m", "UBS a 1 km", "UBS a 3 km")))
			.andExpect(jsonPath("$.atendimentos[0].distanciaMetros").value(is(300)))
			.andExpect(jsonPath("$.creditoDados", containsString("OpenStreetMap")));
		buscar("tipo=UBS")
			.andExpect(jsonPath("$.atendimentos", hasSize(3)));
		buscar("tipo=UBS&limite=2&raioMetros=2000")
			.andExpect(jsonPath("$.atendimentos[*].nome", contains("UBS a 300 m", "UBS a 1 km")))
			.andExpect(jsonPath("$.mensagemFalada").value("A UBS mais perto fica a 300 metros. Quer que eu abra o caminho?"));
	}

	@Test
	void prioridadeAcessivel() throws Exception {
		inserir("teste/1", "Hospital sem informação", "HOSPITAL", 0.5, "DESCONHECIDA");
		inserir("teste/2", "Hospital com rampa", "HOSPITAL", 2, "SIM");
		inserir("teste/3", "Hospital sem acesso", "HOSPITAL", 0.3, "NAO");

		buscar("tipo=HOSPITAL&acessivel=true")
			.andExpect(jsonPath("$.atendimentos[*].nome", contains("Hospital com rampa")))
			.andExpect(jsonPath("$.semAcessivelNoRaio").value(false))
			.andExpect(jsonPath("$.atendimentos[0].aviso").value(nullValue()))
			.andExpect(jsonPath("$.observacao", containsString("rota")))
			.andExpect(jsonPath("$.mensagemFalada", containsString("O hospital mais perto com acessibilidade fica a 2 km.")));
		buscar("tipo=HOSPITAL")
			.andExpect(jsonPath("$.atendimentos[0].nome").value("Hospital sem acesso"))
			.andExpect(jsonPath("$.observacao").value(nullValue()));
	}

	@Test
	void semNenhumAcessivelDevolveOsDesconhecidosSinalizados() throws Exception {
		inserir("teste/1", "UBS sem informação", "UBS", 0.5, "DESCONHECIDA");
		inserir("teste/2", "UBS sem acesso", "UBS", 0.2, "NAO");

		buscar("tipo=UBS&acessivel=true")
			.andExpect(jsonPath("$.atendimentos[*].nome", contains("UBS sem informação")))
			.andExpect(jsonPath("$.semAcessivelNoRaio").value(true))
			.andExpect(jsonPath("$.atendimentos[0].acessibilidade").value("DESCONHECIDA"))
			.andExpect(jsonPath("$.atendimentos[0].aviso", containsString("Ligue antes")))
			.andExpect(jsonPath("$.mensagemFalada", containsString("ligue antes para confirmar")));
	}

	@Test
	void respostaTrazTelefoneELinksDeRota() throws Exception {
		inserir("teste/1", "Farmácia Central", "FARMACIA", 0.5, "SIM");

		buscar("tipo=FARMACIA")
			.andExpect(jsonPath("$.atendimentos[0].telefone").value("+55 11 3651-1900"))
			.andExpect(jsonPath("$.atendimentos[0].rotas.googleMaps",
					containsString("destination=10.004500,10.000000")))
			.andExpect(jsonPath("$.atendimentos[0].rotas.waze", containsString("ll=10.004500,10.000000&navigate=yes")))
			.andExpect(jsonPath("$.atendimentos[0].rotas.geo").value("geo:10.004500,10.000000"));
	}

	@Test
	void orientacaoEmergencia() throws Exception {
		for (String tipo : List.of("HOSPITAL", "UPA")) {
			buscar("tipo=" + tipo)
				.andExpect(jsonPath("$.telefoneEmergencia").value("192"))
				.andExpect(jsonPath("$.orientacaoEmergencia", containsString("192")))
				.andExpect(jsonPath("$.mensagemFalada", containsString("ligue 192")));
		}
		buscar("tipo=FARMACIA")
			.andExpect(jsonPath("$.telefoneEmergencia").value(nullValue()))
			.andExpect(jsonPath("$.orientacaoEmergencia").value(nullValue()));
	}

	@Test
	void nadaNoRaio() throws Exception {
		buscar("tipo=DELEGACIA&raioMetros=1000")
			.andExpect(jsonPath("$.atendimentos", empty()))
			.andExpect(jsonPath("$.mensagemFalada").value("Não encontrei delegacia a até 1 km de você."));
	}

	@Test
	void validacao400() throws Exception {
		for (String parametros : List.of("tipo=UBS&latitude=91&longitude=10", "tipo=UBS&latitude=10&longitude=-181",
				"tipo=PADARIA&latitude=10&longitude=10", "tipo=UBS&longitude=10",
				"tipo=UBS&latitude=10&longitude=10&limite=11", "tipo=UBS&latitude=10&longitude=10&raioMetros=50")) {
			mvc.perform(get("/api/v1/atendimentos/proximos?" + parametros)
					.header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("1").bearer()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensagemFalada").isNotEmpty());
		}
	}

	@Test
	void localizacaoNaoPersistida() throws Exception {
		Map<String, Integer> antes = linhasPorTabela();
		assertThat(antes).containsKeys("pontos_atendimento", "emergencias", "verificacoes_golpe");

		mvc.perform(get("/api/v1/atendimentos/proximos?tipo=HOSPITAL&latitude=-23.111111&longitude=-46.222222")
				.header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("1").bearer()))
			.andExpect(status().isOk());

		assertThat(linhasPorTabela()).isEqualTo(antes);
	}

	@Test
	void cargaDoOpenStreetMapEncontraHospitaisPertoDaEstacaoOsasco() throws Exception {
		mvc.perform(get("/api/v1/atendimentos/proximos?tipo=HOSPITAL&latitude=-23.52648&longitude=-46.77665")
				.header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cuidador("2").bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.atendimentos", not(empty())))
			.andExpect(jsonPath("$.atendimentos[*].distanciaMetros", everyItem(lessThanOrEqualTo(5000))))
			.andExpect(jsonPath("$.atendimentos[*].tipo", everyItem(is("HOSPITAL"))));
	}

	@Test
	void listaPaginadaEDetalhe() throws Exception {
		Long id = inserir("teste/1", "AAA UPA de teste", "UPA", 1, "PARCIAL");

		mvc.perform(get("/api/v1/atendimentos?tipo=UPA&size=50")
				.header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("1").bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.conteudo[0].nome").value("AAA UPA de teste"))
			.andExpect(jsonPath("$.conteudo[*].tipo", everyItem(is("UPA"))))
			.andExpect(jsonPath("$.totalElementos", greaterThan(1)));
		mvc.perform(get("/api/v1/atendimentos/" + id).header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("1").bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.acessibilidade").value("PARCIAL"))
			.andExpect(jsonPath("$.fonte").value("OpenStreetMap (ODbL)"))
			.andExpect(jsonPath("$.rotas.geo").value("geo:10.009000,10.000000"));
		mvc.perform(get("/api/v1/atendimentos/999999999").header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("1").bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/atendimentos?sort=osmId").header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("1").bearer()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void exigeLogin() throws Exception {
		mvc.perform(get("/api/v1/atendimentos/proximos?tipo=UBS&latitude=10&longitude=10"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions buscar(String parametros) throws Exception {
		return mvc.perform(get("/api/v1/atendimentos/proximos?latitude=" + LAT + "&longitude=" + LON + "&" + parametros)
				.header(HttpHeaders.AUTHORIZATION, TokenDeTeste.cidadao("1").bearer()))
			.andExpect(status().isOk());
	}

	/** Local ao norte do ponto de busca, a {@code km} quilômetros. */
	private Long inserir(String osmId, String nome, String tipo, double km, String acessibilidade) {
		jdbc.update("""
				insert into pontos_atendimento (osm_id, nome, tipo, endereco, telefone, latitude, longitude, acessibilidade,
				  banheiro_adaptado, funciona_24h, fonte, atualizado_em)
				values (?, ?, ?, 'Rua de Teste, 1', '+55 11 3651-1900', ?, ?, ?, null, null, 'OpenStreetMap (ODbL)', current_timestamp)""",
				osmId, nome, tipo, LAT + km * GRAU_POR_KM, LON, acessibilidade);
		return jdbc.queryForObject("select id from pontos_atendimento where osm_id = ?", Long.class, osmId);
	}

	private Map<String, Integer> linhasPorTabela() {
		Map<String, Integer> linhas = new LinkedHashMap<>();
		for (String tabela : jdbc.queryForList("""
				select table_name from INFORMATION_SCHEMA.TABLES
				where table_schema = (select table_schema from INFORMATION_SCHEMA.TABLES where lower(table_name) = 'pontos_atendimento')
				order by table_name""", String.class)) {
			linhas.put(tabela, jdbc.queryForObject("select count(*) from " + tabela, Integer.class));
		}
		return linhas;
	}

}
