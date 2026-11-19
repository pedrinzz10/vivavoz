package br.com.fiap.vozviva.service;

import br.com.fiap.vozviva.repository.InteracaoVozRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetencaoHistoricoVozTest {

	@Test
	void apagaOQuePassouDoPrazo() {
		InteracaoVozRepository interacoes = mock(InteracaoVozRepository.class);
		Instant agora = Instant.parse("2026-11-19T03:30:00Z");
		when(interacoes.apagarAnterioresA(Instant.parse("2026-08-21T03:30:00Z"))).thenReturn(4);

		int apagadas = new RetencaoHistoricoVoz(interacoes, 90, Clock.fixed(agora, ZoneOffset.UTC)).apagarVencidas();

		assertThat(apagadas).isEqualTo(4);
		verify(interacoes).apagarAnterioresA(Instant.parse("2026-08-21T03:30:00Z"));
	}

}
