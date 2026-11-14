package br.com.fiap.vozviva.emergencia;

import br.com.fiap.vozviva.comum.erro.ConflitoException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmergenciaTest {

	private final Instant agora = Instant.parse("2026-11-14T15:00:00Z");

	@Test
	void nasceAcionada() {
		assertThat(nova().getStatus()).isEqualTo(StatusEmergencia.ACIONADA);
	}

	@Test
	void acionadaViraNotificadaEDepoisAtendida() {
		Emergencia emergencia = nova();

		emergencia.marcarNotificada(2, agora);
		emergencia.atender(agora.plusSeconds(60));

		assertThat(emergencia.getStatus()).isEqualTo(StatusEmergencia.ATENDIDA);
		assertThat(emergencia.getContatosNotificados()).isEqualTo(2);
		assertThat(emergencia.getEncerradaEm()).isEqualTo(agora.plusSeconds(60));
	}

	@Test
	void podeCancelarEnquantoAberta() {
		Emergencia emergencia = nova();
		emergencia.marcarNotificada(1, agora);

		emergencia.cancelar(agora);

		assertThat(emergencia.getStatus()).isEqualTo(StatusEmergencia.CANCELADA);
	}

	@Test
	void naoReabreNemEncerraDuasVezes() {
		Emergencia emergencia = nova();
		emergencia.atender(agora);

		assertThatThrownBy(() -> emergencia.cancelar(agora)).isInstanceOf(ConflitoException.class);
		assertThatThrownBy(() -> emergencia.atender(agora)).isInstanceOf(ConflitoException.class);
	}

	@Test
	void soNotificaUmaVez() {
		Emergencia emergencia = nova();
		emergencia.marcarNotificada(1, agora);

		assertThatThrownBy(() -> emergencia.marcarNotificada(1, agora)).isInstanceOf(ConflitoException.class);
	}

	private Emergencia nova() {
		return new Emergencia(null, null, null, null, agora);
	}

}
