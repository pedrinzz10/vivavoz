package br.com.fiap.vozviva.usuario;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitadorTentativasLoginTest {

	private static final int MAXIMO = LimitadorTentativasLogin.MAXIMO_TENTATIVAS;

	private final RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-11-14T10:00:00Z"));
	private final LimitadorTentativasLogin limitador = new LimitadorTentativasLogin(relogio);

	@Test
	void permiteCincoTentativasEBloqueiaASexta() {
		tentar("a@x.com", MAXIMO);

		assertThatThrownBy(() -> limitador.consumirTentativa("a@x.com")).isInstanceOf(MuitasTentativasException.class);
	}

	@Test
	void bloqueioAcabaDepoisDoTempo() {
		tentar("b@x.com", MAXIMO);

		relogio.avancar(LimitadorTentativasLogin.BLOQUEIO.plusSeconds(1));

		assertThatCode(() -> limitador.consumirTentativa("b@x.com")).doesNotThrowAnyException();
	}

	@Test
	void sucessoDevolveAsTentativas() {
		tentar("c@x.com", MAXIMO - 1);
		limitador.registrarSucesso("c@x.com");

		assertThatCode(() -> tentar("c@x.com", MAXIMO)).doesNotThrowAnyException();
	}

	@Test
	void sucessoNaUltimaTentativaDesfazOBloqueio() {
		tentar("d@x.com", MAXIMO);
		limitador.registrarSucesso("d@x.com");

		assertThatCode(() -> limitador.consumirTentativa("d@x.com")).doesNotThrowAnyException();
	}

	@Test
	void tentativasAntigasForaDaJanelaNaoContam() {
		tentar("e@x.com", MAXIMO - 1);
		relogio.avancar(LimitadorTentativasLogin.JANELA.plusSeconds(1));

		assertThatCode(() -> tentar("e@x.com", MAXIMO)).doesNotThrowAnyException();
	}

	@Test
	void bloqueioDeUmEmailNaoAfetaOutro() {
		tentar("f@x.com", MAXIMO);

		assertThatCode(() -> limitador.consumirTentativa("g@x.com")).doesNotThrowAnyException();
	}

	@Test
	void tentativasSimultaneasNaoPassamDoLimite() throws Exception {
		int simultaneas = 20;
		ExecutorService executor = Executors.newFixedThreadPool(simultaneas);
		CountDownLatch largada = new CountDownLatch(1);
		List<Callable<Boolean>> tarefas = new ArrayList<>();
		for (int i = 0; i < simultaneas; i++) {
			tarefas.add(() -> {
				largada.await();
				try {
					limitador.consumirTentativa("h@x.com");
					return true;
				}
				catch (MuitasTentativasException ex) {
					return false;
				}
			});
		}

		List<Future<Boolean>> resultados = new ArrayList<>();
		for (Callable<Boolean> tarefa : tarefas) {
			resultados.add(executor.submit(tarefa));
		}
		largada.countDown();
		int permitidas = 0;
		for (Future<Boolean> resultado : resultados) {
			permitidas += resultado.get() ? 1 : 0;
		}
		executor.shutdown();

		assertThat(permitidas).isEqualTo(MAXIMO);
	}

	private void tentar(String email, int vezes) {
		for (int i = 0; i < vezes; i++) {
			limitador.consumirTentativa(email);
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
