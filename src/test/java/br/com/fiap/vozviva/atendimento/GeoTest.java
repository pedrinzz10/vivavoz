package br.com.fiap.vozviva.atendimento;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoTest {

	/** Referências calculadas no elipsoide WGS84 (fórmula de Vincenty), independente do código testado. */
	@ParameterizedTest(name = "{0}")
	@CsvSource({
			"estação Osasco -> hospital,        -23.526480, -46.776650, -23.535600, -46.781000, 1103.4",
			"estação Osasco -> UPA Centro,      -23.526480, -46.776650, -23.532900, -46.791600, 1684.1",
			"UPA Centro -> Shopping União,      -23.532900, -46.791600, -23.539000, -46.772500, 2064.1",
			"estação Osasco -> Praça da Sé,     -23.526480, -46.776650, -23.550520, -46.633308, 14877.0" })
	void haversineComErroMenorQueUmPorCento(String trecho, double lat1, double lon1, double lat2, double lon2,
			double esperadoMetros) {
		double calculado = Geo.distanciaMetros(lat1, lon1, lat2, lon2);

		assertThat(calculado).isCloseTo(esperadoMetros, within(esperadoMetros * 0.01));
		assertThat(Geo.distanciaMetros(lat2, lon2, lat1, lon1)).isCloseTo(calculado, within(1e-6));
	}

	@Test
	void mesmoPontoEstaAZeroMetros() {
		assertThat(Geo.distanciaMetros(-23.5, -46.7, -23.5, -46.7)).isZero();
	}

	@Test
	void areaContemOCirculoDoRaio() {
		Geo.Area area = Geo.areaAoRedor(-23.53, -46.78, 5000);

		// 5 km para o norte, sul, leste e oeste ficam dentro do retângulo.
		assertThat(Geo.distanciaMetros(-23.53, -46.78, area.latitudeMaxima(), -46.78)).isCloseTo(5000, within(5.0));
		assertThat(Geo.distanciaMetros(-23.53, -46.78, -23.53, area.longitudeMaxima())).isCloseTo(5000, within(5.0));
		assertThat(area.latitudeMinima()).isLessThan(-23.53);
		assertThat(area.longitudeMinima()).isLessThan(-46.78);
	}

	@Test
	void areaPertoDoPoloNaoPassaDosLimites() {
		Geo.Area area = Geo.areaAoRedor(89.99, 179.9, 20000);

		assertThat(area.latitudeMaxima()).isLessThanOrEqualTo(90);
		assertThat(area.longitudeMaxima()).isLessThanOrEqualTo(180);
		assertThat(area.longitudeMinima()).isGreaterThanOrEqualTo(-180);
	}

}
