package br.com.fiap.vozviva.ai;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteUsoIaTest {

	private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-11-19T12:00:00Z"));

	private final Clock relogio = new Clock() {
		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return agora.get();
		}
	};

	@Test
	void permiteAteOLimiteENegaODepoisSemAfetarOutraPessoa() {
		LimiteUsoIa limite = new LimiteUsoIa(3, relogio);

		assertThat(limite.consumir(1L)).isTrue();
		assertThat(limite.consumir(1L)).isTrue();
		assertThat(limite.consumir(1L)).isTrue();
		assertThat(limite.consumir(1L)).isFalse();
		assertThat(limite.consumir(2L)).isTrue();
	}

	@Test
	void depoisDeUmaHoraOLimiteVolta() {
		LimiteUsoIa limite = new LimiteUsoIa(1, relogio);
		assertThat(limite.consumir(1L)).isTrue();
		assertThat(limite.consumir(1L)).isFalse();

		agora.set(agora.get().plus(Duration.ofHours(1)));

		assertThat(limite.consumir(1L)).isTrue();
	}

	@Test
	void semUsuarioNaoLimita() {
		LimiteUsoIa limite = new LimiteUsoIa(0, relogio);
		assertThat(limite.consumir(null)).isTrue();
	}

}
