package br.com.fiap.vozviva.atendimento.importacao;

import br.com.fiap.vozviva.atendimento.Acessibilidade;
import br.com.fiap.vozviva.atendimento.TipoAtendimento;
import br.com.fiap.vozviva.atendimento.importacao.ImportadorOsm.Ponto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refazer a carga mantém o id dos locais que continuam no mapa (apontamento da revisão do PR #17).
 * Roda o SQL gerado duas vezes dentro de uma transação desfeita no fim, sem afetar a carga real.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CargaPontosAtendimentoTest {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void recargaMantemOsIdsEAtualizaOsDados() {
		aplicar(List.of(ponto("node/1", "UBS Centro", Acessibilidade.DESCONHECIDA), ponto("node/2", "UBS Vila", Acessibilidade.SIM)));
		Long idAntes = id("node/1");

		aplicar(List.of(ponto("node/1", "UBS Centro (reformada)", Acessibilidade.SIM), ponto("node/3", "UBS Nova", Acessibilidade.NAO)));

		assertThat(id("node/1")).isEqualTo(idAntes);
		assertThat(jdbc.queryForObject("select nome from pontos_atendimento where osm_id = 'node/1'", String.class))
			.isEqualTo("UBS Centro (reformada)");
		assertThat(jdbc.queryForObject("select acessibilidade from pontos_atendimento where osm_id = 'node/1'", String.class))
			.isEqualTo("SIM");
		assertThat(jdbc.queryForList("select osm_id from pontos_atendimento order by osm_id", String.class))
			.containsExactly("node/1", "node/3");
	}

	private void aplicar(List<Ponto> pontos) {
		String sql = ImportadorOsm.gerarSql(pontos, Instant.parse("2026-07-28T02:16:18Z"));
		Connection conexao = DataSourceUtils.getConnection(dataSource);
		ScriptUtils.executeSqlScript(conexao, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
	}

	private Long id(String osmId) {
		return jdbc.queryForObject("select id from pontos_atendimento where osm_id = ?", Long.class, osmId);
	}

	private static Ponto ponto(String osmId, String nome, Acessibilidade acessibilidade) {
		return new Ponto(osmId, nome, TipoAtendimento.UBS, "Rua A, 1", null, -23.53, -46.78, acessibilidade, null, true);
	}

}
