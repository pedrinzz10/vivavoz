package br.com.fiap.vozviva.util;

import br.com.fiap.vozviva.dto.AtendimentoProximoResponse.Rotas;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LinksRotaTest {

	@Test
	void linksComAsCoordenadasDoDestino() {
		Rotas rotas = LinksRota.para(-23.5356, -46.781);

		assertThat(rotas.googleMaps()).isEqualTo("https://www.google.com/maps/dir/?api=1&destination=-23.535600,-46.781000");
		assertThat(rotas.waze()).isEqualTo("https://waze.com/ul?ll=-23.535600,-46.781000&navigate=yes");
		assertThat(rotas.geo()).isEqualTo("geo:-23.535600,-46.781000");
	}

	@Test
	void pontoDecimalMesmoComOServidorEmPortugues() {
		Locale anterior = Locale.getDefault();
		try {
			Locale.setDefault(Locale.of("pt", "BR"));

			assertThat(LinksRota.para(-23.5, -46.75).geo()).isEqualTo("geo:-23.500000,-46.750000");
		}
		finally {
			Locale.setDefault(anterior);
		}
	}

}
