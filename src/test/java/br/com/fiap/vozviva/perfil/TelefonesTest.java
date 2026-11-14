package br.com.fiap.vozviva.perfil;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TelefonesTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"(11) 91234-5678    | +5511912345678",
			"11 91234-5678      | +5511912345678",
			"11912345678        | +5511912345678",
			"+55 11 91234-5678  | +5511912345678",
			"5511912345678      | +5511912345678",
			"(11) 3456-7890     | +551134567890",
			"1134567890         | +551134567890"
	})
	void normalizaParaOPadraoInternacional(String digitado, String esperado) {
		assertThat(Telefones.normalizar(digitado.trim())).isEqualTo(esperado);
	}

}
