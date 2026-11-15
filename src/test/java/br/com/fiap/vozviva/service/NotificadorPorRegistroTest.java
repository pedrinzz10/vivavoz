package br.com.fiap.vozviva.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NotificadorPorRegistroTest {

	@ParameterizedTest
	@CsvSource({
			"+5511912345678, +5511*****5678",
			"+551134567890,  +5511****7890",
			"1234,           ****"
	})
	void mascaraOTelefoneNoLog(String telefone, String esperado) {
		assertThat(NotificadorPorRegistro.mascarar(telefone)).isEqualTo(esperado);
	}

}
