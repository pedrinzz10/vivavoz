package br.com.fiap.vozviva.usuario;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitadorTentativasLoginTest {

	private final RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-11-14T10:00:00Z"));
	private final LimitadorTentativasLogin limitador = new LimitadorTentativasLogin(relogio);

	@Test
	void permiteAteQuatroFalhasEBloqueiaNaQuinta() {
		falhar("a@x.com", LimitadorTentativasLogin.MAXIMO_FALHAS - 1);
		assertThatCode(() -> limitador.verificar("a@x.com")).doesNotThrowAnyException();

		falhar("a@x.com", 1);
		assertThatThrownBy(() -> limitador.verificar("a@x.com")).isInstanceOf(MuitasTentativasException.class);
	}

	@Test
	void bloqueioAcabaDepoisDoTempo() {
		falhar("b@x.com", LimitadorTentativasLogin.MAXIMO_FALHAS);

		relogio.avancar(LimitadorTentativasLogin.BLOQUEIO.plusSeconds(1));

		assertThatCode(() -> limitador.verificar("b@x.com")).doesNotThrowAnyException();
	}

	@Test
	void sucessoZeraAsFalhas() {
		falhar("c@x.com", LimitadorTentativasLogin.MAXIMO_FALHAS - 1);
		limitador.registrarSucesso("c@x.com");
		falhar("c@x.com", LimitadorTentativasLogin.MAXIMO_FALHAS - 1);

		assertThatCode(() -> limitador.verificar("c@x.com")).doesNotThrowAnyException();
	}

	@Test
	void falhasAntigasForaDaJanelaNaoContam() {
		falhar("d@x.com", LimitadorTentativasLogin.MAXIMO_FALHAS - 1);
		relogio.avancar(LimitadorTentativasLogin.JANELA.plusSeconds(1));
		falhar("d@x.com", 1);

		assertThatCode(() -> limitador.verificar("d@x.com")).doesNotThrowAnyException();
	}

	@Test
	void bloqueioDeUmEmailNaoAfetaOutro() {
		falhar("e@x.com", LimitadorTentativasLogin.MAXIMO_FALHAS);

		assertThatCode(() -> limitador.verificar("f@x.com")).doesNotThrowAnyException();
	}

	private void falhar(String email, int vezes) {
		for (int i = 0; i < vezes; i++) {
			limitador.registrarFalha(email);
		}
	}

	private static final class RelogioAjustavel extends Clock {

		private Instant agora;

		RelogioAjustavel(Instant agora) {
			this.agora = agora;
		}

		void avancar(Duration duracao) {
			agora = agora.plus(duracao);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return agora;
		}

	}

}
