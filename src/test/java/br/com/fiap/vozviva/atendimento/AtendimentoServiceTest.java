package br.com.fiap.vozviva.atendimento;

import br.com.fiap.vozviva.atendimento.dto.AtendimentoProximoResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AtendimentoServiceTest {

	@ParameterizedTest(name = "{0} m = {1}")
	@CsvSource({ "3, 10 metros", "347, 350 metros", "999, 1 km", "1000, 1 km", "1234, '1,2 km'", "15000, 15 km" })
	void distanciaFalada(long metros, String esperado) {
		assertThat(AtendimentoService.distanciaFalada(metros)).isEqualTo(esperado);
	}

	@Test
	void fraseDoHospitalAcessivelComOrientacaoDo192() {
		String frase = AtendimentoService.mensagemFalada(TipoAtendimento.HOSPITAL, List.of(item(1200, Acessibilidade.SIM)),
				true, false, 5000);

		assertThat(frase).isEqualTo("O hospital mais perto com acessibilidade fica a 1,2 km. Se for urgência, ligue 192. "
				+ "Quer que eu abra o caminho?");
	}

	@Test
	void fraseDeAcessibilidadeParcial() {
		assertThat(AtendimentoService.mensagemFalada(TipoAtendimento.FARMACIA, List.of(item(400, Acessibilidade.PARCIAL)),
				true, false, 5000))
			.isEqualTo("A farmácia mais perto com acessibilidade parcial fica a 400 metros. Quer que eu abra o caminho?");
	}

	@ParameterizedTest
	@EnumSource(TipoAtendimento.class)
	void fraseCurtaParaQualquerTipo(TipoAtendimento tipo) {
		String frase = AtendimentoService.mensagemFalada(tipo, List.of(item(19999, Acessibilidade.DESCONHECIDA)),
				true, true, 20000);

		assertThat(frase).hasSizeLessThanOrEqualTo(200).contains(tipo.nomeFalado());
	}

	@Test
	void desconhecidaNuncaApareceComoAcessivel() {
		assertThat(Acessibilidade.DESCONHECIDA.confirmada()).isFalse();
		assertThat(AtendimentoService.aviso(Acessibilidade.DESCONHECIDA)).contains("não informada");
		assertThat(AtendimentoService.aviso(Acessibilidade.SIM)).isNull();
	}

	private static AtendimentoProximoResponse item(long distancia, Acessibilidade acessibilidade) {
		return new AtendimentoProximoResponse(1L, "Local", TipoAtendimento.UBS, null, null, 0, 0, distancia,
				acessibilidade, null, null, null, null);
	}

}
