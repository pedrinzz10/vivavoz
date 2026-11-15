package br.com.fiap.vozviva.atendimento.importacao;

import br.com.fiap.vozviva.atendimento.Acessibilidade;
import br.com.fiap.vozviva.atendimento.TipoAtendimento;
import br.com.fiap.vozviva.atendimento.importacao.ImportadorOsm.Ponto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportadorOsmTest {

	private static final String RESPOSTA = """
			{"osm3s": {"timestamp_osm_base": "2026-07-28T02:16:18Z"},
			 "elements": [
			  {"type": "node", "id": 1, "lat": -23.5356, "lon": -46.781,
			   "tags": {"name": "Hospital D'Ávila", "amenity": "hospital", "wheelchair": "yes"}},
			  {"type": "way", "id": 2, "center": {"lat": -23.5357, "lon": -46.7811},
			   "tags": {"name": "Hospital D'Ávila", "amenity": "hospital"}},
			  {"type": "node", "id": 3, "lat": -23.54, "lon": -46.79,
			   "tags": {"name": "Consultório particular", "amenity": "doctors"}},
			  {"type": "node", "id": 4, "lat": -23.55, "lon": -46.80, "tags": {"amenity": "pharmacy"}},
			  {"type": "node", "id": 5, "lat": -23.56, "lon": -46.77,
			   "tags": {"name": "Drogaria Central", "amenity": "pharmacy", "opening_hours": "24/7"}}
			 ]}""";

	@Test
	void converteClassificaERemoveODuplicadoFicandoComOMaisCompleto() {
		List<Ponto> pontos = ImportadorOsm.converter(JsonMapper.builder().build().readTree(RESPOSTA));

		assertThat(pontos).extracting(Ponto::osmId).containsExactly("node/1", "node/5");
		assertThat(pontos.getFirst().tipo()).isEqualTo(TipoAtendimento.HOSPITAL);
		assertThat(pontos.getFirst().acessibilidade()).isEqualTo(Acessibilidade.SIM);
		assertThat(pontos.get(1).funciona24h()).isTrue();
	}

	@Test
	void mesmoNomeLongeNaoEDuplicata() {
		Ponto a = ponto("node/1", -23.50);
		Ponto b = ponto("node/2", -23.60);

		assertThat(ImportadorOsm.removerDuplicatas(List.of(a, b))).hasSize(2);
	}

	@Test
	void sqlEscapaApostrofoUsaPontoDecimalENull() {
		List<Ponto> pontos = ImportadorOsm.converter(JsonMapper.builder().build().readTree(RESPOSTA));

		String sql = ImportadorOsm.gerarSql(pontos, Instant.parse("2026-07-28T02:16:18Z"));

		assertThat(sql)
			.startsWith("-- SPEC-007")
			.contains("colaboradores do OpenStreetMap", "DELETE FROM pontos_atendimento WHERE osm_id NOT IN (N'node/1', N'node/5');")
			.contains("MERGE INTO pontos_atendimento AS destino", "ON destino.osm_id = origem.osm_id")
			.contains("(N'node/1', N'Hospital D''Ávila', N'HOSPITAL', NULL, NULL, -23.535600, -46.781000, N'SIM', NULL, NULL, "
					+ "N'OpenStreetMap (ODbL)', '2026-07-28 02:16:18'),")
			.contains("N'Drogaria Central', N'FARMACIA', NULL, NULL, -23.560000, -46.770000, N'DESCONHECIDA', NULL, 1,")
			.endsWith("origem.atualizado_em);\n");
	}

	@Test
	void importacaoVaziaNaoGeraCargaQueApagariaTudo() {
		assertThatThrownBy(() -> ImportadorOsm.gerarSql(List.of(), Instant.now()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("não será substituída");
	}

	private static Ponto ponto(String osmId, double latitude) {
		return new Ponto(osmId, "UBS Centro", TipoAtendimento.UBS, null, null, latitude, -46.78,
				Acessibilidade.DESCONHECIDA, null, null);
	}

}
